# Listing Search, Detail Fix & Report Merge — Design Spec

## Goal

Three related improvements to the listings experience:
1. Server-side paginated search on the listings list page
2. Fix the listing detail page (currently blank due to a frontend/backend type mismatch)
3. Merge the report page into the detail page (remove the separate `/listings/:id/report` route)

---

## 1. Backend: Search Endpoint

### What changes

`GET /api/listings` gains five optional query parameters: `street`, `houseNumber`, `houseNumberAddition`, `zipCode`, `province`. All are case-insensitive partial matches. Results remain paginated.

### OpenAPI

Add these parameters to the `getListings` operation in `api.yaml`:

```yaml
- name: street
  in: query
  schema: { type: string }
- name: houseNumber
  in: query
  schema: { type: string }
- name: houseNumberAddition
  in: query
  schema: { type: string }
- name: zipCode
  in: query
  schema: { type: string }
- name: province
  in: query
  schema: { type: string }
```

### Implementation

A new `ListingSearchParams` record in the `listing` package carries the five optional fields (all `String`, all nullable). `ListingRepository` must also extend `JpaSpecificationExecutor<Listing>` (currently it only extends `JpaRepository`) to gain `findAll(Specification<Listing>, Pageable)`. A package-private `ListingSpecifications` class builds the `Specification` from `ListingSearchParams` using `like` predicates (`%value%`, case-insensitive via `lower()`). `ListingService.findAll` is overloaded to accept `ListingSearchParams`, using the spec path when params are present and the plain `findAll(pageable)` path when all are null. `ListingController.getListings` maps the new query params to the record.

**No new table columns or migration needed** — all search fields already exist on the `Listing` entity.

---

## 2. Frontend: Type Fixes

### `api.types.ts`

Three fixes:

- `ListingDetailResponse`: remove `latestSnapshot?: SnapshotResponse`, add `currentPrice?: number` and `status?: ListingStatus`. The backend has been returning these fields since the snapshot model was removed.
- `PricePointResponse`: rename fields from `{ scrapedAt, askingPrice }` to `{ timestamp: string, price?: number }` to match the backend's `PricePointResponse` schema.
- `ListingReportResponse`: remove `statusHistory` and `daysListedOnFunda` fields (backend no longer returns them). Keep `daysInHermes`, `currentPrice`, `initialPrice`, `priceChangePct`, `priceHistory`, `currentStatus`.
- `ListingPage`: no changes needed at the type level for search — the search params are query-string concerns, not response-type concerns.

The `SnapshotResponse` interface can be deleted since nothing will reference it.

---

## 3. Frontend: Search UI

### `listings.service.ts`

`loadListings(page, size, params?)` accepts an optional `ListingSearchParams` object (`{ street?, houseNumber?, houseNumberAddition?, zipCode?, province? }`). The method builds the query string by appending only non-empty params. Existing callers passing only `page`/`size` continue to work.

### `listings-page.component.ts`

Five separate `<input>` fields (street, house number, addition, zip code, province) added above the table in a responsive grid row. Each field is bound to a signal or plain property. A `Subject<void>` is piped through `debounceTime(300)` and `distinctUntilChanged` to trigger `svc.loadListings(0, pageSize, currentParams)` — resetting to page 0 on every filter change. The pagination controls remain unchanged. A "Clear" button resets all fields and reloads.

The debounce is implemented with a single `filterChange$` subject; all five inputs call `filterChange$.next()` on `(input)` event. The subscription is set up in `ngOnInit` and torn down in `ngOnDestroy`.

---

## 4. Frontend: Detail Page Fix & Report Merge

### What the current detail page shows (broken)

`listing.latestSnapshot` is always `undefined` because the backend stopped returning it. The entire "Latest snapshot" card is invisible, and the page shows only the address and the AI summary.

### Fix

Replace the "Latest snapshot" card with a simple two-column info grid using `listing.currentPrice` (via `euroPrice` pipe) and `listing.status` (via `app-status-badge`).

### Report merge

The detail page loads both `GET /api/listings/:id` and `GET /api/listings/:id/report` in parallel via `forkJoin`. On success both results are stored in component signals (`listing` and `report`). The existing `svc.loadListing` / `svc.loadReport` service methods are reused for this. The component moves from `OnInit` to calling both directly in `ngOnInit`.

The merged detail page layout:

1. Back link + address header (unchanged)
2. **Stats row** (4 cards): Days in Hermes · Price change % · Current price · Status — sourced from the report response
3. **Price history chart** (if `report.priceHistory.length > 0`) — Chart.js line chart, same configuration as the current report page
4. Two-column grid below: left = address details card; right = AI summary + rescrape (unchanged)

The `BaseChartDirective` import and `chartData`/`chartOptions` computed/property are copied from `listing-report-page.component.ts` verbatim, adjusted to read from `reportSignal()` instead of `svc.report()`.

The "View full report →" link is removed.

### Route and component deletion

- `listings/:id/report` route removed from `app.routes.ts`
- `hermes-frontend/src/app/pages/listing-report/` directory deleted
- `ListingReportPageComponent` deleted

The `svc.loadReport` method and `svc.report` signal in `ListingsService` are still used (by the detail page now), so they stay.

---

## Error handling

- Search: a backend error replaces the table with the existing error banner (no new treatment needed).
- Detail + report parallel load: if the report call fails (e.g. 404), the detail still renders — the stats row and chart are conditionally rendered only when `report()` is non-null. The detail 404 path is unchanged.

---

## Testing

- Backend: unit test `ListingSpecifications` with a mock `Root`/`CriteriaBuilder` for each predicate; integration test `ListingController` with `@WebMvcTest` exercising the search query params against a mocked `ListingService`.
- Frontend: the type fixes are compile-time verified. No new unit tests are strictly needed for the template changes, but the `listings.service.ts` query-string builder should be tested with a simple unit test covering empty/partial/full params.
