# Listing Map View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show listings on an OpenStreetMap/Leaflet map — a list/map toggle on the search results page (current page of results, pins + bounding-box rectangles) and a single-listing map on the listing detail page — backed by a resilient, read-only projection over the existing PostGIS geo columns, plus a manual backfill for listings that predate the geocoding pipeline.

**Architecture:** No schema change. A new native-query projection (`ListingGeoProjection` / `findGeoByIds`) derives plain lat/lon/bbox doubles from the existing `listings.location`/`bounding_box` PostGIS columns on read, wrapped in a try/catch so failures never break listing search/detail. Values are merged into a new nested `GeoLocation` field on `ListingDto`, batched once per page/list to avoid N+1. A new backfill service re-queues geocoding (via the existing rate-limited `GeocodingConsumer`) for listings that have none yet. The frontend gets a reusable Leaflet `ListingMapComponent` wired into the listings page (toggle), the listing detail page, and a backfill trigger button on the scraping admin page.

**Tech Stack:** Spring Boot, PostgreSQL + PostGIS, Spring Data JPA (native queries), MapStruct (`unmappedTargetPolicy=ERROR`, `unmappedSourcePolicy=ERROR`), openapi-generator-maven-plugin (spring generator, `interfaceOnly=true`), Angular 22 standalone components + signals, Leaflet.

## Global Constraints

- Java package root: `com.kropholler.dev.hermes`
- All backend tests run from `hermes-backend/` with `mvnw.cmd test -Dtest=ClassName`
- **No new Flyway migration.** The design deliberately avoids touching `ListingEntity`/the PostGIS geometry columns (see spec's "Non-goals" and the brainstorming discussion) — everything reads existing columns via a native projection query. Do not add a `V9__...sql` file. (V9 is already taken by the completed `docs/superpowers/plans/2026-06-19-agentic-ai.md` plan, which is further confirmation none is needed here.)
- `MapStructConfig` sets `unmappedTargetPolicy=ERROR` and `unmappedSourcePolicy=ERROR` — every DTO/entity/response field must be explicitly mapped or explicitly ignored, or the build fails at compile time.
- All new controller endpoints must be added to the relevant `src/main/resources/openapi/*.yaml` and implement the generated interface (`openapi-generator-maven-plugin` runs automatically in `generate-sources`, bound to `mvnw.cmd compile`/`test` — no manual regeneration step needed).
- `ListingDto` is a positional Java record — adding a field breaks every `new ListingDto(...)` call site across the codebase (there are 22, in 15 files). Every one must be updated in the same task that adds the field, or the module will not compile.
- Do not touch `ListingSpecifications.withinRadius` or `ListingRepository.searchForChatNearLocation` — both are existing, tested radius-search paths and are out of scope.
- Frontend tests run with `ng test` from `hermes-frontend/`.
- Existing UI style conventions: Tailwind utility classes, `app-section-card` wrapper, Angular signals + standalone components (see `ListingsPageComponent`, `ScrapingPageComponent`).

---

## Progress

| Task | Status | Commit |
|------|--------|--------|
| Task 1: Repository — `ListingGeoProjection`, `findGeoByIds`, `findIdsMissingLocation` | ✅ Complete | 5a4b6e5 |
| Task 2: `GeoLocation` DTO + `ListingDto`/`ListingMapper`/OpenAPI wiring + fix all call sites | ✅ Complete | f912189 |
| Task 3: `ListingService` — resilient batched geo merge | ✅ Complete | 090eedb |
| Task 4: Geocoding backfill endpoint | ⬜ Pending | — |
| Task 5: Frontend — add Leaflet dependency | ⬜ Pending | — |
| Task 6: Frontend — `api.types.ts` geo fields | ⬜ Pending | — |
| Task 7: Frontend — `ListingMapComponent` | ✅ Complete | 803de4d |
| Task 8: Frontend — listings page list/map toggle | ⬜ Pending | — |
| Task 9: Frontend — listing detail page map | ⬜ Pending | — |
| Task 10: Frontend — admin backfill trigger | ⬜ Pending | — |

**Resuming after an interruption:** re-read this table. Pick up at the first `⬜ Pending` row — every task before it is done and its commit is on `main`. Each task's own section below is self-contained (files, code, exact commands), so you don't need prior tasks' sections in context to execute it, only their *outputs* (types/signatures), which are listed under each task's "Interfaces" block.

---

## Task 1: Repository — `ListingGeoProjection`, `findGeoByIds`, `findIdsMissingLocation`

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/data/ListingGeoProjection.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/data/ListingRepository.java`
- Test: `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/data/ListingRepositoryGeoTest.java`

**Interfaces:**
- Produces: `ListingGeoProjection` (getters: `getId(): UUID`, `getLatitude(): Double`, `getLongitude(): Double`, `getBboxLatMin(): Double`, `getBboxLatMax(): Double`, `getBboxLonMin(): Double`, `getBboxLonMax(): Double`), `ListingRepository.findGeoByIds(List<UUID> ids): List<ListingGeoProjection>`, `ListingRepository.findIdsMissingLocation(): List<String>` (UUID strings, same convention as the existing `findListingIdsWithPriceDrop`).

- [ ] **Step 1: Create the projection interface**

```java
package com.kropholler.dev.hermes.listing.data;

import java.util.UUID;

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

- [ ] **Step 2: Add the two native queries to `ListingRepository`**

Add these two methods to the interface (anywhere after the existing `updateBoundingBox` method, before the closing brace):

```java
    @Query(value = """
            SELECT
              l.id                              AS id,
              ST_Y(l.location)                   AS latitude,
              ST_X(l.location)                   AS longitude,
              ST_YMin(l.bounding_box)             AS bboxLatMin,
              ST_YMax(l.bounding_box)             AS bboxLatMax,
              ST_XMin(l.bounding_box)             AS bboxLonMin,
              ST_XMax(l.bounding_box)             AS bboxLonMax
            FROM listings l
            WHERE l.id IN (:ids) AND l.location IS NOT NULL
            """, nativeQuery = true)
    List<ListingGeoProjection> findGeoByIds(@Param("ids") List<UUID> ids);

    @Query(value = """
            SELECT l.id::text FROM listings l
            WHERE l.deleted_at IS NULL AND l.location IS NULL
              AND l.street IS NOT NULL AND l.city IS NOT NULL
            """, nativeQuery = true)
    List<String> findIdsMissingLocation();
```

- [ ] **Step 3: Write the integration test**

This mirrors the existing `ListingRepositoryRadiusTest` Testcontainers setup exactly (same PostGIS image, same `@DataJpaTest` config).

```java
package com.kropholler.dev.hermes.listing.data;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(ListingRepositoryGeoTest.Containers.class)
@TestPropertySource(properties = {
    "spring.test.database.replace=none",
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class ListingRepositoryGeoTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(
                DockerImageName.parse("postgis/postgis:16-3.4")
                    .asCompatibleSubstituteFor("postgres")
            );
        }
    }

    @Autowired ListingRepository listingRepository;
    @Autowired EntityManager em;

    private ListingEntity savedListing(String street, String city) {
        ListingEntity e = new ListingEntity();
        e.setFundaId(UUID.randomUUID().toString());
        e.setUrl("https://funda.nl/" + UUID.randomUUID());
        e.setStreet(street);
        e.setCity(city);
        return listingRepository.saveAndFlush(e);
    }

    private void geocode(UUID id, double lon, double lat) {
        listingRepository.updateLocation(id, lon, lat);
        listingRepository.updateBoundingBox(id, lon - 0.001, lat - 0.001, lon + 0.001, lat + 0.001);
        em.flush();
        em.clear();
    }

    @Test
    void findGeoByIds_geocodedListing_returnsLatLonAndBoundingBox() {
        ListingEntity listing = savedListing("Kerkstraat", "Amsterdam");
        geocode(listing.getId(), 4.9041, 52.3676);

        List<ListingGeoProjection> result = listingRepository.findGeoByIds(List.of(listing.getId()));

        assertThat(result).hasSize(1);
        ListingGeoProjection projection = result.get(0);
        assertThat(projection.getId()).isEqualTo(listing.getId());
        assertThat(projection.getLatitude()).isEqualTo(52.3676, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(projection.getLongitude()).isEqualTo(4.9041, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(projection.getBboxLatMin()).isEqualTo(52.3666, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(projection.getBboxLatMax()).isEqualTo(52.3686, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(projection.getBboxLonMin()).isEqualTo(4.9031, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(projection.getBboxLonMax()).isEqualTo(4.9051, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void findGeoByIds_listingWithoutLocation_isExcludedFromResults() {
        ListingEntity listing = savedListing("Kerkstraat", "Amsterdam"); // never geocoded

        List<ListingGeoProjection> result = listingRepository.findGeoByIds(List.of(listing.getId()));

        assertThat(result).isEmpty();
    }

    @Test
    void findGeoByIds_mixOfGeocodedAndNot_returnsOnlyGeocoded() {
        ListingEntity geocoded = savedListing("Kerkstraat", "Amsterdam");
        geocode(geocoded.getId(), 4.9041, 52.3676);
        ListingEntity notGeocoded = savedListing("Damrak", "Amsterdam");

        List<ListingGeoProjection> result = listingRepository.findGeoByIds(
            List.of(geocoded.getId(), notGeocoded.getId()));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(geocoded.getId());
    }

    @Test
    void findIdsMissingLocation_returnsOnlyListingsWithoutLocation() {
        ListingEntity geocoded = savedListing("Kerkstraat", "Amsterdam");
        geocode(geocoded.getId(), 4.9041, 52.3676);
        ListingEntity notGeocoded = savedListing("Damrak", "Amsterdam");

        List<String> ids = listingRepository.findIdsMissingLocation();

        assertThat(ids).containsExactly(notGeocoded.getId().toString());
    }

    @Test
    void findIdsMissingLocation_deletedListing_isExcluded() {
        ListingEntity deleted = savedListing("Kerkstraat", "Amsterdam");
        deleted.setDeletedAt(java.time.Instant.now());
        listingRepository.saveAndFlush(deleted);

        List<String> ids = listingRepository.findIdsMissingLocation();

        assertThat(ids).isEmpty();
    }

    @Test
    void findIdsMissingLocation_missingStreetOrCity_isExcluded() {
        ListingEntity noStreet = new ListingEntity();
        noStreet.setFundaId(UUID.randomUUID().toString());
        noStreet.setUrl("https://funda.nl/" + UUID.randomUUID());
        noStreet.setCity("Amsterdam"); // street left null
        listingRepository.saveAndFlush(noStreet);

        List<String> ids = listingRepository.findIdsMissingLocation();

        assertThat(ids).isEmpty();
    }
}
```

- [ ] **Step 4: Run the test**

```
cd hermes-backend && mvnw.cmd test -Dtest=ListingRepositoryGeoTest
```
Expected: 6 tests pass (requires Docker running for Testcontainers).

- [ ] **Step 5: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/data/ListingGeoProjection.java hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/data/ListingRepository.java hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/data/ListingRepositoryGeoTest.java
git commit -m "feat(listing): add read-only geo projection over PostGIS columns"
```

After committing, **update the Progress table**: mark Task 1 `✅ Complete` and fill in the commit hash (`git rev-parse --short HEAD`).

---

## Task 2: `GeoLocation` DTO + `ListingDto`/`ListingMapper`/OpenAPI wiring + fix all call sites

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/GeoLocation.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingDto.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingMapper.java`
- Modify: `hermes-backend/src/main/resources/openapi/listing.yaml`
- Modify (test, call-site fix only — append `null` as the new last constructor argument): all files listed in Step 3.
- Modify (test, new assertions): `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingMapperTest.java`, `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingApiMapperTest.java`

**Interfaces:**
- Consumes: nothing new from Task 1 yet (this task only shapes the DTO/API surface; Task 3 wires the repository into it).
- Produces: `GeoLocation(double latitude, double longitude, Double bboxLatMin, Double bboxLatMax, Double bboxLonMin, Double bboxLonMax)` record; `ListingDto` gains a 20th component `GeoLocation location`; `ListingMapper.toDto(ListingEntity listing, Integer currentPrice, GeoLocation location): ListingDto`; generated `com.kropholler.dev.hermes.listing.openapi.GeoLocation` model; `ListingSummaryResponse`/`ListingDetailResponse` each gain a `location` field of that type.

- [ ] **Step 1: Create the `GeoLocation` record**

```java
package com.kropholler.dev.hermes.listing;

public record GeoLocation(
    double latitude,
    double longitude,
    Double bboxLatMin,
    Double bboxLatMax,
    Double bboxLonMin,
    Double bboxLonMax
) {}
```

- [ ] **Step 2: Add `location` to `ListingDto`**

Replace the full file content:

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
    Integer plotAreaM2,
    GeoLocation location
) {}
```

- [ ] **Step 3: Fix every `new ListingDto(...)` call site (22 call sites, 15 files)**

Each site below needs exactly one thing: append `null` (or, where noted, a real `GeoLocation`) as the final constructor argument. Shown as exact before → after.

**`hermes-backend/src/test/java/com/kropholler/dev/hermes/report/ReportServiceTest.java`** (3 call sites, lines 36-41, 73-78, 91-96 — identical trailing line `null, null, null, null, null, null` in all three):

Before (all three occurrences):
```java
            null, null, null, null, null, null
        );
```
After (all three occurrences):
```java
            null, null, null, null, null, null, null
        );
```

**`hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingControllerTest.java`** (line 41):

Before:
```java
            null, 80, 4, 2, "A", null);
```
After:
```java
            null, 80, 4, 2, "A", null, null);
```

**`hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/tool/ListingSearchToolTest.java`** (line 42, need lines 41-42 for context):

Before:
```java
                "Ruim appartement met balkon.", 85, 4,
                3, "A", null);
```
After:
```java
                "Ruim appartement met balkon.", 85, 4,
                3, "A", null, null);
```

**`hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/tool/FindPriceDropToolTest.java`** (2 call sites, lines 31-33 and 101-103, identical trailing text):

Before (both occurrences):
```java
            280000, ListingStatus.FOR_SALE, null, 90, 4, 2, "B", null);
```
After (both occurrences):
```java
            280000, ListingStatus.FOR_SALE, null, 90, 4, 2, "B", null, null);
```

**`hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingApiMapperTest.java`** (2 call sites):

Line 23, before:
```java
            350000, ListingStatus.FOR_SALE, "Nice house", 90, 5, 3, "B", 120);
```
After:
```java
            350000, ListingStatus.FOR_SALE, "Nice house", 90, 5, 3, "B", 120, null);
```

Line 50, before:
```java
            Instant.now(), Instant.now(), null, null, null, null, null, null, null, null);
```
After:
```java
            Instant.now(), Instant.now(), null, null, null, null, null, null, null, null, null);
```

**`hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/tool/GetPriceHistoryToolTest.java`** (line 35):

Before:
```java
            280000, ListingStatus.FOR_SALE, null, 85, 4, 2, "B", null);
```
After:
```java
            280000, ListingStatus.FOR_SALE, null, 85, 4, 2, "B", null, null);
```

**`hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingServiceFindForChatTest.java`** (lines 63-65):

Before:
```java
        ListingDto dto = new ListingDto(id, null, null, null, null, null, null, null, null,
                null, null, price, null, null, null, null, null, null, null);
        when(mapper.toDto(listing, price)).thenReturn(dto);
```
After:
```java
        ListingDto dto = new ListingDto(id, null, null, null, null, null, null, null, null,
                null, null, price, null, null, null, null, null, null, null, null);
        when(mapper.toDto(listing, price, null)).thenReturn(dto);
```

**`hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/tool/CompareListingsToolTest.java`** (2 call sites):

Line 34, before:
```java
            300000, ListingStatus.FOR_SALE, null, 80, 4, 2, "B", null);
```
After:
```java
            300000, ListingStatus.FOR_SALE, null, 80, 4, 2, "B", null, null);
```

Line 111, before:
```java
            null, null, null, null, null, null, null, 50);
```
After:
```java
            null, null, null, null, null, null, null, 50, null);
```

**`hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/tool/GetListingSummaryToolTest.java`** (line 35):

Before:
```java
            250000, ListingStatus.FOR_SALE, description, 75, 4, 2, "C", null);
```
After:
```java
            250000, ListingStatus.FOR_SALE, description, 75, 4, 2, "C", null, null);
```

**`hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingServiceTest.java`** (line 48):

Before:
```java
                null, null, null, null, null, null, null, null, null, null, null);
```
After:
```java
                null, null, null, null, null, null, null, null, null, null, null, null);
```

**`hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/tool/GetFavouriteListingsToolTest.java`** (line 38):

Before:
```java
            400000, ListingStatus.FOR_SALE, null, 100, 5, 3, "A", null);
```
After:
```java
            400000, ListingStatus.FOR_SALE, null, 100, 5, 3, "A", null, null);
```

**`hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/schedule/NightlyRescrapeSchedulerTest.java`** (line 39):

Before:
```java
            null, 70, 3, 2, "B", null);
```
After:
```java
            null, 70, 3, 2, "B", null, null);
```

**`hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/summary/ListingSummaryGenerationServiceTest.java`** (3 call sites):

Line 55, before:
```java
            "Beautiful house.", 120, 5, 3, "A", 200);
```
After:
```java
            "Beautiful house.", 120, 5, 3, "A", 200, null);
```

Line 78, before:
```java
            Instant.now(), Instant.now(), null, null, "   ", null, null, null, null, null);
```
After:
```java
            Instant.now(), Instant.now(), null, null, "   ", null, null, null, null, null, null);
```

Line 113, before:
```java
            null, 80, 4, 2, "B", null);
```
After:
```java
            null, 80, 4, 2, "B", null, null);
```

**`hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatListingCardMapperTest.java`** (line 21):

Before:
```java
            "Description", 100, 5, 3, "A", 200);
```
After:
```java
            "Description", 100, 5, 3, "A", 200, null);
```

**`hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/handler/WatchTaskHandlerTest.java`** (2 call sites):

Line 152, before:
```java
            null, ListingStatus.FOR_SALE, null, 90, 3, 2, null, null);
```
After:
```java
            null, ListingStatus.FOR_SALE, null, 90, 3, 2, null, null, null);
```

Lines 187-207 (the commented, one-arg-per-line constructor), before:
```java
            null,                   // energyLabel
            null                    // plotAreaM2
        );
```
After:
```java
            null,                   // energyLabel
            null,                   // plotAreaM2
            null                    // location
        );
```

- [ ] **Step 4: Add the `GeoLocation` field/param to `ListingMapper`**

Replace the full file content:

```java
package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.config.MapStructConfig;
import com.kropholler.dev.hermes.listing.data.ListingEntity;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryEntryEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
interface ListingMapper {

    @BeanMapping(ignoreUnmappedSourceProperties = {"lastUpdatedAt", "deletedAt"})
    ListingDto toDto(ListingEntity listing, Integer currentPrice, GeoLocation location);

    @BeanMapping(ignoreUnmappedSourceProperties = {"listingId"})
    PriceHistoryEntryDto toDto(PriceHistoryEntryEntity entry);
}
```

- [ ] **Step 5: Add the `GeoLocation` schema and `location` field to `listing.yaml`**

In `hermes-backend/src/main/resources/openapi/listing.yaml`, under `components.schemas`, add a new schema (alongside the existing ones, e.g. right after `ListingPage`):

```yaml
    GeoLocation:
      type: object
      required: [latitude, longitude]
      properties:
        latitude:
          type: number
          format: double
        longitude:
          type: number
          format: double
        bboxLatMin:
          type: number
          format: double
          nullable: true
        bboxLatMax:
          type: number
          format: double
          nullable: true
        bboxLonMin:
          type: number
          format: double
          nullable: true
        bboxLonMax:
          type: number
          format: double
          nullable: true
```

Then add a `location` property to **both** `ListingSummaryResponse` and `ListingDetailResponse` schemas (add as the last property in each, alongside e.g. `firstSeenAt` / `plotAreaM2`):

```yaml
        location:
          $ref: '#/components/schemas/GeoLocation'
          nullable: true
```

- [ ] **Step 6: Add mapping tests for `location` in `ListingMapperTest`**

Add these two tests to the existing file (`hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingMapperTest.java`), inside the class body:

```java
    @Test
    void toDto_withGeoLocation_mapsLocationField() {
        ListingEntity listing = new ListingEntity();
        listing.setFirstSeenAt(Instant.now());
        listing.setLastSeenAt(Instant.now());
        GeoLocation location = new GeoLocation(52.3676, 4.9041, 52.3666, 52.3686, 4.9031, 4.9051);

        ListingDto dto = mapper.toDto(listing, null, location);

        assertThat(dto.location()).isEqualTo(location);
    }

    @Test
    void toDto_nullGeoLocation_producesNullLocation() {
        ListingEntity listing = new ListingEntity();
        listing.setFirstSeenAt(Instant.now());
        listing.setLastSeenAt(Instant.now());

        ListingDto dto = mapper.toDto(listing, null, null);

        assertThat(dto.location()).isNull();
    }
```

Also update the two existing calls in that file from 2-arg to 3-arg:

Before (line 28): `ListingDto dto = mapper.toDto(listing, 275000);`
After: `ListingDto dto = mapper.toDto(listing, 275000, null);`

Before (line 41): `ListingDto dto = mapper.toDto(listing, null);`
After: `ListingDto dto = mapper.toDto(listing, null, null);`

- [ ] **Step 7: Add mapping test for `location` in `ListingApiMapperTest`**

Add this test to the existing file:

```java
    @Test
    void toDetailResponse_withLocation_mapsNestedGeoLocation() {
        UUID id = UUID.randomUUID();
        ListingDto base = dto(id);
        ListingDto withLocation = new ListingDto(base.id(), base.fundaId(), base.url(),
            base.street(), base.houseNumber(), base.houseNumberAddition(), base.zipCode(),
            base.city(), base.province(), base.firstSeenAt(), base.lastSeenAt(),
            base.currentPrice(), base.status(), base.description(), base.livingAreaM2(),
            base.rooms(), base.bedrooms(), base.energyLabel(), base.plotAreaM2(),
            new GeoLocation(52.3676, 4.9041, 52.3666, 52.3686, 4.9031, 4.9051));

        var response = mapper.toDetailResponse(withLocation);

        assertThat(response.getLocation().getLatitude()).isEqualTo(52.3676);
        assertThat(response.getLocation().getLongitude()).isEqualTo(4.9041);
        assertThat(response.getLocation().getBboxLatMin()).isEqualTo(52.3666);
    }

    @Test
    void toDetailResponse_nullLocation_producesNullLocation() {
        ListingDto dto = dto(UUID.randomUUID());

        var response = mapper.toDetailResponse(dto);

        assertThat(response.getLocation()).isNull();
    }
```

(`dto(UUID id)` already produces a `ListingDto` with `location == null` per the current 19-arg helper — Step 3 above already appended the trailing `null` to it.)

- [ ] **Step 8: Compile and run the affected test modules**

```
cd hermes-backend && mvnw.cmd test -Dtest=ListingMapperTest,ListingApiMapperTest,ListingControllerTest,ListingSearchToolTest,FindPriceDropToolTest,GetPriceHistoryToolTest,ListingServiceFindForChatTest,CompareListingsToolTest,GetListingSummaryToolTest,ListingServiceTest,GetFavouriteListingsToolTest,NightlyRescrapeSchedulerTest,ListingSummaryGenerationServiceTest,ChatListingCardMapperTest,WatchTaskHandlerTest,ReportServiceTest
```
Expected: all pass, zero compile errors. (This also transitively proves the openapi-generator regenerated `ListingSummaryResponse`/`ListingDetailResponse`/`GeoLocation` with a matching `location` field, and that MapStruct's implicit nested-object mapping between the domain `GeoLocation` and the generated `openapi.GeoLocation` compiled without needing an explicit `@Mapping`.)

If MapStruct fails with an "unmapped target property: location" error on `ListingApiMapper`, it means the generated `GeoLocation` model didn't come through with matching property names — double check Step 5's YAML property names (`latitude`, `longitude`, `bboxLatMin`, `bboxLatMax`, `bboxLonMin`, `bboxLonMax`) exactly match the domain record's component names from Step 1.

- [ ] **Step 9: Full test suite (catch any missed call site)**

```
cd hermes-backend && mvnw.cmd test
```
Expected: full pass. If any other file constructs `new ListingDto(...)` that wasn't listed above, the compiler error will name the exact file and line — fix it the same way (append `null`) and re-run.

- [ ] **Step 10: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/GeoLocation.java hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingDto.java hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingMapper.java hermes-backend/src/main/resources/openapi/listing.yaml hermes-backend/src/test
git commit -m "feat(listing): add nested GeoLocation to ListingDto and API responses"
```

After committing, **update the Progress table**: mark Task 2 `✅ Complete` and fill in the commit hash.

---

## Task 3: `ListingService` — resilient batched geo merge

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingService.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingServiceTest.java`

**Interfaces:**
- Consumes: `ListingRepository.findGeoByIds(List<UUID>): List<ListingGeoProjection>` (Task 1), `ListingMapper.toDto(ListingEntity, Integer, GeoLocation): ListingDto` (Task 2), `GeoLocation` record (Task 2).
- Produces: no new public methods — `findAll`, `findAllActive`, `findForChat`, `findById`, `findByFundaId`, `findByAddress`, `findPriceDropListings` all now populate `ListingDto.location()` when available, and never throw because of a geo lookup failure.

- [ ] **Step 1: Write the failing resilience tests**

Add to `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingServiceTest.java`, inside the class body (needs `import com.kropholler.dev.hermes.listing.data.ListingGeoProjection;` and `import static org.mockito.ArgumentMatchers.anyList;` added to the imports):

```java
    // ── geo resilience ───────────────────────────────────────────────────────

    @Test
    void findById_geoLookupThrows_returnsDtoWithNullLocation() {
        UUID id = UUID.randomUUID();
        ListingEntity e = entity(id);
        when(listingRepository.findById(id)).thenReturn(Optional.of(e));
        when(priceHistoryRepository.findFirstByListingIdAndStatusOrderByTimestampDesc(id, "asking_price"))
                .thenReturn(Optional.empty());
        when(listingRepository.findGeoByIds(List.of(id))).thenThrow(new RuntimeException("DB error"));
        ListingDto expected = dto(id);
        when(mapper.toDto(e, null, null)).thenReturn(expected);

        Optional<ListingDto> result = service.findById(id);

        assertThat(result).contains(expected);
    }

    @Test
    void findAll_geoLookupThrows_pageStillReturned() {
        Pageable pageable = PageRequest.of(0, 20);
        var params = new ListingSearchParams(null, null, null, null, null, null, null, null, null, null, null);
        UUID id = UUID.randomUUID();
        ListingEntity e = entity(id);
        when(listingRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(e)));
        when(priceHistoryRepository.findFirstByListingIdAndStatusOrderByTimestampDesc(id, "asking_price"))
                .thenReturn(Optional.empty());
        when(listingRepository.findGeoByIds(anyList())).thenThrow(new RuntimeException("DB error"));
        ListingDto expected = dto(id);
        when(mapper.toDto(e, null, null)).thenReturn(expected);

        var result = service.findAll(params, pageable);

        assertThat(result.getContent()).containsExactly(expected);
    }

    @Test
    void findById_geoLookupSucceeds_locationPassedToMapper() {
        UUID id = UUID.randomUUID();
        ListingEntity e = entity(id);
        when(listingRepository.findById(id)).thenReturn(Optional.of(e));
        when(priceHistoryRepository.findFirstByListingIdAndStatusOrderByTimestampDesc(id, "asking_price"))
                .thenReturn(Optional.empty());
        ListingGeoProjection projection = mock(ListingGeoProjection.class);
        when(projection.getId()).thenReturn(id);
        when(projection.getLatitude()).thenReturn(52.3676);
        when(projection.getLongitude()).thenReturn(4.9041);
        when(projection.getBboxLatMin()).thenReturn(52.3666);
        when(projection.getBboxLatMax()).thenReturn(52.3686);
        when(projection.getBboxLonMin()).thenReturn(4.9031);
        when(projection.getBboxLonMax()).thenReturn(4.9051);
        when(listingRepository.findGeoByIds(List.of(id))).thenReturn(List.of(projection));
        GeoLocation expectedLocation = new GeoLocation(52.3676, 4.9041, 52.3666, 52.3686, 4.9031, 4.9051);
        ListingDto expected = dto(id);
        when(mapper.toDto(e, null, expectedLocation)).thenReturn(expected);

        Optional<ListingDto> result = service.findById(id);

        assertThat(result).contains(expected);
    }

    @Test
    void findAll_emptyPage_doesNotCallGeoLookup() {
        Pageable pageable = PageRequest.of(0, 20);
        var params = new ListingSearchParams(null, null, null, null, null, null, null, null, null, null, null);
        when(listingRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

        service.findAll(params, pageable);

        verify(listingRepository, never()).findGeoByIds(any());
    }
```

- [ ] **Step 2: Run the new tests to verify they fail**

```
cd hermes-backend && mvnw.cmd test -Dtest=ListingServiceTest
```
Expected: FAIL — `findGeoByIds` does not exist as a call target in `ListingService` yet (compile error), or the 3-arg `mapper.toDto` stub doesn't match a 2-arg production call.

- [ ] **Step 3: Implement the batched, resilient geo merge in `ListingService`**

Replace the full file content:

```java
package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.geocoding.GeocodeResult;
import com.kropholler.dev.hermes.listing.geocoding.GeocodingService;
import com.kropholler.dev.hermes.listing.data.ListingEntity;
import com.kropholler.dev.hermes.listing.data.ListingGeoProjection;
import com.kropholler.dev.hermes.listing.data.ListingRepository;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryEntryEntity;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryEntryRepository;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListingService {

    private final ListingRepository listingRepository;
    private final PriceHistoryEntryRepository priceHistoryRepository;
    private final PriceHistoryService priceHistoryService;
    private final ListingMapper mapper;
    private final GeocodingService geocodingService;

    @Transactional(readOnly = true)
    public Page<ListingDto> findAll(ListingSearchParams params, Pageable pageable) {
        if (params.isEmpty()) {
            Page<ListingEntity> page = listingRepository.findAll(pageable);
            Map<UUID, ListingGeoProjection> geoById = fetchGeoSafely(idsOf(page.getContent()));
            return page.map(l -> toDto(l, geoById));
        }
        Specification<ListingEntity> spec = params.hasRadiusSearch()
                ? ListingSpecifications.withParamsForRadius(params)
                : ListingSpecifications.withParams(params);
        if (params.hasRadiusSearch()) {
            GeocodeResult latLon = resolveRadiusCenter(params);
            if (latLon != null) {
                spec = spec.and(ListingSpecifications.withinRadius(latLon.lon(), latLon.lat(), params.radiusKm() * 1000));
            }
        }
        Page<ListingEntity> page = listingRepository.findAll(spec, pageable);
        Map<UUID, ListingGeoProjection> geoById = fetchGeoSafely(idsOf(page.getContent()));
        return page.map(l -> toDto(l, geoById));
    }

    private GeocodeResult resolveRadiusCenter(ListingSearchParams params) {
        if (params.street() != null && !params.street().isBlank()) {
            return geocodingService.geocodeAddress(
                    params.houseNumber() != null ? params.houseNumber() : "",
                    params.street(),
                    params.city() != null ? params.city() : ""
            ).orElse(null);
        }
        return geocodingService.findOrFetchCity(params.city())
                .map(c -> new GeocodeResult(c.getLongitude(), c.getLatitude(), null))
                .orElse(null);
    }

    private GeocodeResult resolveLatLon(String nearAddress, String nearCity) {
        if (nearAddress != null && !nearAddress.isBlank()) {
            return geocodingService.geocodeAddress(nearAddress, "", "").orElse(null);
        }
        if (nearCity != null && !nearCity.isBlank()) {
            return geocodingService.findOrFetchCity(nearCity)
                    .map(c -> new GeocodeResult(c.getLongitude(), c.getLatitude(), null))
                    .orElse(null);
        }
        return null;
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
    public Page<ListingDto> findAllActive(Pageable pageable) {
        Page<ListingEntity> page = listingRepository.findAllByDeletedAtIsNull(pageable);
        Map<UUID, ListingGeoProjection> geoById = fetchGeoSafely(idsOf(page.getContent()));
        return page.map(l -> toDto(l, geoById));
    }

    @Transactional(readOnly = true)
    public List<ListingDto> findForChat(Integer minPrice, Integer maxPrice,
                                        Integer minBedrooms, Integer minRooms,
                                        Integer minLivingAreaM2, String province,
                                        String city, String keywords,
                                        boolean sortByPriceDesc,
                                        String nearAddress, String nearCity, Integer radiusKm) {
        if (radiusKm != null && (nearAddress != null || nearCity != null)) {
            GeocodeResult latLon = resolveLatLon(nearAddress, nearCity);
            if (latLon != null) {
                List<ListingEntity> results = listingRepository.searchForChatNearLocation(
                                minBedrooms, minRooms, minLivingAreaM2,
                                province, keywords, minPrice, maxPrice,
                                latLon.lon(), latLon.lat(), radiusKm * 1000);
                Map<UUID, ListingGeoProjection> geoById = fetchGeoSafely(idsOf(results));
                return results.stream().map(l -> toDto(l, geoById)).toList();
            }
        }
        List<ListingEntity> results = listingRepository.searchForChat(minBedrooms, minRooms, minLivingAreaM2,
                        province, city, keywords, minPrice, maxPrice, sortByPriceDesc);
        Map<UUID, ListingGeoProjection> geoById = fetchGeoSafely(idsOf(results));
        return results.stream().map(l -> toDto(l, geoById)).toList();
    }

    @Transactional(readOnly = true)
    public List<PriceDropResult> findPriceDropListings(String city, double minDropPercent) {
        List<UUID> ids = listingRepository.findListingIdsWithPriceDrop(city, minDropPercent)
                .stream().map(UUID::fromString).toList();
        return listingRepository.findByIdIn(ids).stream()
                .map(listing -> {
                    List<PriceHistoryEntryDto> history = priceHistoryRepository
                            .findByListingIdOrderByTimestampAsc(listing.getId())
                            .stream()
                            .filter(e -> "asking_price".equals(e.getStatus()))
                            .map(mapper::toDto)
                            .toList();
                    if (history.size() < 2) return null;
                    int original = history.getFirst().price();
                    int current = history.getLast().price();
                    double drop = (original - current) * 100.0 / original;
                    return new PriceDropResult(toDto(listing), original, current, drop);
                })
                .filter(Objects::nonNull)
                .sorted((a, b) -> Double.compare(b.dropPercent(), a.dropPercent()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ListingDto> findByAddress(String street, String houseNumber, String city) {
        String s = street != null ? street.strip() : null;
        String n = houseNumber != null ? houseNumber.strip() : null;
        String c = city != null && !city.isBlank() ? city.strip() : null;
        if (c != null) {
            List<ListingEntity> withCity = listingRepository
                    .findByStreetIgnoreCaseAndHouseNumberIgnoreCaseAndCityIgnoreCase(s, n, c);
            if (!withCity.isEmpty()) return Optional.of(toDto(withCity.get(0)));
        }
        return listingRepository.findByStreetIgnoreCaseAndHouseNumberIgnoreCase(s, n)
                .stream().findFirst().map(this::toDto);
    }

    public void refreshAllPriceHistory() {
        priceHistoryService.refreshAll();
    }

    @Transactional
    public void deleteAllDeleted() {
        listingRepository.deleteAllByDeletedAtIsNotNull();
    }

    @Transactional(readOnly = true)
    public List<PriceHistoryEntryDto> findPriceHistoryByListingId(UUID listingId) {
        return priceHistoryRepository.findByListingIdOrderByTimestampAsc(listingId)
                .stream().map(mapper::toDto).toList();
    }

    private List<UUID> idsOf(List<ListingEntity> entities) {
        return entities.stream().map(ListingEntity::getId).toList();
    }

    /**
     * Best-effort batched geo lookup. A failure here (e.g. a transient DB issue) must never
     * break listing search or detail — it only means the affected listings won't show a
     * location on the map.
     */
    private Map<UUID, ListingGeoProjection> fetchGeoSafely(List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        try {
            return listingRepository.findGeoByIds(ids).stream()
                    .collect(Collectors.toMap(ListingGeoProjection::getId, p -> p));
        } catch (Exception e) {
            log.warn("Failed to load geo data for {} listing(s); continuing without map data", ids.size(), e);
            return Map.of();
        }
    }

    private GeoLocation toGeoLocation(ListingGeoProjection p) {
        if (p == null || p.getLatitude() == null || p.getLongitude() == null) return null;
        return new GeoLocation(p.getLatitude(), p.getLongitude(),
                p.getBboxLatMin(), p.getBboxLatMax(), p.getBboxLonMin(), p.getBboxLonMax());
    }

    private ListingDto toDto(ListingEntity l, Map<UUID, ListingGeoProjection> geoById) {
        Integer currentPrice = priceHistoryRepository
                .findFirstByListingIdAndStatusOrderByTimestampDesc(l.getId(), "asking_price")
                .map(PriceHistoryEntryEntity::getPrice)
                .orElse(null);
        GeoLocation location = toGeoLocation(geoById.get(l.getId()));
        return mapper.toDto(l, currentPrice, location);
    }

    private ListingDto toDto(ListingEntity l) {
        return toDto(l, fetchGeoSafely(List.of(l.getId())));
    }
}
```

- [ ] **Step 4: Update the existing `stubToDto` helper in `ListingServiceTest`**

Before:
```java
    private void stubToDto(ListingEntity e, ListingDto dto) {
        when(priceHistoryRepository.findFirstByListingIdAndStatusOrderByTimestampDesc(
                e.getId(), "asking_price")).thenReturn(Optional.empty());
        when(mapper.toDto(e, null)).thenReturn(dto);
    }
```
After:
```java
    private void stubToDto(ListingEntity e, ListingDto dto) {
        when(priceHistoryRepository.findFirstByListingIdAndStatusOrderByTimestampDesc(
                e.getId(), "asking_price")).thenReturn(Optional.empty());
        when(mapper.toDto(e, null, null)).thenReturn(dto);
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

```
cd hermes-backend && mvnw.cmd test -Dtest=ListingServiceTest,ListingServiceSearchTest,ListingServiceFindForChatTest
```
Expected: all pass (existing tests are unaffected since unstubbed `findGeoByIds` calls on lists with entries return an empty list by default — no geo data, no exception; the empty-content-page tests never call `findGeoByIds` at all per the new `idsOf(...).isEmpty()` guard).

- [ ] **Step 6: Full backend test suite**

```
cd hermes-backend && mvnw.cmd test
```
Expected: full pass.

- [ ] **Step 7: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingService.java hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingServiceTest.java
git commit -m "feat(listing): merge batched geo data into ListingDto with resilient fallback"
```

After committing, **update the Progress table**: mark Task 3 `✅ Complete` and fill in the commit hash.

---

## Task 4: Geocoding backfill endpoint

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/geocoding/ListingGeocodingBackfillService.java`
- Create: `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/geocoding/ListingGeocodingBackfillServiceTest.java`
- Modify: `hermes-backend/src/main/resources/openapi/listing.yaml`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingController.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingControllerTest.java`

**Interfaces:**
- Consumes: `ListingRepository.findIdsMissingLocation(): List<String>` (Task 1), existing `JmsQueues.GEOCODING_FETCH`, existing `FetchGeocodingCommand(UUID listingId)`.
- Produces: `ListingGeocodingBackfillService.queueMissingGeocoding(): int`; new endpoint `POST /api/listings/geocoding/backfill` → `202 Accepted` `{ "queuedCount": n }`.

- [ ] **Step 1: Write the failing service test**

```java
package com.kropholler.dev.hermes.listing.geocoding;

import com.kropholler.dev.hermes.listing.async.JmsQueues;
import com.kropholler.dev.hermes.listing.async.command.FetchGeocodingCommand;
import com.kropholler.dev.hermes.listing.data.ListingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingGeocodingBackfillServiceTest {

    @Mock private ListingRepository listingRepository;
    @Mock private JmsTemplate jmsTemplate;

    @InjectMocks
    private ListingGeocodingBackfillService service;

    @Test
    void queueMissingGeocoding_sendsOneCommandPerMissingListing() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(listingRepository.findIdsMissingLocation()).thenReturn(List.of(id1.toString(), id2.toString()));

        int result = service.queueMissingGeocoding();

        assertThat(result).isEqualTo(2);
        verify(jmsTemplate).convertAndSend(JmsQueues.GEOCODING_FETCH, new FetchGeocodingCommand(id1));
        verify(jmsTemplate).convertAndSend(JmsQueues.GEOCODING_FETCH, new FetchGeocodingCommand(id2));
    }

    @Test
    void queueMissingGeocoding_noMissingListings_returnsZeroAndSendsNothing() {
        when(listingRepository.findIdsMissingLocation()).thenReturn(List.of());

        int result = service.queueMissingGeocoding();

        assertThat(result).isZero();
        verify(jmsTemplate, org.mockito.Mockito.never()).convertAndSend(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Object.class));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```
cd hermes-backend && mvnw.cmd test -Dtest=ListingGeocodingBackfillServiceTest
```
Expected: FAIL — `ListingGeocodingBackfillService` does not exist.

- [ ] **Step 3: Implement the service**

```java
package com.kropholler.dev.hermes.listing.geocoding;

import com.kropholler.dev.hermes.listing.async.JmsQueues;
import com.kropholler.dev.hermes.listing.async.command.FetchGeocodingCommand;
import com.kropholler.dev.hermes.listing.data.ListingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListingGeocodingBackfillService {

    private final ListingRepository listingRepository;
    private final JmsTemplate jmsTemplate;

    @Transactional(readOnly = true)
    public int queueMissingGeocoding() {
        List<UUID> ids = listingRepository.findIdsMissingLocation().stream()
                .map(UUID::fromString)
                .toList();
        for (UUID id : ids) {
            jmsTemplate.convertAndSend(JmsQueues.GEOCODING_FETCH, new FetchGeocodingCommand(id));
        }
        log.info("Queued {} listing(s) for geocoding backfill", ids.size());
        return ids.size();
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```
cd hermes-backend && mvnw.cmd test -Dtest=ListingGeocodingBackfillServiceTest
```
Expected: PASS.

- [ ] **Step 5: Add the OpenAPI path and schema**

In `hermes-backend/src/main/resources/openapi/listing.yaml`, add a new path (alongside the existing `/api/listings/{id}/rescrape` etc.):

```yaml
  /api/listings/geocoding/backfill:
    post:
      operationId: backfillListingGeocoding
      tags: [Listings]
      responses:
        '202':
          description: Backfill queued
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/GeocodingBackfillResponse'
```

And a new schema under `components.schemas`:

```yaml
    GeocodingBackfillResponse:
      type: object
      properties:
        queuedCount:
          type: integer
```

- [ ] **Step 6: Add the controller endpoint**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingController.java`, add the field:

```java
    private final ListingGeocodingBackfillService backfillService;
```

(add the import `import com.kropholler.dev.hermes.listing.geocoding.ListingGeocodingBackfillService;`)

And add the method:

```java
    @Override
    public ResponseEntity<GeocodingBackfillResponse> backfillListingGeocoding() {
        int queuedCount = backfillService.queueMissingGeocoding();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(new GeocodingBackfillResponse().queuedCount(queuedCount));
    }
```

(`GeocodingBackfillResponse` comes from the existing `import com.kropholler.dev.hermes.listing.openapi.*;` wildcard import already at the top of the file.)

- [ ] **Step 7: Add the controller test**

Add to `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingControllerTest.java`:

Add the mock field alongside the existing `@MockitoBean` fields:
```java
    @MockitoBean com.kropholler.dev.hermes.listing.geocoding.ListingGeocodingBackfillService backfillService;
```

Add the test:
```java
    @Test
    void backfillListingGeocoding_returns202WithQueuedCount() throws Exception {
        when(backfillService.queueMissingGeocoding()).thenReturn(7);

        mockMvc.perform(post("/api/listings/geocoding/backfill"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.queuedCount").value(7));
    }
```

- [ ] **Step 8: Run the controller test**

```
cd hermes-backend && mvnw.cmd test -Dtest=ListingControllerTest
```
Expected: PASS.

- [ ] **Step 9: Full backend test suite**

```
cd hermes-backend && mvnw.cmd test
```
Expected: full pass.

- [ ] **Step 10: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/geocoding/ListingGeocodingBackfillService.java hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/geocoding/ListingGeocodingBackfillServiceTest.java hermes-backend/src/main/resources/openapi/listing.yaml hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingController.java hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingControllerTest.java
git commit -m "feat(listing): add manual geocoding backfill endpoint for pre-existing listings"
```

After committing, **update the Progress table**: mark Task 4 `✅ Complete` and fill in the commit hash. **This is the last backend task** — the API now fully supports the frontend work below.

---

## Task 5: Frontend — add Leaflet dependency

**Files:**
- Modify: `hermes-frontend/package.json`
- Modify: `hermes-frontend/angular.json`

**Interfaces:**
- Produces: `leaflet` importable as `import * as L from 'leaflet'` (or `import L from 'leaflet'` depending on the installed version's typings — verified in Task 7), Leaflet's default marker icons resolvable at runtime.

- [ ] **Step 1: Install the packages**

```
cd hermes-frontend && npm install leaflet && npm install --save-dev @types/leaflet
```

- [ ] **Step 2: Verify `package.json` was updated**

Confirm `leaflet` appears under `dependencies` and `@types/leaflet` under `devDependencies` in `hermes-frontend/package.json`.

- [ ] **Step 3: Add Leaflet's CSS to both the build and test style arrays**

In `hermes-frontend/angular.json`, there are two `"styles"` arrays — one under the `"build"` architect target (around line 30) and one under the `"test"` architect target (around line 90). Add `"node_modules/leaflet/dist/leaflet.css"` as the first entry in **both** arrays, before `"src/styles.css"`:

Before (both occurrences):
```json
            "styles": [
              "src/styles.css"
            ],
```
After (both occurrences):
```json
            "styles": [
              "node_modules/leaflet/dist/leaflet.css",
              "src/styles.css"
            ],
```

- [ ] **Step 4: Verify the app still builds**

```
cd hermes-frontend && npx ng build
```
Expected: build succeeds with no errors.

- [ ] **Step 5: Commit**

```bash
git add hermes-frontend/package.json hermes-frontend/package-lock.json hermes-frontend/angular.json
git commit -m "chore(frontend): add leaflet dependency and global CSS"
```

After committing, **update the Progress table**: mark Task 5 `✅ Complete` and fill in the commit hash.

---

## Task 6: Frontend — `api.types.ts` geo fields

**Files:**
- Modify: `hermes-frontend/src/app/core/api.types.ts`

**Interfaces:**
- Produces: `GeoLocation` interface; `ListingSummaryResponse.location?: GeoLocation | null`; `ListingDetailResponse.location?: GeoLocation | null`.

- [ ] **Step 1: Add the `GeoLocation` interface and wire it into both response types**

In `hermes-frontend/src/app/core/api.types.ts`, add this interface right before `ListingPage`:

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

Then add `location?: GeoLocation | null;` as the last property of `ListingSummaryResponse`:

Before:
```ts
export interface ListingSummaryResponse {
  id: string;
  street: string;
  houseNumber: string;
  houseNumberAddition?: string;
  zipCode: string;
  city: string;
  province: string;
  askingPrice?: number;
  status?: ListingStatus;
  firstSeenAt: string;
}
```
After:
```ts
export interface ListingSummaryResponse {
  id: string;
  street: string;
  houseNumber: string;
  houseNumberAddition?: string;
  zipCode: string;
  city: string;
  province: string;
  askingPrice?: number;
  status?: ListingStatus;
  firstSeenAt: string;
  location?: GeoLocation | null;
}
```

And as the last property of `ListingDetailResponse`:

Before:
```ts
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
After:
```ts
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
  location?: GeoLocation | null;
}
```

- [ ] **Step 2: Also add a `GeocodingBackfillResponse` type for Task 10**

Add this interface, anywhere near `ScrapingSessionResponse`:

```ts
export interface GeocodingBackfillResponse {
  queuedCount: number;
}
```

- [ ] **Step 3: Verify the frontend still compiles**

```
cd hermes-frontend && npx tsc --noEmit -p tsconfig.app.json
```
Expected: no type errors (these are additive, optional fields — nothing currently constructs a `ListingSummaryResponse`/`ListingDetailResponse` object literal missing them since they're optional).

- [ ] **Step 4: Commit**

```bash
git add hermes-frontend/src/app/core/api.types.ts
git commit -m "feat(frontend): add GeoLocation type to listing API types"
```

After committing, **update the Progress table**: mark Task 6 `✅ Complete` and fill in the commit hash.

---

## Task 7: Frontend — `ListingMapComponent`

**Files:**
- Create: `hermes-frontend/src/app/shared/listing-map.component.ts`
- Create: `hermes-frontend/src/app/shared/listing-map.component.spec.ts`

**Interfaces:**
- Consumes: `GeoLocation` (Task 6).
- Produces: `MapListing` interface (`id: string`, `street?: string`, `houseNumber?: string`, `city?: string`, `currentPrice?: number`, `location?: GeoLocation | null`); `ListingMapComponent` with `@Input() listings: MapListing[]` and `@Output() listingSelected: EventEmitter<string>`.

- [ ] **Step 1: Write the failing spec**

```ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ListingMapComponent, MapListing } from './listing-map.component';

describe('ListingMapComponent', () => {
  let fixture: ComponentFixture<ListingMapComponent>;
  let component: ListingMapComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ListingMapComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ListingMapComponent);
    component = fixture.componentInstance;
  });

  const withLocation: MapListing = {
    id: 'a1',
    street: 'Kerkstraat',
    houseNumber: '5',
    city: 'Amsterdam',
    currentPrice: 350000,
    location: { latitude: 52.3676, longitude: 4.9041, bboxLatMin: 52.3666, bboxLatMax: 52.3686, bboxLonMin: 4.9031, bboxLonMax: 4.9051 },
  };

  const withoutLocation: MapListing = {
    id: 'b2',
    street: 'Damrak',
    houseNumber: '1',
    city: 'Amsterdam',
    currentPrice: 400000,
    location: null,
  };

  it('creates without error when given no listings', () => {
    fixture.componentRef.setInput('listings', []);
    fixture.detectChanges();

    expect(component).toBeTruthy();
  });

  it('only plots listings that have a location', () => {
    fixture.componentRef.setInput('listings', [withLocation, withoutLocation]);
    fixture.detectChanges();

    expect(component.plottedCount()).toBe(1);
  });

  it('emits listingSelected with the correct id when a marker is clicked', () => {
    fixture.componentRef.setInput('listings', [withLocation]);
    fixture.detectChanges();

    const emitted: string[] = [];
    component.listingSelected.subscribe(id => emitted.push(id));

    component.selectListing(withLocation.id);

    expect(emitted).toEqual(['a1']);
  });

  it('recomputes plotted markers when the listings input changes', () => {
    fixture.componentRef.setInput('listings', [withoutLocation]);
    fixture.detectChanges();
    expect(component.plottedCount()).toBe(0);

    fixture.componentRef.setInput('listings', [withLocation, withoutLocation]);
    fixture.detectChanges();
    expect(component.plottedCount()).toBe(1);
  });
});
```

- [ ] **Step 2: Run the spec to verify it fails**

```
cd hermes-frontend && npx ng test --watch=false --include='**/listing-map.component.spec.ts'
```
Expected: FAIL — `ListingMapComponent` does not exist.

- [ ] **Step 3: Implement `ListingMapComponent`**

```ts
import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  ViewChild,
  computed,
  signal,
} from '@angular/core';
import * as L from 'leaflet';
import { GeoLocation } from '../core/api.types';

// Angular's build pipeline does not resolve Leaflet's default marker icon
// paths correctly; point them at the bundled asset URLs explicitly.
delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'leaflet/marker-icon-2x.png',
  iconUrl: 'leaflet/marker-icon.png',
  shadowUrl: 'leaflet/marker-shadow.png',
});

export interface MapListing {
  id: string;
  street?: string;
  houseNumber?: string;
  city?: string;
  currentPrice?: number;
  location?: GeoLocation | null;
}

@Component({
  selector: 'app-listing-map',
  standalone: true,
  template: `<div #mapContainer class="w-full h-full min-h-[320px] rounded-xl"></div>`,
})
export class ListingMapComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input() listings: MapListing[] = [];
  @Output() listingSelected = new EventEmitter<string>();

  @ViewChild('mapContainer', { static: true }) private mapContainer!: ElementRef<HTMLDivElement>;

  private map?: L.Map;
  private layerGroup?: L.LayerGroup;
  private viewReady = false;

  private readonly listingsSignal = signal<MapListing[]>([]);
  protected readonly plottedCount = computed(
    () => this.listingsSignal().filter(l => this.hasLocation(l)).length,
  );

  ngAfterViewInit(): void {
    this.map = L.map(this.mapContainer.nativeElement).setView([52.1326, 5.2913], 7); // Netherlands default
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap contributors',
      maxZoom: 19,
    }).addTo(this.map);
    this.layerGroup = L.layerGroup().addTo(this.map);
    this.viewReady = true;
    this.render();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['listings']) {
      this.listingsSignal.set(this.listings ?? []);
      if (this.viewReady) this.render();
    }
  }

  ngOnDestroy(): void {
    this.map?.remove();
  }

  private hasLocation(l: MapListing): l is MapListing & { location: GeoLocation } {
    return l.location != null;
  }

  private render(): void {
    if (!this.layerGroup || !this.map) return;
    this.layerGroup.clearLayers();

    const plotted = this.listingsSignal().filter(l => this.hasLocation(l));
    const bounds: L.LatLngBoundsExpression = [];

    for (const listing of plotted) {
      const loc = listing.location!;
      const marker = L.marker([loc.latitude, loc.longitude]);
      marker.bindTooltip(this.tooltipText(listing));
      marker.on('click', () => this.selectListing(listing.id));
      marker.addTo(this.layerGroup!);
      bounds.push([loc.latitude, loc.longitude]);

      if (loc.bboxLatMin != null && loc.bboxLatMax != null && loc.bboxLonMin != null && loc.bboxLonMax != null) {
        const rectBounds: L.LatLngBoundsExpression = [
          [loc.bboxLatMin, loc.bboxLonMin],
          [loc.bboxLatMax, loc.bboxLonMax],
        ];
        const rect = L.rectangle(rectBounds, { color: '#06b6d4', weight: 1, fillOpacity: 0.1 });
        rect.on('click', () => this.selectListing(listing.id));
        rect.addTo(this.layerGroup!);
        bounds.push([loc.bboxLatMin, loc.bboxLonMin], [loc.bboxLatMax, loc.bboxLonMax]);
      }
    }

    if (bounds.length > 0) {
      this.map.fitBounds(bounds, { padding: [24, 24] });
    }
  }

  private tooltipText(listing: MapListing): string {
    const address = [listing.street, listing.houseNumber].filter(Boolean).join(' ');
    const price = listing.currentPrice != null ? `€ ${listing.currentPrice.toLocaleString('nl-NL')}` : '';
    return [address, listing.city, price].filter(Boolean).join(' · ');
  }

  protected selectListing(id: string): void {
    this.listingSelected.emit(id);
  }
}
```

- [ ] **Step 4: Copy Leaflet's marker image assets so the icon paths resolve**

```
mkdir hermes-frontend/src/assets/leaflet
cp hermes-frontend/node_modules/leaflet/dist/images/marker-icon.png hermes-frontend/src/assets/leaflet/
cp hermes-frontend/node_modules/leaflet/dist/images/marker-icon-2x.png hermes-frontend/src/assets/leaflet/
cp hermes-frontend/node_modules/leaflet/dist/images/marker-shadow.png hermes-frontend/src/assets/leaflet/
```

Then fix the icon URLs in `listing-map.component.ts` (Step 3's code) to point at `assets/leaflet/...` instead of the bare `leaflet/...` paths — replace:

```ts
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'leaflet/marker-icon-2x.png',
  iconUrl: 'leaflet/marker-icon.png',
  shadowUrl: 'leaflet/marker-shadow.png',
});
```
with:
```ts
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'assets/leaflet/marker-icon-2x.png',
  iconUrl: 'assets/leaflet/marker-icon.png',
  shadowUrl: 'assets/leaflet/marker-shadow.png',
});
```

(`src/assets` is already registered as a build asset in `angular.json`'s `"assets"` array, so anything copied there is served as-is at `/assets/...` in both `ng serve` and `ng build`.)

- [ ] **Step 5: Run the spec to verify it passes**

```
cd hermes-frontend && npx ng test --watch=false --include='**/listing-map.component.spec.ts'
```
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add hermes-frontend/src/app/shared/listing-map.component.ts hermes-frontend/src/app/shared/listing-map.component.spec.ts hermes-frontend/src/assets/leaflet
git commit -m "feat(frontend): add reusable ListingMapComponent"
```

After committing, **update the Progress table**: mark Task 7 `✅ Complete` and fill in the commit hash.

---

## Task 8: Frontend — listings page list/map toggle

**Files:**
- Modify: `hermes-frontend/src/app/pages/listings/listings-page.component.ts`
- Modify: `hermes-frontend/src/app/pages/listings/listings-page.component.html`
- Test: `hermes-frontend/src/app/pages/listings/listings-page.component.spec.ts` (create if it doesn't already exist — check first with `ls hermes-frontend/src/app/pages/listings/`)

**Interfaces:**
- Consumes: `ListingMapComponent`, `MapListing` (Task 7).
- Produces: `ListingsPageComponent.viewMode: 'list' | 'map'`, `ListingsPageComponent.toggleView(mode: 'list' | 'map'): void`, `ListingsPageComponent.mapListings(): MapListing[]` (computed).

- [ ] **Step 1: Check for an existing spec file**

```
ls hermes-frontend/src/app/pages/listings/
```
If `listings-page.component.spec.ts` exists, add the new tests (Step 4 below) to it instead of creating a new file — adjust the remaining steps accordingly.

- [ ] **Step 2: Add view-mode state to the component**

In `hermes-frontend/src/app/pages/listings/listings-page.component.ts`, add these imports:

```ts
import { computed } from '@angular/core';
import { ListingMapComponent, MapListing } from '../../shared/listing-map.component';
```

Add `computed` to the existing `@angular/core` import line (it currently imports `Component, inject, OnDestroy, OnInit` — extend that list) and add `ListingMapComponent` to the component's `imports` array.

Add this property and method to the class body (near `currentPage`/`pageSize`):

```ts
  protected viewMode: 'list' | 'map' = 'list';

  protected readonly mapListings = computed<MapListing[]>(() =>
    this.svc.listings().content.map(l => ({
      id: l.id,
      street: l.street,
      houseNumber: l.houseNumber,
      city: l.city,
      currentPrice: l.askingPrice,
      location: l.location,
    })),
  );

  protected readonly listingsWithoutLocationCount = computed(
    () => this.mapListings().filter(l => l.location == null).length,
  );

  toggleView(mode: 'list' | 'map'): void {
    this.viewMode = mode;
  }
```

- [ ] **Step 3: Wire the toggle and map view into the template**

In `hermes-frontend/src/app/pages/listings/listings-page.component.html`, add a toggle control right after the filters card (`</app-section-card>` that closes the filter card, before the `@if (svc.error())` block):

```html
<div class="flex items-center gap-2 mb-4">
  <button (click)="toggleView('list')"
    class="rounded-lg px-3 py-1.5 text-sm font-medium transition-colors"
    [class]="viewMode === 'list' ? 'bg-cyan-500 text-white' : 'bg-white text-slate-500 border border-slate-200 hover:bg-slate-50'">
    List
  </button>
  <button (click)="toggleView('map')"
    class="rounded-lg px-3 py-1.5 text-sm font-medium transition-colors"
    [class]="viewMode === 'map' ? 'bg-cyan-500 text-white' : 'bg-white text-slate-500 border border-slate-200 hover:bg-slate-50'">
    Map
  </button>
</div>
```

Then wrap the existing `@else { ... }` block (the table + pagination, currently everything after `@if (svc.loading()) { ... }`) so it only renders in list mode, and add the map branch. Change:

```html
} @else {
  <app-section-card padding="" extraClass="overflow-hidden">
    <div class="overflow-x-auto">
      <table ...>
        ...
      </table>
    </div>
  </app-section-card>

  <div class="mt-4 flex ...">
    ...
  </div>
}
```
to:
```html
} @else if (viewMode === 'list') {
  <app-section-card padding="" extraClass="overflow-hidden">
    <div class="overflow-x-auto">
      <table ...>
        ...
      </table>
    </div>
  </app-section-card>

  <div class="mt-4 flex ...">
    ...
  </div>
} @else {
  @if (listingsWithoutLocationCount() > 0) {
    <p class="text-xs text-slate-400 mb-2">
      {{ mapListings().length - listingsWithoutLocationCount() }} of {{ mapListings().length }} shown on map
      ({{ listingsWithoutLocationCount() }} without location data yet)
    </p>
  }
  <app-section-card padding="p-2" extraClass="h-[480px]">
    <app-listing-map [listings]="mapListings()" (listingSelected)="navigate($event)" />
  </app-section-card>
}
```

(Do not retype the table/pagination markup — only change the two block-opening lines shown, `} @else {` → `} @else if (viewMode === 'list') {`, and append the new `} @else { ... }` map branch after the existing closing `}` of the pagination div.)

- [ ] **Step 4: Write the spec (if none existed, create the file; otherwise append tests)**

```ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ListingsPageComponent } from './listings-page.component';
import { ListingsService } from '../../core/listings.service';

describe('ListingsPageComponent', () => {
  let fixture: ComponentFixture<ListingsPageComponent>;
  let component: ListingsPageComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ListingsPageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(ListingsPageComponent);
    component = fixture.componentInstance;
  });

  it('defaults to list view', () => {
    expect((component as any).viewMode).toBe('list');
  });

  it('toggleView switches to map and back', () => {
    (component as any).toggleView('map');
    expect((component as any).viewMode).toBe('map');

    (component as any).toggleView('list');
    expect((component as any).viewMode).toBe('list');
  });

  it('mapListings maps the current page content with location passed through', () => {
    const svc = TestBed.inject(ListingsService);
    svc.listings.set({
      content: [
        { id: '1', street: 'Kerkstraat', houseNumber: '5', zipCode: '1000AA', city: 'Amsterdam', province: 'Noord-Holland', askingPrice: 350000, firstSeenAt: '2026-01-01', location: { latitude: 52.1, longitude: 4.9 } },
      ],
      totalElements: 1, totalPages: 1, page: 0, size: 20,
    });
    fixture.detectChanges();

    const mapped = (component as any).mapListings();

    expect(mapped).toEqual([
      { id: '1', street: 'Kerkstraat', houseNumber: '5', city: 'Amsterdam', currentPrice: 350000, location: { latitude: 52.1, longitude: 4.9 } },
    ]);
  });
});
```

- [ ] **Step 5: Run the spec**

```
cd hermes-frontend && npx ng test --watch=false --include='**/listings-page.component.spec.ts'
```
Expected: PASS.

- [ ] **Step 6: Manual smoke check**

```
cd hermes-frontend && npx ng serve
```
Open `/listings`, confirm the List/Map toggle appears and switching to Map renders a Leaflet map (may show no pins if no listings are geocoded yet — expected until Task 4's backfill endpoint has been triggered against real data).

- [ ] **Step 7: Commit**

```bash
git add hermes-frontend/src/app/pages/listings/listings-page.component.ts hermes-frontend/src/app/pages/listings/listings-page.component.html hermes-frontend/src/app/pages/listings/listings-page.component.spec.ts
git commit -m "feat(frontend): add list/map toggle to the listings search page"
```

After committing, **update the Progress table**: mark Task 8 `✅ Complete` and fill in the commit hash.

---

## Task 9: Frontend — listing detail page map

**Files:**
- Modify: `hermes-frontend/src/app/pages/listing-detail/listing-detail-page.component.ts`
- Modify: `hermes-frontend/src/app/pages/listing-detail/listing-detail-page.component.html`

**Interfaces:**
- Consumes: `ListingMapComponent`, `MapListing` (Task 7).
- Produces: `ListingDetailPageComponent.mapListings(): MapListing[]` (computed, 0 or 1 entries).

- [ ] **Step 1: Add the computed map input to the component**

In `hermes-frontend/src/app/pages/listing-detail/listing-detail-page.component.ts`, add `computed` is already imported — add `ListingMapComponent, MapListing` import:

```ts
import { ListingMapComponent, MapListing } from '../../shared/listing-map.component';
```

Add `ListingMapComponent` to the `imports` array in the `@Component` decorator.

Add this computed property to the class body:

```ts
  protected readonly mapListings = computed<MapListing[]>(() => {
    const listing = this.svc.currentListing();
    if (!listing || !listing.location) return [];
    return [{
      id: listing.id,
      street: listing.street,
      houseNumber: listing.houseNumber,
      city: listing.city,
      currentPrice: listing.currentPrice,
      location: listing.location,
    }];
  });
```

- [ ] **Step 2: Add the map section to the template**

In `hermes-frontend/src/app/pages/listing-detail/listing-detail-page.component.html`, add a new section right after the "Details"/"AI Summary" grid (`</div>` that closes the `grid grid-cols-1 md:grid-cols-2 gap-6` block, right before the final `}` that closes `@if (svc.currentListing(); as listing) {`):

```html
    @if (mapListings().length > 0) {
      <app-section-card class="mt-6" padding="p-2" extraClass="h-[360px]">
        <app-listing-map [listings]="mapListings()" />
      </app-section-card>
    } @else {
      <app-section-card class="mt-6">
        <p class="text-sm text-slate-400 italic">Location not yet available for this listing.</p>
      </app-section-card>
    }
```

- [ ] **Step 3: Manual smoke check**

```
cd hermes-frontend && npx ng serve
```
Open any listing detail page. If it has no location yet, confirm the "Location not yet available" message shows instead of an empty/broken map.

- [ ] **Step 4: Commit**

```bash
git add hermes-frontend/src/app/pages/listing-detail/listing-detail-page.component.ts hermes-frontend/src/app/pages/listing-detail/listing-detail-page.component.html
git commit -m "feat(frontend): show listing location on the listing detail page"
```

After committing, **update the Progress table**: mark Task 9 `✅ Complete` and fill in the commit hash.

---

## Task 10: Frontend — admin backfill trigger

**Files:**
- Modify: `hermes-frontend/src/app/core/scraping.service.ts`
- Modify: `hermes-frontend/src/app/pages/scraping/scraping-page.component.ts`
- Modify: `hermes-frontend/src/app/pages/scraping/scraping-page.component.html`

**Interfaces:**
- Consumes: `GeocodingBackfillResponse` (Task 6).
- Produces: `ScrapingService.backfillGeocoding(): void`, `ScrapingService.backfillResult: WritableSignal<GeocodingBackfillResponse | null>`, `ScrapingService.backfillLoading: WritableSignal<boolean>`.

- [ ] **Step 1: Add the backfill call to `ScrapingService`**

In `hermes-frontend/src/app/core/scraping.service.ts`, add the import:

```ts
import { GeocodingBackfillResponse } from './api.types';
```

Add these members to the class body:

```ts
  readonly backfillResult = signal<GeocodingBackfillResponse | null>(null);
  readonly backfillLoading = signal(false);
  readonly backfillError = signal<string | null>(null);

  backfillGeocoding(): void {
    this.backfillLoading.set(true);
    this.backfillError.set(null);
    this.backfillResult.set(null);
    this.http.post<GeocodingBackfillResponse>('/api/listings/geocoding/backfill', {}).subscribe({
      next: data => {
        this.backfillResult.set(data);
        this.backfillLoading.set(false);
      },
      error: err => {
        this.backfillError.set(err.error?.detail ?? 'Failed to queue geocoding backfill');
        this.backfillLoading.set(false);
      },
    });
  }
```

- [ ] **Step 2: Add the trigger button to the scraping page**

In `hermes-frontend/src/app/pages/scraping/scraping-page.component.ts`, no new component-class members are needed — the template will call `svc.backfillGeocoding()` directly.

In `hermes-frontend/src/app/pages/scraping/scraping-page.component.html`, add a new section at the end of the file (after the closing `}` of the `@if (svc.session(); as session) { ... }` block):

```html
<app-section-card class="mt-6" extraClass="max-w-lg space-y-3">
  <h2 class="text-xs font-semibold text-slate-400 uppercase tracking-wider">Geocoding backfill</h2>
  <p class="text-xs text-slate-500">
    Queue geocoding for listings that don't have map coordinates yet (e.g. listings scraped before the map feature existed).
  </p>
  <button (click)="svc.backfillGeocoding()" [disabled]="svc.backfillLoading()"
    class="rounded-lg bg-slate-800 px-4 py-2.5 text-sm font-semibold text-white
           hover:bg-slate-700 disabled:opacity-50 transition-colors">
    @if (svc.backfillLoading()) {
      <app-spinner color="white" label="Queuing..." />
    } @else {
      Backfill missing coordinates
    }
  </button>
  @if (svc.backfillResult(); as result) {
    <p class="text-sm text-emerald-600 font-medium">Queued {{ result.queuedCount }} listing(s) for geocoding.</p>
  }
  @if (svc.backfillError()) {
    <app-error-alert [message]="svc.backfillError()!" />
  }
</app-section-card>
```

- [ ] **Step 3: Manual smoke check**

```
cd hermes-frontend && npx ng serve
```
Open `/scraping`, click "Backfill missing coordinates", confirm the queued-count message appears (or an error alert if the backend isn't running).

- [ ] **Step 4: Commit**

```bash
git add hermes-frontend/src/app/core/scraping.service.ts hermes-frontend/src/app/pages/scraping/scraping-page.component.ts hermes-frontend/src/app/pages/scraping/scraping-page.component.html
git commit -m "feat(frontend): add manual geocoding backfill trigger to the scraping admin page"
```

After committing, **update the Progress table**: mark Task 10 `✅ Complete` and fill in the commit hash. **All tasks are now complete** — do a final full-repo check:

```
cd hermes-backend && mvnw.cmd test
cd ../hermes-frontend && npx ng test --watch=false && npx ng build
```
