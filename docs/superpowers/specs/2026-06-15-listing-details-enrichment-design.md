# Listing Details Enrichment — Design Spec

**Date:** 2026-06-15
**Scope:** `funda-proxy/`, `hermes-backend/`, `hermes-frontend/`

## Goal

Persist and expose six additional listing fields (description, living area, plot area, rooms, bedrooms, energy label) fetched asynchronously from the funda-proxy detail endpoint. All fields are also searchable on the listings page. Rescraping a listing re-fetches and updates all detail fields.

## Architecture Overview

Follows the existing price-history pattern:

1. A scraping session completes → `ScrapingSessionCompleted` event fires
2. `ListingPersistenceService` saves/updates the listing row and enqueues a `FetchListingDetailsCommand` for **every** listing in the event (new and existing)
3. `ListingDetailsConsumer` (rate-limited) dequeues the command, calls `FundaProxyFacade.getListing()`, and writes the six enrichment fields onto the `Listing` row

This means "trigger rescrape" automatically re-enriches all detail fields at no extra code cost.

## 1. funda-proxy Changes

**File:** `funda-proxy/models.py`

Add two fields to `ListingResponse`:
```python
description: str | None = None
plot_area_m2: int | None = None
```

Update `from_listing()`:
```python
description=listing.description,
plot_area_m2=getattr(listing.areas, "plot", None),
```

**File:** `funda-proxy/tests/test_models.py`

Add assertions in `test_from_listing_maps_all_fields` for the two new fields (mock `listing.description` and `listing.areas.plot`).

## 2. Backend — Data Model

### 2a. Listing entity (`hermes-backend/.../listing/internal/Listing.java`)

Add six nullable fields:
```java
private String description;
private Integer livingAreaM2;
private Integer rooms;
private Integer bedrooms;
private String energyLabel;
private Integer plotAreaM2;
```

### 2b. Flyway migration

**New file:** `hermes-backend/src/main/resources/db/migration/V4__add_listing_details.sql`

```sql
ALTER TABLE listings ADD COLUMN description TEXT;
ALTER TABLE listings ADD COLUMN living_area_m2 INTEGER;
ALTER TABLE listings ADD COLUMN rooms INTEGER;
ALTER TABLE listings ADD COLUMN bedrooms INTEGER;
ALTER TABLE listings ADD COLUMN energy_label VARCHAR(10);
ALTER TABLE listings ADD COLUMN plot_area_m2 INTEGER;
```

### 2c. RawListing (`hermes-backend/.../scraping/RawListing.java`)

Add six fields:
```java
public record RawListing(
    String fundaId, String url,
    String street, String houseNumber, String houseNumberAddition,
    String zipCode, String city, String province,
    Integer askingPrice, String status,
    String description, Integer livingAreaM2, Integer rooms,
    Integer bedrooms, String energyLabel, Integer plotAreaM2
) {}
```

### 2d. FundaProxyClient (`hermes-backend/.../scraping/internal/FundaProxyClient.java`)

Update `toRawListing()` to map the six new fields from `FundaProxyListing`.

`FundaProxyListing` already has `livingAreaM2`, `rooms`, `bedrooms`, `energyLabel`. Add `description` and `plotAreaM2` to `FundaProxyListing`:
```java
@JsonProperty("description")  String description,
@JsonProperty("plot_area_m2") Integer plotAreaM2,
```

Update `toRawListing()`:
```java
private RawListing toRawListing(FundaProxyListing p) {
    return new RawListing(
        p.globalId() != null ? p.globalId().toString() : p.tinyId(),
        p.url(), p.street(), p.houseNumber(), p.houseNumberSuffix(),
        p.zipCode(), p.city(), p.province(), p.askingPrice(), p.status(),
        p.description(), p.livingAreaM2(), p.rooms(), p.bedrooms(),
        p.energyLabel(), p.plotAreaM2()
    );
}
```

### 2e. FundaProxyFacade (`hermes-backend/.../scraping/FundaProxyFacade.java`)

Add a `getListing(String fundaId)` method that delegates to `FundaProxyClient.getListing()` and returns `Optional<RawListing>`.

## 3. Backend — Async Detail Fetch

### 3a. JMS queue constant (`hermes-backend/.../listing/internal/JmsQueues.java`)

```java
public static final String LISTING_DETAILS_FETCH = "listing.details.fetch";
```

### 3b. Command record

**New file:** `hermes-backend/.../listing/internal/FetchListingDetailsCommand.java`

```java
public record FetchListingDetailsCommand(UUID listingId, String fundaId) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
}
```

### 3c. ListingPersistenceService

In `onScrapingSessionCompleted()`, after saving each listing, send a `FetchListingDetailsCommand` unconditionally (for both new and existing listings). Keep the existing `if (isNew)` guard around the price history send — price history is still only fetched for new listings. The result:

```java
// Always enqueue detail fetch (runs on both initial scrape and rescrape)
jmsTemplate.convertAndSend(
    JmsQueues.LISTING_DETAILS_FETCH,
    new FetchListingDetailsCommand(saved.getId(), saved.getFundaId())
);

if (isNew) {
    jmsTemplate.convertAndSend(JmsQueues.PRICE_HISTORY_FETCH,
        new FetchPriceHistoryCommand(saved.getId(), saved.getFundaId()));
}
```

### 3d. ListingDetailsConsumer

**New file:** `hermes-backend/.../listing/internal/ListingDetailsConsumer.java`

```java
@Slf4j
@Component
@RequiredArgsConstructor
class ListingDetailsConsumer {
    private static final RateLimiter RATE_LIMITER = RateLimiter.create(50.0 / 60.0);

    private final ListingRepository listingRepository;
    private final FundaProxyFacade proxyFacade;

    @JmsListener(destination = JmsQueues.LISTING_DETAILS_FETCH)
    @Transactional
    public void onMessage(FetchListingDetailsCommand command) {
        RATE_LIMITER.acquire();
        log.debug("Fetching listing details for {}", command.listingId());
        proxyFacade.getListing(command.fundaId()).ifPresent(raw ->
            listingRepository.findById(command.listingId()).ifPresent(listing -> {
                listing.setDescription(raw.description());
                listing.setLivingAreaM2(raw.livingAreaM2());
                listing.setRooms(raw.rooms());
                listing.setBedrooms(raw.bedrooms());
                listing.setEnergyLabel(raw.energyLabel());
                listing.setPlotAreaM2(raw.plotAreaM2());
                listingRepository.save(listing);
            })
        );
    }
}
```

## 4. Backend — DTO, Search, API

### 4a. ListingDto (`hermes-backend/.../listing/ListingDto.java`)

Add six fields to the record. Update `ListingService.toDto()` to include them from `Listing`.

### 4b. ListingSearchParams (`hermes-backend/.../listing/ListingSearchParams.java`)

Add four new fields:
```java
public record ListingSearchParams(
    String street, String houseNumber, String houseNumberAddition,
    String zipCode, String province,
    Integer minBedrooms, Integer minRooms, Integer minLivingAreaM2,
    String energyLabel
) { ... }
```

Update `isEmpty()` to include the new fields.

### 4c. ListingSpecifications (`hermes-backend/.../listing/ListingSpecifications.java`)

Add a private `andIfAtLeast()` helper for `>=` integer predicates. Use it for `minBedrooms`, `minRooms`, `minLivingAreaM2`. Use existing `andIfPresent()` for `energyLabel`.

```java
private static Specification<Listing> andIfAtLeast(Specification<Listing> base, String field, Integer value) {
    if (value == null) return base;
    return base.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get(field), value));
}
```

### 4d. ListingController (`hermes-backend/.../api/ListingController.java`)

Add four query parameters to `getListings()` signature to match the OpenAPI spec. Construct `ListingSearchParams` with the new fields.

Map the six new fields in `toDetailResponse()`.

### 4e. OpenAPI spec (`hermes-backend/src/main/resources/openapi/api.yaml`)

Add to `GET /api/listings` parameters:
- `minBedrooms` (integer, nullable)
- `minRooms` (integer, nullable)
- `minLivingAreaM2` (integer, nullable)
- `energyLabel` (string, nullable)

Add to `ListingDetailResponse` schema:
- `description` (string, nullable)
- `livingAreaM2` (integer, nullable)
- `plotAreaM2` (integer, nullable)
- `rooms` (integer, nullable)
- `bedrooms` (integer, nullable)
- `energyLabel` (string, nullable)

## 5. Frontend Changes

### 5a. api.types.ts (`hermes-frontend/src/app/core/api.types.ts`)

`ListingDetailResponse` gains:
```typescript
description?: string | null;
livingAreaM2?: number | null;
plotAreaM2?: number | null;
rooms?: number | null;
bedrooms?: number | null;
energyLabel?: string | null;
```

`ListingSearchFilter` gains:
```typescript
minBedrooms?: number | null;
minRooms?: number | null;
minLivingAreaM2?: number | null;
energyLabel?: string | null;
```

### 5b. listings.service.ts (`hermes-frontend/src/app/core/listings.service.ts`)

In `loadListings()`, add four new params to the `HttpParams` builder:
```typescript
if (filter?.minBedrooms) params = params.set('minBedrooms', filter.minBedrooms);
if (filter?.minRooms) params = params.set('minRooms', filter.minRooms);
if (filter?.minLivingAreaM2) params = params.set('minLivingAreaM2', filter.minLivingAreaM2);
if (filter?.energyLabel) params = params.set('energyLabel', filter.energyLabel);
```

### 5c. listings-page.component.html

Add a second row of filter inputs below the existing ones, bound to the filter signal:
- "Min bedrooms" — number input → `minBedrooms`
- "Min rooms" — number input → `minRooms`
- "Min area (m²)" — number input → `minLivingAreaM2`
- "Energy label" — text input → `energyLabel`

### 5d. listing-detail-page.component.html

Add new stat-cards for the five numeric/label fields (living area, plot area, rooms, bedrooms, energy label) in the existing stat-cards grid.

Add a description section below the stat-cards — only rendered when `description` is non-null:
```html
@if (svc.currentListing()?.description) {
  <app-section-card class="mt-6">
    <h2 class="text-sm font-semibold text-slate-500 uppercase tracking-wider mb-3">Description</h2>
    <p class="text-sm text-slate-700 leading-relaxed whitespace-pre-line">
      {{ svc.currentListing()?.description }}
    </p>
  </app-section-card>
}
```

## 6. Constraints

- `description` column is TEXT (unbounded); all other new columns are INTEGER or VARCHAR(10)
- Rate limiter on `ListingDetailsConsumer`: 50 calls per minute (same as price history consumer)
- `ListingDetailsConsumer` is in the `listing.internal` package (alongside `ListingPersistenceService` and `PriceHistoryConsumer`)
- `RawListing` is in the `scraping` package (public API surface between modules); adding fields to it is a deliberate cross-module contract change
- No changes to the `ListingSummaryResponse` — detail fields only appear on the detail endpoint and page
- Existing tests that construct `RawListing` must be updated to pass the six new arguments (pass `null` for all)
