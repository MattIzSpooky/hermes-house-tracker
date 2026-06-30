# Price History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

## Progress

| Task | Status | Commit |
|------|--------|--------|
| Task 1: funda-proxy — PriceChangeResponse model + price-history endpoint | ✅ Done | f5f519e |
| Task 2: Flyway V3 migration | ✅ Done | 65e94a4 |
| Task 3: ListingStatus.DELETED + Listing entity new fields | ✅ Done | 7224ce3 |
| Task 4: PriceHistoryEntry entity + repository + DTO | ✅ Done | 42eec83 |
| Task 5: New events | ✅ Done | 08297d8 |
| Task 6: RawPriceChange + FundaProxyClient.getPriceHistory + FundaProxyFacade | ✅ Done | 2a2296e |
| Task 7: ScrapingWorker — emit ListingNotFound on 404 rescrape | ✅ Done | 85b2a69 |
| Task 8: ListingPersistenceService rewrite | ✅ Done | 71a96d8 |
| Task 9: PriceHistoryService | ✅ Done | 0fd74b3 |
| Task 10: ListingRepository — add active/deleted queries | ✅ Done | 7fb19f6 |
| Task 11: ListingDto + ListingService | ✅ Done | 2b5b5b0 |
| Task 12: OpenAPI spec update + ListingController | ✅ Done | 9d7e438 |
| Task 13: ReportService + ListingReport + PricePoint | ✅ Done | a518647 |
| Task 14: ListingSummaryGenerationService | ✅ Done | — |
| Task 15: NightlyRescrapeScheduler + DeletedListingCleanupScheduler | ✅ Done | 9106a6b |
| Task 16: Remove dead code | ✅ Done | 46560b1 |

**Goal:** Replace `ListingSnapshot` with pyfunda's `price_history()` API, adding real Funda price change history fetched on listing creation and refreshed nightly, plus soft-delete support for 404 listings.

**Architecture:** A new `PriceHistoryService` (in `listing` public package) listens to `ListingCreated` for initial fetches and exposes `refreshAll()` for the nightly scheduler. A `FundaProxyFacade` in the `scraping` module exposes `getPriceHistory()` as a public API. Dead `ListingSnapshot` code is removed in the final task.

**Tech Stack:** Spring Boot 4 / Spring Modulith, JPA/Hibernate, PostgreSQL/Flyway, Python FastAPI, pyfunda 3.1.1+, dateparser (new Python dep), JUnit 5 / Mockito, pytest

---

## File Map

### New files
| File | Responsibility |
|---|---|
| `funda-proxy/requirements.txt` | Add `dateparser` |
| `hermes-backend/src/main/resources/db/migration/V3__add_price_history.sql` | Schema: add columns to listings, create price_history_entries, drop listing_snapshots |
| `hermes-backend/src/main/java/.../listing/internal/PriceHistoryEntry.java` | JPA entity for price_history_entries |
| `hermes-backend/src/main/java/.../listing/internal/PriceHistoryEntryRepository.java` | Spring Data repo |
| `hermes-backend/src/main/java/.../listing/PriceHistoryEntryDto.java` | Public DTO |
| `hermes-backend/src/main/java/.../listing/ListingCreated.java` | Event: new listing first seen |
| `hermes-backend/src/main/java/.../listing/PriceHistoryUpdated.java` | Event: replaces ListingSnapshotsCreated |
| `hermes-backend/src/main/java/.../scraping/ListingNotFound.java` | Event: 404 during rescrape |
| `hermes-backend/src/main/java/.../scraping/RawPriceChange.java` | Public DTO from FundaProxyFacade |
| `hermes-backend/src/main/java/.../scraping/FundaProxyFacade.java` | Public wrapper for FundaProxyClient |
| `hermes-backend/src/main/java/.../listing/PriceHistoryService.java` | Fetch+store price history, refreshAll |
| `hermes-backend/src/main/java/.../DeletedListingCleanupScheduler.java` | Biweekly hard-delete of soft-deleted listings |
| `hermes-backend/src/test/java/.../listing/PriceHistoryServiceTest.java` | Unit tests |

### Modified files
| File | Change |
|---|---|
| `funda-proxy/models.py` | Add `PriceChangeResponse` + parse helpers |
| `funda-proxy/main.py` | Add `GET /listings/{id}/price-history` endpoint |
| `funda-proxy/tests/test_models.py` | Add PriceChangeResponse tests |
| `funda-proxy/tests/test_endpoints.py` | Add price-history endpoint tests |
| `listing/ListingStatus.java` | Add `DELETED` |
| `listing/internal/Listing.java` | Add `status`, `lastUpdatedAt`, `deletedAt` |
| `listing/internal/ListingRepository.java` | Add `findAllByDeletedAtIsNull`, `deleteAllByDeletedAtIsNotNull` |
| `listing/internal/ListingPersistenceService.java` | Update status/lastUpdatedAt, emit ListingCreated, handle ListingNotFound, remove snapshot creation |
| `listing/ListingDto.java` | Replace `latestSnapshot` with `currentPrice` + `status` |
| `listing/ListingService.java` | Remove snapshot code, add price history |
| `listing/internal/FundaProxyClient.java` | Add `getPriceHistory` |
| `scraping/internal/ScrapingWorker.java` | Emit `ListingNotFound` on 404 rescrape |
| `report/ListingReport.java` | Remove `daysListedOnFunda`, `statusHistory` |
| `report/PricePoint.java` | Rename fields to `timestamp`/`price` |
| `report/ReportService.java` | Read from PriceHistoryEntryRepository |
| `report/StatusPoint.java` | Delete |
| `ai/internal/ListingSummaryGenerationService.java` | Listen to PriceHistoryUpdated, simplify prompt |
| `NightlyRescrapeScheduler.java` | Filter deleted listings, call refreshAll |
| `resources/openapi/api.yaml` | Update schemas for new model |
| `api/ListingController.java` | Update mapping methods |
| `test/.../listing/internal/ListingPersistenceServiceTest.java` | Rewrite for new behavior |
| `test/.../report/ReportServiceTest.java` | Rewrite for new behavior |

### Deleted files
`listing/internal/ListingSnapshot.java`, `listing/internal/ListingSnapshotRepository.java`, `listing/ListingSnapshotDto.java`, `listing/ListingSnapshotsCreated.java`, `report/StatusPoint.java`

---

## Task 1: funda-proxy — PriceChangeResponse model + price-history endpoint

**Files:**
- Modify: `funda-proxy/requirements.txt`
- Modify: `funda-proxy/models.py`
- Modify: `funda-proxy/main.py`
- Test: `funda-proxy/tests/test_models.py`
- Test: `funda-proxy/tests/test_endpoints.py`

- [ ] **Step 1: Add dateparser to requirements.txt**

```text
fastapi>=0.115.0
uvicorn[standard]>=0.34.0
pyfunda>=3.1.1
dateparser>=1.2.0
opentelemetry-sdk>=1.30.0
opentelemetry-instrumentation-fastapi>=0.51b0
opentelemetry-exporter-otlp-proto-http>=1.30.0
opentelemetry-instrumentation-logging>=0.51b0
python-json-logger>=3.0.0
```

- [ ] **Step 2: Write failing tests for PriceChangeResponse.from_change**

Append to `funda-proxy/tests/test_models.py`:

```python
from unittest.mock import MagicMock
from datetime import date, datetime, timezone
from models import PriceChangeResponse


def _make_change(**overrides):
    m = MagicMock()
    m.price = 350000
    m.human_price = "€ 350.000 k.k."
    m.status = "asking_price"
    m.source = "walter"
    m.date = "15 mei 2024"
    m.timestamp = "2024-05-15T00:00:00+00:00"
    for k, v in overrides.items():
        setattr(m, k, v)
    return m


def test_price_change_response_maps_all_fields():
    result = PriceChangeResponse.from_change(_make_change())
    assert result.price == 350000
    assert result.human_price == "€ 350.000 k.k."
    assert result.status == "asking_price"
    assert result.source == "walter"
    assert result.date == date(2024, 5, 15)
    assert result.timestamp == datetime(2024, 5, 15, 0, 0, 0, tzinfo=timezone.utc)


def test_price_change_response_null_date_becomes_none():
    result = PriceChangeResponse.from_change(_make_change(date=None))
    assert result.date is None


def test_price_change_response_null_timestamp_becomes_none():
    result = PriceChangeResponse.from_change(_make_change(timestamp=None))
    assert result.timestamp is None


def test_price_change_response_unparseable_date_becomes_none():
    result = PriceChangeResponse.from_change(_make_change(date="not a date"))
    assert result.date is None


def test_price_change_response_unparseable_timestamp_becomes_none():
    result = PriceChangeResponse.from_change(_make_change(timestamp="not a timestamp"))
    assert result.timestamp is None
```

- [ ] **Step 3: Run tests to confirm they fail**

```
cd funda-proxy && python -m pytest tests/test_models.py::test_price_change_response_maps_all_fields -v
```

Expected: `ImportError` or `AttributeError` — `PriceChangeResponse` not defined yet.

- [ ] **Step 4: Add PriceChangeResponse to models.py**

```python
import dateparser
from datetime import date, datetime, timezone
from pydantic import BaseModel


class PriceChangeResponse(BaseModel):
    price: int | None = None
    human_price: str | None = None
    status: str | None = None
    source: str | None = None
    date: date | None = None
    timestamp: datetime | None = None

    @classmethod
    def from_change(cls, change) -> "PriceChangeResponse":
        return cls(
            price=change.price,
            human_price=change.human_price,
            status=change.status,
            source=change.source,
            date=_parse_date(change.date),
            timestamp=_parse_timestamp(change.timestamp),
        )


def _parse_date(raw: str | None) -> date | None:
    if not raw:
        return None
    try:
        parsed = dateparser.parse(raw, languages=["nl"])
        return parsed.date() if parsed else None
    except Exception:
        return None


def _parse_timestamp(raw: str | None) -> datetime | None:
    if not raw:
        return None
    try:
        dt = datetime.fromisoformat(raw)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return dt
    except Exception:
        return None
```

- [ ] **Step 5: Run model tests — expect pass**

```
cd funda-proxy && python -m pytest tests/test_models.py -v
```

Expected: all tests pass.

- [ ] **Step 6: Write failing endpoint test**

Append to `funda-proxy/tests/test_endpoints.py`:

```python
def _make_change():
    m = MagicMock()
    m.price = 350000
    m.human_price = "€ 350.000 k.k."
    m.status = "asking_price"
    m.source = "walter"
    m.date = "15 mei 2024"
    m.timestamp = "2024-05-15T00:00:00+00:00"
    return m


def test_get_price_history_returns_changes(api):
    client, mock_funda = api
    history = MagicMock()
    history.changes = [_make_change()]
    mock_funda.price_history.return_value = history
    resp = client.get("/listings/12345678/price-history")
    assert resp.status_code == 200
    data = resp.json()
    assert len(data) == 1
    assert data[0]["price"] == 350000
    assert data[0]["status"] == "asking_price"
    assert data[0]["date"] == "2024-05-15"
    assert "2024-05-15" in data[0]["timestamp"]


def test_get_price_history_not_found_returns_404(api):
    client, mock_funda = api
    from funda.exceptions import ListingNotFound
    mock_funda.price_history.side_effect = ListingNotFound("not found")
    resp = client.get("/listings/99999999/price-history")
    assert resp.status_code == 404


def test_get_price_history_funda_error_returns_502(api):
    client, mock_funda = api
    from funda.exceptions import FundaError
    mock_funda.price_history.side_effect = FundaError("upstream down")
    resp = client.get("/listings/12345678/price-history")
    assert resp.status_code == 502
```

- [ ] **Step 7: Run endpoint tests to confirm they fail**

```
cd funda-proxy && python -m pytest tests/test_endpoints.py::test_get_price_history_returns_changes -v
```

Expected: `404 Not Found` — endpoint not defined yet.

- [ ] **Step 8: Add price-history endpoint to main.py**

```python
from models import ListingResponse, PriceChangeResponse

@app.get("/listings/{listing_id}/price-history", response_model=list[PriceChangeResponse])
def get_price_history(listing_id: str):
    logger.info("get_price_history id=%s", listing_id)
    try:
        history = get_client().price_history(listing_id)
    except ListingNotFound as e:
        logger.warning("get_price_history id=%s not found: %s", listing_id, e)
        raise HTTPException(status_code=404, detail=str(e))
    except FundaError as e:
        logger.warning("get_price_history id=%s failed: %s", listing_id, e)
        raise HTTPException(status_code=502, detail=str(e))
    return [PriceChangeResponse.from_change(c) for c in history.changes]
```

- [ ] **Step 9: Run all endpoint tests — expect pass**

```
cd funda-proxy && python -m pytest tests/ -v
```

Expected: all tests pass.

- [ ] **Step 10: Commit**

```
git add funda-proxy/requirements.txt funda-proxy/models.py funda-proxy/main.py funda-proxy/tests/test_models.py funda-proxy/tests/test_endpoints.py
git commit -m "feat(funda-proxy): add price history endpoint"
```

---

## Task 2: Flyway V3 migration

**Files:**
- Create: `hermes-backend/src/main/resources/db/migration/V3__add_price_history.sql`

- [ ] **Step 1: Create the migration file**

```sql
-- Add new columns to listings
ALTER TABLE listings ADD COLUMN status VARCHAR(50);
ALTER TABLE listings ADD COLUMN last_updated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE listings ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;

-- New price history table
CREATE TABLE price_history_entries (
    id          UUID                     NOT NULL,
    listing_id  UUID                     NOT NULL,
    price       INTEGER,
    status      VARCHAR(50),
    source      VARCHAR(255),
    date        DATE,
    timestamp   TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id),
    CONSTRAINT uk_price_history_listing_timestamp UNIQUE (listing_id, timestamp),
    CONSTRAINT fk_price_history_listing FOREIGN KEY (listing_id)
        REFERENCES listings (id) ON DELETE CASCADE
);

-- Drop old snapshots table
DROP TABLE listing_snapshots;
```

- [ ] **Step 2: Verify the migration file has no syntax errors by checking names**

Open the file and confirm it matches exactly: table name `price_history_entries`, columns match the entity we'll build next.

- [ ] **Step 3: Commit**

```
git add hermes-backend/src/main/resources/db/migration/V3__add_price_history.sql
git commit -m "feat(hermes-backend): add V3 price history migration"
```

---

## Task 3: ListingStatus.DELETED + Listing entity new fields

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingStatus.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/Listing.java`

- [ ] **Step 1: Add DELETED to ListingStatus**

Full file replacement:

```java
package com.kropholler.dev.hermes.listing;

public enum ListingStatus {
    FOR_SALE, UNDER_OFFER, SOLD, WITHDRAWN, DELETED
}
```

- [ ] **Step 2: Add status, lastUpdatedAt, deletedAt to Listing entity**

Full file replacement:

```java
package com.kropholler.dev.hermes.listing.internal;

import com.kropholler.dev.hermes.listing.ListingStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "listings")
@Getter
@Setter
@NoArgsConstructor
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String fundaId;

    @Column(nullable = false)
    private String url;

    private String street;
    private String houseNumber;
    private String houseNumberAddition;
    private String zipCode;
    private String city;
    private String province;

    @Column(nullable = false)
    private Instant firstSeenAt = Instant.now();

    @Column(nullable = false)
    private Instant lastSeenAt = Instant.now();

    @Enumerated(EnumType.STRING)
    private ListingStatus status;

    private Instant lastUpdatedAt;

    private Instant deletedAt;
}
```

- [ ] **Step 3: Compile to confirm no errors**

```
cd hermes-backend && ./mvnw compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingStatus.java hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/Listing.java
git commit -m "feat(hermes-backend): add status and soft-delete fields to Listing"
```

---

## Task 4: PriceHistoryEntry entity + repository + DTO

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/PriceHistoryEntry.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/PriceHistoryEntryRepository.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/PriceHistoryEntryDto.java`

- [ ] **Step 1: Create PriceHistoryEntry entity**

```java
package com.kropholler.dev.hermes.listing.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "price_history_entries")
@Getter
@Setter
@NoArgsConstructor
public class PriceHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID listingId;

    private Integer price;

    private String status;

    private String source;

    private LocalDate date;

    private Instant timestamp;
}
```

- [ ] **Step 2: Create PriceHistoryEntryRepository**

```java
package com.kropholler.dev.hermes.listing.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PriceHistoryEntryRepository extends JpaRepository<PriceHistoryEntry, UUID> {

    List<PriceHistoryEntry> findByListingIdOrderByTimestampAsc(UUID listingId);

    Optional<PriceHistoryEntry> findFirstByListingIdAndStatusOrderByTimestampDesc(
            UUID listingId, String status);

    boolean existsByListingIdAndTimestamp(UUID listingId, Instant timestamp);
}
```

- [ ] **Step 3: Create PriceHistoryEntryDto**

```java
package com.kropholler.dev.hermes.listing;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PriceHistoryEntryDto(
    UUID id,
    Integer price,
    String status,
    String source,
    LocalDate date,
    Instant timestamp
) {}
```

- [ ] **Step 4: Compile**

```
cd hermes-backend && ./mvnw compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/PriceHistoryEntry.java hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/PriceHistoryEntryRepository.java hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/PriceHistoryEntryDto.java
git commit -m "feat(hermes-backend): add PriceHistoryEntry entity and repository"
```

---

## Task 5: New events

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingCreated.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/PriceHistoryUpdated.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/ListingNotFound.java`

- [ ] **Step 1: Create ListingCreated event**

```java
package com.kropholler.dev.hermes.listing;

import java.util.UUID;

public record ListingCreated(UUID listingId, String fundaId) {}
```

- [ ] **Step 2: Create PriceHistoryUpdated event**

```java
package com.kropholler.dev.hermes.listing;

import java.util.List;
import java.util.UUID;

public record PriceHistoryUpdated(List<UUID> listingIds) {}
```

- [ ] **Step 3: Create ListingNotFound event**

```java
package com.kropholler.dev.hermes.scraping;

public record ListingNotFound(String fundaId) {}
```

- [ ] **Step 4: Compile**

```
cd hermes-backend && ./mvnw compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingCreated.java hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/PriceHistoryUpdated.java hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/ListingNotFound.java
git commit -m "feat(hermes-backend): add ListingCreated, PriceHistoryUpdated, ListingNotFound events"
```

---

## Task 6: RawPriceChange + FundaProxyClient.getPriceHistory + FundaProxyFacade

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/RawPriceChange.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/internal/FundaProxyPriceChange.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/internal/FundaProxyClient.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/FundaProxyFacade.java`

- [ ] **Step 1: Create RawPriceChange (public DTO)**

```java
package com.kropholler.dev.hermes.scraping;

import java.time.Instant;
import java.time.LocalDate;

public record RawPriceChange(
    Integer price,
    String status,
    String source,
    LocalDate date,
    Instant timestamp
) {}
```

- [ ] **Step 2: Create FundaProxyPriceChange (internal JSON DTO)**

```java
package com.kropholler.dev.hermes.scraping.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;

record FundaProxyPriceChange(
    @JsonProperty("price")       Integer price,
    @JsonProperty("human_price") String humanPrice,
    @JsonProperty("status")      String status,
    @JsonProperty("source")      String source,
    @JsonProperty("date")        LocalDate date,
    @JsonProperty("timestamp")   Instant timestamp
) {}
```

- [ ] **Step 3: Add getPriceHistory to FundaProxyClient**

Add the following import and constant at the top of `FundaProxyClient.java`:

```java
import com.kropholler.dev.hermes.scraping.funda.RawPriceChange;
```

Add the new constant after the existing ones:
```java
private static final ParameterizedTypeReference<List<FundaProxyPriceChange>> PRICE_CHANGE_LIST =
    new ParameterizedTypeReference<>() {};
```

Add the new method after `getListing`:
```java
List<RawPriceChange> getPriceHistory(String fundaId) {
    log.info("Calling funda-proxy: GET /listings/{}/price-history", fundaId);
    try {
        List<FundaProxyPriceChange> results = restClient.get()
            .uri("/listings/{id}/price-history", fundaId)
            .retrieve()
            .body(PRICE_CHANGE_LIST);
        return results == null ? List.of()
            : results.stream().map(this::toRawPriceChange).toList();
    } catch (HttpClientErrorException e) {
        if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
            log.warn("Price history for {} not found in funda-proxy", fundaId);
            return List.of();
        }
        throw e;
    }
}

private RawPriceChange toRawPriceChange(FundaProxyPriceChange p) {
    return new RawPriceChange(p.price(), p.status(), p.source(), p.date(), p.timestamp());
}
```

- [ ] **Step 4: Create FundaProxyFacade (public wrapper)**

```java
package com.kropholler.dev.hermes.scraping;

import com.kropholler.dev.hermes.scraping.funda.FundaProxyClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class FundaProxyFacade {

    private final FundaProxyClient client;

    public List<RawPriceChange> getPriceHistory(String fundaId) {
        return client.getPriceHistory(fundaId);
    }
}
```

- [ ] **Step 5: Compile**

```
cd hermes-backend && ./mvnw compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/RawPriceChange.java hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/internal/FundaProxyPriceChange.java hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/internal/FundaProxyClient.java hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/FundaProxyFacade.java
git commit -m "feat(hermes-backend): add getPriceHistory to FundaProxyClient and FundaProxyFacade"
```

---

## Task 7: ScrapingWorker — emit ListingNotFound on 404 rescrape

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/internal/ScrapingWorker.java`

- [ ] **Step 1: Write failing test**

Create `hermes-backend/src/test/java/com/kropholler/dev/hermes/scraping/internal/ScrapingWorkerTest.java`:

```java
package com.kropholler.dev.hermes.scraping.internal;

import com.kropholler.dev.hermes.scraping.funda.ListingNotFound;
import com.kropholler.dev.hermes.scraping.ScrapingSessionCompleted;
import com.kropholler.dev.hermes.scraping.ScrapingSessionStatus;
import com.kropholler.dev.hermes.scraping.ScrapingSessionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScrapingWorkerTest {

    @Mock private ScrapingSessionRepository sessionRepository;
    @Mock private FundaProxyClient proxyClient;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ScrapingWorker worker;

    @Test
    void rescrape_publishesListingNotFound_when404() {
        ScrapingSession session = new ScrapingSession();
        session.setType(ScrapingSessionType.RESCRAPE);
        session.setCity("amsterdam");
        session.setPageLimit(1);
        session.setFundaUrl("https://funda.nl/koop/amsterdam/huis-12345678/");
        session.setTargetListingUrl("https://funda.nl/koop/amsterdam/huis-12345678/");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(proxyClient.extractFundaId("https://funda.nl/koop/amsterdam/huis-12345678/"))
            .thenReturn("12345678");
        when(proxyClient.getListing("12345678")).thenReturn(Optional.empty());

        worker.process(session);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        assertThat(captor.getAllValues())
            .anyMatch(e -> e instanceof ListingNotFound n && "12345678".equals(n.fundaId()));
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```
cd hermes-backend && ./mvnw test -Dtest=ScrapingWorkerTest -q
```

Expected: `FAIL` — `ListingNotFound` event not published.

- [ ] **Step 3: Update scrapeAllPages in ScrapingWorker.java**

Replace the RESCRAPE block in `scrapeAllPages`:

```java
if (session.getType() == ScrapingSessionType.RESCRAPE) {
    String fundaId = proxyClient.extractFundaId(session.getTargetListingUrl());
    Optional<RawListing> listing = proxyClient.getListing(fundaId);
    if (listing.isEmpty()) {
        eventPublisher.publishEvent(new ListingNotFound(fundaId));
    }
    return listing.map(List::of).orElse(List.of());
}
```

Add the import at the top:
```java
import com.kropholler.dev.hermes.scraping.funda.ListingNotFound;
```

- [ ] **Step 4: Run test to confirm it passes**

```
cd hermes-backend && ./mvnw test -Dtest=ScrapingWorkerTest -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/internal/ScrapingWorker.java hermes-backend/src/test/java/com/kropholler/dev/hermes/scraping/internal/ScrapingWorkerTest.java
git commit -m "feat(hermes-backend): emit ListingNotFound event on 404 rescrape"
```

---

## Task 8: ListingPersistenceService rewrite

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceService.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceServiceTest.java`

- [ ] **Step 1: Rewrite the test**

Full replacement of `ListingPersistenceServiceTest.java`:

```java
package com.kropholler.dev.hermes.listing.internal;

import com.kropholler.dev.hermes.listing.ListingCreated;
import com.kropholler.dev.hermes.listing.ListingStatus;
import com.kropholler.dev.hermes.scraping.funda.ListingNotFound;
import com.kropholler.dev.hermes.scraping.funda.RawListing;
import com.kropholler.dev.hermes.scraping.ScrapingSessionCompleted;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListingPersistenceServiceTest {

    @Mock private ListingRepository listingRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ListingPersistenceService service;

    @Test
    void newListing_setsStatusAndPublishesListingCreated() {
        RawListing raw = new RawListing(
            "12345678", "https://www.funda.nl/koop/amsterdam/huis-12345678/",
            "Teststraat", "10", null, "1234AB", "amsterdam", "Noord-Holland",
            450000, 75, 3, "A", null, "FOR_SALE"
        );
        ScrapingSessionCompleted event = new ScrapingSessionCompleted(UUID.randomUUID(), List.of(raw));

        Listing saved = new Listing();
        saved.setFundaId("12345678");
        when(listingRepository.findByFundaId("12345678")).thenReturn(Optional.empty());
        when(listingRepository.save(any())).thenReturn(saved);

        service.onScrapingSessionCompleted(event);

        ArgumentCaptor<Listing> listingCaptor = ArgumentCaptor.forClass(Listing.class);
        verify(listingRepository).save(listingCaptor.capture());
        assertThat(listingCaptor.getValue().getStatus()).isEqualTo(ListingStatus.FOR_SALE);
        assertThat(listingCaptor.getValue().getLastUpdatedAt()).isNotNull();

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(ListingCreated.class);
        assertThat(((ListingCreated) eventCaptor.getValue()).fundaId()).isEqualTo("12345678");
    }

    @Test
    void existingListing_updatesStatusAndDoesNotPublishListingCreated() {
        Listing existing = new Listing();
        existing.setFundaId("12345678");

        RawListing raw = new RawListing(
            "12345678", "https://www.funda.nl/koop/amsterdam/huis-12345678/",
            "Teststraat", "10", null, "1234AB", "amsterdam", "Noord-Holland",
            460000, 75, 3, "A", null, "UNDER_OFFER"
        );
        ScrapingSessionCompleted event = new ScrapingSessionCompleted(UUID.randomUUID(), List.of(raw));

        when(listingRepository.findByFundaId("12345678")).thenReturn(Optional.of(existing));
        when(listingRepository.save(any())).thenReturn(existing);

        service.onScrapingSessionCompleted(event);

        assertThat(existing.getStatus()).isEqualTo(ListingStatus.UNDER_OFFER);
        verify(eventPublisher, never()).publishEvent(any(ListingCreated.class));
    }

    @Test
    void onListingNotFound_setsStatusDeletedAndDeletedAt() {
        Listing listing = new Listing();
        listing.setFundaId("12345678");

        when(listingRepository.findByFundaId("12345678")).thenReturn(Optional.of(listing));
        when(listingRepository.save(any())).thenReturn(listing);

        service.onListingNotFound(new ListingNotFound("12345678"));

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.DELETED);
        assertThat(listing.getDeletedAt()).isNotNull();
        assertThat(listing.getLastUpdatedAt()).isNotNull();
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```
cd hermes-backend && ./mvnw test -Dtest=ListingPersistenceServiceTest -q
```

Expected: compilation errors or test failures — `ListingPersistenceService` still references old snapshot code.

- [ ] **Step 3: Rewrite ListingPersistenceService**

Full replacement:

```java
package com.kropholler.dev.hermes.listing.internal;

import com.kropholler.dev.hermes.listing.ListingCreated;
import com.kropholler.dev.hermes.listing.ListingStatus;
import com.kropholler.dev.hermes.scraping.funda.ListingNotFound;
import com.kropholler.dev.hermes.scraping.funda.RawListing;
import com.kropholler.dev.hermes.scraping.ScrapingSessionCompleted;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ListingPersistenceService {

    private final ListingRepository listingRepository;
    private final ApplicationEventPublisher eventPublisher;

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onScrapingSessionCompleted(ScrapingSessionCompleted event) {
        for (RawListing raw : event.listings()) {
            boolean isNew = listingRepository.findByFundaId(raw.fundaId()).isEmpty();
            Listing listing = listingRepository.findByFundaId(raw.fundaId())
                .orElseGet(() -> createListing(raw));

            listing.setLastSeenAt(Instant.now());
            listing.setLastUpdatedAt(Instant.now());
            listing.setStatus(parseStatus(raw.status()));
            listingRepository.save(listing);

            if (isNew) {
                eventPublisher.publishEvent(new ListingCreated(listing.getId(), listing.getFundaId()));
            }
        }
    }

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onListingNotFound(ListingNotFound event) {
        listingRepository.findByFundaId(event.fundaId()).ifPresent(listing -> {
            listing.setStatus(ListingStatus.DELETED);
            listing.setDeletedAt(Instant.now());
            listing.setLastUpdatedAt(Instant.now());
            listingRepository.save(listing);
        });
    }

    private Listing createListing(RawListing raw) {
        Listing l = new Listing();
        l.setFundaId(raw.fundaId());
        l.setUrl(raw.url());
        l.setStreet(raw.street());
        l.setHouseNumber(raw.houseNumber());
        l.setHouseNumberAddition(raw.houseNumberAddition());
        l.setZipCode(raw.zipCode());
        l.setCity(raw.city());
        l.setProvince(raw.province());
        return l;
    }

    private ListingStatus parseStatus(String status) {
        try {
            return ListingStatus.valueOf(status);
        } catch (Exception e) {
            return ListingStatus.FOR_SALE;
        }
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```
cd hermes-backend && ./mvnw test -Dtest=ListingPersistenceServiceTest -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceService.java hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceServiceTest.java
git commit -m "feat(hermes-backend): rewrite ListingPersistenceService for price history flow"
```

---

## Task 9: PriceHistoryService

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/PriceHistoryService.java`
- Create: `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/PriceHistoryServiceTest.java`

- [ ] **Step 1: Write failing tests**

```java
package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.data.Listing;
import com.kropholler.dev.hermes.listing.data.ListingRepository;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryEntry;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryEntryRepository;
import com.kropholler.dev.hermes.scraping.funda.FundaProxyFacade;
import com.kropholler.dev.hermes.scraping.funda.RawPriceChange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceHistoryServiceTest {

    @Mock private ListingRepository listingRepository;
    @Mock private PriceHistoryEntryRepository priceHistoryRepository;
    @Mock private FundaProxyFacade proxyFacade;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PriceHistoryService service;

    @Test
    void fetchAndStore_savesNewEntry() {
        UUID listingId = UUID.randomUUID();
        Instant ts = Instant.parse("2024-05-15T00:00:00Z");
        RawPriceChange change = new RawPriceChange(350000, "asking_price", "walter",
            LocalDate.of(2024, 5, 15), ts);

        when(proxyFacade.getPriceHistory("12345678")).thenReturn(List.of(change));
        when(priceHistoryRepository.existsByListingIdAndTimestamp(listingId, ts)).thenReturn(false);

        service.fetchAndStore(listingId, "12345678");

        ArgumentCaptor<PriceHistoryEntry> captor = ArgumentCaptor.forClass(PriceHistoryEntry.class);
        verify(priceHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getPrice()).isEqualTo(350000);
        assertThat(captor.getValue().getStatus()).isEqualTo("asking_price");
        assertThat(captor.getValue().getTimestamp()).isEqualTo(ts);
    }

    @Test
    void fetchAndStore_skipsDuplicateEntry() {
        UUID listingId = UUID.randomUUID();
        Instant ts = Instant.parse("2024-05-15T00:00:00Z");
        RawPriceChange change = new RawPriceChange(350000, "asking_price", "walter",
            LocalDate.of(2024, 5, 15), ts);

        when(proxyFacade.getPriceHistory("12345678")).thenReturn(List.of(change));
        when(priceHistoryRepository.existsByListingIdAndTimestamp(listingId, ts)).thenReturn(true);

        service.fetchAndStore(listingId, "12345678");

        verify(priceHistoryRepository, never()).save(any());
    }

    @Test
    void fetchAndStore_skipsEntryWithNullTimestamp() {
        UUID listingId = UUID.randomUUID();
        RawPriceChange change = new RawPriceChange(350000, "asking_price", "walter",
            LocalDate.of(2024, 5, 15), null);

        when(proxyFacade.getPriceHistory("12345678")).thenReturn(List.of(change));

        service.fetchAndStore(listingId, "12345678");

        verify(priceHistoryRepository, never()).save(any());
        verify(priceHistoryRepository, never()).existsByListingIdAndTimestamp(any(), any());
    }

    @Test
    void refreshAll_publishesPriceHistoryUpdated() {
        Listing listing = new Listing();
        listing.setFundaId("12345678");
        UUID listingId = UUID.randomUUID();
        listing.setId(listingId);

        when(listingRepository.findAllByDeletedAtIsNull(any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(listing)));
        when(proxyFacade.getPriceHistory("12345678")).thenReturn(List.of());

        service.refreshAll();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(PriceHistoryUpdated.class);
    }

    @Test
    void onListingCreated_callsFetchAndStoreAndPublishesEvent() {
        UUID listingId = UUID.randomUUID();
        when(proxyFacade.getPriceHistory("12345678")).thenReturn(List.of());

        service.onListingCreated(new ListingCreated(listingId, "12345678"));

        verify(proxyFacade).getPriceHistory("12345678");
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(PriceHistoryUpdated.class);
        assertThat(((PriceHistoryUpdated) captor.getValue()).listingIds()).contains(listingId);
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```
cd hermes-backend && ./mvnw test -Dtest=PriceHistoryServiceTest -q
```

Expected: compilation error — `PriceHistoryService` not found.

- [ ] **Step 3: Implement PriceHistoryService**

```java
package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.data.Listing;
import com.kropholler.dev.hermes.listing.data.ListingRepository;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryEntry;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryEntryRepository;
import com.kropholler.dev.hermes.scraping.funda.FundaProxyFacade;
import com.kropholler.dev.hermes.scraping.funda.RawPriceChange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceHistoryService {

    private final ListingRepository listingRepository;
    private final PriceHistoryEntryRepository priceHistoryRepository;
    private final FundaProxyFacade proxyFacade;
    private final ApplicationEventPublisher eventPublisher;

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onListingCreated(ListingCreated event) {
        fetchAndStore(event.listingId(), event.fundaId());
        eventPublisher.publishEvent(new PriceHistoryUpdated(List.of(event.listingId())));
    }

    public void refreshAll() {
        List<UUID> updated = new ArrayList<>();
        int page = 0;
        Page<Listing> batch;
        do {
            batch = listingRepository.findAllByDeletedAtIsNull(PageRequest.of(page, 100));
            for (Listing listing : batch.getContent()) {
                try {
                    fetchAndStore(listing.getId(), listing.getFundaId());
                    updated.add(listing.getId());
                } catch (Exception e) {
                    log.warn("Failed to refresh price history for listing {}: {}",
                        listing.getId(), e.getMessage());
                }
            }
            page++;
        } while (batch.hasNext());
        if (!updated.isEmpty()) {
            eventPublisher.publishEvent(new PriceHistoryUpdated(updated));
        }
    }

    void fetchAndStore(UUID listingId, String fundaId) {
        List<RawPriceChange> changes = proxyFacade.getPriceHistory(fundaId);
        for (RawPriceChange change : changes) {
            if (change.timestamp() == null) continue;
            if (priceHistoryRepository.existsByListingIdAndTimestamp(listingId, change.timestamp())) {
                continue;
            }
            PriceHistoryEntry entry = new PriceHistoryEntry();
            entry.setListingId(listingId);
            entry.setPrice(change.price());
            entry.setStatus(change.status());
            entry.setSource(change.source());
            entry.setDate(change.date());
            entry.setTimestamp(change.timestamp());
            priceHistoryRepository.save(entry);
        }
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```
cd hermes-backend && ./mvnw test -Dtest=PriceHistoryServiceTest -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/PriceHistoryService.java hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/PriceHistoryServiceTest.java
git commit -m "feat(hermes-backend): add PriceHistoryService"
```

---

## Task 10: ListingRepository — add active/deleted queries

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingRepository.java`

- [ ] **Step 1: Add queries to ListingRepository**

Full replacement:

```java
package com.kropholler.dev.hermes.listing.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ListingRepository extends JpaRepository<Listing, UUID> {
    Optional<Listing> findByFundaId(String fundaId);
    Page<Listing> findAllByDeletedAtIsNull(Pageable pageable);
    void deleteAllByDeletedAtIsNotNull();
}
```

- [ ] **Step 2: Compile**

```
cd hermes-backend && ./mvnw compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingRepository.java
git commit -m "feat(hermes-backend): add active/deleted listing queries to ListingRepository"
```

---

## Task 11: ListingDto + ListingService

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingDto.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingService.java`

- [ ] **Step 1: Update ListingDto**

Full replacement:

```java
package com.kropholler.dev.hermes.listing;

import java.time.Instant;
import java.util.UUID;

public record ListingDto(
    UUID id,
    String fundaId,
    String url,
    String street,
    String houseNumber,
    String houseNumberAddition,
    String zipCode,
    String city,
    String province,
    Instant firstSeenAt,
    Instant lastSeenAt,
    Integer currentPrice,
    ListingStatus status
) {}
```

- [ ] **Step 2: Update ListingService**

Full replacement:

```java
package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.data.Listing;
import com.kropholler.dev.hermes.listing.data.ListingRepository;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryEntry;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListingService {

    private final ListingRepository listingRepository;
    private final PriceHistoryEntryRepository priceHistoryRepository;

    @Transactional(readOnly = true)
    public Page<ListingDto> findAll(Pageable pageable) {
        return listingRepository.findAll(pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<ListingDto> findById(UUID id) {
        return listingRepository.findById(id).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<ListingDto> findByFundaId(String fundaId) {
        return listingRepository.findByFundaId(fundaId).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<PriceHistoryEntryDto> findPriceHistoryByListingId(UUID listingId) {
        return priceHistoryRepository.findByListingIdOrderByTimestampAsc(listingId)
            .stream().map(this::toPriceHistoryDto).toList();
    }

    private ListingDto toDto(Listing l) {
        Integer currentPrice = priceHistoryRepository
            .findFirstByListingIdAndStatusOrderByTimestampDesc(l.getId(), "asking_price")
            .map(PriceHistoryEntry::getPrice)
            .orElse(null);
        return new ListingDto(l.getId(), l.getFundaId(), l.getUrl(),
            l.getStreet(), l.getHouseNumber(), l.getHouseNumberAddition(),
            l.getZipCode(), l.getCity(), l.getProvince(),
            l.getFirstSeenAt(), l.getLastSeenAt(), currentPrice, l.getStatus());
    }

    private PriceHistoryEntryDto toPriceHistoryDto(PriceHistoryEntry e) {
        return new PriceHistoryEntryDto(e.getId(), e.getPrice(), e.getStatus(),
            e.getSource(), e.getDate(), e.getTimestamp());
    }
}
```

- [ ] **Step 3: Compile (expect errors from ListingController still referencing latestSnapshot — that's OK for now)**

```
cd hermes-backend && ./mvnw compile -q 2>&1 | head -30
```

The `ListingController` will have compile errors. Note them — they'll be fixed in Task 12.

- [ ] **Step 4: Commit**

```
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingDto.java hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingService.java
git commit -m "feat(hermes-backend): update ListingDto and ListingService for price history"
```

---

## Task 12: OpenAPI spec update + ListingController

**Files:**
- Modify: `hermes-backend/src/main/resources/openapi/api.yaml`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/api/ListingController.java`

- [ ] **Step 1: Update api.yaml**

In `components/schemas`, make these changes:

**Replace `ListingDetailResponse`** — remove `latestSnapshot`, add `currentPrice` and `status`:
```yaml
    ListingDetailResponse:
      type: object
      properties:
        id:
          type: string
          format: uuid
        fundaId:
          type: string
        url:
          type: string
        street:
          type: string
        houseNumber:
          type: string
        houseNumberAddition:
          type: string
          nullable: true
        zipCode:
          type: string
        city:
          type: string
        province:
          type: string
        firstSeenAt:
          type: string
          format: date-time
        lastSeenAt:
          type: string
          format: date-time
        currentPrice:
          type: integer
          nullable: true
        status:
          type: string
          nullable: true
```

**Delete the entire `SnapshotResponse` schema** (lines for `SnapshotResponse`).

**Replace `ListingReportResponse`** — remove `daysListedOnFunda` and `statusHistory`:
```yaml
    ListingReportResponse:
      type: object
      properties:
        listingId:
          type: string
          format: uuid
        daysInHermes:
          type: integer
          format: int64
        currentPrice:
          type: integer
          nullable: true
        initialPrice:
          type: integer
          nullable: true
        priceChangePct:
          type: number
          format: double
          nullable: true
        priceHistory:
          type: array
          items:
            $ref: '#/components/schemas/PricePointResponse'
        currentStatus:
          type: string
          nullable: true
```

**Replace `PricePointResponse`** — rename fields to `timestamp` and `price`:
```yaml
    PricePointResponse:
      type: object
      properties:
        timestamp:
          type: string
          format: date-time
        price:
          type: integer
          nullable: true
```

**Delete the entire `StatusPointResponse` schema**.

- [ ] **Step 2: Regenerate API classes**

```
cd hermes-backend && ./mvnw generate-sources -q
```

Expected: `BUILD SUCCESS`. New generated classes in `target/generated-sources/openapi/`.

- [ ] **Step 3: Rewrite ListingController**

Full replacement:

```java
package com.kropholler.dev.hermes.api;

import com.kropholler.dev.hermes.listing.summary.ListingSummaryService;
import com.kropholler.dev.hermes.api.generated.ListingsApi;
import com.kropholler.dev.hermes.api.generated.model.*;
import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.report.ListingReport;
import com.kropholler.dev.hermes.report.ReportService;
import com.kropholler.dev.hermes.scraping.ScrapingQueueService;
import com.kropholler.dev.hermes.scraping.ScrapingSessionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneOffset;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
class ListingController implements ListingsApi {

    private final ListingService listingService;
    private final ScrapingQueueService queueService;
    private final ReportService reportService;
    private final ListingSummaryService summaryService;

    @Override
    public ResponseEntity<ListingPage> getListings(Integer page, Integer size) {
        Page<ListingDto> result = listingService.findAll(PageRequest.of(page, size));
        ListingPage response = new ListingPage()
            .content(result.getContent().stream().map(this::toSummaryResponse).toList())
            .totalElements(result.getTotalElements())
            .totalPages(result.getTotalPages())
            .page(result.getNumber())
            .size(result.getSize());
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ListingDetailResponse> getListing(UUID id) {
        return listingService.findById(id)
            .map(dto -> ResponseEntity.ok(toDetailResponse(dto)))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Listing " + id + " not found"));
    }

    @Override
    public ResponseEntity<ScrapingSessionResponse> rescrapeListing(UUID id) {
        ListingDto listing = listingService.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Listing " + id + " not found"));
        ScrapingSessionDto session = queueService.enqueueRescrape(listing.url(), listing.city());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(toSessionResponse(session));
    }

    @Override
    public ResponseEntity<ListingReportResponse> getListingReport(UUID id) {
        return reportService.generateReport(id)
            .map(report -> ResponseEntity.ok(toReportResponse(report)))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Listing " + id + " not found"));
    }

    @Override
    public ResponseEntity<AiSummaryResponse> getListingSummary(UUID id) {
        return summaryService.findByListingId(id)
            .map(dto -> ResponseEntity.ok(new AiSummaryResponse()
                .listingId(dto.listingId())
                .summary(dto.summary())
                .generatedAt(dto.generatedAt().atOffset(ZoneOffset.UTC))))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Summary for listing " + id + " not found"));
    }

    private ListingSummaryResponse toSummaryResponse(ListingDto dto) {
        return new ListingSummaryResponse()
            .id(dto.id())
            .street(dto.street())
            .houseNumber(dto.houseNumber())
            .houseNumberAddition(dto.houseNumberAddition())
            .zipCode(dto.zipCode())
            .city(dto.city())
            .province(dto.province())
            .askingPrice(dto.currentPrice())
            .status(dto.status() != null ? dto.status().name() : null)
            .firstSeenAt(dto.firstSeenAt().atOffset(ZoneOffset.UTC));
    }

    private ListingDetailResponse toDetailResponse(ListingDto dto) {
        return new ListingDetailResponse()
            .id(dto.id())
            .fundaId(dto.fundaId())
            .url(dto.url())
            .street(dto.street())
            .houseNumber(dto.houseNumber())
            .houseNumberAddition(dto.houseNumberAddition())
            .zipCode(dto.zipCode())
            .city(dto.city())
            .province(dto.province())
            .firstSeenAt(dto.firstSeenAt().atOffset(ZoneOffset.UTC))
            .lastSeenAt(dto.lastSeenAt().atOffset(ZoneOffset.UTC))
            .currentPrice(dto.currentPrice())
            .status(dto.status() != null ? dto.status().name() : null);
    }

    private ListingReportResponse toReportResponse(ListingReport r) {
        return new ListingReportResponse()
            .listingId(r.listingId())
            .daysInHermes(r.daysInHermes())
            .currentPrice(r.currentPrice())
            .initialPrice(r.initialPrice())
            .priceChangePct(r.priceChangePct())
            .priceHistory(r.priceHistory().stream()
                .map(p -> new PricePointResponse()
                    .timestamp(p.timestamp().atOffset(ZoneOffset.UTC))
                    .price(p.price()))
                .toList())
            .currentStatus(r.currentStatus() != null ? r.currentStatus().name() : null);
    }

    private ScrapingSessionResponse toSessionResponse(ScrapingSessionDto dto) {
        return new ScrapingSessionResponse()
            .id(dto.id())
            .status(ScrapingSessionResponse.StatusEnum.valueOf(dto.status().name()))
            .type(ScrapingSessionResponse.TypeEnum.valueOf(dto.type().name()))
            .createdAt(dto.createdAt().atOffset(ZoneOffset.UTC))
            .completedAt(dto.completedAt() != null ? dto.completedAt().atOffset(ZoneOffset.UTC) : null);
    }
}
```

- [ ] **Step 4: Compile**

```
cd hermes-backend && ./mvnw compile -q
```

Expected: `BUILD SUCCESS` (there will still be test compile errors from `ReportServiceTest` — those are fixed in Task 13).

- [ ] **Step 5: Commit**

```
git add hermes-backend/src/main/resources/openapi/api.yaml hermes-backend/src/main/java/com/kropholler/dev/hermes/api/ListingController.java
git commit -m "feat(hermes-backend): update OpenAPI spec and ListingController for price history"
```

---

## Task 13: ReportService + ListingReport + PricePoint

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/report/PricePoint.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/report/ListingReport.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/report/ReportService.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/report/ReportServiceTest.java`

- [ ] **Step 1: Update PricePoint**

Full replacement:

```java
package com.kropholler.dev.hermes.report;

import java.time.Instant;

public record PricePoint(Instant timestamp, Integer price) {}
```

- [ ] **Step 2: Update ListingReport — remove daysListedOnFunda and statusHistory**

Full replacement:

```java
package com.kropholler.dev.hermes.report;

import com.kropholler.dev.hermes.listing.ListingStatus;

import java.util.List;
import java.util.UUID;

public record ListingReport(
    UUID listingId,
    Long daysInHermes,
    Integer currentPrice,
    Integer initialPrice,
    Double priceChangePct,
    List<PricePoint> priceHistory,
    ListingStatus currentStatus
) {}
```

- [ ] **Step 3: Rewrite ReportServiceTest**

Full replacement of `ReportServiceTest.java`:

```java
package com.kropholler.dev.hermes.report;

import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.ListingStatus;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryEntryDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private ListingService listingService;

    @InjectMocks
    private ReportService service;

    @Test
    void generateReport_computesPriceChange() {
        UUID listingId = UUID.randomUUID();
        Instant now = Instant.now();

        ListingDto listing = new ListingDto(
            listingId, "12345678", "https://funda.nl/...",
            "Teststraat", "1", null, "1234AB", "Amsterdam", "Noord-Holland",
            now.minus(30, ChronoUnit.DAYS), now, 380000, ListingStatus.FOR_SALE
        );

        List<PriceHistoryEntryDto> history = List.of(
            entry(now.minus(30, ChronoUnit.DAYS), 400000, "asking_price"),
            entry(now.minus(10, ChronoUnit.DAYS), 380000, "asking_price")
        );

        when(listingService.findById(listingId)).thenReturn(Optional.of(listing));
        when(listingService.findPriceHistoryByListingId(listingId)).thenReturn(history);

        Optional<ListingReport> result = service.generateReport(listingId);

        assertThat(result).isPresent();
        assertThat(result.get().initialPrice()).isEqualTo(400000);
        assertThat(result.get().currentPrice()).isEqualTo(380000);
        assertThat(result.get().priceChangePct()).isEqualTo(-5.0);
        assertThat(result.get().priceHistory()).hasSize(2);
    }

    @Test
    void generateReport_returnsEmptyWhenListingNotFound() {
        UUID id = UUID.randomUUID();
        when(listingService.findById(id)).thenReturn(Optional.empty());

        assertThat(service.generateReport(id)).isEmpty();
    }

    @Test
    void generateReport_returnsEmptyWhenNoPriceHistory() {
        UUID listingId = UUID.randomUUID();
        Instant now = Instant.now();

        ListingDto listing = new ListingDto(
            listingId, "12345678", "https://funda.nl/...",
            "Teststraat", "1", null, "1234AB", "Amsterdam", "Noord-Holland",
            now.minus(5, ChronoUnit.DAYS), now, null, null
        );

        when(listingService.findById(listingId)).thenReturn(Optional.of(listing));
        when(listingService.findPriceHistoryByListingId(listingId)).thenReturn(List.of());

        assertThat(service.generateReport(listingId)).isEmpty();
    }

    @Test
    void generateReport_currentStatusFromListing() {
        UUID listingId = UUID.randomUUID();
        Instant now = Instant.now();

        ListingDto listing = new ListingDto(
            listingId, "12345678", "https://funda.nl/...",
            "Teststraat", "1", null, "1234AB", "Amsterdam", "Noord-Holland",
            now.minus(10, ChronoUnit.DAYS), now, 400000, ListingStatus.SOLD
        );

        when(listingService.findById(listingId)).thenReturn(Optional.of(listing));
        when(listingService.findPriceHistoryByListingId(listingId))
            .thenReturn(List.of(entry(now.minus(10, ChronoUnit.DAYS), 400000, "asking_price")));

        ListingReport report = service.generateReport(listingId).orElseThrow();

        assertThat(report.currentStatus()).isEqualTo(ListingStatus.SOLD);
    }

    private PriceHistoryEntryDto entry(Instant timestamp, int price, String status) {
        return new PriceHistoryEntryDto(UUID.randomUUID(), price, status, "walter",
            LocalDate.now().minusDays(10), timestamp);
    }
}
```

- [ ] **Step 4: Run tests to confirm they fail**

```
cd hermes-backend && ./mvnw test -Dtest=ReportServiceTest -q
```

Expected: compilation errors — `ReportService` still references old code.

- [ ] **Step 5: Rewrite ReportService**

Full replacement:

```java
package com.kropholler.dev.hermes.report;

import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryEntryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ListingService listingService;

    @Transactional(readOnly = true)
    public Optional<ListingReport> generateReport(UUID listingId) {
        return listingService.findById(listingId).map(listing -> {
            List<PriceHistoryEntryDto> history = listingService.findPriceHistoryByListingId(listingId);

            List<PriceHistoryEntryDto> askingPrices = history.stream()
                .filter(e -> "asking_price".equals(e.status()))
                .toList();

            if (askingPrices.isEmpty()) return null;

            Integer initialPrice = askingPrices.get(0).price();
            Integer currentPrice = askingPrices.get(askingPrices.size() - 1).price();

            Double priceChangePct = null;
            if (initialPrice != null && currentPrice != null && initialPrice != 0) {
                priceChangePct = Math.round(
                    ((currentPrice - initialPrice) / (double) initialPrice * 100) * 10.0) / 10.0;
            }

            long daysInHermes = ChronoUnit.DAYS.between(
                listing.firstSeenAt().atZone(ZoneOffset.UTC).toLocalDate(), LocalDate.now());

            List<PricePoint> priceHistory = askingPrices.stream()
                .map(e -> new PricePoint(e.timestamp(), e.price()))
                .toList();

            return new ListingReport(
                listingId, daysInHermes,
                currentPrice, initialPrice, priceChangePct,
                priceHistory, listing.status()
            );
        });
    }
}
```

- [ ] **Step 6: Run tests to confirm they pass**

```
cd hermes-backend && ./mvnw test -Dtest=ReportServiceTest -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/report/PricePoint.java hermes-backend/src/main/java/com/kropholler/dev/hermes/report/ListingReport.java hermes-backend/src/main/java/com/kropholler/dev/hermes/report/ReportService.java hermes-backend/src/test/java/com/kropholler/dev/hermes/report/ReportServiceTest.java
git commit -m "feat(hermes-backend): rewrite ReportService to use price history entries"
```

---

## Task 14: ListingSummaryGenerationService

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/internal/ListingSummaryGenerationService.java`

- [ ] **Step 1: Rewrite ListingSummaryGenerationService**

Full replacement:

```java
package com.kropholler.dev.hermes.ai.internal;

import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryUpdated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListingSummaryGenerationService {

    private final ListingSummaryRepository summaryRepository;
    private final ListingService listingService;
    private final ChatClient.Builder chatClientBuilder;

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPriceHistoryUpdated(PriceHistoryUpdated event) {
        ChatClient chatClient = chatClientBuilder.build();
        for (UUID listingId : event.listingIds()) {
            listingService.findById(listingId).ifPresent(listing -> {
                String summary = generateSummary(chatClient, listing);
                upsertSummary(listingId, summary);
            });
        }
    }

    private String generateSummary(ChatClient chatClient, ListingDto listing) {
        String prompt = buildPrompt(listing);
        try {
            return chatClient.prompt().user(prompt).call().content();
        } catch (Exception e) {
            log.error("Failed to generate AI summary for listing {}", listing.id(), e);
            return "Summary not available.";
        }
    }

    private String buildPrompt(ListingDto listing) {
        return String.format(
            """
            Write a concise, plain-language summary (2-3 sentences) of this Dutch property listing.
            Include the key selling points, price, and location.

            Address: %s %s%s, %s %s, %s
            Price: €%s
            Status: %s
            """,
            listing.street(), listing.houseNumber(),
            listing.houseNumberAddition() != null ? " " + listing.houseNumberAddition() : "",
            listing.zipCode(), listing.city(), listing.province(),
            listing.currentPrice() != null ? String.format("%,d", listing.currentPrice()) : "unknown",
            listing.status() != null ? listing.status().name() : "unknown"
        );
    }

    private void upsertSummary(UUID listingId, String summaryText) {
        ListingSummary summary = summaryRepository.findByListingId(listingId)
            .orElseGet(() -> {
                ListingSummary s = new ListingSummary();
                s.setListingId(listingId);
                return s;
            });
        summary.setSummary(summaryText);
        summary.setGeneratedAt(Instant.now());
        summaryRepository.save(summary);
    }
}
```

- [ ] **Step 2: Compile**

```
cd hermes-backend && ./mvnw compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/internal/ListingSummaryGenerationService.java
git commit -m "feat(hermes-backend): update ListingSummaryGenerationService to use PriceHistoryUpdated"
```

---

## Task 15: NightlyRescrapeScheduler + DeletedListingCleanupScheduler

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/NightlyRescrapeScheduler.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/DeletedListingCleanupScheduler.java`

- [ ] **Step 1: Update NightlyRescrapeScheduler**

Full replacement:

```java
package com.kropholler.dev.hermes;

import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryService;
import com.kropholler.dev.hermes.scraping.ScrapingQueueService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class NightlyRescrapeScheduler {

    private final ListingService listingService;
    private final ScrapingQueueService queueService;
    private final PriceHistoryService priceHistoryService;
    private final ObservationRegistry observationRegistry;

    @Scheduled(cron = "0 0 2 * * *")
    public void enqueueNightlyRescrapes() {
        Observation.createNotStarted("scheduler.nightly-rescrape", observationRegistry)
            .observe(this::doEnqueue);
    }

    private void doEnqueue() {
        log.info("Starting nightly rescrape job");
        int count = 0;
        int page = 0;
        Page<ListingDto> batch;

        do {
            batch = listingService.findAllActive(PageRequest.of(page, 100));
            for (ListingDto listing : batch.getContent()) {
                queueService.enqueueRescrape(listing.url(), listing.city());
                count++;
            }
            page++;
        } while (batch.hasNext());

        log.info("Nightly rescrape enqueued {} sessions, starting price history refresh", count);
        priceHistoryService.refreshAll();
        log.info("Nightly rescrape job complete");
    }
}
```

- [ ] **Step 2: Add findAllActive to ListingService**

Add the following method to `ListingService.java` after `findAll`:

```java
@Transactional(readOnly = true)
public Page<ListingDto> findAllActive(Pageable pageable) {
    return listingRepository.findAllByDeletedAtIsNull(pageable).map(this::toDto);
}
```

- [ ] **Step 3: Create DeletedListingCleanupScheduler**

```java
package com.kropholler.dev.hermes;

import com.kropholler.dev.hermes.listing.data.ListingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
class DeletedListingCleanupScheduler {

    private final ListingRepository listingRepository;

    @Scheduled(cron = "0 0 3 1,15 * *")
    @Transactional
    public void cleanupDeletedListings() {
        log.info("Starting biweekly deleted listing cleanup");
        listingRepository.deleteAllByDeletedAtIsNotNull();
        log.info("Deleted listing cleanup complete");
    }
}
```

- [ ] **Step 4: Compile**

```
cd hermes-backend && ./mvnw compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/NightlyRescrapeScheduler.java hermes-backend/src/main/java/com/kropholler/dev/hermes/DeletedListingCleanupScheduler.java hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingService.java
git commit -m "feat(hermes-backend): add nightly price history refresh and biweekly cleanup scheduler"
```

---

## Task 16: Remove dead code

**Files to delete:**
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingSnapshot.java`
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingSnapshotRepository.java`
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingSnapshotDto.java`
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingSnapshotsCreated.java`
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/report/StatusPoint.java`

- [ ] **Step 1: Delete the dead files**

```
cd hermes-backend
rm src/main/java/com/kropholler/dev/hermes/listing/internal/ListingSnapshot.java
rm src/main/java/com/kropholler/dev/hermes/listing/internal/ListingSnapshotRepository.java
rm src/main/java/com/kropholler/dev/hermes/listing/ListingSnapshotDto.java
rm src/main/java/com/kropholler/dev/hermes/listing/ListingSnapshotsCreated.java
rm src/main/java/com/kropholler/dev/hermes/report/StatusPoint.java
```

- [ ] **Step 2: Also remove the dead RawListing fields**

`RawListing.java` still has `livingAreaM2`, `rooms`, `energyLabel`, `listedOnFundaSince` — these are no longer consumed by `ListingPersistenceService`. Remove them.

Full replacement of `RawListing.java`:

```java
package com.kropholler.dev.hermes.scraping;

public record RawListing(
    String fundaId,
    String url,
    String street,
    String houseNumber,
    String houseNumberAddition,
    String zipCode,
    String city,
    String province,
    Integer askingPrice,
    String status
) {}
```

Update `FundaProxyClient.toRawListing` accordingly:

```java
private RawListing toRawListing(FundaProxyListing p) {
    return new RawListing(
        p.globalId() != null ? p.globalId().toString() : p.tinyId(),
        p.url(),
        p.street(),
        p.houseNumber(),
        p.houseNumberSuffix(),
        p.zipCode(),
        p.city(),
        p.province(),
        p.askingPrice(),
        p.status()
    );
}
```

Remove the `parseDate` method from `FundaProxyClient` — it is no longer used.

- [ ] **Step 3: Update ListingPersistenceServiceTest to use the slimmed RawListing constructor**

In `ListingPersistenceServiceTest`, replace the 14-arg `RawListing` constructor calls with the new 10-arg form:

```java
RawListing raw = new RawListing(
    "12345678", "https://www.funda.nl/koop/amsterdam/huis-12345678/",
    "Teststraat", "10", null, "1234AB", "amsterdam", "Noord-Holland",
    450000, "FOR_SALE"
);
```

And in the second test, change `"UNDER_OFFER"` accordingly:

```java
RawListing raw = new RawListing(
    "12345678", "https://www.funda.nl/koop/amsterdam/huis-12345678/",
    "Teststraat", "10", null, "1234AB", "amsterdam", "Noord-Holland",
    460000, "UNDER_OFFER"
);
```

- [ ] **Step 4: Run full test suite**

```
cd hermes-backend && ./mvnw test -q
```

Expected: `BUILD SUCCESS` with all tests passing.

- [ ] **Step 5: Commit**

```
git add -A
git commit -m "chore(hermes-backend): remove ListingSnapshot and dead scraping fields"
```
