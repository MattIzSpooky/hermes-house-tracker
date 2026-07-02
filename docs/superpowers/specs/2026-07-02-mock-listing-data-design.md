# Mock listing data for testing

## Problem

Manually testing the scraping pipeline and frontend requires hitting the real
Funda API through `funda-proxy` repeatedly. This risks getting IP-blocked and
is slow. There's no way to get realistic listing data into the system without
going through Funda.

## Goal

Let a developer populate the app with realistic fake listing data, exercising
the real pipeline (search → scrape → persist → geocode), without any request
reaching Funda.

## Design

### 1. funda-proxy mock mode

`funda-proxy` gains a `FUNDA_MOCK_MODE` environment variable (default
`false`). When `true`, `client.py`'s `lifespan()` constructs a `MockFunda`
instead of the real `Funda` client from `pyfunda`, and logs a warning that
mock mode is active.

- `funda-proxy/mock_client.py` — `MockFunda` implements the subset of
  `Funda`'s interface that `main.py` calls: `search(...)`, `listing(id)`,
  `price_history(id)`, `close()`. It's built from `funda.listing.Listing` and
  friends (pyfunda's own dataclasses), so `ListingResponse.from_listing()`
  and `PriceChangeResponse.from_change()` work unchanged — no parallel mock
  response schema to maintain.
- `funda-proxy/mock_fixtures.py` — a small fixed list (~5) of fixture
  `Listing` objects using real Dutch street addresses (Amsterdam, Utrecht,
  Rotterdam, Eindhoven, Haarlem) so the existing geocoding pipeline
  (Nominatim — a separate, free service, not Funda) can resolve real
  coordinates for them. A couple of fixtures also carry canned
  `PriceHistory` entries.
- `MockFunda.listing()` / `.price_history()` raise `ListingNotFound` for
  unknown IDs, matching real `Funda` behavior, so error handling in
  `main.py` needs no changes.
- `MockFunda.search()` applies `min_price`/`max_price`/`min_area`/`max_area`
  filters against the fixture set for realism. Location text is not matched
  against city — mock mode is a proxy-wide toggle, not a per-request
  feature, so any search returns the (filtered) fixture set. Page > 0
  returns an empty list, mirroring pagination exhaustion.
- `docker-compose.yml`: `FUNDA_MOCK_MODE: ${FUNDA_MOCK_MODE:-false}` added to
  the `funda-proxy` service definition, off by default, overridable via a
  local `.env` file without editing the compose file.

### 2. Triggering a seed from the admin page

No new backend endpoint is needed. `POST /api/scraping-sessions` already
exists (`ScrapingSessionController` / `ScrapingQueueService`) and runs the
full real pipeline: search → queue → `ScrapingWorker` → persist → geocode.

The scraping admin page (`scraping-page.component.html`) gets a new "Seed
mock listings" button, placed next to the existing "Backfill missing
coordinates" button. It calls the existing `ScrapingService.createSession()`
with a fixed request (`{ city: 'Mock data', pageLimit: 1 }`) and reuses the
existing session-status/polling UI. Helper text clarifies this only returns
fixture data when `FUNDA_MOCK_MODE=true` is set on the `funda-proxy`
service — otherwise it performs a harmless real search for the literal city
"Mock data" that returns zero results.

### 3. Testing

- `funda-proxy/tests/test_mock_client.py` — unit tests for `MockFunda`:
  search returns/filters fixtures, `listing()` found/`ListingNotFound`,
  `price_history()` found/empty/`ListingNotFound`.
- `funda-proxy/tests/test_mock_mode.py` — integration test that sets
  `FUNDA_MOCK_MODE=true` before constructing the `TestClient` (so the real
  `lifespan()` picks `MockFunda`), then hits `/search`, `/listings/{id}`,
  and `/listings/{id}/price-history` for real against the fixtures, without
  mocking `main.get_client`.

No hermes-backend (Java) code changes are needed — it already treats
funda-proxy responses as the source of truth and has no knowledge of mock
mode.

## Out of scope

- A direct DB-insert bypass endpoint in hermes-backend (rejected during
  design — would require maintaining a second, duplicate fixture set in
  Java).
- Per-request / per-location mock data selection (mock mode is an
  all-or-nothing proxy-wide toggle).
- Automated E2E/frontend tests using the mock data (this spec only covers
  making the data available; wiring it into CI is a separate concern).
