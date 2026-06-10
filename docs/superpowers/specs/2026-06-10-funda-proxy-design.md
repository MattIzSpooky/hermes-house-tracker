# funda-proxy Design Spec

**Date:** 2026-06-10
**Status:** Approved

## Purpose

`funda-proxy` is a Python microservice that wraps [pyfunda](https://github.com/0xMH/pyfunda) and exposes its functionality as HTTP endpoints, allowing the Java `hermes-backend` (and any other non-Python service) to consume Funda's real-estate data without bundling a Python runtime or Playwright browser.

It replaces the existing Playwright-based `FundaScraperService` in `hermes-backend`.

## Scope

Only the subset of pyfunda required by the current Java backend:

- **Search** — find listings by location with optional price/area filters
- **Listing detail** — fetch a single listing by ID or URL

All other pyfunda methods (enrichment, broker, autocomplete, price history) are out of scope.

## Architecture

New top-level directory in the monorepo alongside `hermes-backend` and `hermes-frontend`:

```
funda-proxy/
  main.py          # FastAPI app, lifespan, and endpoint handlers
  models.py        # Pydantic response models mapped from pyfunda Listing
  client.py        # Singleton Funda client (created on startup, closed on shutdown)
  Dockerfile
  pyproject.toml
```

A single `Funda` client instance is created during the FastAPI lifespan startup event and shared across all requests. It is closed cleanly on shutdown. No connection pool is needed — pyfunda handles its own HTTP session internally.

## Endpoints

### `GET /search`

Returns a page of listings matching the given location and filters.

**Query parameters:**

| Parameter   | Type    | Required | Default | Notes                          |
|-------------|---------|----------|---------|--------------------------------|
| `location`  | string  | yes      | —       | City name, e.g. `amsterdam`    |
| `min_price` | integer | no       | —       |                                |
| `max_price` | integer | no       | —       |                                |
| `min_area`  | integer | no       | —       | Living area in m²              |
| `max_area`  | integer | no       | —       | Living area in m²              |
| `page`      | integer | no       | 0       | Zero-based page index          |

Calls `client.search(location, min_price=..., max_price=..., min_area=..., max_area=..., page=...)`.

**Responses:**
- `200` — `list[ListingResponse]`
- `422` — FastAPI validation error (missing `location`)
- `502` — pyfunda raised an exception; body: `{ "detail": "<message>" }`

### `GET /listings/{listing_id}`

Returns a single listing by global ID, tiny ID, or Funda URL.

**Path parameter:** `listing_id` — string (can be a numeric ID or full URL)

Calls `client.listing(listing_id)`.

**Responses:**
- `200` — `ListingResponse`
- `404` — listing not found (pyfunda returned `None` or raised a not-found error)
- `502` — any other pyfunda exception; body: `{ "detail": "<message>" }`

### `GET /health`

Returns `{ "status": "ok" }`. Used by Docker health checks.

## Response Model

`ListingResponse` is a flat Pydantic model. Fields are sourced from pyfunda's nested `Listing` dataclass. The `raw` dict is excluded.

```
ListingResponse:
  global_id:           int | None
  tiny_id:             str | None
  url:                 str | None
  street:              str | None
  house_number:        str | None
  house_number_suffix: str | None
  zip_code:            str | None
  city:                str | None
  province:            str | None
  asking_price:        int | None
  living_area_m2:      int | None
  rooms:               int | None
  bedrooms:            int | None
  energy_label:        str | None
  publication_date:    str | None   # ISO date string from pyfunda
  status:              str | None   # e.g. "beschikbaar", "verkocht"
  offering_type:       str | None   # "koop" or "huur"
```

Fields that pyfunda does not populate for a given listing are serialized as `null`.

## Error Handling

- pyfunda exceptions that indicate a network/API failure → `502`
- pyfunda returns `None` for a listing ID → `404`
- Invalid query parameters (non-integer price, etc.) → `422` (FastAPI default)

## Deployment

Added to root `docker-compose.yml` as a new service:

```yaml
funda-proxy:
  build: ./funda-proxy
  ports:
    - "8001:8000"
```

The Java backend calls `http://funda-proxy:8000` from within the Docker network (no port exposure needed for internal communication; the `8001` host port is for local development only).

The `Dockerfile` uses a slim Python image, installs pyfunda via pip, and runs `uvicorn main:app`.

## Out of Scope

- Authentication / API keys on the proxy itself (internal service, trusted network)
- Caching or rate limiting (pyfunda handles retries internally)
- Replacing the Playwright dependency in `hermes-backend` (that is a follow-up task once the proxy is running)
- Any pyfunda methods beyond `search` and `listing`
