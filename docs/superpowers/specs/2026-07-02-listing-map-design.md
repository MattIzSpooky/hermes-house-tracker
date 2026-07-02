# Listing Map View — Design Spec

**Date:** 2026-07-02

## Goal

Show listings visually on an OpenStreetMap-based map, in two places:

1. **Search results** — a "List / Map" toggle on the listings page. Map view plots the current page of results as pins + bounding-box rectangles.
2. **Listing detail** — a small map showing the single listing's pin and bounding box.

The backend already geocodes listings (PostGIS `location`/`bounding_box` columns, populated asynchronously via Nominatim for newly scraped listings), but never exposes those coordinates through the API. This spec adds a read-only, resilient path from that existing geo data to the frontend, plus a manual backfill for listings that predate the geocoding pipeline.

## Non-goals

- No map-driven search (e.g. "search this area" / drag-to-filter) — map view only *displays* the current filtered/paginated result set, it doesn't drive new queries.
- No change to how geocoding is triggered for new listings, or to the existing radius-search functionality (`ListingSpecifications.withinRadius`, `searchForChatNearLocation`) — both are left untouched.
- No marker clustering — page sizes are small (max 100), and clustering can be added later if needed.

---

## Architecture Overview

Three additive changes, no changes to existing writes or radius search:

1. **Backend read path** — a new batched, best-effort projection query derives `latitude`/`longitude`/bounding box from the existing PostGIS columns and merges them into `ListingDto` as a nested, nullable `GeoLocation`. Failures degrade gracefully (listing search/detail keep working with `location: null`).
2. **Backend backfill** — a manually-triggered endpoint queues geocoding for existing listings that predate the pipeline, reusing the existing rate-limited `GeocodingConsumer` unchanged.
3. **Frontend** — a reusable Leaflet-based `ListingMapComponent`, wired into a list/map toggle on the listings page, a small map on the listing detail page, and an admin trigger button on the scraping page.

---

## Backend

### Reading geo data (no schema change)

`ListingEntity` is not modified, and the `location`/`bounding_box` PostGIS geometry columns are not touched. A new projection interface and repository method derive plain values from them on read:

```java
public interface ListingGeoProjection {
    UUID getId();
    Double getLatitude();
    Double getLongitude();
    Double getBboxLatMin();
    Double getBboxLatMax();
    Double getBboxLonMin();
    Double getBboxLonMax();
}
```

```java
@Query(value = """
        SELECT
          l.id                              AS id,
          ST_Y(l.location::geometry)        AS latitude,
          ST_X(l.location::geometry)        AS longitude,
          ST_YMin(l.bounding_box::geometry)  AS bboxLatMin,
          ST_YMax(l.bounding_box::geometry)  AS bboxLatMax,
          ST_XMin(l.bounding_box::geometry)  AS bboxLonMin,
          ST_XMax(l.bounding_box::geometry)  AS bboxLonMax
        FROM listings l
        WHERE l.id IN (:ids) AND l.location IS NOT NULL
        """, nativeQuery = true)
List<ListingGeoProjection> findGeoByIds(@Param("ids") List<UUID> ids);
```

Listings with no `location` yet are simply absent from the result set.

### Merging into `ListingDto` — resilient, batched

`ListingService` already loads pages/lists of `ListingEntity` for every read path (`findAll`, `findForChat`, `findPriceDropListings`, `findById`, etc.). After loading a batch, it does **one** `findGeoByIds` call for the ids in that batch, wrapped so a failure never propagates:

```java
private Map<UUID, ListingGeoProjection> fetchGeoSafely(List<UUID> ids) {
    try {
        return listingRepository.findGeoByIds(ids).stream()
                .collect(Collectors.toMap(ListingGeoProjection::getId, p -> p));
    } catch (Exception e) {
        log.warn("Failed to load geo data for {} listing(s); continuing without map data", ids.size(), e);
        return Map.of();
    }
}
```

Each entity is then mapped to a DTO with `GeoLocation location = geoMap.containsKey(id) ? buildGeoLocation(...) : null`. A missing or failed lookup simply means `location` is `null` on the DTO — listing search and listing detail continue to work exactly as before.

### `GeoLocation` — nested DTO

```java
public record GeoLocation(
    double latitude,
    double longitude,
    Double bboxLatMin,
    Double bboxLatMax,
    Double bboxLonMin,
    Double bboxLonMax
) {}
```

`ListingDto` gains one new field: `GeoLocation location` (nullable). This is deliberately nested rather than flattened — a bounding box without its point is meaningless, so grouping makes "no location data" a single null check instead of six independently-nullable fields, and it matches how the frontend map component consumes the data.

`ListingMapper` (MapStruct) gains an extra `GeoLocation location` parameter alongside the existing `currentPrice` parameter, mapped straight onto the `ListingDto.location` component — same pattern already used for merging computed scalars into the entity-based mapping.

### API surface

`listing.yaml` gains a `GeoLocation` schema (nullable `latitude`/`longitude`, nullable bbox fields) referenced from both `ListingSummaryResponse.location` and `ListingDetailResponse.location`.

### Backfill for pre-existing listings

Geocoding is currently only queued for newly scraped listings (`ListingPersistenceService`). A new method (on `GeocodingService`, or a small new `ListingGeocodingBackfillService`) finds active listings with no coordinates yet and re-queues them onto the existing pipeline:

```sql
SELECT id FROM listings
WHERE deleted_at IS NULL AND location IS NULL
  AND street IS NOT NULL AND city IS NOT NULL
```

For each id found, send a `FetchGeocodingCommand` to the existing `JmsQueues.GEOCODING_FETCH` queue — reusing `GeocodingConsumer`'s existing 1 req/sec rate limiter unchanged. Returns the count queued.

New endpoint, added to `listing.yaml` and implemented in `ListingController`:

```
POST /api/listings/geocoding/backfill
→ 202 Accepted
  { "queuedCount": 42 }
```

---

## Frontend

### Dependencies

Add `leaflet` and `@types/leaflet` to `hermes-frontend`. Import Leaflet's CSS globally (`angular.json` styles array). Fix the standard Angular-bundler marker-icon path issue by explicitly re-pointing `L.Icon.Default`'s icon URLs at bundled asset paths.

### `ListingMapComponent` (new, `shared/listing-map.component.ts`)

A reusable, dumb display component:

```ts
export interface MapListing {
  id: string;
  street?: string;
  houseNumber?: string;
  city?: string;
  currentPrice?: number;
  location?: GeoLocation | null;
}
```

- `@Input() listings: MapListing[]`
- `@Output() listingSelected = new EventEmitter<string>()`

Behavior:
- Listings with `location == null` are skipped (not plotted).
- Each remaining listing renders one `L.marker` (point) and, when bbox fields are present, one `L.rectangle` (bounding box).
- Hover shows a lightweight Leaflet tooltip with address + price.
- Clicking a marker or its bounding-box rectangle emits `listingSelected` with the listing id.
- Whenever `listings` changes, the map auto-fits its bounds to cover all rendered points/rectangles.

### Listings page (list/map toggle)

`ListingsPageComponent` gains a `viewMode: 'list' | 'map'` property with a toggle control. Map view renders `ListingMapComponent` bound to the **current page** of results (consistent with existing pagination — no separate unpaginated map query). `(listingSelected)` navigates the same way the existing list-row click does (`navigate(id)`). If some listings on the page have no coordinates, a small notice shows ("X of Y shown on map").

### Listing detail page

If the listing's `location` is present, `ListingDetailPageComponent` renders `ListingMapComponent` with that single listing; the map fits to its bounding box (falling back to a fixed zoom around the point if bbox fields are missing but lat/lon are present). If `location` is null, a small "Location not yet available" message is shown instead.

### Admin backfill trigger

A new section on the existing `ScrapingPageComponent` (matching the existing admin-action pattern: form/button → service call → status feedback) with a button that calls the new backfill endpoint and displays "Queued N listings for geocoding."

### Types

`api.types.ts` gains:

```ts
export interface GeoLocation {
  latitude: number;
  longitude: number;
  bboxLatMin?: number | null;
  bboxLatMax?: number | null;
  bboxLonMin?: number | null;
  bboxLonMax?: number | null;
}
```

`ListingSummaryResponse` and `ListingDetailResponse` each gain `location?: GeoLocation | null`.

---

## Error Handling

- Backend geo lookup failure (any exception from `findGeoByIds`) is caught and logged; affected listings simply get `location: null` in the response. Listing search and detail endpoints never fail because of this.
- Geocoding failures during backfill (e.g. Nominatim can't resolve an address) behave exactly as they do today for new listings: logged and skipped: the listing stays without coordinates and just won't appear on the map.
- Frontend never assumes `location` is present; missing coordinates are a normal, expected state (either "not backfilled yet" or "geocoding failed"), not an error.

## Testing

**Backend:**
- `ListingService` test: `findGeoByIds` throwing does not propagate — DTOs come back with `location: null`.
- `ListingMapper` test: `GeoLocation` is mapped correctly, and is `null` when no projection exists for a given id.
- Backfill service test: only listings with `location IS NULL` (and non-null street/city) are queued; existing geocoded listings are untouched.
- No changes needed to existing radius-search tests — that code path is untouched.

**Frontend:**
- `ListingMapComponent`: unit tests for `@Input`/`@Output` wiring and the skip-if-no-location behavior.
- Actual Leaflet tile/DOM rendering and bounds-fitting is verified manually — standard for map widgets, not meaningfully unit-testable through Karma.
