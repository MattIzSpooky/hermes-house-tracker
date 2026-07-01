# Geospatial Radius Search — Design Spec

**Date:** 2026-06-17

## Goal

Enrich every scraped listing with a geographic location (latitude/longitude) and bounding box fetched from the OpenStreetMap Nominatim API. Store these as PostGIS geometry types. Add a `cities` reference table so the system can serve city-level centroid lookups without hitting Nominatim on every request. Expose radius-based search — "all listings within X km of Weert" or "… within X km of Rentmeesterlaan 9, Weert" — in both the paginated filter UI and the AI chat tool.

---

## Architecture Overview

Three new concerns are layered on top of the existing listing module:

1. **Async geocoding pipeline** — after a new listing is created, a `FetchGeocodingCommand` is enqueued on a new JMS queue. A rate-limited consumer calls Nominatim (max 1 req/sec per OSM terms) and writes a PostGIS `Point` and `Polygon` back to the listing row.
2. **City cache** — a `cities` table stores geocoded city centroids. `GeocodingService` checks this cache before hitting Nominatim; a miss results in a Nominatim call and a DB write.
3. **Radius search** — two query paths are added:
   - **Paginated filter (`GET /api/listings`)**: when `nearAddress`/`nearCity` + `radiusKm` are present, the existing Specification path is bypassed in favour of a native `ST_DWithin` query ordered by distance.
   - **AI chat tool (`searchListings`)**: three new optional tool parameters trigger an equivalent native query for chat results.

---

## Database

### PostGIS image

The `postgres:latest` Docker image is replaced with `postgis/postgis:16-3.4` in `docker-compose.yml`. The Testcontainers `PostgreSQLContainer` bean in test configuration uses the same image via `.asCompatibleSubstituteFor("postgres")`.

### Flyway V7 migration

```sql
CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE listings ADD COLUMN IF NOT EXISTS location     geometry(Point,   4326);
ALTER TABLE listings ADD COLUMN IF NOT EXISTS bounding_box geometry(Polygon, 4326);

CREATE INDEX IF NOT EXISTS idx_listings_location ON listings USING gist(location);

CREATE TABLE IF NOT EXISTS cities (
    id           UUID                     NOT NULL,
    name         VARCHAR(255)             NOT NULL,
    location     geometry(Point, 4326)    NOT NULL,
    bounding_box geometry(Polygon, 4326),
    fetched_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_cities_name UNIQUE (name)
);

CREATE INDEX IF NOT EXISTS idx_cities_location ON cities USING gist(location);
```

---

## Backend

### Dependency

Only `org.locationtech.jts:jts-core:1.20.0` is required. **No `hibernate-spatial` artifact is needed**: spatial support was merged into `hibernate-core` in Hibernate 6 and remains there in Hibernate 7 (the version this project uses). Adding `hibernate-spatial` explicitly would be redundant.

### Listing entity changes

Two new fields are added to `com.kropholler.dev.hermes.listing.data.Listing`:

```java
@Column(columnDefinition = "geometry(Point,4326)")
private Point location;

@Column(columnDefinition = "geometry(Polygon,4326)")
private Polygon boundingBox;
```

Both fields are added to `@BeanMapping(ignoreUnmappedSourceProperties = {...})` in `ListingMapper` so MapStruct does not complain about unmapped source properties.

### City entity

`com.kropholler.dev.hermes.listing.city.City` — JPA entity on the `cities` table. Fields: `id` (UUID), `name` (String, unique), `location` (Point), `boundingBox` (Polygon), `fetchedAt` (Instant).

`CityRepository` extends `JpaRepository<City, UUID>` and adds `Optional<City> findByNameIgnoreCase(String name)`.

### Nominatim integration

**`NominatimResponse`** — Jackson-annotated record mapping the Nominatim JSON response:

| Field | JSON key | Type |
|---|---|---|
| `lat` | `lat` | String |
| `lon` | `lon` | String |
| `boundingbox` | `boundingbox` | `List<String>` — `[latMin, latMax, lonMin, lonMax]` |
| `placeRank` | `place_rank` | int |
| `addressType` | `addresstype` | String |
| `displayName` | `display_name` | String |

**`NominatimClient`** — internal Spring component using `RestClient`. Base URL: `https://nominatim.openstreetmap.org`. Sends `User-Agent: HermesHouseTracker/1.0` on every request (required by OSM terms of use).

Two methods:
- `geocodeAddress(String houseNumber, String street, String city)` → calls `/search?q={houseNumber}+{street}+{city}&format=jsonv2&limit=1`, returns `Optional<NominatimResponse>`.
- `geocodeCity(String cityName)` → calls `/search?q={cityName}&format=jsonv2&countrycodes=nl&limit=5`, returns the first result with `addresstype` in `{municipality, city, town, administrative}`, or the first result overall if none match.

Both methods swallow exceptions and return `Optional.empty()` on failure.

### Async geocoding pipeline

**`FetchGeocodingCommand`** — serialisable JMS record: `UUID listingId`.

**`JmsQueues.GEOCODING_FETCH`** — new constant: `"geocoding.fetch"`.

**`ListingPersistenceService`** — on `ScrapingSessionCompleted`, for **new listings only** (not rescrapes), sends a `FetchGeocodingCommand` to `GEOCODING_FETCH` after existing commands.

**`GeocodingConsumer`** — `@JmsListener` on `GEOCODING_FETCH`:
- Acquires a Guava `RateLimiter` permit (rate: 1.0/sec) before every Nominatim call to comply with OSM usage policy.
- Loads the `Listing` by ID; skips if `street` or `city` is null.
- Calls `NominatimClient.geocodeAddress`. On success, converts `lat`/`lon` to a JTS `Point` (SRID 4326) and the `boundingbox` array to a JTS `Polygon` (5 coordinates, closed ring), then saves the listing.
- A listing without a geocoded `location` is excluded from radius search results — this is acceptable because geocoding happens asynchronously and may lag behind the initial scrape by seconds.

**Geometry construction** (shared between `GeocodingConsumer` and `GeocodingService`):

```java
GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

// Point: note lon is X, lat is Y in JTS/WGS-84
Point point = FACTORY.createPoint(new Coordinate(lon, lat));
point.setSRID(4326);

// Polygon from [latMin, latMax, lonMin, lonMax]:
Coordinate[] coords = {
    new Coordinate(lonMin, latMin),
    new Coordinate(lonMax, latMin),
    new Coordinate(lonMax, latMax),
    new Coordinate(lonMin, latMax),
    new Coordinate(lonMin, latMin)   // close the ring
};
Polygon poly = FACTORY.createPolygon(coords);
poly.setSRID(4326);
```

### GeocodingService (public)

`com.kropholler.dev.hermes.listing.geocoding.GeocodingService` — `@Service`, accessible by other modules.

| Method | Behaviour |
|---|---|
| `Optional<City> findOrFetchCity(String cityName)` | Check `CityRepository` first; on miss, call `NominatimClient.geocodeCity`, persist, return. |
| `Optional<double[]> geocodeAddress(String houseNumber, String street, String city)` | Delegate to `NominatimClient.geocodeAddress`, parse lat/lon, return `{lat, lon}`. No caching. |

### Radius search queries

Two new native queries in `ListingRepository`:

**`findNearby`** — paginated, used by `ListingService.findAll` when radius search is active:

```sql
SELECT l.* FROM listings l
WHERE l.deleted_at IS NULL
  AND l.location IS NOT NULL
  AND ST_DWithin(
      l.location::geography,
      ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
      :radiusMeters
  )
ORDER BY ST_Distance(
    l.location::geography,
    ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
) ASC
```

Paired with a `countQuery` for `Page<Listing>` support.

**`searchForChatNearLocation`** — non-paginated (limit 5), used by `ListingService.findForChat`:

Same `ST_DWithin` predicate combined with the existing `minBedrooms`, `minRooms`, `minLivingAreaM2`, `province`, `keywords`, `minPrice`, `maxPrice` filters. Results ordered by distance ascending.

### ListingService changes

`findAll(ListingSearchParams, Pageable)` — checks `params.hasRadiusSearch()` first. If true, resolves lat/lon via `GeocodingService` and delegates to `findNearby`; falls through to existing Specification path otherwise.

`findForChat(...)` — extended with three new parameters: `String nearAddress`, `String nearCity`, `Integer radiusKm`. If `radiusKm` is non-null and a location can be resolved, delegates to `searchForChatNearLocation`; otherwise uses the existing `searchForChat` path unchanged.

**Lat/lon resolution logic** (shared by both methods):
- If `nearAddress` is set: parse as `"houseNumber, street, city"` (comma-separated, up to 3 parts), call `GeocodingService.geocodeAddress`.
- If `nearCity` is set: call `GeocodingService.findOrFetchCity`, read `location.getY()` (lat) and `location.getX()` (lon).
- On failure (Nominatim unavailable, city unknown): fall back to non-radius search silently.

### ListingSearchParams

Three new record components are added:

| Component | Type | Purpose |
|---|---|---|
| `nearAddress` | `String` | Free-form `"houseNumber, street, city"` |
| `nearCity` | `String` | City name for centroid lookup |
| `radiusKm` | `Integer` | Radius in kilometres |

New helper: `boolean hasRadiusSearch()` — true when `radiusKm != null` and at least one of `nearAddress` or `nearCity` is non-blank.

### OpenAPI contract

Three optional query parameters added to `GET /api/listings`:

| Parameter | Type | Description |
|---|---|---|
| `nearAddress` | string (nullable) | Address to search near: `"houseNumber, street, city"` |
| `nearCity` | string (nullable) | City name to search near |
| `radiusKm` | integer (nullable) | Radius in km; requires `nearAddress` or `nearCity` |

`ListingController.getListings` signature gains these three params and passes them through to `ListingSearchParams`.

### AI chat tool changes

`ListingSearchTool.searchListings` gains three new `@ToolParam` parameters:

| Param | Type | LLM description |
|---|---|---|
| `nearAddress` | String | Address to search near, format: `"houseNumber, street, city"`. Use when user asks about listings near a specific address. |
| `nearCity` | String | City name to search near. Use when user asks about listings near a city. |
| `radiusKm` | Integer | Search radius in km. Required when `nearAddress` or `nearCity` is set. |

The `@Tool` description is updated to mention radius search capability. All three new params are forwarded to `ListingService.findForChat`.

---

## Frontend

### api.types.ts

`ListingSearchFilter` gains three optional fields:

```typescript
nearAddress?: string | null;
nearCity?: string | null;
radiusKm?: number | null;
```

### ListingsService

`loadListings` appends the three new fields to the HTTP params when non-empty/non-null.

### Listings page UI

A new "Radius search" section is added below the existing filter fields:

- **Near address** text input — placeholder: `"9, Rentmeesterlaan, Weert"`
- **Near city** text input — placeholder: `"Weert"`
- **Radius (km)** number input — min: 1, max: 100

All three fields are wired via `[(ngModel)]` and call `onFilterChange()` on change (same debounce as existing filters). `clearFilters()` resets all three fields. The `currentFilter` getter includes the three new values.

A short helper text below the section reads: "Fill in a city or address together with a radius to search nearby listings. Results are ordered by distance."

---

## Behaviour and constraints

| Scenario | Behaviour |
|---|---|
| `radiusKm` set but no `nearAddress`/`nearCity` | Treated as no radius search; existing filter path runs |
| `nearAddress`/`nearCity` set but no `radiusKm` | Treated as no radius search; existing filter path runs |
| Nominatim unavailable during filter search | Falls back to non-radius search; no error surfaced |
| Listing not yet geocoded | Excluded from radius results (`location IS NOT NULL` predicate) |
| `nearAddress` format has fewer than 3 comma parts | Missing parts treated as empty string; Nominatim may return no result |
| City not in Dutch territory | Nominatim `countrycodes=nl` filter limits city geocoding to the Netherlands |

---

## Error handling

- `NominatimClient` catches all exceptions and returns `Optional.empty()`, preventing geocoding failures from crashing the JMS consumer or the search path.
- `GeocodingConsumer` skips listings with null `street` or `city` silently.
- `GeocodingService.findOrFetchCity` returns `Optional.empty()` when Nominatim fails; `ListingService` falls through to non-radius search.

---

## Testing

### Backend

| Test | Type | Verifies |
|---|---|---|
| `NominatimClientTest` | `@RestClientTest` | Address and city geocoding with mocked HTTP responses; empty response returns `Optional.empty()` |
| `GeocodingConsumerTest` | Mockito unit | Successful geocoding writes `Point` and `Polygon` to listing; empty Nominatim response skips `save` |
| `GeocodingServiceTest` | Mockito unit | `findOrFetchCity` hits cache first, saves on miss, returns empty on Nominatim failure; `geocodeAddress` delegates and parses |
| `ListingServiceFindForChatTest` | Mockito unit | Updated to include `GeocodingService` mock; `findForChat` calls updated with three new null args |

### Frontend

No new component tests required for this feature — the radius inputs use the same debounce/filter pattern as existing inputs and are covered by the existing `loadListings` integration behaviour.