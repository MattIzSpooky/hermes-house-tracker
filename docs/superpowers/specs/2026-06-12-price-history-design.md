# Price History Design

**Date:** 2026-06-12
**Replaces:** `ListingSnapshot` (point-in-time scrape snapshots)

## Summary

Replace `ListingSnapshot` with `PriceHistoryEntry` — actual Funda price change events fetched via
pyfunda's `price_history()` API. This gives richer, deeper history (back-dated before Hermes first
saw the listing) rather than synthetic snapshots built from nightly scrapes.

Non-price fields previously in `ListingSnapshot` (area, rooms, energy label, listed-since date) are
dropped. Listing status is promoted to a first-class field on the `Listing` entity, updated on every
scrape. Soft delete support is added so that listings returning 404 are quarantined and hard-deleted
by a biweekly cleanup job.

---

## Architecture & Data Flow

### On listing creation (initial price history fetch)

```
ScrapingSessionCompleted
  → ListingPersistenceService
      saves Listing, updates Listing.status + lastUpdatedAt
      if brand-new listing → publishes ListingCreated
        → PriceHistoryService.fetchAndStore(listingId, fundaId)
            GET /listings/{id}/price-history on funda-proxy
            → pyfunda client.price_history()
            merges PriceHistoryEntry records (dedup by timestamp)
          → publishes PriceHistoryUpdated(listingIds)
              → ListingSummaryGenerationService regenerates AI summary
```

### Nightly rescrape (02:00 UTC)

```
NightlyRescrapeScheduler
  ├─ existing scraping flow — calls /listings/{id} per non-deleted listing
  │    → ListingPersistenceService updates Listing.status + lastUpdatedAt
  │    → ScrapingWorker catches 404 → sets status=DELETED, deletedAt=now()
  └─ PriceHistoryService.refreshAll()
       iterates all listings where deletedAt IS NULL
       fetchAndStore for each → merges new entries
       publishes PriceHistoryUpdated
```

### Biweekly cleanup (03:00 UTC, 1st and 15th of each month)

```
DeletedListingCleanupScheduler
  finds all listings where deletedAt IS NOT NULL
  hard-deletes each listing + cascade-deletes their PriceHistoryEntry records
```

---

## Data Model

### `Listing` entity — new fields

| Field           | Type           | Notes                                                   |
|-----------------|----------------|---------------------------------------------------------|
| `status`        | `ListingStatus`| nullable; updated on every scrape cycle                 |
| `lastUpdatedAt` | `Instant`      | set whenever the listing is touched by a scrape         |
| `deletedAt`     | `Instant`      | nullable; set when Funda returns 404 (soft-delete marker)|

### `ListingStatus` enum — new value

```
FOR_SALE, UNDER_OFFER, SOLD, WITHDRAWN, DELETED
```

### `PriceHistoryEntry` entity (new, replaces `ListingSnapshot`)

Table: `price_history_entries`

| Column       | Type         | Constraints                                      |
|--------------|--------------|--------------------------------------------------|
| `id`         | UUID PK      | auto-generated                                   |
| `listing_id` | UUID NOT NULL| FK to `listings.id`                              |
| `price`      | INTEGER      | nullable                                         |
| `status`     | VARCHAR(50)  | `asking_price`, `sold`, or `woz`                 |
| `source`     | VARCHAR(255) | nullable                                         |
| `date`       | VARCHAR(100) | Dutch date string from Funda, e.g. "15 mei 2024" |
| `timestamp`  | VARCHAR(100) | ISO timestamp string from Funda                  |

Unique constraint on `(listing_id, timestamp)` — merge strategy skips entries that already exist.

### `PriceHistoryEntryDto` (public DTO, replaces `ListingSnapshotDto`)

```java
record PriceHistoryEntryDto(UUID id, Integer price, String status, String date, String timestamp)
```

### `ListingDto` changes

- Remove `ListingSnapshotDto latestSnapshot`
- Add `Integer currentPrice` (latest `asking_price` entry for this listing)
- Add `ListingStatus status`

---

## funda-proxy Changes

### New Pydantic model (`models.py`)

```python
class PriceChangeResponse(BaseModel):
    price: int | None = None
    human_price: str | None = None
    status: str | None = None
    source: str | None = None
    date: str | None = None
    timestamp: str | None = None

    @classmethod
    def from_change(cls, change) -> "PriceChangeResponse":
        return cls(
            price=change.price,
            human_price=change.human_price,
            status=change.status,
            source=change.source,
            date=change.date,
            timestamp=change.timestamp,
        )
```

### New endpoint (`main.py`)

```
GET /listings/{listing_id}/price-history
→ response_model=list[PriceChangeResponse]
→ calls client.price_history(listing_id)
→ returns [PriceChangeResponse.from_change(c) for c in history.changes]
```

Error handling mirrors the existing `/listings/{id}` endpoint:
- `ListingNotFound` → 404
- `FundaError` → 502

---

## Backend Changes

### New: `PriceHistoryEntry` entity + `PriceHistoryEntryRepository`

In `listing.internal`. Repository exposes:
- `findByListingIdOrderByTimestampAsc(UUID listingId)`
- `existsByListingIdAndTimestamp(UUID listingId, String timestamp)` — for dedup

### New: `PriceHistoryService` (in `listing.internal`)

```
fetchAndStore(UUID listingId, String fundaId)
  → calls FundaProxyFacade.getPriceHistory(fundaId)
  → for each PriceChangeResponse:
      if not existsByListingIdAndTimestamp → save new PriceHistoryEntry

refreshAll()
  → for each listing where deletedAt IS NULL:
      fetchAndStore(listing.id, listing.fundaId)
  → publishes PriceHistoryUpdated(affectedListingIds)
```

Listens to `ListingCreated` via `@ApplicationModuleListener` for the initial fetch.

### New: `FundaProxyFacade` (public API in `scraping` module)

Wraps `FundaProxyClient` and exposes `getPriceHistory(String fundaId)` as part of the `scraping`
module's public surface, so `listing.internal` can call it without reaching into `scraping.internal`.

### New: `ListingCreated` event

```java
record ListingCreated(UUID listingId, String fundaId)
```

Published by `ListingPersistenceService` when a brand-new listing is first saved.

### New: `PriceHistoryUpdated` event (replaces `ListingSnapshotsCreated`)

```java
record PriceHistoryUpdated(List<UUID> listingIds)
```

Published by `PriceHistoryService` after storing new entries.

### Modified: `ListingPersistenceService`

- On each `ScrapingSessionCompleted`: update `Listing.status` and `Listing.lastUpdatedAt` from `RawListing`
- When brand-new listing: publish `ListingCreated`
- Remove all snapshot creation logic
- Handle 404 from scraping worker: set `status = DELETED`, `deletedAt = Instant.now()`

### Modified: `NightlyRescrapeScheduler`

- Filter out listings where `deletedAt IS NOT NULL` before enqueuing rescrapes
- After enqueuing, call `priceHistoryService.refreshAll()`

### New: `DeletedListingCleanupScheduler`

```java
@Scheduled(cron = "0 0 3 1,15 * *")
```

Finds all listings where `deletedAt IS NOT NULL`, hard-deletes them. `PriceHistoryEntry` records
are cascade-deleted via `ON DELETE CASCADE` on the FK.

### Modified: `ListingService`

- Remove `findSnapshotsByListingId` — replace with `findPriceHistoryByListingId`
- `toDtoWithLatestSnapshot` → `toDto`: derives `currentPrice` from latest `asking_price` entry,
  reads `status` directly from `Listing.status`

### Modified: `ReportService`

- Reads from `PriceHistoryEntryRepository` instead of `ListingSnapshotRepository`
- `priceHistory` → `PriceHistoryEntry` records where `status = "asking_price"`, ordered by timestamp
- `currentPrice` / `initialPrice` → last/first asking_price entries
- `currentStatus` → read from `Listing.status` directly
- `daysListedOnFunda` → removed (field no longer tracked)
- `statusHistory` → removed

### Modified: `ListingSummaryGenerationService`

- Listens to `PriceHistoryUpdated` instead of `ListingSnapshotsCreated`
- Prompt simplified: uses `listing.currentPrice()` instead of `snapshot.askingPrice()`; drops area,
  rooms, energy label, status from prompt (no longer available)

---

## Database Migration (Flyway V3)

```sql
-- Add new columns to listings
ALTER TABLE listings ADD COLUMN status VARCHAR(50);
ALTER TABLE listings ADD COLUMN last_updated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE listings ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;

-- New price history table
CREATE TABLE price_history_entries (
    id          UUID         NOT NULL,
    listing_id  UUID         NOT NULL,
    price       INTEGER,
    status      VARCHAR(50),
    source      VARCHAR(255),
    date        VARCHAR(100),
    timestamp   VARCHAR(100),
    PRIMARY KEY (id),
    CONSTRAINT uk_price_history_listing_timestamp UNIQUE (listing_id, timestamp),
    CONSTRAINT fk_price_history_listing FOREIGN KEY (listing_id)
        REFERENCES listings (id) ON DELETE CASCADE
);

-- Drop old snapshots table
DROP TABLE listing_snapshots;
```

---

## Removed

| Artifact | Reason |
|---|---|
| `ListingSnapshot` entity | Replaced by `PriceHistoryEntry` |
| `ListingSnapshotRepository` | Replaced by `PriceHistoryEntryRepository` |
| `ListingSnapshotDto` | Replaced by `PriceHistoryEntryDto` |
| `ListingSnapshotsCreated` event | Replaced by `PriceHistoryUpdated` |
| `ListingDto.latestSnapshot` | Replaced by `currentPrice` + `status` |
| `ListingService.findSnapshotsByListingId` | Replaced by `findPriceHistoryByListingId` |
| `listing_snapshots` table | Dropped in migration |
