# Listing Details Enrichment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

## Progress

| Task | Status | Commit |
|------|--------|--------|
| Task 1: funda-proxy — add description and plot_area_m2 | ✅ Done | a801c41 |
| Task 2: Backend — extend RawListing and FundaProxyListing/Client | ✅ Done | b86c8fb |
| Task 3: Backend — DB migration and Listing entity | ✅ Done | 6973b0a |
| Task 4: Backend — FetchListingDetailsCommand, JmsQueues, FundaProxyFacade | ✅ Done | 1dcd42d |
| Task 5: Backend — ListingPersistenceService sends details command for all listings | ✅ Done | 08dae9f |
| Task 6: Backend — ListingDetailsConsumer | ✅ Done | 328669e |
| Task 7: Backend — ListingDto, ListingService, and search infrastructure | ✅ Done | 5c5d720 |
| Task 8: Backend — OpenAPI spec and ListingController | ✅ Done | 8480d6a |
| Task 9: Frontend — types, service, listings page filters | ✅ Done | 258e9a9 |
| Task 10: Frontend — listing detail page enrichment display | ✅ Done | 37e6081 |

**Goal:** Persist and display six new listing fields (description, living area, plot area, rooms, bedrooms, energy label) fetched asynchronously from the funda-proxy detail endpoint; make four of them searchable.

**Architecture:** When a scraping session completes, `ListingPersistenceService` enqueues a `FetchListingDetailsCommand` for every listing (new and existing). `ListingDetailsConsumer` dequeues it, calls the funda-proxy `/listings/{id}` endpoint, and writes all six enrichment fields onto the `Listing` row. This mirrors the existing price-history pattern exactly, so rescraping a listing automatically refreshes its details.

**Tech Stack:** Python/FastAPI (funda-proxy), Spring Boot 3 / JPA / JMS / Flyway (backend), Angular 22 standalone components / Tailwind CSS (frontend)

---

## File Map

**Created:**
- `funda-proxy/tests/test_models.py` — updated (existing file)
- `hermes-backend/src/main/resources/db/migration/V4__add_listing_details.sql`
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/FetchListingDetailsCommand.java`
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingDetailsConsumer.java`
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/internal/ListingDetailsConsumerTest.java`

**Modified:**
- `funda-proxy/models.py` — add `description`, `plot_area_m2`
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/RawListing.java` — add 6 new fields
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/internal/FundaProxyListing.java` — add `description`, `plotAreaM2`
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/internal/FundaProxyClient.java` — make `getListing()` public, update `toRawListing()`
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/FundaProxyFacade.java` — add `getListing()`
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/Listing.java` — add 6 fields
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/JmsQueues.java` — add `LISTING_DETAILS_FETCH`
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceService.java` — enqueue details command for all listings
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingDto.java` — add 6 fields
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingService.java` — update `toDto()`
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingSearchParams.java` — add 4 filter fields
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingSpecifications.java` — add `andIfAtLeast()`
- `hermes-backend/src/main/resources/openapi/api.yaml` — add fields + query params
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/api/ListingController.java` — add params, map fields
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceServiceTest.java` — fix `RawListing` construction, update assertions
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingServiceSearchTest.java` — fix `ListingSearchParams` construction
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/api/ListingControllerSearchTest.java` — fix `ListingSearchParams` construction, add new param tests
- `hermes-frontend/src/app/core/api.types.ts` — add 6 fields to `ListingDetailResponse`, 4 to `ListingSearchFilter`
- `hermes-frontend/src/app/core/listings.service.ts` — pass 4 new filter params
- `hermes-frontend/src/app/pages/listings/listings-page.component.ts` — add 4 new filter fields
- `hermes-frontend/src/app/pages/listings/listings-page.component.html` — add second filter row
- `hermes-frontend/src/app/pages/listing-detail/listing-detail-page.component.html` — add stat-cards + description block

---

## Task 1: funda-proxy — add description and plot_area_m2

**Files:**
- Modify: `funda-proxy/models.py`
- Modify: `funda-proxy/tests/test_models.py`

- [ ] **Step 1: Update the test to cover the two new fields**

In `funda-proxy/tests/test_models.py`, in the `_make_listing()` helper add:
```python
m.description = "Ruim appartement met balkon"
m.areas.plot = 85
```

In `test_from_listing_maps_all_fields`, add assertions:
```python
assert result.description == "Ruim appartement met balkon"
assert result.plot_area_m2 == 85
```

- [ ] **Step 2: Run the test to verify it fails**

```
cd funda-proxy
python -m pytest tests/test_models.py::test_from_listing_maps_all_fields -v
```

Expected: FAIL — `ListingResponse` has no field `description`

- [ ] **Step 3: Add the two fields to `ListingResponse` in `funda-proxy/models.py`**

After `energy_label`:
```python
description: str | None = None
plot_area_m2: int | None = None
```

In `from_listing()`, after `energy_label=...`:
```python
description=listing.description,
plot_area_m2=getattr(listing.areas, "plot", None),
```

- [ ] **Step 4: Run all funda-proxy tests**

```
cd funda-proxy
python -m pytest -v
```

Expected: all tests PASS

- [ ] **Step 5: Commit**

```bash
git add funda-proxy/models.py funda-proxy/tests/test_models.py
git commit -m "feat(funda-proxy): expose description and plot_area_m2 in ListingResponse"
```

---

## Task 2: Backend — extend RawListing and FundaProxyListing/Client

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/RawListing.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/internal/FundaProxyListing.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/internal/FundaProxyClient.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceServiceTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/scraping/internal/ScrapingWorkerTest.java` (if it constructs RawListing)

**Context:** `RawListing` is a Java record in the `scraping` package — the public data contract between the scraping module and the listing module. Adding fields is a breaking change: every call site that constructs a `RawListing` must be updated. The only production call site is `FundaProxyClient.toRawListing()`. Tests also construct `RawListing` inline.

- [ ] **Step 1: Update `RawListing.java`**

Replace the entire file content:
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
    String status,
    String description,
    Integer livingAreaM2,
    Integer rooms,
    Integer bedrooms,
    String energyLabel,
    Integer plotAreaM2
) {}
```

- [ ] **Step 2: Add `description` and `plotAreaM2` to `FundaProxyListing.java`**

Add after the `energyLabel` line (line 19):
```java
@JsonProperty("description")   String description,
@JsonProperty("plot_area_m2")  Integer plotAreaM2,
```

The full record after editing:
```java
package com.kropholler.dev.hermes.scraping.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FundaProxyListing(
    @JsonProperty("global_id")            Long globalId,
    @JsonProperty("tiny_id")              String tinyId,
    @JsonProperty("url")                  String url,
    @JsonProperty("street")               String street,
    @JsonProperty("house_number")         String houseNumber,
    @JsonProperty("house_number_suffix")  String houseNumberSuffix,
    @JsonProperty("zip_code")             String zipCode,
    @JsonProperty("city")                 String city,
    @JsonProperty("province")             String province,
    @JsonProperty("asking_price")         Integer askingPrice,
    @JsonProperty("living_area_m2")       Integer livingAreaM2,
    @JsonProperty("rooms")                Integer rooms,
    @JsonProperty("bedrooms")             Integer bedrooms,
    @JsonProperty("energy_label")         String energyLabel,
    @JsonProperty("description")          String description,
    @JsonProperty("plot_area_m2")         Integer plotAreaM2,
    @JsonProperty("publication_date")     String publicationDate,
    @JsonProperty("status")              String status,
    @JsonProperty("offering_type")        String offeringType
) {}
```

- [ ] **Step 3: Update `FundaProxyClient.toRawListing()` and make `getListing()` public**

In `FundaProxyClient.java`, change `Optional<RawListing> getListing(...)` to `public Optional<RawListing> getListing(...)`.

Replace `toRawListing()`:
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
        p.status(),
        p.description(),
        p.livingAreaM2(),
        p.rooms(),
        p.bedrooms(),
        p.energyLabel(),
        p.plotAreaM2()
    );
}
```

- [ ] **Step 4: Fix `RawListing` construction in `ListingPersistenceServiceTest.java`**

There are two `new RawListing(...)` calls in this test. Both need 6 `null` arguments appended.

Replace the first (in `newListing_setsStatusAndSendsFetchCommand`):
```java
RawListing raw = new RawListing(
    "12345678", "https://www.funda.nl/koop/amsterdam/huis-12345678/",
    "Teststraat", "10", null, "1234AB", "amsterdam", "Noord-Holland",
    450000, "FOR_SALE",
    null, null, null, null, null, null
);
```

Replace the second (in `existingListing_updatesStatusAndDoesNotSendFetchCommand`):
```java
RawListing raw = new RawListing(
    "12345678", "https://www.funda.nl/koop/amsterdam/huis-12345678/",
    "Teststraat", "10", null, "1234AB", "amsterdam", "Noord-Holland",
    460000, "UNDER_OFFER",
    null, null, null, null, null, null
);
```

- [ ] **Step 5: Build the backend to verify compilation**

```
cd hermes-backend
./mvnw compile -q
```

Expected: BUILD SUCCESS (no compilation errors)

- [ ] **Step 6: Run backend tests**

```
cd hermes-backend
./mvnw test -pl . -Dtest="ListingPersistenceServiceTest,ScrapingWorkerTest" -q
```

Expected: Tests pass (note: `existingListing_updatesStatusAndDoesNotSendFetchCommand` will still pass since details command isn't wired yet)

- [ ] **Step 7: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/RawListing.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/internal/FundaProxyListing.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/internal/FundaProxyClient.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceServiceTest.java
git commit -m "feat(scraping): extend RawListing with 6 enrichment fields"
```

---

## Task 3: Backend — DB migration and Listing entity

**Files:**
- Create: `hermes-backend/src/main/resources/db/migration/V4__add_listing_details.sql`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/Listing.java`

- [ ] **Step 1: Create the Flyway migration**

Create `hermes-backend/src/main/resources/db/migration/V4__add_listing_details.sql`:
```sql
ALTER TABLE listings ADD COLUMN description TEXT;
ALTER TABLE listings ADD COLUMN living_area_m2 INTEGER;
ALTER TABLE listings ADD COLUMN rooms INTEGER;
ALTER TABLE listings ADD COLUMN bedrooms INTEGER;
ALTER TABLE listings ADD COLUMN energy_label VARCHAR(10);
ALTER TABLE listings ADD COLUMN plot_area_m2 INTEGER;
```

- [ ] **Step 2: Add the six fields to `Listing.java`**

After the `province` field (line 33), add:
```java
@Column(columnDefinition = "TEXT")
private String description;

private Integer livingAreaM2;
private Integer rooms;
private Integer bedrooms;

@Column(length = 10)
private String energyLabel;

private Integer plotAreaM2;
```

The full class after editing (fields section only, rest unchanged):
```java
private String street;
private String houseNumber;
private String houseNumberAddition;
private String zipCode;
private String city;
private String province;

@Column(columnDefinition = "TEXT")
private String description;
private Integer livingAreaM2;
private Integer rooms;
private Integer bedrooms;
@Column(length = 10)
private String energyLabel;
private Integer plotAreaM2;
```

- [ ] **Step 3: Build to verify compilation**

```
cd hermes-backend
./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add hermes-backend/src/main/resources/db/migration/V4__add_listing_details.sql \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/Listing.java
git commit -m "feat(listing): add 6 enrichment columns to listings table and entity"
```

---

## Task 4: Backend — FetchListingDetailsCommand, JmsQueues, FundaProxyFacade

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/JmsQueues.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/FetchListingDetailsCommand.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/FundaProxyFacade.java`

- [ ] **Step 1: Add the queue constant to `JmsQueues.java`**

```java
package com.kropholler.dev.hermes.listing.internal;

public final class JmsQueues {
    public static final String PRICE_HISTORY_FETCH  = "price.history.fetch";
    public static final String LISTING_DETAILS_FETCH = "listing.details.fetch";

    private JmsQueues() {}
}
```

- [ ] **Step 2: Create `FetchListingDetailsCommand.java`**

```java
package com.kropholler.dev.hermes.listing.internal;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

public record FetchListingDetailsCommand(UUID listingId, String fundaId) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
```

- [ ] **Step 3: Add `getListing()` to `FundaProxyFacade.java`**

```java
package com.kropholler.dev.hermes.scraping;

import com.kropholler.dev.hermes.scraping.internal.FundaProxyClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class FundaProxyFacade {

    private final FundaProxyClient client;

    public List<RawPriceChange> getPriceHistory(String fundaId) {
        return client.getPriceHistory(fundaId);
    }

    public Optional<RawListing> getListing(String fundaId) {
        return client.getListing(fundaId);
    }
}
```

- [ ] **Step 4: Build to verify compilation**

```
cd hermes-backend
./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/JmsQueues.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/FetchListingDetailsCommand.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/FundaProxyFacade.java
git commit -m "feat(listing): add FetchListingDetailsCommand and FundaProxyFacade.getListing()"
```

---

## Task 5: Backend — ListingPersistenceService sends details command for all listings

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceService.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceServiceTest.java`

**Context:** The current `onScrapingSessionCompleted()` only sends a `FetchPriceHistoryCommand` for new listings. We need to also send a `FetchListingDetailsCommand` for ALL listings (new and existing). The price history guard stays — price history is still only fetched once for new listings.

- [ ] **Step 1: Update the tests first**

In `ListingPersistenceServiceTest.java`, replace the full file:

```java
package com.kropholler.dev.hermes.listing.internal;

import com.kropholler.dev.hermes.listing.ListingStatus;
import com.kropholler.dev.hermes.scraping.ListingNotFound;
import com.kropholler.dev.hermes.scraping.RawListing;
import com.kropholler.dev.hermes.scraping.ScrapingSessionCompleted;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListingPersistenceServiceTest {

    @Mock private ListingRepository listingRepository;
    @Mock private JmsTemplate jmsTemplate;

    @InjectMocks
    private ListingPersistenceService service;

    private static RawListing rawListing(String fundaId, String status) {
        return new RawListing(
            fundaId, "https://www.funda.nl/koop/amsterdam/huis-" + fundaId + "/",
            "Teststraat", "10", null, "1234AB", "amsterdam", "Noord-Holland",
            450000, status,
            null, null, null, null, null, null
        );
    }

    @Test
    void newListing_setsStatusAndSendsBothFetchCommands() {
        RawListing raw = rawListing("12345678", "FOR_SALE");
        ScrapingSessionCompleted event = new ScrapingSessionCompleted(UUID.randomUUID(), List.of(raw));

        Listing saved = new Listing();
        saved.setId(UUID.randomUUID());
        saved.setFundaId("12345678");
        when(listingRepository.findByFundaId("12345678")).thenReturn(Optional.empty());
        when(listingRepository.save(any())).thenReturn(saved);

        service.onScrapingSessionCompleted(event);

        ArgumentCaptor<Listing> listingCaptor = ArgumentCaptor.forClass(Listing.class);
        verify(listingRepository).save(listingCaptor.capture());
        assertThat(listingCaptor.getValue().getStatus()).isEqualTo(ListingStatus.FOR_SALE);
        assertThat(listingCaptor.getValue().getLastUpdatedAt()).isNotNull();

        // Details command sent for all listings
        ArgumentCaptor<FetchListingDetailsCommand> detailsCaptor =
            ArgumentCaptor.forClass(FetchListingDetailsCommand.class);
        verify(jmsTemplate).convertAndSend(eq(JmsQueues.LISTING_DETAILS_FETCH), detailsCaptor.capture());
        assertThat(detailsCaptor.getValue().fundaId()).isEqualTo("12345678");
        assertThat(detailsCaptor.getValue().listingId()).isEqualTo(saved.getId());

        // Price history command sent only for new listings
        ArgumentCaptor<FetchPriceHistoryCommand> priceCaptor =
            ArgumentCaptor.forClass(FetchPriceHistoryCommand.class);
        verify(jmsTemplate).convertAndSend(eq(JmsQueues.PRICE_HISTORY_FETCH), priceCaptor.capture());
        assertThat(priceCaptor.getValue().fundaId()).isEqualTo("12345678");
    }

    @Test
    void existingListing_updatesStatusAndSendsDetailsCommandOnly() {
        Listing existing = new Listing();
        existing.setId(UUID.randomUUID());
        existing.setFundaId("12345678");

        RawListing raw = rawListing("12345678", "UNDER_OFFER");
        ScrapingSessionCompleted event = new ScrapingSessionCompleted(UUID.randomUUID(), List.of(raw));

        when(listingRepository.findByFundaId("12345678")).thenReturn(Optional.of(existing));
        when(listingRepository.save(any())).thenReturn(existing);

        service.onScrapingSessionCompleted(event);

        assertThat(existing.getStatus()).isEqualTo(ListingStatus.UNDER_OFFER);

        // Details command sent for existing listing too
        verify(jmsTemplate).convertAndSend(eq(JmsQueues.LISTING_DETAILS_FETCH),
            any(FetchListingDetailsCommand.class));

        // Price history command NOT sent for existing listings
        verify(jmsTemplate, never()).convertAndSend(eq(JmsQueues.PRICE_HISTORY_FETCH), any());
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

- [ ] **Step 2: Run tests to verify they fail (details command not yet sent)**

```
cd hermes-backend
./mvnw test -Dtest="ListingPersistenceServiceTest" -q
```

Expected: FAIL — `newListing_setsStatusAndSendsBothFetchCommands` fails because no LISTING_DETAILS_FETCH call is made yet

- [ ] **Step 3: Update `ListingPersistenceService.onScrapingSessionCompleted()`**

Replace the `onScrapingSessionCompleted` method body in `ListingPersistenceService.java`. Also add the import for `FetchListingDetailsCommand`.

The updated method:
```java
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
        Listing saved = listingRepository.save(listing);

        // Always enqueue detail fetch — runs on both initial scrape and rescrape
        jmsTemplate.convertAndSend(JmsQueues.LISTING_DETAILS_FETCH,
            new FetchListingDetailsCommand(saved.getId(), saved.getFundaId()));

        if (isNew) {
            jmsTemplate.convertAndSend(JmsQueues.PRICE_HISTORY_FETCH,
                new FetchPriceHistoryCommand(saved.getId(), saved.getFundaId()));
        }
    }
}
```

Add import at the top of the file:
```java
import com.kropholler.dev.hermes.listing.internal.FetchListingDetailsCommand;
```

- [ ] **Step 4: Run the tests to verify they pass**

```
cd hermes-backend
./mvnw test -Dtest="ListingPersistenceServiceTest" -q
```

Expected: all 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceService.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceServiceTest.java
git commit -m "feat(listing): enqueue FetchListingDetailsCommand for all listings on scrape"
```

---

## Task 6: Backend — ListingDetailsConsumer

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingDetailsConsumer.java`
- Create: `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/internal/ListingDetailsConsumerTest.java`

- [ ] **Step 1: Write the test**

Create `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/internal/ListingDetailsConsumerTest.java`:

```java
package com.kropholler.dev.hermes.listing.internal;

import com.kropholler.dev.hermes.scraping.FundaProxyFacade;
import com.kropholler.dev.hermes.scraping.RawListing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListingDetailsConsumerTest {

    @Mock private ListingRepository listingRepository;
    @Mock private FundaProxyFacade proxyFacade;

    @InjectMocks
    private ListingDetailsConsumer consumer;

    private static RawListing richListing(String fundaId) {
        return new RawListing(
            fundaId, "https://www.funda.nl/koop/amsterdam/huis-" + fundaId + "/",
            "Teststraat", "10", null, "1234AB", "amsterdam", "Noord-Holland",
            450000, "FOR_SALE",
            "Mooie woning", 95, 4, 3, "A", 120
        );
    }

    @Test
    void onMessage_updatesAllSixFields_whenProxyReturnData() {
        UUID listingId = UUID.randomUUID();
        FetchListingDetailsCommand command = new FetchListingDetailsCommand(listingId, "12345678");

        RawListing raw = richListing("12345678");
        when(proxyFacade.getListing("12345678")).thenReturn(Optional.of(raw));

        Listing listing = new Listing();
        listing.setId(listingId);
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(listingRepository.save(any())).thenReturn(listing);

        consumer.onMessage(command);

        ArgumentCaptor<Listing> captor = ArgumentCaptor.forClass(Listing.class);
        verify(listingRepository).save(captor.capture());
        Listing saved = captor.getValue();
        assertThat(saved.getDescription()).isEqualTo("Mooie woning");
        assertThat(saved.getLivingAreaM2()).isEqualTo(95);
        assertThat(saved.getRooms()).isEqualTo(4);
        assertThat(saved.getBedrooms()).isEqualTo(3);
        assertThat(saved.getEnergyLabel()).isEqualTo("A");
        assertThat(saved.getPlotAreaM2()).isEqualTo(120);
    }

    @Test
    void onMessage_doesNothing_whenProxyReturnsEmpty() {
        UUID listingId = UUID.randomUUID();
        FetchListingDetailsCommand command = new FetchListingDetailsCommand(listingId, "99999999");

        when(proxyFacade.getListing("99999999")).thenReturn(Optional.empty());

        consumer.onMessage(command);

        verify(listingRepository, never()).save(any());
    }

    @Test
    void onMessage_doesNothing_whenListingNotFoundInDb() {
        UUID listingId = UUID.randomUUID();
        FetchListingDetailsCommand command = new FetchListingDetailsCommand(listingId, "12345678");

        RawListing raw = richListing("12345678");
        when(proxyFacade.getListing("12345678")).thenReturn(Optional.of(raw));
        when(listingRepository.findById(listingId)).thenReturn(Optional.empty());

        consumer.onMessage(command);

        verify(listingRepository, never()).save(any());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```
cd hermes-backend
./mvnw test -Dtest="ListingDetailsConsumerTest" -q
```

Expected: FAIL — `ListingDetailsConsumer` does not exist yet

- [ ] **Step 3: Implement `ListingDetailsConsumer.java`**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingDetailsConsumer.java`:

```java
package com.kropholler.dev.hermes.listing.internal;

import com.google.common.util.concurrent.RateLimiter;
import com.kropholler.dev.hermes.scraping.FundaProxyFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
class ListingDetailsConsumer {

    @SuppressWarnings("UnstableApiUsage")
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

- [ ] **Step 4: Run the tests to verify they pass**

```
cd hermes-backend
./mvnw test -Dtest="ListingDetailsConsumerTest" -q
```

Expected: all 3 tests PASS

- [ ] **Step 5: Run the full backend test suite**

```
cd hermes-backend
./mvnw test -q
```

Expected: all tests PASS

- [ ] **Step 6: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingDetailsConsumer.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/internal/ListingDetailsConsumerTest.java
git commit -m "feat(listing): add ListingDetailsConsumer to persist enrichment fields"
```

---

## Task 7: Backend — ListingDto, ListingService, and search infrastructure

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingDto.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingService.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingSearchParams.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingSpecifications.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingServiceSearchTest.java`

**Context:** `ListingSearchParams` is a record — adding fields is a breaking change. Two test files construct it inline: `ListingServiceSearchTest` and `ListingControllerSearchTest`. Fix them both here (controller test in next task).

- [ ] **Step 1: Update `ListingDto.java`**

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
    ListingStatus status,
    String description,
    Integer livingAreaM2,
    Integer rooms,
    Integer bedrooms,
    String energyLabel,
    Integer plotAreaM2
) {}
```

- [ ] **Step 2: Update `ListingService.toDto()` to include the new fields**

Replace the `toDto` method in `ListingService.java`:
```java
private ListingDto toDto(Listing l) {
    Integer currentPrice = priceHistoryRepository
        .findFirstByListingIdAndStatusOrderByTimestampDesc(l.getId(), "asking_price")
        .map(PriceHistoryEntry::getPrice)
        .orElse(null);
    return new ListingDto(
        l.getId(), l.getFundaId(), l.getUrl(),
        l.getStreet(), l.getHouseNumber(), l.getHouseNumberAddition(),
        l.getZipCode(), l.getCity(), l.getProvince(),
        l.getFirstSeenAt(), l.getLastSeenAt(), currentPrice, l.getStatus(),
        l.getDescription(), l.getLivingAreaM2(), l.getRooms(),
        l.getBedrooms(), l.getEnergyLabel(), l.getPlotAreaM2()
    );
}
```

- [ ] **Step 3: Update `ListingSearchParams.java`**

```java
package com.kropholler.dev.hermes.listing;

public record ListingSearchParams(
    String street,
    String houseNumber,
    String houseNumberAddition,
    String zipCode,
    String province,
    Integer minBedrooms,
    Integer minRooms,
    Integer minLivingAreaM2,
    String energyLabel
) {
    public boolean isEmpty() {
        return isBlank(street) && isBlank(houseNumber) && isBlank(houseNumberAddition)
            && isBlank(zipCode) && isBlank(province)
            && minBedrooms == null && minRooms == null && minLivingAreaM2 == null
            && isBlank(energyLabel);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
```

- [ ] **Step 4: Update `ListingSpecifications.java`**

```java
package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.internal.Listing;
import org.springframework.data.jpa.domain.Specification;

class ListingSpecifications {

    static Specification<Listing> withParams(ListingSearchParams params) {
        Specification<Listing> spec = (root, query, cb) -> cb.conjunction();
        spec = andIfPresent(spec, "street", params.street());
        spec = andIfPresent(spec, "houseNumber", params.houseNumber());
        spec = andIfPresent(spec, "houseNumberAddition", params.houseNumberAddition());
        spec = andIfPresent(spec, "zipCode", params.zipCode());
        spec = andIfPresent(spec, "province", params.province());
        spec = andIfPresent(spec, "energyLabel", params.energyLabel());
        spec = andIfAtLeast(spec, "bedrooms", params.minBedrooms());
        spec = andIfAtLeast(spec, "rooms", params.minRooms());
        spec = andIfAtLeast(spec, "livingAreaM2", params.minLivingAreaM2());
        return spec;
    }

    private static Specification<Listing> andIfPresent(Specification<Listing> base, String field, String value) {
        if (value == null || value.isBlank()) return base;
        String pattern = "%" + value.toLowerCase() + "%";
        Specification<Listing> predicate = (root, query, cb) -> cb.like(cb.lower(root.get(field)), pattern);
        return base.and(predicate);
    }

    private static Specification<Listing> andIfAtLeast(Specification<Listing> base, String field, Integer value) {
        if (value == null) return base;
        return base.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get(field), value));
    }
}
```

- [ ] **Step 5: Fix `ListingServiceSearchTest.java`**

`ListingSearchParams` now has 9 constructor parameters. All 4 existing `new ListingSearchParams(...)` calls need updating.

Replace the full file:
```java
package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.internal.ListingRepository;
import com.kropholler.dev.hermes.listing.internal.PriceHistoryEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListingServiceSearchTest {

    @Mock private ListingRepository listingRepository;
    @Mock private PriceHistoryEntryRepository priceHistoryRepository;

    @InjectMocks
    private ListingService service;

    @Test
    void findAll_withNonEmptyParams_usesSpecificationPath() {
        var params = new ListingSearchParams("Teststraat", null, null, null, null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 20);
        when(listingRepository.findAll(any(Specification.class), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of()));

        service.findAll(params, pageable);

        verify(listingRepository).findAll(any(Specification.class), eq(pageable));
        verify(listingRepository, never()).findAll(eq(pageable));
    }

    @Test
    void findAll_withEmptyParams_usesSimplePath() {
        var params = new ListingSearchParams(null, null, null, null, null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 20);
        when(listingRepository.findAll(eq(pageable)))
            .thenReturn(new PageImpl<>(List.of()));

        service.findAll(params, pageable);

        verify(listingRepository).findAll(eq(pageable));
        verify(listingRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void listingSearchParams_isEmpty_trueWhenAllBlank() {
        var params = new ListingSearchParams("", " ", null, "", null, null, null, null, null);
        assertThat(params.isEmpty()).isTrue();
    }

    @Test
    void listingSearchParams_isEmpty_falseWhenAnyNonBlank() {
        var params = new ListingSearchParams(null, null, null, "1234AB", null, null, null, null, null);
        assertThat(params.isEmpty()).isFalse();
    }

    @Test
    void listingSearchParams_isEmpty_falseWhenMinBedroomsSet() {
        var params = new ListingSearchParams(null, null, null, null, null, 2, null, null, null);
        assertThat(params.isEmpty()).isFalse();
    }

    @Test
    void listingSearchParams_isEmpty_falseWhenMinLivingAreaSet() {
        var params = new ListingSearchParams(null, null, null, null, null, null, null, 80, null);
        assertThat(params.isEmpty()).isFalse();
    }
}
```

- [ ] **Step 6: Run affected tests**

```
cd hermes-backend
./mvnw test -Dtest="ListingServiceSearchTest,ListingDetailsConsumerTest,ListingPersistenceServiceTest" -q
```

Expected: all tests PASS

- [ ] **Step 7: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingDto.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingService.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingSearchParams.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingSpecifications.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingServiceSearchTest.java
git commit -m "feat(listing): add enrichment fields to ListingDto and extend search params"
```

---

## Task 8: Backend — OpenAPI spec and ListingController

**Files:**
- Modify: `hermes-backend/src/main/resources/openapi/api.yaml`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/api/ListingController.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/api/ListingControllerSearchTest.java`

**Context:** The controller implements `ListingsApi` which is generated from `api.yaml` at build time. You must update the spec first, then rebuild, then update the controller to compile.

- [ ] **Step 1: Update `api.yaml` — add query params to `GET /api/listings`**

In the `parameters` list of `GET /api/listings`, after the existing `province` parameter, add:
```yaml
        - name: minBedrooms
          in: query
          schema:
            type: integer
            nullable: true
        - name: minRooms
          in: query
          schema:
            type: integer
            nullable: true
        - name: minLivingAreaM2
          in: query
          schema:
            type: integer
            nullable: true
        - name: energyLabel
          in: query
          schema:
            type: string
            nullable: true
```

- [ ] **Step 2: Update `api.yaml` — add fields to `ListingDetailResponse`**

In the `ListingDetailResponse` schema `properties`, after `status`, add:
```yaml
        description:
          type: string
          nullable: true
        livingAreaM2:
          type: integer
          nullable: true
        plotAreaM2:
          type: integer
          nullable: true
        rooms:
          type: integer
          nullable: true
        bedrooms:
          type: integer
          nullable: true
        energyLabel:
          type: string
          nullable: true
```

- [ ] **Step 3: Rebuild to regenerate the API interface**

```
cd hermes-backend
./mvnw generate-sources -q
```

Expected: BUILD SUCCESS — the `ListingsApi` interface now has `minBedrooms`, `minRooms`, `minLivingAreaM2`, `energyLabel` parameters in `getListings()`, and `ListingDetailResponse` has the new fields.

- [ ] **Step 4: Update `ListingController.java`**

Update the `getListings` method signature to include the four new parameters and construct `ListingSearchParams` with them:
```java
@Override
public ResponseEntity<ListingPage> getListings(Integer page, Integer size,
        String street, String houseNumber, String houseNumberAddition,
        String zipCode, String province,
        Integer minBedrooms, Integer minRooms, Integer minLivingAreaM2, String energyLabel) {
    ListingSearchParams params = new ListingSearchParams(
        street, houseNumber, houseNumberAddition, zipCode, province,
        minBedrooms, minRooms, minLivingAreaM2, energyLabel
    );
    Page<ListingDto> result = listingService.findAll(params, PageRequest.of(page, size));
    ListingPage response = new ListingPage()
        .content(result.getContent().stream().map(this::toSummaryResponse).toList())
        .totalElements(result.getTotalElements())
        .totalPages(result.getTotalPages())
        .page(result.getNumber())
        .size(result.getSize());
    return ResponseEntity.ok(response);
}
```

Update `toDetailResponse()` to map the six new fields:
```java
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
        .status(dto.status() != null ? dto.status().name() : null)
        .description(dto.description())
        .livingAreaM2(dto.livingAreaM2())
        .plotAreaM2(dto.plotAreaM2())
        .rooms(dto.rooms())
        .bedrooms(dto.bedrooms())
        .energyLabel(dto.energyLabel());
}
```

- [ ] **Step 5: Update `ListingControllerSearchTest.java`**

`ListingSearchParams` now takes 9 args. Update the assertions and add tests for the new params. Replace the full file:

```java
package com.kropholler.dev.hermes.api;

import com.kropholler.dev.hermes.ai.ListingSummaryService;
import com.kropholler.dev.hermes.listing.ListingSearchParams;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.report.ReportService;
import com.kropholler.dev.hermes.scraping.ScrapingQueueService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ListingController.class)
class ListingControllerSearchTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ListingService listingService;
    @MockitoBean ScrapingQueueService queueService;
    @MockitoBean ReportService reportService;
    @MockitoBean ListingSummaryService summaryService;

    @Test
    void getListings_passesStreetParamToService() throws Exception {
        when(listingService.findAll(any(ListingSearchParams.class), any()))
            .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/listings").param("street", "Dorpstraat"))
            .andExpect(status().isOk());

        ArgumentCaptor<ListingSearchParams> cap = ArgumentCaptor.forClass(ListingSearchParams.class);
        verify(listingService).findAll(cap.capture(), any());
        assertThat(cap.getValue().street()).isEqualTo("Dorpstraat");
    }

    @Test
    void getListings_withNoParams_passesEmptyParamsToService() throws Exception {
        when(listingService.findAll(any(ListingSearchParams.class), any()))
            .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/listings"))
            .andExpect(status().isOk());

        ArgumentCaptor<ListingSearchParams> cap = ArgumentCaptor.forClass(ListingSearchParams.class);
        verify(listingService).findAll(cap.capture(), any());
        assertThat(cap.getValue().isEmpty()).isTrue();
    }

    @Test
    void getListings_passesAllSearchParamsToService() throws Exception {
        when(listingService.findAll(any(ListingSearchParams.class), any()))
            .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/listings")
                .param("street", "Kerkstraat")
                .param("houseNumber", "5")
                .param("zipCode", "1234AB")
                .param("province", "Noord-Holland"))
            .andExpect(status().isOk());

        ArgumentCaptor<ListingSearchParams> cap = ArgumentCaptor.forClass(ListingSearchParams.class);
        verify(listingService).findAll(cap.capture(), any());
        assertThat(cap.getValue().street()).isEqualTo("Kerkstraat");
        assertThat(cap.getValue().houseNumber()).isEqualTo("5");
        assertThat(cap.getValue().zipCode()).isEqualTo("1234AB");
        assertThat(cap.getValue().province()).isEqualTo("Noord-Holland");
    }

    @Test
    void getListings_passesNewFilterParamsToService() throws Exception {
        when(listingService.findAll(any(ListingSearchParams.class), any()))
            .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/listings")
                .param("minBedrooms", "2")
                .param("minRooms", "3")
                .param("minLivingAreaM2", "80")
                .param("energyLabel", "A"))
            .andExpect(status().isOk());

        ArgumentCaptor<ListingSearchParams> cap = ArgumentCaptor.forClass(ListingSearchParams.class);
        verify(listingService).findAll(cap.capture(), any());
        assertThat(cap.getValue().minBedrooms()).isEqualTo(2);
        assertThat(cap.getValue().minRooms()).isEqualTo(3);
        assertThat(cap.getValue().minLivingAreaM2()).isEqualTo(80);
        assertThat(cap.getValue().energyLabel()).isEqualTo("A");
    }
}
```

- [ ] **Step 6: Run the full backend test suite**

```
cd hermes-backend
./mvnw test -q
```

Expected: all tests PASS

- [ ] **Step 7: Commit**

```bash
git add hermes-backend/src/main/resources/openapi/api.yaml \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/api/ListingController.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/api/ListingControllerSearchTest.java
git commit -m "feat(api): expose listing detail fields and new search params in OpenAPI"
```

---

## Task 9: Frontend — types, service, listings page filters

**Files:**
- Modify: `hermes-frontend/src/app/core/api.types.ts`
- Modify: `hermes-frontend/src/app/core/listings.service.ts`
- Modify: `hermes-frontend/src/app/pages/listings/listings-page.component.ts`
- Modify: `hermes-frontend/src/app/pages/listings/listings-page.component.html`

- [ ] **Step 1: Update `api.types.ts`**

Add to `ListingDetailResponse`:
```typescript
export interface ListingDetailResponse {
  id: string;
  fundaId: string;
  url: string;
  street: string;
  houseNumber: string;
  houseNumberAddition?: string;
  zipCode: string;
  city: string;
  province: string;
  firstSeenAt: string;
  lastSeenAt: string;
  currentPrice?: number;
  status?: ListingStatus;
  description?: string | null;
  livingAreaM2?: number | null;
  plotAreaM2?: number | null;
  rooms?: number | null;
  bedrooms?: number | null;
  energyLabel?: string | null;
}
```

Add to `ListingSearchFilter`:
```typescript
export interface ListingSearchFilter {
  street?: string;
  houseNumber?: string;
  houseNumberAddition?: string;
  zipCode?: string;
  province?: string;
  minBedrooms?: number | null;
  minRooms?: number | null;
  minLivingAreaM2?: number | null;
  energyLabel?: string | null;
}
```

- [ ] **Step 2: Update `listings.service.ts` to pass the new filter params**

In `loadListings()`, after the existing `province` param check, add:
```typescript
if (filter?.minBedrooms) params = params.set('minBedrooms', filter.minBedrooms);
if (filter?.minRooms) params = params.set('minRooms', filter.minRooms);
if (filter?.minLivingAreaM2) params = params.set('minLivingAreaM2', filter.minLivingAreaM2);
if (filter?.energyLabel) params = params.set('energyLabel', filter.energyLabel);
```

- [ ] **Step 3: Add four new filter fields to `listings-page.component.ts`**

Add four new properties after `province`:
```typescript
protected minBedrooms: number | null = null;
protected minRooms: number | null = null;
protected minLivingAreaM2: number | null = null;
protected energyLabel = '';
```

Update `clearFilters()` to reset them:
```typescript
clearFilters(): void {
  this.street = '';
  this.houseNumber = '';
  this.houseNumberAddition = '';
  this.zipCode = '';
  this.province = '';
  this.minBedrooms = null;
  this.minRooms = null;
  this.minLivingAreaM2 = null;
  this.energyLabel = '';
  this.currentPage = 0;
  this.svc.loadListings(0, this.pageSize);
}
```

Update `currentFilter` getter:
```typescript
private get currentFilter(): ListingSearchFilter {
  return {
    street: this.street || undefined,
    houseNumber: this.houseNumber || undefined,
    houseNumberAddition: this.houseNumberAddition || undefined,
    zipCode: this.zipCode || undefined,
    province: this.province || undefined,
    minBedrooms: this.minBedrooms || undefined,
    minRooms: this.minRooms || undefined,
    minLivingAreaM2: this.minLivingAreaM2 || undefined,
    energyLabel: this.energyLabel || undefined,
  };
}
```

- [ ] **Step 4: Add the second filter row to `listings-page.component.html`**

In the filter card, after the closing `</div>` of the first grid row (after `province` input) and before the "Clear filters" button, add:
```html
  <div class="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-3">
    <input [(ngModel)]="minBedrooms" type="number" min="0" (input)="onFilterChange()"
      placeholder="Min bedrooms"
      class="rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-cyan-500 focus:ring-1 focus:ring-cyan-500 outline-none" />
    <input [(ngModel)]="minRooms" type="number" min="0" (input)="onFilterChange()"
      placeholder="Min rooms"
      class="rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-cyan-500 focus:ring-1 focus:ring-cyan-500 outline-none" />
    <input [(ngModel)]="minLivingAreaM2" type="number" min="0" (input)="onFilterChange()"
      placeholder="Min area (m²)"
      class="rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-cyan-500 focus:ring-1 focus:ring-cyan-500 outline-none" />
    <input [(ngModel)]="energyLabel" (input)="onFilterChange()"
      placeholder="Energy label"
      class="rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-cyan-500 focus:ring-1 focus:ring-cyan-500 outline-none" />
  </div>
```

- [ ] **Step 5: Run Angular tests**

```
cd hermes-frontend
npm test -- --watch=false
```

Expected: all tests PASS

- [ ] **Step 6: Commit**

```bash
git add hermes-frontend/src/app/core/api.types.ts \
        hermes-frontend/src/app/core/listings.service.ts \
        hermes-frontend/src/app/pages/listings/listings-page.component.ts \
        hermes-frontend/src/app/pages/listings/listings-page.component.html
git commit -m "feat(frontend): add search filters for bedrooms, rooms, area, and energy label"
```

---

## Task 10: Frontend — listing detail page enrichment display

**Files:**
- Modify: `hermes-frontend/src/app/pages/listing-detail/listing-detail-page.component.html`

**Context:** The detail page currently shows 4 stat-cards in a `grid-cols-2 sm:grid-cols-4` row, followed by the price-history chart, then a two-column grid with Details and AI Summary/Rescrape cards. We add 5 more stat-cards in a second row and a description section below the chart.

- [ ] **Step 1: Add five new stat-cards and a description block**

After the closing `</div>` of the existing 4-card stat grid (the `mb-6` div on line 29), add a second row of stat-cards:

```html
    <div class="grid grid-cols-2 sm:grid-cols-5 gap-4 mb-6">
      <app-stat-card label="Bedrooms">
        <p class="text-2xl font-bold text-slate-900 mt-2 tabular-nums">
          {{ listing.bedrooms ?? '—' }}
        </p>
      </app-stat-card>
      <app-stat-card label="Rooms">
        <p class="text-2xl font-bold text-slate-900 mt-2 tabular-nums">
          {{ listing.rooms ?? '—' }}
        </p>
      </app-stat-card>
      <app-stat-card label="Living area">
        <p class="text-2xl font-bold text-slate-900 mt-2 tabular-nums">
          {{ listing.livingAreaM2 != null ? listing.livingAreaM2 + ' m²' : '—' }}
        </p>
      </app-stat-card>
      <app-stat-card label="Plot area">
        <p class="text-2xl font-bold text-slate-900 mt-2 tabular-nums">
          {{ listing.plotAreaM2 != null ? listing.plotAreaM2 + ' m²' : '—' }}
        </p>
      </app-stat-card>
      <app-stat-card label="Energy label">
        <p class="text-2xl font-bold text-slate-900 mt-2">
          {{ listing.energyLabel ?? '—' }}
        </p>
      </app-stat-card>
    </div>
```

After the closing `}` of the `@if (svc.report(); ...)` price history block (after line 68), add the description block:

```html
    @if (listing.description) {
      <app-section-card class="mb-6">
        <h2 class="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-3">Description</h2>
        <p class="text-sm text-slate-700 leading-relaxed whitespace-pre-line">{{ listing.description }}</p>
      </app-section-card>
    }
```

- [ ] **Step 2: Run Angular tests**

```
cd hermes-frontend
npm test -- --watch=false
```

Expected: all tests PASS

- [ ] **Step 3: Run the full backend test suite one final time**

```
cd hermes-backend
./mvnw test -q
```

Expected: all tests PASS

- [ ] **Step 4: Commit**

```bash
git add hermes-frontend/src/app/pages/listing-detail/listing-detail-page.component.html
git commit -m "feat(frontend): display enrichment fields on listing detail page"
```
