# Geospatial Radius Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

## Progress

| Task | Status | Commit |
|------|--------|--------|
| Task 1: Infrastructure — PostGIS image, Flyway migration, Testcontainers | ✅ Done | 89db4d7 |
| Task 2: Maven dependency + Listing entity + City entity | ✅ Done | 761b6cb |
| Task 3: Nominatim client + geocoding JMS infrastructure | ✅ Done | 03c9083 |
| Task 4: GeocodingConsumer — async geocoding of listings after scraping | ✅ Done | 973689b |
| Task 5: GeocodingService — public service for on-demand geocoding | ✅ Done | fc564a7 |
| Task 6: Radius search in ListingRepository and ListingService | ✅ Done | 4458a2b |
| Task 7: Update ListingSearchParams, OpenAPI spec, and ListingController | ✅ Done | 4458a2b |
| Task 8: Extend AI ListingSearchTool with radius search | ✅ Done | 4458a2b |
| Task 9: Frontend — radius search UI | ✅ Done | bdbf90c |

**Goal:** Add PostGIS-backed geospatial data to listings and cities, geocode addresses via Nominatim during scraping, and allow users to search listings within a radius of an address or city — in both the paginated filter UI and the AI chat.

**Architecture:** The `listings` table gains a `location` (Point) and `bounding_box` (Polygon) column populated asynchronously via a new JMS queue (`geocoding.fetch`) after each scrape. A `cities` table caches geocoded city centroids for fast city-radius lookup. `ListingService.findAll` and `findForChat` both gain `nearAddress`, `nearCity`, and `radiusKm` parameters that resolve to lat/lon via a new `GeocodingService` before delegating to PostGIS `ST_DWithin` native queries.

**Tech Stack:** PostGIS 16-3.4 (Docker), `org.locationtech.jts:jts-core:1.20.0` (JTS geometry types), Hibernate 6 (spatial support built-in), Nominatim OSM API (HTTP), Spring RestClient, Spring JMS, Angular 22

---

## File Map

### New files
| File | Responsibility |
|---|---|
| `hermes-backend/src/main/resources/db/migration/V7__add_postgis_and_geo.sql` | PostGIS extension, geo columns on listings, cities table |
| `hermes-backend/src/main/java/…/listing/internal/City.java` | City entity (name, location, boundingBox) |
| `hermes-backend/src/main/java/…/listing/internal/CityRepository.java` | City JPA repo with name lookup |
| `hermes-backend/src/main/java/…/listing/internal/NominatimResponse.java` | Record mapping Nominatim JSON response |
| `hermes-backend/src/main/java/…/listing/internal/NominatimClient.java` | RestClient-based HTTP client for Nominatim |
| `hermes-backend/src/main/java/…/listing/internal/FetchGeocodingCommand.java` | JMS command record |
| `hermes-backend/src/main/java/…/listing/internal/GeocodingConsumer.java` | JMS listener: geocodes listing address, saves Point/Polygon |
| `hermes-backend/src/main/java/…/listing/GeocodingService.java` | Public service: `geocodeAddress`, `findOrFetchCity` |

### Modified files
| File | Change |
|---|---|
| `hermes-backend/docker-compose.yml` | `postgres:latest` → `postgis/postgis:16-3.4` |
| `hermes-backend/pom.xml` | Add `jts-core` dependency |
| `hermes-backend/src/main/java/…/listing/internal/Listing.java` | Add `Point location`, `Polygon boundingBox` |
| `hermes-backend/src/main/java/…/listing/ListingMapper.java` | Ignore `location`, `boundingBox` in MapStruct mapping |
| `hermes-backend/src/main/java/…/listing/internal/JmsQueues.java` | Add `GEOCODING_FETCH` constant |
| `hermes-backend/src/main/java/…/listing/internal/ListingPersistenceService.java` | Send `FetchGeocodingCommand` for new listings |
| `hermes-backend/src/main/java/…/listing/internal/ListingRepository.java` | Add `findNearby` (paginated) and `searchForChatNearLocation` native queries |
| `hermes-backend/src/main/java/…/listing/ListingSearchParams.java` | Add `nearAddress`, `nearCity`, `radiusKm` |
| `hermes-backend/src/main/java/…/listing/ListingService.java` | Inject `GeocodingService`; add radius path in `findAll` and `findForChat` |
| `hermes-backend/src/main/resources/openapi/api.yaml` | Add `nearAddress`, `nearCity`, `radiusKm` query params to `GET /api/listings` |
| `hermes-backend/src/main/java/…/api/ListingController.java` | Extract and pass three new params to `ListingSearchParams` |
| `hermes-backend/src/main/java/…/ai/internal/ListingSearchTool.java` | Add `nearAddress`, `nearCity`, `radiusKm` `@ToolParam`s |
| `hermes-backend/src/test/java/…/TestcontainersConfiguration.java` | Switch Postgres image to `postgis/postgis:16-3.4` |
| `hermes-backend/src/test/java/…/listing/ListingServiceFindForChatTest.java` | Add `@Mock GeocodingService`, update `stubSearchForChat` |
| `hermes-frontend/src/app/core/api.types.ts` | Add `nearAddress`, `nearCity`, `radiusKm` to `ListingSearchFilter` |
| `hermes-frontend/src/app/core/listings.service.ts` | Pass three new params in `loadListings` |
| `hermes-frontend/src/app/pages/listings/listings-page.component.ts` | Add radius search fields and bindings |
| `hermes-frontend/src/app/pages/listings/listings-page.component.html` | Add radius search UI section |

---

## Task 1: Infrastructure — PostGIS image, Flyway migration, Testcontainers

**Files:**
- Modify: `hermes-backend/docker-compose.yml`
- Create: `hermes-backend/src/main/resources/db/migration/V7__add_postgis_and_geo.sql`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/TestcontainersConfiguration.java`

- [ ] **Step 1: Swap postgres image to postgis in docker-compose**

In `hermes-backend/docker-compose.yml`, find the `postgres:` service and change:
```yaml
  postgres:
    image: 'postgres:latest'
```
to:
```yaml
  postgres:
    image: 'postgis/postgis:16-3.4'
```

- [ ] **Step 2: Create Flyway migration for PostGIS and geo columns**

Create `hermes-backend/src/main/resources/db/migration/V7__add_postgis_and_geo.sql`:
```sql
CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE listings ADD COLUMN IF NOT EXISTS location geometry(Point, 4326);
ALTER TABLE listings ADD COLUMN IF NOT EXISTS bounding_box geometry(Polygon, 4326);

CREATE INDEX IF NOT EXISTS idx_listings_location ON listings USING gist(location);

CREATE TABLE IF NOT EXISTS cities (
    id          UUID                     NOT NULL,
    name        VARCHAR(255)             NOT NULL,
    location    geometry(Point, 4326)    NOT NULL,
    bounding_box geometry(Polygon, 4326),
    fetched_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_cities_name UNIQUE (name)
);

CREATE INDEX IF NOT EXISTS idx_cities_location ON cities USING gist(location);
```

- [ ] **Step 3: Update Testcontainers to use PostGIS image**

In `TestcontainersConfiguration.java`, change:
```java
@Bean
@ServiceConnection
PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
}
```
to:
```java
@Bean
@ServiceConnection
PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer(
        DockerImageName.parse("postgis/postgis:16-3.4")
            .asCompatibleSubstituteFor("postgres")
    );
}
```

- [ ] **Step 4: Verify the backend compiles and existing tests still pass**

```bash
cd hermes-backend
./mvnw test -pl . -Dtest="HermesBackendApplicationTests" -q
```
Expected: BUILD SUCCESS (the Modulith structure test passes, app context loads with PostGIS container).

- [ ] **Step 5: Commit**

```bash
git add hermes-backend/docker-compose.yml \
        hermes-backend/src/main/resources/db/migration/V7__add_postgis_and_geo.sql \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/TestcontainersConfiguration.java
git commit -m "feat(geo): switch to postgis image and add geo columns + cities table migration"
```

---

## Task 2: Maven dependency + Listing entity + City entity

**Files:**
- Modify: `hermes-backend/pom.xml`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/Listing.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingMapper.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/City.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/CityRepository.java`

- [ ] **Step 1: Add jts-core dependency to pom.xml**

Inside the `<dependencies>` block in `pom.xml`, add after the `postgresql` dependency:
```xml
<dependency>
    <groupId>org.locationtech.jts</groupId>
    <artifactId>jts-core</artifactId>
    <version>1.20.0</version>
</dependency>
```

> **Note:** Do NOT add `org.hibernate.orm:hibernate-spatial`. Spatial support (JTS `Point`, `Polygon`, PostGIS dialect) was merged into `hibernate-core` in Hibernate 6 and is still there in Hibernate 7 (which this project uses). Adding `hibernate-spatial` would be redundant. Only `jts-core` is needed for the geometry classes.

- [ ] **Step 2: Add spatial fields to Listing entity**

In `Listing.java`, add imports and two new fields after `private Instant deletedAt;`:
```java
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
```
```java
@Column(columnDefinition = "geometry(Point,4326)")
private Point location;

@Column(columnDefinition = "geometry(Polygon,4326)")
private Polygon boundingBox;
```

- [ ] **Step 3: Ignore new fields in ListingMapper**

In `ListingMapper.java`, update the annotation:
```java
@BeanMapping(ignoreUnmappedSourceProperties = {"lastUpdatedAt", "deletedAt", "location", "boundingBox"})
ListingDto toDto(Listing listing, Integer currentPrice);
```

- [ ] **Step 4: Create City entity**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/City.java`:
```java
package com.kropholler.dev.hermes.listing.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cities")
@Getter
@Setter
@NoArgsConstructor
public class City {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point location;

    @Column(columnDefinition = "geometry(Polygon,4326)")
    private Polygon boundingBox;

    @Column(nullable = false)
    private Instant fetchedAt = Instant.now();
}
```

- [ ] **Step 5: Create CityRepository**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/CityRepository.java`:
```java
package com.kropholler.dev.hermes.listing.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CityRepository extends JpaRepository<City, UUID> {
    Optional<City> findByNameIgnoreCase(String name);
}
```

- [ ] **Step 6: Verify compilation**

```bash
cd hermes-backend
./mvnw compile -q
```
Expected: BUILD SUCCESS (no MapStruct warnings about unmapped properties).

- [ ] **Step 7: Commit**

```bash
git add hermes-backend/pom.xml \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/Listing.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingMapper.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/City.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/CityRepository.java
git commit -m "feat(geo): add jts-core, Point/Polygon fields to Listing, and City entity"
```

---

## Task 3: Nominatim client + geocoding JMS infrastructure

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/NominatimResponse.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/NominatimClient.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/FetchGeocodingCommand.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/JmsQueues.java`

- [ ] **Step 1: Write test for NominatimClient**

Create `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/internal/NominatimClientTest.java`:
```java
package com.kropholler.dev.hermes.listing.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(NominatimClient.class)
class NominatimClientTest {

    @Autowired
    private NominatimClient client;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void geocodeAddress_returnsParsedLatLon() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("Rentmeesterlaan")))
            .andRespond(withSuccess("""
                [{"lat":"51.2574224","lon":"5.6972390",
                  "boundingbox":["51.2573724","51.2574724","5.6971890","5.6972890"],
                  "place_rank":30,"addresstype":"place","display_name":"9, Rentmeesterlaan, Weert"}]
                """, MediaType.APPLICATION_JSON));

        Optional<NominatimResponse> result = client.geocodeAddress("9", "Rentmeesterlaan", "Weert");

        assertThat(result).isPresent();
        assertThat(result.get().lat()).isEqualTo("51.2574224");
        assertThat(result.get().lon()).isEqualTo("5.6972390");
        assertThat(result.get().boundingbox()).hasSize(4);
    }

    @Test
    void geocodeCity_filtersToNetherlandsAndReturnsFirst() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("countrycodes=nl")))
            .andRespond(withSuccess("""
                [{"lat":"51.2355829","lon":"5.7050797",
                  "boundingbox":["51.1804207","51.2905755","5.5660454","5.7917701"],
                  "place_rank":14,"addresstype":"municipality","display_name":"Weert, Limburg, Netherlands"}]
                """, MediaType.APPLICATION_JSON));

        Optional<NominatimResponse> result = client.geocodeCity("Weert");

        assertThat(result).isPresent();
        assertThat(result.get().addressType()).isEqualTo("municipality");
    }

    @Test
    void geocodeAddress_emptyResponse_returnsEmpty() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("search")))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        Optional<NominatimResponse> result = client.geocodeAddress("1", "Onbekend", "Nergens");

        assertThat(result).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
cd hermes-backend
./mvnw test -Dtest="NominatimClientTest" -q
```
Expected: FAIL — `NominatimClient` and `NominatimResponse` do not exist yet.

- [ ] **Step 3: Create NominatimResponse record**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/NominatimResponse.java`:
```java
package com.kropholler.dev.hermes.listing.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record NominatimResponse(
    String lat,
    String lon,
    List<String> boundingbox,
    @JsonProperty("place_rank") int placeRank,
    @JsonProperty("addresstype") String addressType,
    @JsonProperty("display_name") String displayName
) {}
```

- [ ] **Step 4: Create NominatimClient**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/NominatimClient.java`:
```java
package com.kropholler.dev.hermes.listing.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
class NominatimClient {

    private static final String BASE_URL = "https://nominatim.openstreetmap.org";
    private static final String USER_AGENT = "HermesHouseTracker/1.0 (https://github.com/MattIzSpooky/hermes-house-tracker)";

    private final RestClient restClient;

    NominatimClient(RestClient.Builder builder) {
        this.restClient = builder
            .baseUrl(BASE_URL)
            .defaultHeader("User-Agent", USER_AGENT)
            .build();
    }

    Optional<NominatimResponse> geocodeAddress(String houseNumber, String street, String city) {
        String query = houseNumber + " " + street + " " + city;
        log.debug("Geocoding address: {}", query);
        try {
            List<NominatimResponse> results = restClient.get()
                .uri("/search?q={q}&format=jsonv2&limit=1", query)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<>() {});
            return results == null || results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            log.warn("Nominatim address geocoding failed for '{}': {}", query, e.getMessage());
            return Optional.empty();
        }
    }

    Optional<NominatimResponse> geocodeCity(String cityName) {
        log.debug("Geocoding city: {}", cityName);
        try {
            List<NominatimResponse> results = restClient.get()
                .uri("/search?q={q}&format=jsonv2&countrycodes=nl&limit=5", cityName)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<>() {});
            if (results == null || results.isEmpty()) return Optional.empty();
            return results.stream()
                .filter(r -> List.of("municipality", "city", "town", "administrative").contains(r.addressType()))
                .findFirst()
                .or(() -> Optional.of(results.get(0)));
        } catch (Exception e) {
            log.warn("Nominatim city geocoding failed for '{}': {}", cityName, e.getMessage());
            return Optional.empty();
        }
    }
}
```

- [ ] **Step 5: Add GEOCODING_FETCH to JmsQueues**

In `JmsQueues.java`, add:
```java
public static final String GEOCODING_FETCH = "geocoding.fetch";
```

- [ ] **Step 6: Create FetchGeocodingCommand**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/FetchGeocodingCommand.java`:
```java
package com.kropholler.dev.hermes.listing.internal;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

public record FetchGeocodingCommand(UUID listingId) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
```

- [ ] **Step 7: Run NominatimClientTest to confirm it passes**

```bash
cd hermes-backend
./mvnw test -Dtest="NominatimClientTest" -q
```
Expected: PASS (3 tests green).

- [ ] **Step 8: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/NominatimResponse.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/NominatimClient.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/FetchGeocodingCommand.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/JmsQueues.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/internal/NominatimClientTest.java
git commit -m "feat(geo): add NominatimClient, FetchGeocodingCommand, and GEOCODING_FETCH queue"
```

---

## Task 4: GeocodingConsumer — async geocoding of listings after scraping

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/GeocodingConsumer.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceService.java`

- [ ] **Step 1: Write test for GeocodingConsumer**

Create `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/internal/GeocodingConsumerTest.java`:
```java
package com.kropholler.dev.hermes.listing.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Point;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeocodingConsumerTest {

    @Mock private ListingRepository listingRepository;
    @Mock private NominatimClient nominatimClient;
    @InjectMocks private GeocodingConsumer consumer;

    @Test
    void onMessage_geocodesListingAndSavesLocation() {
        UUID listingId = UUID.randomUUID();
        Listing listing = new Listing();
        listing.setHouseNumber("9");
        listing.setStreet("Rentmeesterlaan");
        listing.setCity("Weert");

        NominatimResponse response = new NominatimResponse(
            "51.2574224", "5.6972390",
            List.of("51.2573724", "51.2574724", "5.6971890", "5.6972890"),
            30, "place", "9, Rentmeesterlaan, Weert"
        );

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(nominatimClient.geocodeAddress("9", "Rentmeesterlaan", "Weert"))
            .thenReturn(Optional.of(response));

        consumer.onMessage(new FetchGeocodingCommand(listingId));

        ArgumentCaptor<Listing> captor = ArgumentCaptor.forClass(Listing.class);
        verify(listingRepository).save(captor.capture());
        assertThat(captor.getValue().getLocation()).isInstanceOf(Point.class);
        assertThat(captor.getValue().getBoundingBox()).isNotNull();
    }

    @Test
    void onMessage_nominatimReturnsEmpty_doesNotSave() {
        UUID listingId = UUID.randomUUID();
        Listing listing = new Listing();
        listing.setHouseNumber("1");
        listing.setStreet("Onbekend");
        listing.setCity("Nergens");

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(nominatimClient.geocodeAddress(any(), any(), any())).thenReturn(Optional.empty());

        consumer.onMessage(new FetchGeocodingCommand(listingId));

        verify(listingRepository, never()).save(any());
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
cd hermes-backend
./mvnw test -Dtest="GeocodingConsumerTest" -q
```
Expected: FAIL — `GeocodingConsumer` does not exist yet.

- [ ] **Step 3: Create GeocodingConsumer**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/GeocodingConsumer.java`:
```java
package com.kropholler.dev.hermes.listing.internal;

import com.google.common.util.concurrent.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
class GeocodingConsumer {

    @SuppressWarnings("UnstableApiUsage")
    private static final RateLimiter RATE_LIMITER = RateLimiter.create(1.0);

    private static final GeometryFactory GEOMETRY_FACTORY =
        new GeometryFactory(new PrecisionModel(), 4326);

    private final ListingRepository listingRepository;
    private final NominatimClient nominatimClient;

    @JmsListener(destination = JmsQueues.GEOCODING_FETCH)
    @Transactional
    public void onMessage(FetchGeocodingCommand command) {
        RATE_LIMITER.acquire();
        listingRepository.findById(command.listingId()).ifPresent(listing -> {
            if (listing.getStreet() == null || listing.getCity() == null) return;
            nominatimClient.geocodeAddress(
                listing.getHouseNumber(), listing.getStreet(), listing.getCity()
            ).ifPresent(response -> {
                listing.setLocation(toPoint(response.lat(), response.lon()));
                listing.setBoundingBox(toBoundingBox(response.boundingbox()));
                listingRepository.save(listing);
                log.debug("Geocoded listing {} to {},{}", command.listingId(), response.lat(), response.lon());
            });
        });
    }

    private Point toPoint(String lat, String lon) {
        double latitude = Double.parseDouble(lat);
        double longitude = Double.parseDouble(lon);
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(4326);
        return point;
    }

    private Polygon toBoundingBox(List<String> bbox) {
        if (bbox == null || bbox.size() < 4) return null;
        double latMin = Double.parseDouble(bbox.get(0));
        double latMax = Double.parseDouble(bbox.get(1));
        double lonMin = Double.parseDouble(bbox.get(2));
        double lonMax = Double.parseDouble(bbox.get(3));
        Coordinate[] coords = {
            new Coordinate(lonMin, latMin),
            new Coordinate(lonMax, latMin),
            new Coordinate(lonMax, latMax),
            new Coordinate(lonMin, latMax),
            new Coordinate(lonMin, latMin)
        };
        Polygon polygon = GEOMETRY_FACTORY.createPolygon(coords);
        polygon.setSRID(4326);
        return polygon;
    }
}
```

- [ ] **Step 4: Update ListingPersistenceService to send FetchGeocodingCommand for new listings**

In `ListingPersistenceService.java`, in `onScrapingSessionCompleted`, add after the `isNew` check:
```java
if (isNew) {
    jmsTemplate.convertAndSend(JmsQueues.GEOCODING_FETCH,
        new FetchGeocodingCommand(saved.getId()));
}
```

The full updated block in `onScrapingSessionCompleted` should look like:
```java
boolean isNew = listingRepository.findByFundaId(raw.fundaId()).isEmpty();
Listing listing = listingRepository.findByFundaId(raw.fundaId())
    .orElseGet(() -> createListing(raw));

listing.setLastSeenAt(Instant.now());
listing.setLastUpdatedAt(Instant.now());
listing.setStatus(parseStatus(raw.status()));
Listing saved = listingRepository.save(listing);

jmsTemplate.convertAndSend(JmsQueues.LISTING_DETAILS_FETCH,
    new FetchListingDetailsCommand(saved.getId(), saved.getFundaId()));

if (isNew || event.type() == ScrapingSessionType.RESCRAPE) {
    jmsTemplate.convertAndSend(JmsQueues.PRICE_HISTORY_FETCH,
        new FetchPriceHistoryCommand(saved.getId(), saved.getFundaId()));
}

if (isNew) {
    jmsTemplate.convertAndSend(JmsQueues.GEOCODING_FETCH,
        new FetchGeocodingCommand(saved.getId()));
}
```

- [ ] **Step 5: Run GeocodingConsumerTest**

```bash
cd hermes-backend
./mvnw test -Dtest="GeocodingConsumerTest" -q
```
Expected: PASS (2 tests green).

- [ ] **Step 6: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/GeocodingConsumer.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceService.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/internal/GeocodingConsumerTest.java
git commit -m "feat(geo): add GeocodingConsumer to async-geocode new listings via Nominatim"
```

---

## Task 5: GeocodingService — public service for on-demand geocoding

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/GeocodingService.java`

- [ ] **Step 1: Write test for GeocodingService**

Create `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/GeocodingServiceTest.java`:
```java
package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.internal.City;
import com.kropholler.dev.hermes.listing.internal.CityRepository;
import com.kropholler.dev.hermes.listing.internal.NominatimClient;
import com.kropholler.dev.hermes.listing.internal.NominatimResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Point;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeocodingServiceTest {

    @Mock private CityRepository cityRepository;
    @Mock private NominatimClient nominatimClient;
    @InjectMocks private GeocodingService service;

    @Test
    void findOrFetchCity_cachedCity_returnsWithoutCallingNominatim() {
        City city = new City();
        when(cityRepository.findByNameIgnoreCase("Weert")).thenReturn(Optional.of(city));

        Optional<City> result = service.findOrFetchCity("Weert");

        assertThat(result).isPresent();
        verifyNoInteractions(nominatimClient);
    }

    @Test
    void findOrFetchCity_notCached_fetchesFromNominatimAndSaves() {
        NominatimResponse response = new NominatimResponse(
            "51.2355829", "5.7050797",
            List.of("51.1804207", "51.2905755", "5.5660454", "5.7917701"),
            14, "municipality", "Weert, Limburg, Netherlands"
        );

        when(cityRepository.findByNameIgnoreCase("Weert")).thenReturn(Optional.empty());
        when(nominatimClient.geocodeCity("Weert")).thenReturn(Optional.of(response));
        when(cityRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Optional<City> result = service.findOrFetchCity("Weert");

        assertThat(result).isPresent();
        assertThat(result.get().getLocation()).isInstanceOf(Point.class);
        verify(cityRepository).save(any(City.class));
    }

    @Test
    void findOrFetchCity_nominatimReturnsEmpty_returnsEmpty() {
        when(cityRepository.findByNameIgnoreCase("Unknown")).thenReturn(Optional.empty());
        when(nominatimClient.geocodeCity("Unknown")).thenReturn(Optional.empty());

        Optional<City> result = service.findOrFetchCity("Unknown");

        assertThat(result).isEmpty();
        verify(cityRepository, never()).save(any());
    }

    @Test
    void geocodeAddress_delegatesToNominatimClient() {
        NominatimResponse response = new NominatimResponse(
            "51.2574224", "5.6972390",
            List.of("51.2573724", "51.2574724", "5.6971890", "5.6972890"),
            30, "place", "9, Rentmeesterlaan, Weert"
        );
        when(nominatimClient.geocodeAddress("9", "Rentmeesterlaan", "Weert"))
            .thenReturn(Optional.of(response));

        Optional<double[]> result = service.geocodeAddress("9", "Rentmeesterlaan", "Weert");

        assertThat(result).isPresent();
        assertThat(result.get()[0]).isEqualTo(51.2574224, org.assertj.core.api.Assertions.within(0.0001));
        assertThat(result.get()[1]).isEqualTo(5.6972390, org.assertj.core.api.Assertions.within(0.0001));
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
cd hermes-backend
./mvnw test -Dtest="GeocodingServiceTest" -q
```
Expected: FAIL — `GeocodingService` does not exist yet.

- [ ] **Step 3: Create GeocodingService**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/GeocodingService.java`:
```java
package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.internal.City;
import com.kropholler.dev.hermes.listing.internal.CityRepository;
import com.kropholler.dev.hermes.listing.internal.NominatimClient;
import com.kropholler.dev.hermes.listing.internal.NominatimResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodingService {

    private static final GeometryFactory GEOMETRY_FACTORY =
        new GeometryFactory(new PrecisionModel(), 4326);

    private final CityRepository cityRepository;
    private final NominatimClient nominatimClient;

    @Transactional
    public Optional<City> findOrFetchCity(String cityName) {
        Optional<City> cached = cityRepository.findByNameIgnoreCase(cityName);
        if (cached.isPresent()) return cached;

        return nominatimClient.geocodeCity(cityName).map(response -> {
            City city = new City();
            city.setName(cityName);
            city.setLocation(toPoint(response.lat(), response.lon()));
            city.setBoundingBox(toBoundingBox(response.boundingbox()));
            city.setFetchedAt(Instant.now());
            return cityRepository.save(city);
        });
    }

    public Optional<double[]> geocodeAddress(String houseNumber, String street, String city) {
        return nominatimClient.geocodeAddress(houseNumber, street, city)
            .map(r -> new double[]{Double.parseDouble(r.lat()), Double.parseDouble(r.lon())});
    }

    private Point toPoint(String lat, String lon) {
        Point point = GEOMETRY_FACTORY.createPoint(
            new Coordinate(Double.parseDouble(lon), Double.parseDouble(lat)));
        point.setSRID(4326);
        return point;
    }

    private Polygon toBoundingBox(List<String> bbox) {
        if (bbox == null || bbox.size() < 4) return null;
        double latMin = Double.parseDouble(bbox.get(0));
        double latMax = Double.parseDouble(bbox.get(1));
        double lonMin = Double.parseDouble(bbox.get(2));
        double lonMax = Double.parseDouble(bbox.get(3));
        Coordinate[] coords = {
            new Coordinate(lonMin, latMin),
            new Coordinate(lonMax, latMin),
            new Coordinate(lonMax, latMax),
            new Coordinate(lonMin, latMax),
            new Coordinate(lonMin, latMin)
        };
        Polygon polygon = GEOMETRY_FACTORY.createPolygon(coords);
        polygon.setSRID(4326);
        return polygon;
    }
}
```

- [ ] **Step 4: Run GeocodingServiceTest**

```bash
cd hermes-backend
./mvnw test -Dtest="GeocodingServiceTest" -q
```
Expected: PASS (4 tests green).

- [ ] **Step 5: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/GeocodingService.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/GeocodingServiceTest.java
git commit -m "feat(geo): add GeocodingService for on-demand address and city geocoding"
```

---

## Task 6: Radius search in ListingRepository and ListingService

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingRepository.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingService.java`

- [ ] **Step 1: Add radius search queries to ListingRepository**

In `ListingRepository.java`, add two new query methods:

```java
@Query(value = """
    SELECT l.* FROM listings l
    LEFT JOIN LATERAL (
        SELECT phe.price FROM price_history_entries phe
        WHERE phe.listing_id = l.id AND phe.status = 'asking_price'
        ORDER BY phe.timestamp DESC LIMIT 1
    ) latest_price ON true
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
    """,
    countQuery = """
    SELECT count(l.id) FROM listings l
    WHERE l.deleted_at IS NULL
    AND l.location IS NOT NULL
    AND ST_DWithin(
        l.location::geography,
        ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
        :radiusMeters
    )
    """,
    nativeQuery = true)
Page<Listing> findNearby(
    @Param("lon") double lon,
    @Param("lat") double lat,
    @Param("radiusMeters") int radiusMeters,
    Pageable pageable
);

@Query(value = """
    SELECT l.* FROM listings l
    LEFT JOIN LATERAL (
        SELECT phe.price FROM price_history_entries phe
        WHERE phe.listing_id = l.id AND phe.status = 'asking_price'
        ORDER BY phe.timestamp DESC LIMIT 1
    ) latest_price ON true
    WHERE l.deleted_at IS NULL
    AND l.location IS NOT NULL
    AND ST_DWithin(
        l.location::geography,
        ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
        :radiusMeters
    )
    AND (:minBedrooms IS NULL OR l.bedrooms >= :minBedrooms)
    AND (:minRooms IS NULL OR l.rooms >= :minRooms)
    AND (:minLivingAreaM2 IS NULL OR l.living_area_m2 >= :minLivingAreaM2)
    AND (:province IS NULL OR lower(l.province) LIKE lower(concat('%', :province, '%')))
    AND (:keywords IS NULL OR
         to_tsvector('dutch', coalesce(l.description, '')) @@
         plainto_tsquery('dutch', :keywords))
    AND (:minPrice IS NULL OR latest_price.price >= :minPrice)
    AND (:maxPrice IS NULL OR latest_price.price <= :maxPrice)
    ORDER BY ST_Distance(
        l.location::geography,
        ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
    ) ASC
    LIMIT 5
    """,
    nativeQuery = true)
List<Listing> searchForChatNearLocation(
    @Param("minBedrooms") Integer minBedrooms,
    @Param("minRooms") Integer minRooms,
    @Param("minLivingAreaM2") Integer minLivingAreaM2,
    @Param("province") String province,
    @Param("keywords") String keywords,
    @Param("minPrice") Integer minPrice,
    @Param("maxPrice") Integer maxPrice,
    @Param("lon") double lon,
    @Param("lat") double lat,
    @Param("radiusMeters") int radiusMeters
);
```

- [ ] **Step 2: Extend ListingService with radius search**

In `ListingService.java`:

1. Add `GeocodingService` dependency:
```java
private final GeocodingService geocodingService;
```

2. Add new `findAll` overload that accepts radius params — update the existing `findAll` method to check for radius params in `ListingSearchParams`:
```java
@Transactional(readOnly = true)
public Page<ListingDto> findAll(ListingSearchParams params, Pageable pageable) {
    if (params.hasRadiusSearch()) {
        double[] latLon = resolveLatLon(params);
        if (latLon != null) {
            int radiusMeters = params.radiusKm() * 1000;
            return listingRepository.findNearby(latLon[1], latLon[0], radiusMeters, pageable)
                .map(this::toDto);
        }
    }
    if (params.isEmpty()) {
        return listingRepository.findAll(pageable).map(this::toDto);
    }
    return listingRepository.findAll(ListingSpecifications.withParams(params), pageable)
        .map(this::toDto);
}

private double[] resolveLatLon(ListingSearchParams params) {
    if (params.nearAddress() != null && !params.nearAddress().isBlank()) {
        String[] parts = params.nearAddress().split(",", 3);
        String houseNumber = parts.length > 0 ? parts[0].strip() : "";
        String street = parts.length > 1 ? parts[1].strip() : "";
        String city = parts.length > 2 ? parts[2].strip() : "";
        return geocodingService.geocodeAddress(houseNumber, street, city).orElse(null);
    }
    if (params.nearCity() != null && !params.nearCity().isBlank()) {
        return geocodingService.findOrFetchCity(params.nearCity())
            .map(c -> new double[]{c.getLocation().getY(), c.getLocation().getX()})
            .orElse(null);
    }
    return null;
}
```

3. Extend `findForChat` to accept radius parameters:
```java
@Transactional(readOnly = true)
public List<ListingDto> findForChat(Integer minPrice, Integer maxPrice,
                                     Integer minBedrooms, Integer minRooms,
                                     Integer minLivingAreaM2, String province,
                                     String city, String keywords,
                                     boolean sortByPriceDesc,
                                     String nearAddress, String nearCity, Integer radiusKm) {
    if (radiusKm != null && (nearAddress != null || nearCity != null)) {
        double[] latLon = resolveLatLonForChat(nearAddress, nearCity);
        if (latLon != null) {
            return listingRepository.searchForChatNearLocation(
                    minBedrooms, minRooms, minLivingAreaM2,
                    province, keywords, minPrice, maxPrice,
                    latLon[1], latLon[0], radiusKm * 1000)
                .stream().map(this::toDto).toList();
        }
    }
    return listingRepository.searchForChat(minBedrooms, minRooms, minLivingAreaM2,
                    province, city, keywords, minPrice, maxPrice, sortByPriceDesc)
            .stream().map(this::toDto).toList();
}

private double[] resolveLatLonForChat(String nearAddress, String nearCity) {
    if (nearAddress != null && !nearAddress.isBlank()) {
        String[] parts = nearAddress.split(",", 3);
        String houseNumber = parts.length > 0 ? parts[0].strip() : "";
        String street = parts.length > 1 ? parts[1].strip() : "";
        String city = parts.length > 2 ? parts[2].strip() : "";
        return geocodingService.geocodeAddress(houseNumber, street, city).orElse(null);
    }
    if (nearCity != null && !nearCity.isBlank()) {
        return geocodingService.findOrFetchCity(nearCity)
            .map(c -> new double[]{c.getLocation().getY(), c.getLocation().getX()})
            .orElse(null);
    }
    return null;
}
```

- [ ] **Step 3: Update ListingServiceFindForChatTest**

In `ListingServiceFindForChatTest.java`, add the missing mock and update the `stubSearchForChat` method, and update all `findForChat` call signatures:

Add field:
```java
@Mock private GeocodingService geocodingService;
```

Update `stubSearchForChat`:
```java
private void stubSearchForChat(List<Listing> results) {
    when(listingRepository.searchForChat(any(), any(), any(), any(), any(), any(), any(), any(), any(Boolean.class)))
            .thenReturn(results);
}
```
(signature unchanged — the mock stubs `searchForChat`, not `findForChat` directly)

Update every `service.findForChat(...)` call by appending three `null` arguments for `nearAddress`, `nearCity`, `radiusKm`:
```java
// Before:
service.findForChat(200_000, null, null, null, null, null, null, null, false)
// After:
service.findForChat(200_000, null, null, null, null, null, null, null, false, null, null, null)
```
Apply this to all 5 test methods.

- [ ] **Step 4: Run affected tests**

```bash
cd hermes-backend
./mvnw test -Dtest="ListingServiceFindForChatTest,GeocodingServiceTest" -q
```
Expected: PASS (all tests green).

- [ ] **Step 5: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingRepository.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingService.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingServiceFindForChatTest.java
git commit -m "feat(geo): add findNearby and searchForChatNearLocation queries; extend ListingService with radius search"
```

---

## Task 7: Update ListingSearchParams, OpenAPI spec, and ListingController

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingSearchParams.java`
- Modify: `hermes-backend/src/main/resources/openapi/api.yaml`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/api/ListingController.java`

- [ ] **Step 1: Update ListingSearchParams**

Replace the entire record in `ListingSearchParams.java`:
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
    String energyLabel,
    String nearAddress,
    String nearCity,
    Integer radiusKm
) {
    public boolean isEmpty() {
        return isBlank(street) && isBlank(houseNumber) && isBlank(houseNumberAddition)
            && isBlank(zipCode) && isBlank(province)
            && minBedrooms == null && minRooms == null && minLivingAreaM2 == null
            && isBlank(energyLabel) && !hasRadiusSearch();
    }

    public boolean hasRadiusSearch() {
        return radiusKm != null && (!isBlank(nearAddress) || !isBlank(nearCity));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
```

- [ ] **Step 2: Add query params to api.yaml**

In `api.yaml`, under `GET /api/listings` parameters (after `energyLabel`), add:
```yaml
        - name: nearAddress
          in: query
          required: false
          description: "Address to search near (format: 'houseNumber, street, city')"
          schema:
            type: string
            nullable: true
        - name: nearCity
          in: query
          required: false
          description: "City name to search near"
          schema:
            type: string
            nullable: true
        - name: radiusKm
          in: query
          required: false
          description: "Search radius in kilometres (requires nearAddress or nearCity)"
          schema:
            type: integer
            nullable: true
```

- [ ] **Step 3: Update ListingController to handle new params**

The OpenAPI generator will regenerate `ListingsApi` to include `nearAddress`, `nearCity`, `radiusKm`. Update `ListingController.getListings` signature and body:

```java
@Override
public ResponseEntity<ListingPage> getListings(Integer page, Integer size,
        String street, String houseNumber, String houseNumberAddition,
        String zipCode, String province,
        Integer minBedrooms, Integer minRooms, Integer minLivingAreaM2, String energyLabel,
        String nearAddress, String nearCity, Integer radiusKm) {
    ListingSearchParams params = new ListingSearchParams(
        street, houseNumber, houseNumberAddition, zipCode, province,
        minBedrooms, minRooms, minLivingAreaM2, energyLabel,
        nearAddress, nearCity, radiusKm
    );
    Page<ListingDto> result = listingService.findAll(params, PageRequest.of(page, size));
    ListingPage response = new ListingPage()
        .content(result.getContent().stream().map(apiMapper::toSummaryResponse).toList())
        .totalElements(result.getTotalElements())
        .totalPages(result.getTotalPages())
        .page(result.getNumber())
        .size(result.getSize());
    return ResponseEntity.ok(response);
}
```

- [ ] **Step 4: Update ListingControllerSearchTest if it exists**

In `hermes-backend/src/test/java/com/kropholler/dev/hermes/api/ListingControllerSearchTest.java`, check if any test calls `getListings` with a fixed number of arguments and update to include the three new `null` params. (The controller is a `@RestController` tested via MockMvc HTTP calls, so this is only needed if the test uses direct method calls.)

- [ ] **Step 5: Compile and run tests**

```bash
cd hermes-backend
./mvnw test -Dtest="ListingControllerSearchTest,ListingServiceSearchTest" -q
```
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingSearchParams.java \
        hermes-backend/src/main/resources/openapi/api.yaml \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/api/ListingController.java
git commit -m "feat(geo): add nearAddress, nearCity, radiusKm to search params and OpenAPI spec"
```

---

## Task 8: Extend AI ListingSearchTool with radius search

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/internal/ListingSearchTool.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/internal/ListingSearchToolTest.java`

- [ ] **Step 1: Add radius params to ListingSearchTool**

In `ListingSearchTool.java`, update the `@Tool` description and add three new `@ToolParam`s to `searchListings`:

```java
@Tool(description = "Search for property listings matching the user's criteria. "
        + "ALWAYS call this tool before describing any listings — never invent property details. "
        + "Use priceSort='desc' for 'most expensive'/'highest price'/'luxury'; use priceSort='asc' or omit for 'cheapest'/'lowest price' or no sort preference. "
        + "For radius searches: set nearAddress (format: 'houseNumber, street, city') or nearCity and radiusKm.")
public List<ChatListingCard> searchListings(
        @ToolParam(required = false, description = "City to filter by, omit if not specified") String city,
        @ToolParam(required = false, description = "Province to filter by, omit if not specified") String province,
        @ToolParam(required = false, description = "Minimum asking price in euros, omit if no minimum") Integer minPrice,
        @ToolParam(required = false, description = "Maximum asking price in euros, omit if no maximum") Integer maxPrice,
        @ToolParam(required = false, description = "Minimum number of bedrooms, omit if no minimum") Integer minBedrooms,
        @ToolParam(required = false, description = "Minimum total number of rooms, omit if no minimum") Integer minRooms,
        @ToolParam(required = false, description = "Minimum living area in square metres, omit if no minimum") Integer minLivingAreaM2,
        @ToolParam(required = false, description = "Free-text keywords to search in property descriptions, omit if not specified") String keywords,
        @ToolParam(required = false, description = "Price sort: use 'desc' for most expensive first, 'asc' or omit for cheapest first or no preference") String priceSort,
        @ToolParam(required = false, description = "Address to search near, format: 'houseNumber, street, city'. Use when user asks about listings near a specific address.") String nearAddress,
        @ToolParam(required = false, description = "City name to search near. Use when user asks about listings near a city.") String nearCity,
        @ToolParam(required = false, description = "Search radius in kilometres. Required when nearAddress or nearCity is set.") Integer radiusKm
) {
    boolean sortDesc = "desc".equalsIgnoreCase(priceSort);
    log.info("searchListings called: city={}, province={}, minBedrooms={}, minPrice={}, maxPrice={}, priceSort={}, nearAddress={}, nearCity={}, radiusKm={}",
            city, province, minBedrooms, minPrice, maxPrice, priceSort, nearAddress, nearCity, radiusKm);
    callCounter.increment();
    List<ChatListingCard> cards = listingService.findForChat(
            minPrice, maxPrice,
            minBedrooms, minRooms, minLivingAreaM2,
            blankToNull(province), blankToNull(city), blankToNull(keywords),
            sortDesc,
            blankToNull(nearAddress), blankToNull(nearCity), radiusKm
    ).stream().map(mapper::toChatListingCard).toList();
    log.info("searchListings returned {} results", cards.size());
    resultHolder.set(cards);
    return cards;
}
```

- [ ] **Step 2: Update ListingSearchToolTest**

Open `ListingSearchToolTest.java` and find any calls to `listingService.findForChat(...)`. Update to include three extra `null` arguments for `nearAddress`, `nearCity`, `radiusKm`. Also update any `when(listingService.findForChat(...))` mock stubs to match the new 12-arg signature using `any()` for the new params or explicit `null` matchers where needed.

Typical stub update:
```java
// Before:
when(listingService.findForChat(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
    .thenReturn(List.of());

// After:
when(listingService.findForChat(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
        isNull(), isNull(), isNull()))
    .thenReturn(List.of());
```

- [ ] **Step 3: Run AI tool tests**

```bash
cd hermes-backend
./mvnw test -Dtest="ListingSearchToolTest" -q
```
Expected: PASS.

- [ ] **Step 4: Run full test suite**

```bash
cd hermes-backend
./mvnw test -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/internal/ListingSearchTool.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/internal/ListingSearchToolTest.java
git commit -m "feat(geo): add nearAddress, nearCity, radiusKm to AI ListingSearchTool"
```

---

## Task 9: Frontend — radius search UI

**Files:**
- Modify: `hermes-frontend/src/app/core/api.types.ts`
- Modify: `hermes-frontend/src/app/core/listings.service.ts`
- Modify: `hermes-frontend/src/app/pages/listings/listings-page.component.ts`
- Modify: `hermes-frontend/src/app/pages/listings/listings-page.component.html`

- [ ] **Step 1: Add radius fields to ListingSearchFilter**

In `api.types.ts`, update `ListingSearchFilter`:
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
  nearAddress?: string | null;
  nearCity?: string | null;
  radiusKm?: number | null;
}
```

- [ ] **Step 2: Pass new params in ListingsService**

In `listings.service.ts`, in the `loadListings` method, add after the existing filter params:
```typescript
if (filter?.nearAddress?.trim()) params = params.set('nearAddress', filter.nearAddress.trim());
if (filter?.nearCity?.trim()) params = params.set('nearCity', filter.nearCity.trim());
if (filter?.radiusKm) params = params.set('radiusKm', filter.radiusKm);
```

- [ ] **Step 3: Add radius fields to listings-page component**

In `listings-page.component.ts`, add three new protected fields after `energyLabel`:
```typescript
protected nearAddress = '';
protected nearCity = '';
protected radiusKm: number | null = null;
```

Update `clearFilters()` to reset the new fields:
```typescript
this.nearAddress = '';
this.nearCity = '';
this.radiusKm = null;
```

Update `currentFilter` getter to include the new fields:
```typescript
private get currentFilter(): ListingSearchFilter {
  return {
    street: this.street || undefined,
    houseNumber: this.houseNumber || undefined,
    houseNumberAddition: this.houseNumberAddition || undefined,
    zipCode: this.zipCode || undefined,
    province: this.province || undefined,
    minBedrooms: this.minBedrooms,
    minRooms: this.minRooms,
    minLivingAreaM2: this.minLivingAreaM2,
    energyLabel: this.energyLabel || undefined,
    nearAddress: this.nearAddress || undefined,
    nearCity: this.nearCity || undefined,
    radiusKm: this.radiusKm,
  };
}
```

- [ ] **Step 4: Add radius search UI section to template**

In `listings-page.component.html`, find the filter form section and add a new "Radius search" section after the existing filters. Add this block (adapt to match existing Tailwind class patterns in the file):

```html
<!-- Radius Search -->
<div class="mt-4 border-t border-cyan-800 pt-4">
  <p class="text-xs text-cyan-400 uppercase tracking-wide mb-2">Radius search</p>
  <div class="grid grid-cols-1 sm:grid-cols-3 gap-2">
    <input
      type="text"
      placeholder="Near address (9, Rentmeesterlaan, Weert)"
      [(ngModel)]="nearAddress"
      (ngModelChange)="onFilterChange()"
      class="input input-sm"
    />
    <input
      type="text"
      placeholder="Near city (e.g. Weert)"
      [(ngModel)]="nearCity"
      (ngModelChange)="onFilterChange()"
      class="input input-sm"
    />
    <input
      type="number"
      placeholder="Radius (km)"
      [(ngModel)]="radiusKm"
      (ngModelChange)="onFilterChange()"
      min="1"
      max="100"
      class="input input-sm"
    />
  </div>
  <p class="text-xs text-cyan-600 mt-1">
    Fill in a city or address together with a radius to search nearby listings. Results are ordered by distance.
  </p>
</div>
```

Note: use the same CSS classes (e.g. `input`, background, border, text colours) that existing filter inputs in the template use — copy the class string from an existing `<input>` field so the styling is consistent.

- [ ] **Step 5: Start dev server and verify UI**

```bash
cd hermes-frontend
npm start
```
Open `http://localhost:4200/listings` in a browser. Verify:
- The radius search section appears below the existing filters
- Typing in "Near city" and a radius number triggers a debounced search
- Clearing filters resets all three radius fields
- The browser network tab shows the `nearCity` and `radiusKm` query params in the `/api/listings` request

- [ ] **Step 6: Commit**

```bash
git add hermes-frontend/src/app/core/api.types.ts \
        hermes-frontend/src/app/core/listings.service.ts \
        hermes-frontend/src/app/pages/listings/listings-page.component.ts \
        hermes-frontend/src/app/pages/listings/listings-page.component.html
git commit -m "feat(geo): add radius search UI to listings page filter"
```

---

## Self-review checklist

**Spec coverage:**
- [x] Replace postgres with postgis → Task 1
- [x] Nominatim geocoding during async scrape → Task 3 + 4
- [x] `location` Point and `bounding_box` Polygon on listings → Task 2
- [x] Hibernate Spatial / JTS dependency → Task 2
- [x] Cities table with coordinates → Task 2 + 5
- [x] Fetch city from Nominatim if not in table → Task 5
- [x] ST_DWithin radius query → Task 6
- [x] Radius search via AI chat → Task 8
- [x] Radius search via filter UI → Task 7 + 9
- [x] Search by address radius → Tasks 6–9
- [x] Search by city radius → Tasks 5–9
- [x] Frontend updated → Task 9

**Known constraints:**
- Nominatim terms require a max of 1 request/second and a valid User-Agent. `GeocodingConsumer` enforces 1 req/sec via Guava `RateLimiter`. `GeocodingService` is called synchronously during user searches; city results are cached in the DB, so Nominatim is only hit once per new city.
- `nearAddress` format expected by the backend is `"houseNumber, street, city"` — the frontend placeholder text instructs users accordingly.
- Listings without a geocoded `location` are excluded from radius search results (the `ST_DWithin` query filters `l.location IS NOT NULL`). Listings are only geocoded once on first scrape.
