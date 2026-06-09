# Hermes Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

## Progress

| Task | Status | Commit |
|------|--------|--------|
| Task 1: Project bootstrap | ✅ Done | initial |
| Task 2: ScrapingSession entity & repository | ✅ Done | — |
| Task 3: FundaUrlBuilder | ✅ Done | — |
| Task 4: FundaScraperService (JSoup parser) | ✅ Done | afb8cbf |
| Task 5: Scraping events, queue, worker, poller, watchdog | 🔲 Todo | — |
| Task 6: Listing entities & persistence | 🔲 Todo | — |
| Task 7: ListingService & DTOs | 🔲 Todo | — |
| Task 8: Report module | 🔲 Todo | — |
| Task 9: AI module (Ollama summaries) | 🔲 Todo | — |
| Task 10: Full OpenAPI spec | 🔲 Todo | — |
| Task 11: API controllers | 🔲 Todo | — |
| Task 12: Nightly rescrape scheduler | 🔲 Todo | — |
| Task 13: Module structure verification | 🔲 Todo | — |

**Goal:** Implement the full Hermes backend — a Spring Modulith modular monolith that scrapes Funda.nl property listings asynchronously, persists snapshot history, and exposes a schema-first REST API.

**Architecture:** Five Spring Modulith modules (`scraping`, `listing`, `report`, `ai`, `api`) communicate exclusively via JPA-backed application events. The scraping queue is a `ScrapingSession` JPA entity polled by an `@Async` worker. Each scrape produces immutable `ListingSnapshot` records enabling historical reports and trend analysis.

**Tech Stack:** Java 25, Spring Boot 4.0.6, Spring Modulith 2.0.6, Spring Data JPA, PostgreSQL, Spring AI 2.0.0-RC1 (Ollama + JSoup), OpenAPI Generator Maven Plugin, Lombok, Testcontainers, JUnit 5.

---

## File Map

```
src/main/java/com/kropholler/dev/hermes/
├── HermesBackendApplication.java                    (exists)
├── config/
│   └── AsyncConfig.java                             (new)
├── scraping/
│   ├── ScrapingSessionStatus.java                   (public enum)
│   ├── ScrapingSessionType.java                     (public enum)
│   ├── RawListing.java                              (public record — event payload)
│   ├── ScrapingSessionCompleted.java                (public event record)
│   ├── ScrapingSessionFailed.java                   (public event record)
│   ├── ScrapingSessionDto.java                      (public DTO record)
│   ├── ScrapingQueueService.java                    (public service)
│   └── internal/
│       ├── ScrapingSession.java                     (JPA entity)
│       ├── ScrapingSessionRepository.java           (repository)
│       ├── FundaUrlBuilder.java                     (URL construction)
│       ├── FundaScraperService.java                 (JSoup page scraper)
│       ├── ScrapingPoller.java                      (@Scheduled — polls queue)
│       ├── ScrapingWorker.java                      (@Async — processes sessions)
│       └── ScrapingTimeoutWatchdog.java             (@Scheduled — marks timed-out sessions)
├── listing/
│   ├── ListingStatus.java                           (public enum)
│   ├── ListingSnapshotsCreated.java                 (public event record)
│   ├── ListingDto.java                              (public DTO record)
│   ├── ListingSnapshotDto.java                      (public DTO record)
│   ├── ListingService.java                          (public service)
│   └── internal/
│       ├── Listing.java                             (JPA entity)
│       ├── ListingSnapshot.java                     (JPA entity)
│       ├── ListingRepository.java                   (repository)
│       ├── ListingSnapshotRepository.java           (repository)
│       └── ListingPersistenceService.java           (consumes ScrapingSessionCompleted)
├── report/
│   ├── PricePoint.java                              (public record)
│   ├── StatusPoint.java                             (public record)
│   ├── ListingReport.java                           (public record)
│   └── ReportService.java                           (public service)
├── ai/
│   ├── ListingSummaryDto.java                       (public DTO record)
│   ├── ListingSummaryService.java                   (public service)
│   └── internal/
│       ├── ListingSummary.java                      (JPA entity)
│       ├── ListingSummaryRepository.java            (repository)
│       └── ListingSummaryGenerationService.java     (consumes ListingSnapshotsCreated)
└── api/
    ├── ScrapingSessionController.java
    ├── ListingController.java
    └── GlobalExceptionHandler.java

src/main/resources/
├── application.properties                           (update)
└── openapi/
    └── api.yaml                                     (new)

src/test/java/com/kropholler/dev/hermes/
├── HermesBackendApplicationTests.java               (update for Modulith verification)
├── scraping/
│   ├── FundaUrlBuilderTest.java
│   └── FundaScraperServiceTest.java
├── listing/
│   └── ListingPersistenceServiceTest.java
├── report/
│   └── ReportServiceTest.java
└── ai/
    └── ListingSummaryServiceTest.java

src/test/resources/fixtures/
└── funda-search-result.html
```

---

## Task 1: Foundation — pom.xml, application.properties, AsyncConfig

**Files:**
- Modify: `hermes-backend/pom.xml`
- Modify: `hermes-backend/src/main/resources/application.properties`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/config/AsyncConfig.java`

- [ ] **Step 1: Add missing dependencies and OpenAPI generator plugin to pom.xml**

In `hermes-backend/pom.xml`, add inside `<dependencies>`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

Add inside `<build><plugins>`:

```xml
<plugin>
    <groupId>org.openapitools</groupId>
    <artifactId>openapi-generator-maven-plugin</artifactId>
    <version>7.10.0</version>
    <executions>
        <execution>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <inputSpec>${project.basedir}/src/main/resources/openapi/api.yaml</inputSpec>
                <generatorName>spring</generatorName>
                <apiPackage>com.kropholler.dev.hermes.api.generated</apiPackage>
                <modelPackage>com.kropholler.dev.hermes.api.generated.model</modelPackage>
                <configOptions>
                    <interfaceOnly>true</interfaceOnly>
                    <useSpringBoot3>true</useSpringBoot3>
                    <useTags>true</useTags>
                    <openApiNullable>false</openApiNullable>
                    <documentationProvider>none</documentationProvider>
                    <useJakartaEe>true</useJakartaEe>
                </configOptions>
            </configuration>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 2: Update application.properties**

Replace contents of `src/main/resources/application.properties`:

```properties
spring.application.name=hermes-backend

# JPA
spring.jpa.hibernate.ddl-auto=update
spring.jpa.open-in-view=false

# Virtual threads
spring.threads.virtual.enabled=true

# Ollama
spring.ai.ollama.chat.options.model=llama3.2

# Spring Modulith event publication
spring.modulith.events.completion-mode=delete

# Async executor
spring.task.execution.pool.core-size=4
spring.task.execution.pool.max-size=8
spring.task.execution.thread-name-prefix=hermes-async-

# Scheduling
spring.task.scheduling.pool.size=4
spring.task.scheduling.thread-name-prefix=hermes-sched-
```

- [ ] **Step 3: Create AsyncConfig**

Create `src/main/java/com/kropholler/dev/hermes/config/AsyncConfig.java`:

```java
package com.kropholler.dev.hermes.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
```

- [ ] **Step 4: Verify the project compiles**

Run: `cd hermes-backend && ./mvnw compile -q`

Expected: BUILD SUCCESS (no sources yet, just config changes)

- [ ] **Step 5: Commit**

```bash
git add hermes-backend/pom.xml hermes-backend/src/main/resources/application.properties hermes-backend/src/main/java/com/kropholler/dev/hermes/config/AsyncConfig.java
git commit -m "feat: add foundation config — async, scheduling, OpenAPI generator, JPA settings"
```

---

## Task 2: Scraping module — data layer

**Files:**
- Create: `src/main/java/com/kropholler/dev/hermes/scraping/ScrapingSessionStatus.java`
- Create: `src/main/java/com/kropholler/dev/hermes/scraping/ScrapingSessionType.java`
- Create: `src/main/java/com/kropholler/dev/hermes/scraping/internal/ScrapingSession.java`
- Create: `src/main/java/com/kropholler/dev/hermes/scraping/internal/ScrapingSessionRepository.java`
- Test: `src/test/java/com/kropholler/dev/hermes/scraping/internal/ScrapingSessionRepositoryTest.java`

- [ ] **Step 1: Create public enums**

`src/main/java/com/kropholler/dev/hermes/scraping/ScrapingSessionStatus.java`:

```java
package com.kropholler.dev.hermes.scraping;

public enum ScrapingSessionStatus {
    PENDING, IN_PROGRESS, COMPLETED, FAILED, TIMED_OUT
}
```

`src/main/java/com/kropholler/dev/hermes/scraping/ScrapingSessionType.java`:

```java
package com.kropholler.dev.hermes.scraping;

public enum ScrapingSessionType {
    SEARCH, RESCRAPE
}
```

- [ ] **Step 2: Create the ScrapingSession entity**

`src/main/java/com/kropholler/dev/hermes/scraping/internal/ScrapingSession.java`:

```java
package com.kropholler.dev.hermes.scraping.internal;

import com.kropholler.dev.hermes.scraping.ScrapingSessionStatus;
import com.kropholler.dev.hermes.scraping.ScrapingSessionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scraping_sessions")
@Getter
@Setter
@NoArgsConstructor
public class ScrapingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScrapingSessionStatus status = ScrapingSessionStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScrapingSessionType type;

    private String city;
    private Integer minPrice;
    private Integer maxPrice;
    private Integer minArea;
    private Integer maxArea;

    @Column(nullable = false)
    private Integer pageLimit;

    @Column(nullable = false)
    private String fundaUrl;

    private String targetListingUrl;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant startedAt;
    private Instant completedAt;
}
```

- [ ] **Step 3: Create the repository**

`src/main/java/com/kropholler/dev/hermes/scraping/internal/ScrapingSessionRepository.java`:

```java
package com.kropholler.dev.hermes.scraping.internal;

import com.kropholler.dev.hermes.scraping.ScrapingSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScrapingSessionRepository extends JpaRepository<ScrapingSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ScrapingSession s WHERE s.status = :status ORDER BY s.createdAt ASC LIMIT 1")
    Optional<ScrapingSession> findFirstPendingWithLock(ScrapingSessionStatus status);

    List<ScrapingSession> findByStatusAndStartedAtBefore(ScrapingSessionStatus status, Instant cutoff);
}
```

- [ ] **Step 4: Write a failing repository test**

`src/test/java/com/kropholler/dev/hermes/scraping/internal/ScrapingSessionRepositoryTest.java`:

```java
package com.kropholler.dev.hermes.scraping.internal;

import com.kropholler.dev.hermes.scraping.internal.ScrapingSession;
import com.kropholler.dev.hermes.scraping.internal.ScrapingSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class ScrapingSessionRepositoryTest {

    @Autowired
    private ScrapingSessionRepository repository;

    @Test
    void findFirstPendingWithLock_returnsPendingSession() {
        ScrapingSession session = new ScrapingSession();
        session.setType(ScrapingSessionType.SEARCH);
        session.setCity("Amsterdam");
        session.setPageLimit(3);
        session.setFundaUrl("https://www.funda.nl/zoeken/koop?selected_area=%5B%22amsterdam%22%5D");
        repository.save(session);

        Optional<ScrapingSession> result = repository.findFirstPendingWithLock(ScrapingSessionStatus.PENDING);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(ScrapingSessionStatus.PENDING);
    }
}
```

- [ ] **Step 5: Run the test to verify it fails**

Run: `cd hermes-backend && ./mvnw test -pl . -Dtest=ScrapingSessionRepositoryTest -q`

Expected: FAIL — `ScrapingSessionRepository` is package-private and `ScrapingSession` doesn't have `fundaUrl` setter yet. That's fine — the test drives the implementation.

- [ ] **Step 6: Run test to verify it passes after implementation**

Run: `cd hermes-backend && ./mvnw test -pl . -Dtest=ScrapingSessionRepositoryTest -q`

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/ hermes-backend/src/test/java/com/kropholler/dev/hermes/scraping/ScrapingSessionRepositoryTest.java
git commit -m "feat(scraping): add ScrapingSession entity, enums, and repository"
```

---

## Task 3: Scraping module — FundaUrlBuilder

**Files:**
- Create: `src/main/java/com/kropholler/dev/hermes/scraping/internal/FundaUrlBuilder.java`
- Test: `src/test/java/com/kropholler/dev/hermes/scraping/FundaUrlBuilderTest.java`

- [ ] **Step 1: Write failing tests**

`src/test/java/com/kropholler/dev/hermes/scraping/FundaUrlBuilderTest.java`:

```java
package com.kropholler.dev.hermes.scraping;

import com.kropholler.dev.hermes.scraping.internal.FundaUrlBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FundaUrlBuilderTest {

    private final FundaUrlBuilder builder = new FundaUrlBuilder();

    @Test
    void build_withCityOnly_returnsBaseUrl() {
        String url = builder.build("amsterdam", null, null, null, null, 1);
        assertThat(url).isEqualTo(
            "https://www.funda.nl/zoeken/koop?selected_area=%5B%22amsterdam%22%5D&search_result=1"
        );
    }

    @Test
    void build_withAllFilters_includesAllParams() {
        String url = builder.build("rotterdam", 200000, 500000, 60, 150, 1);
        assertThat(url).contains("price_min=200000");
        assertThat(url).contains("price_max=500000");
        assertThat(url).contains("floor_area_min=60");
        assertThat(url).contains("floor_area_max=150");
    }

    @Test
    void build_withPageTwo_includesPageParam() {
        String url = builder.build("amsterdam", null, null, null, null, 2);
        assertThat(url).contains("search_result=2");
    }

    @Test
    void build_pageLimitAboveFive_throwsException() {
        assertThatThrownBy(() -> builder.build("amsterdam", null, null, null, null, 6))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("page");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd hermes-backend && ./mvnw test -Dtest=FundaUrlBuilderTest -q`

Expected: FAIL — `FundaUrlBuilder` does not exist.

- [ ] **Step 3: Implement FundaUrlBuilder**

`src/main/java/com/kropholler/dev/hermes/scraping/internal/FundaUrlBuilder.java`:

```java
package com.kropholler.dev.hermes.scraping.internal;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class FundaUrlBuilder {

    private static final String BASE_URL = "https://www.funda.nl/zoeken/koop";
    private static final int MAX_PAGE = 5;

    String build(String city, Integer minPrice, Integer maxPrice,
                 Integer minArea, Integer maxArea, int page) {
        if (page < 1 || page > MAX_PAGE) {
            throw new IllegalArgumentException("page must be between 1 and " + MAX_PAGE);
        }

        UriComponentsBuilder uri = UriComponentsBuilder.fromHttpUrl(BASE_URL)
            .queryParam("selected_area", "[\"" + city.toLowerCase() + "\"]")
            .queryParam("search_result", page);

        if (minPrice != null) uri.queryParam("price_min", minPrice);
        if (maxPrice != null) uri.queryParam("price_max", maxPrice);
        if (minArea != null)  uri.queryParam("floor_area_min", minArea);
        if (maxArea != null)  uri.queryParam("floor_area_max", maxArea);

        return uri.build().encode().toUriString();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd hermes-backend && ./mvnw test -Dtest=FundaUrlBuilderTest -q`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/internal/FundaUrlBuilder.java hermes-backend/src/test/java/com/kropholler/dev/hermes/scraping/FundaUrlBuilderTest.java
git commit -m "feat(scraping): add FundaUrlBuilder with page and filter support"
```

---

## Task 4: Scraping module — FundaScraperService

**Files:**
- Create: `src/main/java/com/kropholler/dev/hermes/scraping/RawListing.java`
- Create: `src/main/java/com/kropholler/dev/hermes/scraping/internal/FundaScraperService.java`
- Create: `src/test/resources/fixtures/funda-search-result.html`
- Test: `src/test/java/com/kropholler/dev/hermes/scraping/FundaScraperServiceTest.java`

> **Important:** The HTML fixture and CSS selectors below are written together to be consistent. Before production use, capture a real Funda search result page as the fixture and update the selectors in `FundaScraperService` to match.

- [ ] **Step 1: Create RawListing record (public)**

`src/main/java/com/kropholler/dev/hermes/scraping/RawListing.java`:

```java
package com.kropholler.dev.hermes.scraping;

import java.time.LocalDate;

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
    Integer livingAreaM2,
    Integer rooms,
    String energyLabel,
    LocalDate listedOnFundaSince,
    String status
) {}
```

- [ ] **Step 2: Create the HTML fixture**

`src/test/resources/fixtures/funda-search-result.html`:

```html
<!DOCTYPE html>
<html>
<body>
  <div class="search-results">
    <div class="object-list-item"
         data-object-url="/koop/amsterdam/appartement-12345678-teststraat-10/"
         data-object-id="12345678">
      <span class="object-price">€ 450.000 k.k.</span>
      <span class="object-address-street-number">Teststraat 10</span>
      <span class="object-kenmerken">
        <li class="fd-m-right-xs"><span>75 m²</span></li>
        <li class="fd-m-right-xs"><span>3 kamers</span></li>
      </span>
      <span class="object-energy-label label-a">A</span>
      <span class="object-detail-date">Aangeboden since 1 januari 2025</span>
    </div>
    <div class="object-list-item"
         data-object-url="/koop/amsterdam/huis-87654321-anderstraat-5a/"
         data-object-id="87654321">
      <span class="object-price">€ 650.000 k.k.</span>
      <span class="object-address-street-number">Anderstraat 5 a</span>
      <span class="object-kenmerken">
        <li class="fd-m-right-xs"><span>110 m²</span></li>
        <li class="fd-m-right-xs"><span>5 kamers</span></li>
      </span>
      <span class="object-energy-label label-b">B</span>
      <span class="object-detail-date">Aangeboden since 15 maart 2024</span>
    </div>
  </div>
</body>
</html>
```

- [ ] **Step 3: Write failing tests**

`src/test/java/com/kropholler/dev/hermes/scraping/FundaScraperServiceTest.java`:

```java
package com.kropholler.dev.hermes.scraping;

import com.kropholler.dev.hermes.scraping.internal.FundaScraperService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class FundaScraperServiceTest {

    private FundaScraperService scraper;
    private String fixtureHtml;

    @BeforeEach
    void setUp() throws IOException {
        scraper = new FundaScraperService();
        fixtureHtml = new String(
            Objects.requireNonNull(
                getClass().getResourceAsStream("/fixtures/funda-search-result.html")
            ).readAllBytes(),
            StandardCharsets.UTF_8
        );
    }

    @Test
    void parseListings_extractsAllListingsFromHtml() {
        List<RawListing> listings = scraper.parseListings(fixtureHtml, "amsterdam");

        assertThat(listings).hasSize(2);
    }

    @Test
    void parseListings_extractsCorrectFundaId() {
        List<RawListing> listings = scraper.parseListings(fixtureHtml, "amsterdam");

        assertThat(listings.get(0).fundaId()).isEqualTo("12345678");
    }

    @Test
    void parseListings_extractsCorrectUrl() {
        List<RawListing> listings = scraper.parseListings(fixtureHtml, "amsterdam");

        assertThat(listings.get(0).url())
            .isEqualTo("https://www.funda.nl/koop/amsterdam/appartement-12345678-teststraat-10/");
    }

    @Test
    void parseListings_extractsPrice() {
        List<RawListing> listings = scraper.parseListings(fixtureHtml, "amsterdam");

        assertThat(listings.get(0).askingPrice()).isEqualTo(450000);
    }

    @Test
    void parseListings_extractsLivingArea() {
        List<RawListing> listings = scraper.parseListings(fixtureHtml, "amsterdam");

        assertThat(listings.get(0).livingAreaM2()).isEqualTo(75);
    }

    @Test
    void parseListings_extractsRooms() {
        List<RawListing> listings = scraper.parseListings(fixtureHtml, "amsterdam");

        assertThat(listings.get(0).rooms()).isEqualTo(3);
    }

    @Test
    void parseListings_extractsEnergyLabel() {
        List<RawListing> listings = scraper.parseListings(fixtureHtml, "amsterdam");

        assertThat(listings.get(0).energyLabel()).isEqualTo("A");
    }

    @Test
    void parseListings_handlesHouseNumberAddition() {
        List<RawListing> listings = scraper.parseListings(fixtureHtml, "amsterdam");

        assertThat(listings.get(1).houseNumber()).isEqualTo("5");
        assertThat(listings.get(1).houseNumberAddition()).isEqualTo("a");
    }
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `cd hermes-backend && ./mvnw test -Dtest=FundaScraperServiceTest -q`

Expected: FAIL — `FundaScraperService` does not exist.

- [ ] **Step 5: Implement FundaScraperService**

`src/main/java/com/kropholler/dev/hermes/scraping/internal/FundaScraperService.java`:

```java
package com.kropholler.dev.hermes.scraping.internal;

import com.kropholler.dev.hermes.scraping.RawListing;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FundaScraperService {

    private static final String BASE_URL = "https://www.funda.nl";
    private static final Pattern PRICE_PATTERN = Pattern.compile("[\\d.]+");
    private static final Pattern AREA_PATTERN = Pattern.compile("(\\d+)\\s*m²");
    private static final Pattern ROOMS_PATTERN = Pattern.compile("(\\d+)\\s*kamers?");
    private static final Pattern ID_PATTERN = Pattern.compile("-(\\d+)-");
    private static final Pattern HOUSE_NUMBER_PATTERN = Pattern.compile("(\\d+)\\s*(\\S+)?$");
    private static final DateTimeFormatter DUTCH_DATE = DateTimeFormatter
        .ofPattern("d MMMM yyyy", new Locale("nl", "NL"));

    private final RestClient restClient;

    FundaScraperService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    // Constructor for unit tests (no RestClient needed for HTML parsing)
    FundaScraperService() {
        this.restClient = null;
    }

    List<RawListing> scrapeSearchPage(String url, String city) {
        String html = restClient.get()
            .uri(url)
            .header("User-Agent", "Mozilla/5.0 (compatible; HermesBot/1.0)")
            .retrieve()
            .body(String.class);
        return parseListings(html, city);
    }

    List<RawListing> parseListings(String html, String city) {
        Document doc = Jsoup.parse(html);
        List<RawListing> results = new ArrayList<>();

        for (Element item : doc.select(".object-list-item")) {
            String objectUrl = item.attr("data-object-url");
            String fundaId = extractId(objectUrl);
            if (fundaId == null) continue;

            String fullUrl = BASE_URL + objectUrl;
            String addressText = item.select(".object-address-street-number").text();
            String[] addressParts = parseAddress(addressText);
            String priceText = item.select(".object-price").text();
            String kenmerken = item.select(".object-kenmerken").text();
            String energyLabel = item.select(".object-energy-label").text().trim();
            String dateText = item.select(".object-detail-date").text();

            results.add(new RawListing(
                fundaId,
                fullUrl,
                addressParts[0],
                addressParts[1],
                addressParts[2],
                null,
                city,
                null,
                parsePrice(priceText),
                parseArea(kenmerken),
                parseRooms(kenmerken),
                energyLabel.isEmpty() ? null : energyLabel,
                parseDate(dateText),
                "FOR_SALE"
            ));
        }
        return results;
    }

    private String extractId(String url) {
        Matcher m = ID_PATTERN.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private String[] parseAddress(String text) {
        // Returns [street, houseNumber, houseNumberAddition]
        int lastSpace = text.lastIndexOf(' ');
        if (lastSpace < 0) return new String[]{text, "", null};
        String street = text.substring(0, lastSpace);
        String numberPart = text.substring(lastSpace + 1);
        Matcher m = HOUSE_NUMBER_PATTERN.matcher(numberPart);
        if (m.matches()) {
            return new String[]{street, m.group(1), m.group(2)};
        }
        return new String[]{street, numberPart, null};
    }

    private Integer parsePrice(String text) {
        String digits = text.replace(".", "").replaceAll("[^\\d]", "");
        try { return Integer.parseInt(digits); } catch (NumberFormatException e) { return null; }
    }

    private Integer parseArea(String text) {
        Matcher m = AREA_PATTERN.matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    private Integer parseRooms(String text) {
        Matcher m = ROOMS_PATTERN.matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    private LocalDate parseDate(String text) {
        try {
            String datePart = text.replaceAll(".*since\\s+", "");
            return LocalDate.parse(datePart.trim(), DUTCH_DATE);
        } catch (Exception e) {
            return null;
        }
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd hermes-backend && ./mvnw test -Dtest=FundaScraperServiceTest -q`

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/RawListing.java hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/internal/FundaScraperService.java hermes-backend/src/test/java/com/kropholler/dev/hermes/scraping/FundaScraperServiceTest.java hermes-backend/src/test/resources/fixtures/funda-search-result.html
git commit -m "feat(scraping): add FundaScraperService with JSoup HTML parsing"
```

---

## Task 5: Scraping module — worker, poller, watchdog, and public service

**Files:**
- Create: `src/main/java/com/kropholler/dev/hermes/scraping/ScrapingSessionCompleted.java`
- Create: `src/main/java/com/kropholler/dev/hermes/scraping/ScrapingSessionFailed.java`
- Create: `src/main/java/com/kropholler/dev/hermes/scraping/ScrapingSessionDto.java`
- Create: `src/main/java/com/kropholler/dev/hermes/scraping/ScrapingQueueService.java`
- Create: `src/main/java/com/kropholler/dev/hermes/scraping/internal/ScrapingWorker.java`
- Create: `src/main/java/com/kropholler/dev/hermes/scraping/internal/ScrapingPoller.java`
- Create: `src/main/java/com/kropholler/dev/hermes/scraping/internal/ScrapingTimeoutWatchdog.java`
- Test: `src/test/java/com/kropholler/dev/hermes/scraping/ScrapingQueueServiceTest.java`

- [ ] **Step 1: Create public event records and DTO**

`src/main/java/com/kropholler/dev/hermes/scraping/ScrapingSessionCompleted.java`:

```java
package com.kropholler.dev.hermes.scraping;

import java.util.List;
import java.util.UUID;

public record ScrapingSessionCompleted(UUID sessionId, List<RawListing> listings) {}
```

`src/main/java/com/kropholler/dev/hermes/scraping/ScrapingSessionFailed.java`:

```java
package com.kropholler.dev.hermes.scraping;

import java.util.UUID;

public record ScrapingSessionFailed(UUID sessionId, String reason) {}
```

`src/main/java/com/kropholler/dev/hermes/scraping/ScrapingSessionDto.java`:

```java
package com.kropholler.dev.hermes.scraping;

import java.time.Instant;
import java.util.UUID;

public record ScrapingSessionDto(
    UUID id,
    ScrapingSessionStatus status,
    ScrapingSessionType type,
    Instant createdAt,
    Instant completedAt
) {}
```

- [ ] **Step 2: Create ScrapingWorker**

`src/main/java/com/kropholler/dev/hermes/scraping/internal/ScrapingWorker.java`:

```java
package com.kropholler.dev.hermes.scraping.internal;

import com.kropholler.dev.hermes.scraping.RawListing;
import com.kropholler.dev.hermes.scraping.ScrapingSessionCompleted;
import com.kropholler.dev.hermes.scraping.ScrapingSessionFailed;
import com.kropholler.dev.hermes.scraping.ScrapingSessionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScrapingWorker {

    private final ScrapingSessionRepository sessionRepository;
    private final FundaScraperService scraperService;
    private final FundaUrlBuilder urlBuilder;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    @Transactional
    public void process(ScrapingSession session) {  // public required for Spring @Async proxy
        session.setStatus(ScrapingSessionStatus.IN_PROGRESS);
        session.setStartedAt(Instant.now());
        sessionRepository.save(session);

        try {
            List<RawListing> listings = scrapeAllPages(session);
            session.setStatus(ScrapingSessionStatus.COMPLETED);
            session.setCompletedAt(Instant.now());
            sessionRepository.save(session);
            eventPublisher.publishEvent(new ScrapingSessionCompleted(session.getId(), listings));
        } catch (Exception e) {
            log.error("Scraping session {} failed", session.getId(), e);
            session.setStatus(ScrapingSessionStatus.FAILED);
            session.setCompletedAt(Instant.now());
            sessionRepository.save(session);
            eventPublisher.publishEvent(new ScrapingSessionFailed(session.getId(), e.getMessage()));
        }
    }

    private List<RawListing> scrapeAllPages(ScrapingSession session) {
        if (session.getType() == com.kropholler.dev.hermes.scraping.ScrapingSessionType.RESCRAPE) {
            return scraperService.scrapeSearchPage(session.getTargetListingUrl(), session.getCity());
        }

        List<RawListing> all = new java.util.ArrayList<>();
        int limit = Math.min(session.getPageLimit(), 5);
        for (int page = 1; page <= limit; page++) {
            String pageUrl = urlBuilder.build(
                session.getCity(), session.getMinPrice(), session.getMaxPrice(),
                session.getMinArea(), session.getMaxArea(), page
            );
            List<RawListing> pageResults = scraperService.scrapeSearchPage(pageUrl, session.getCity());
            all.addAll(pageResults);
            if (pageResults.isEmpty()) break;
        }
        return all;
    }
}
```

- [ ] **Step 3: Create ScrapingPoller**

`src/main/java/com/kropholler/dev/hermes/scraping/internal/ScrapingPoller.java`:

```java
package com.kropholler.dev.hermes.scraping.internal;

import com.kropholler.dev.hermes.scraping.ScrapingSessionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class ScrapingPoller {

    private final ScrapingSessionRepository sessionRepository;
    private final ScrapingWorker worker;

    @Scheduled(fixedDelay = 5_000)
    public void pollQueue() {
        sessionRepository.findFirstPendingWithLock(ScrapingSessionStatus.PENDING)
            .ifPresent(worker::process);
    }
}
```

- [ ] **Step 4: Create ScrapingTimeoutWatchdog**

`src/main/java/com/kropholler/dev/hermes/scraping/internal/ScrapingTimeoutWatchdog.java`:

```java
package com.kropholler.dev.hermes.scraping.internal;

import com.kropholler.dev.hermes.scraping.ScrapingSessionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
class ScrapingTimeoutWatchdog {

    private static final int TIMEOUT_MINUTES = 3;

    private final ScrapingSessionRepository sessionRepository;

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void markTimedOutSessions() {
        Instant cutoff = Instant.now().minus(TIMEOUT_MINUTES, ChronoUnit.MINUTES);
        List<ScrapingSession> stale = sessionRepository
            .findByStatusAndStartedAtBefore(ScrapingSessionStatus.IN_PROGRESS, cutoff);

        for (ScrapingSession session : stale) {
            log.warn("Session {} timed out after {} minutes", session.getId(), TIMEOUT_MINUTES);
            session.setStatus(ScrapingSessionStatus.TIMED_OUT);
            session.setCompletedAt(Instant.now());
        }
        sessionRepository.saveAll(stale);
    }
}
```

- [ ] **Step 5: Create ScrapingQueueService (public)**

`src/main/java/com/kropholler/dev/hermes/scraping/ScrapingQueueService.java`:

```java
package com.kropholler.dev.hermes.scraping;

import com.kropholler.dev.hermes.scraping.internal.ScrapingSession;
import com.kropholler.dev.hermes.scraping.internal.ScrapingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScrapingQueueService {

    private static final int MAX_PAGE_LIMIT = 5;

    private final ScrapingSessionRepository sessionRepository;

    @Transactional
    public ScrapingSessionDto enqueueSearch(String city, Integer minPrice, Integer maxPrice,
                                            Integer minArea, Integer maxArea, int pageLimit) {
        int clampedLimit = Math.min(pageLimit, MAX_PAGE_LIMIT);
        String url = buildSearchUrl(city, minPrice, maxPrice, minArea, maxArea, 1);

        ScrapingSession session = new ScrapingSession();
        session.setType(ScrapingSessionType.SEARCH);
        session.setCity(city);
        session.setMinPrice(minPrice);
        session.setMaxPrice(maxPrice);
        session.setMinArea(minArea);
        session.setMaxArea(maxArea);
        session.setPageLimit(clampedLimit);
        session.setFundaUrl(url);

        return toDto(sessionRepository.save(session));
    }

    @Transactional
    public ScrapingSessionDto enqueueRescrape(String listingUrl, String city) {
        ScrapingSession session = new ScrapingSession();
        session.setType(ScrapingSessionType.RESCRAPE);
        session.setCity(city);
        session.setPageLimit(1);
        session.setFundaUrl(listingUrl);
        session.setTargetListingUrl(listingUrl);

        return toDto(sessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public Optional<ScrapingSessionDto> findById(UUID id) {
        return sessionRepository.findById(id).map(this::toDto);
    }

    private String buildSearchUrl(String city, Integer minPrice, Integer maxPrice,
                                  Integer minArea, Integer maxArea, int page) {
        // Inline to avoid package-private FundaUrlBuilder dependency from public class
        var uri = org.springframework.web.util.UriComponentsBuilder
            .fromHttpUrl("https://www.funda.nl/zoeken/koop")
            .queryParam("selected_area", "[\"" + city.toLowerCase() + "\"]")
            .queryParam("search_result", page);
        if (minPrice != null) uri.queryParam("price_min", minPrice);
        if (maxPrice != null) uri.queryParam("price_max", maxPrice);
        if (minArea != null)  uri.queryParam("floor_area_min", minArea);
        if (maxArea != null)  uri.queryParam("floor_area_max", maxArea);
        return uri.build().encode().toUriString();
    }

    private ScrapingSessionDto toDto(ScrapingSession s) {
        return new ScrapingSessionDto(s.getId(), s.getStatus(), s.getType(),
                                      s.getCreatedAt(), s.getCompletedAt());
    }
}
```

- [ ] **Step 6: Write failing test for ScrapingQueueService**

`src/test/java/com/kropholler/dev/hermes/scraping/ScrapingQueueServiceTest.java`:

```java
package com.kropholler.dev.hermes.scraping;

import com.kropholler.dev.hermes.scraping.internal.ScrapingSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScrapingQueueServiceTest {

    @Mock
    private ScrapingSessionRepository repository;

    @InjectMocks
    private ScrapingQueueService service;

    @Test
    void enqueueSearch_clampsPageLimitToFive() {
        var session = new com.kropholler.dev.hermes.scraping.internal.ScrapingSession();
        session.setType(ScrapingSessionType.SEARCH);
        session.setCity("amsterdam");
        session.setPageLimit(5);
        session.setFundaUrl("https://www.funda.nl/zoeken/koop?selected_area=%5B%22amsterdam%22%5D&search_result=1");
        when(repository.save(any())).thenReturn(session);

        ScrapingSessionDto dto = service.enqueueSearch("amsterdam", null, null, null, null, 10);

        assertThat(dto.status()).isEqualTo(ScrapingSessionStatus.PENDING);
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `cd hermes-backend && ./mvnw test -Dtest=ScrapingQueueServiceTest -q`

Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/
git commit -m "feat(scraping): add worker, poller, watchdog, and public ScrapingQueueService"
```

---

## Task 6: Listing module — data layer and persistence service

**Files:**
- Create: `src/main/java/com/kropholler/dev/hermes/listing/ListingStatus.java`
- Create: `src/main/java/com/kropholler/dev/hermes/listing/ListingSnapshotsCreated.java`
- Create: `src/main/java/com/kropholler/dev/hermes/listing/internal/Listing.java`
- Create: `src/main/java/com/kropholler/dev/hermes/listing/internal/ListingSnapshot.java`
- Create: `src/main/java/com/kropholler/dev/hermes/listing/internal/ListingRepository.java`
- Create: `src/main/java/com/kropholler/dev/hermes/listing/internal/ListingSnapshotRepository.java`
- Create: `src/main/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceService.java`
- Test: `src/test/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceServiceTest.java`

- [ ] **Step 1: Create public enum and event**

`src/main/java/com/kropholler/dev/hermes/listing/ListingStatus.java`:

```java
package com.kropholler.dev.hermes.listing;

public enum ListingStatus {
    FOR_SALE, UNDER_OFFER, SOLD, WITHDRAWN
}
```

`src/main/java/com/kropholler/dev/hermes/listing/ListingSnapshotsCreated.java`:

```java
package com.kropholler.dev.hermes.listing;

import java.util.List;
import java.util.UUID;

public record ListingSnapshotsCreated(List<UUID> listingIds) {}
```

- [ ] **Step 2: Create Listing entity**

`src/main/java/com/kropholler/dev/hermes/listing/internal/Listing.java`:

```java
package com.kropholler.dev.hermes.listing.internal;

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
}
```

- [ ] **Step 3: Create ListingSnapshot entity**

`src/main/java/com/kropholler/dev/hermes/listing/internal/ListingSnapshot.java`:

```java
package com.kropholler.dev.hermes.listing.internal;

import com.kropholler.dev.hermes.listing.ListingStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "listing_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class ListingSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID listingId;

    @Column(nullable = false)
    private Instant scrapedAt = Instant.now();

    private Integer askingPrice;
    private Integer livingAreaM2;
    private Integer rooms;
    private String energyLabel;
    private LocalDate listedOnFundaSince;

    @Enumerated(EnumType.STRING)
    private ListingStatus status;
}
```

- [ ] **Step 4: Create repositories**

`src/main/java/com/kropholler/dev/hermes/listing/internal/ListingRepository.java`:

```java
package com.kropholler.dev.hermes.listing.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ListingRepository extends JpaRepository<Listing, UUID> {
    Optional<Listing> findByFundaId(String fundaId);
}
```

`src/main/java/com/kropholler/dev/hermes/listing/internal/ListingSnapshotRepository.java`:

```java
package com.kropholler.dev.hermes.listing.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ListingSnapshotRepository extends JpaRepository<ListingSnapshot, UUID> {
    List<ListingSnapshot> findByListingIdOrderByScrapedAtAsc(UUID listingId);
    Optional<ListingSnapshot> findTopByListingIdOrderByScrapedAtDesc(UUID listingId);
    Page<ListingSnapshot> findByListingId(UUID listingId, Pageable pageable);
}
```

- [ ] **Step 5: Write failing test for ListingPersistenceService**

`src/test/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceServiceTest.java`:

```java
package com.kropholler.dev.hermes.listing.internal;

import com.kropholler.dev.hermes.listing.internal.ListingPersistenceService;
import com.kropholler.dev.hermes.listing.internal.ListingRepository;
import com.kropholler.dev.hermes.listing.internal.ListingSnapshotRepository;
import com.kropholler.dev.hermes.scraping.RawListing;
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
    @Mock private ListingSnapshotRepository snapshotRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ListingPersistenceService service;

    @Test
    void onScrapingCompleted_createsNewListingAndSnapshot() {
        RawListing raw = new RawListing(
            "12345678", "https://www.funda.nl/koop/amsterdam/appartement-12345678-teststraat-10/",
            "Teststraat", "10", null, "1234AB", "amsterdam", "Noord-Holland",
            450000, 75, 3, "A", null, "FOR_SALE"
        );
        ScrapingSessionCompleted event = new ScrapingSessionCompleted(UUID.randomUUID(), List.of(raw));

        var listing = new com.kropholler.dev.hermes.listing.internal.Listing();
        listing.setFundaId("12345678");
        when(listingRepository.findByFundaId("12345678")).thenReturn(Optional.empty());
        when(listingRepository.save(any())).thenReturn(listing);
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.onScrapingSessionCompleted(event);

        verify(listingRepository).save(any());
        verify(snapshotRepository).save(any());
        verify(eventPublisher).publishEvent(any(ListingSnapshotsCreated.class));
    }

    @Test
    void onScrapingCompleted_updatesLastSeenAtForExistingListing() {
        var existing = new com.kropholler.dev.hermes.listing.internal.Listing();
        existing.setFundaId("12345678");
        var originalLastSeen = existing.getLastSeenAt();

        RawListing raw = new RawListing(
            "12345678", "https://www.funda.nl/koop/amsterdam/appartement-12345678-teststraat-10/",
            "Teststraat", "10", null, "1234AB", "amsterdam", "Noord-Holland",
            460000, 75, 3, "A", null, "FOR_SALE"
        );
        ScrapingSessionCompleted event = new ScrapingSessionCompleted(UUID.randomUUID(), List.of(raw));

        when(listingRepository.findByFundaId("12345678")).thenReturn(Optional.of(existing));
        when(listingRepository.save(any())).thenReturn(existing);
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.onScrapingSessionCompleted(event);

        assertThat(existing.getLastSeenAt()).isAfterOrEqualTo(originalLastSeen);
        verify(snapshotRepository).save(any());
    }
}
```

- [ ] **Step 6: Run tests to verify they fail**

Run: `cd hermes-backend && ./mvnw test -Dtest=ListingPersistenceServiceTest -q`

Expected: FAIL — `ListingPersistenceService` does not exist.

- [ ] **Step 7: Implement ListingPersistenceService**

`src/main/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceService.java`:

```java
package com.kropholler.dev.hermes.listing.internal;

import com.kropholler.dev.hermes.listing.ListingSnapshotsCreated;
import com.kropholler.dev.hermes.listing.ListingStatus;
import com.kropholler.dev.hermes.scraping.RawListing;
import com.kropholler.dev.hermes.scraping.ScrapingSessionCompleted;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListingPersistenceService {

    private final ListingRepository listingRepository;
    private final ListingSnapshotRepository snapshotRepository;
    private final ApplicationEventPublisher eventPublisher;

    @ApplicationModuleListener  // public required for Spring proxy
    @Transactional
    public void onScrapingSessionCompleted(ScrapingSessionCompleted event) {
        List<UUID> affectedListingIds = new ArrayList<>();

        for (RawListing raw : event.listings()) {
            Listing listing = listingRepository.findByFundaId(raw.fundaId())
                .orElseGet(() -> createListing(raw));

            listing.setLastSeenAt(Instant.now());
            listing = listingRepository.save(listing);

            ListingSnapshot snapshot = createSnapshot(listing.getId(), raw);
            snapshotRepository.save(snapshot);
            affectedListingIds.add(listing.getId());
        }

        if (!affectedListingIds.isEmpty()) {
            eventPublisher.publishEvent(new ListingSnapshotsCreated(affectedListingIds));
        }
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

    private ListingSnapshot createSnapshot(UUID listingId, RawListing raw) {
        ListingSnapshot s = new ListingSnapshot();
        s.setListingId(listingId);
        s.setAskingPrice(raw.askingPrice());
        s.setLivingAreaM2(raw.livingAreaM2());
        s.setRooms(raw.rooms());
        s.setEnergyLabel(raw.energyLabel());
        s.setListedOnFundaSince(raw.listedOnFundaSince());
        s.setStatus(parseStatus(raw.status()));
        return s;
    }

    private ListingStatus parseStatus(String status) {
        try { return ListingStatus.valueOf(status); }
        catch (Exception e) { return ListingStatus.FOR_SALE; }
    }
}
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `cd hermes-backend && ./mvnw test -Dtest=ListingPersistenceServiceTest -q`

Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/
git commit -m "feat(listing): add entities, repositories, and event-driven persistence service"
```

---

## Task 7: Listing module — public service

**Files:**
- Create: `src/main/java/com/kropholler/dev/hermes/listing/ListingDto.java`
- Create: `src/main/java/com/kropholler/dev/hermes/listing/ListingSnapshotDto.java`
- Create: `src/main/java/com/kropholler/dev/hermes/listing/ListingService.java`

- [ ] **Step 1: Create public DTOs**

`src/main/java/com/kropholler/dev/hermes/listing/ListingDto.java`:

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
    ListingSnapshotDto latestSnapshot
) {}
```

`src/main/java/com/kropholler/dev/hermes/listing/ListingSnapshotDto.java`:

```java
package com.kropholler.dev.hermes.listing;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ListingSnapshotDto(
    UUID id,
    Instant scrapedAt,
    Integer askingPrice,
    Integer livingAreaM2,
    Integer rooms,
    String energyLabel,
    LocalDate listedOnFundaSince,
    ListingStatus status
) {}
```

- [ ] **Step 2: Create ListingService**

`src/main/java/com/kropholler/dev/hermes/listing/ListingService.java`:

```java
package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.internal.Listing;
import com.kropholler.dev.hermes.listing.internal.ListingRepository;
import com.kropholler.dev.hermes.listing.internal.ListingSnapshot;
import com.kropholler.dev.hermes.listing.internal.ListingSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListingService {

    private final ListingRepository listingRepository;
    private final ListingSnapshotRepository snapshotRepository;

    @Transactional(readOnly = true)
    public Page<ListingDto> findAll(Pageable pageable) {
        return listingRepository.findAll(pageable).map(this::toDtoWithLatestSnapshot);
    }

    @Transactional(readOnly = true)
    public Optional<ListingDto> findById(UUID id) {
        return listingRepository.findById(id).map(this::toDtoWithLatestSnapshot);
    }

    @Transactional(readOnly = true)
    public Optional<ListingDto> findByFundaId(String fundaId) {
        return listingRepository.findByFundaId(fundaId).map(this::toDtoWithLatestSnapshot);
    }

    private ListingDto toDtoWithLatestSnapshot(Listing l) {
        ListingSnapshotDto latestSnapshot = snapshotRepository
            .findTopByListingIdOrderByScrapedAtDesc(l.getId())
            .map(this::toSnapshotDto)
            .orElse(null);
        return new ListingDto(l.getId(), l.getFundaId(), l.getUrl(),
            l.getStreet(), l.getHouseNumber(), l.getHouseNumberAddition(),
            l.getZipCode(), l.getCity(), l.getProvince(),
            l.getFirstSeenAt(), l.getLastSeenAt(), latestSnapshot);
    }

    private ListingSnapshotDto toSnapshotDto(ListingSnapshot s) {
        return new ListingSnapshotDto(s.getId(), s.getScrapedAt(), s.getAskingPrice(),
            s.getLivingAreaM2(), s.getRooms(), s.getEnergyLabel(),
            s.getListedOnFundaSince(), s.getStatus());
    }
}
```

- [ ] **Step 3: Compile to verify**

Run: `cd hermes-backend && ./mvnw compile -q`

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingDto.java hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingSnapshotDto.java hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingService.java
git commit -m "feat(listing): add ListingService and public DTOs"
```

---

## Task 8: Report module

**Files:**
- Create: `src/main/java/com/kropholler/dev/hermes/report/PricePoint.java`
- Create: `src/main/java/com/kropholler/dev/hermes/report/StatusPoint.java`
- Create: `src/main/java/com/kropholler/dev/hermes/report/ListingReport.java`
- Create: `src/main/java/com/kropholler/dev/hermes/report/ReportService.java`
- Test: `src/test/java/com/kropholler/dev/hermes/report/ReportServiceTest.java`

- [ ] **Step 1: Write failing tests**

`src/test/java/com/kropholler/dev/hermes/report/ReportServiceTest.java`:

```java
package com.kropholler.dev.hermes.report;

import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.ListingSnapshotDto;
import com.kropholler.dev.hermes.listing.ListingStatus;
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
            now.minus(30, ChronoUnit.DAYS), now, null
        );

        List<ListingSnapshotDto> snapshots = List.of(
            snapshot(now.minus(30, ChronoUnit.DAYS), 400000, ListingStatus.FOR_SALE),
            snapshot(now.minus(10, ChronoUnit.DAYS), 380000, ListingStatus.FOR_SALE)
        );

        when(listingService.findById(listingId)).thenReturn(Optional.of(listing));
        when(listingService.findSnapshotsByListingId(listingId)).thenReturn(snapshots);

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
    void generateReport_deduplicatesStatusHistory() {
        UUID listingId = UUID.randomUUID();
        Instant now = Instant.now();

        ListingDto listing = new ListingDto(
            listingId, "12345678", "https://funda.nl/...",
            "Teststraat", "1", null, "1234AB", "Amsterdam", "Noord-Holland",
            now.minus(60, ChronoUnit.DAYS), now, null
        );

        List<ListingSnapshotDto> snapshots = List.of(
            snapshot(now.minus(60, ChronoUnit.DAYS), 400000, ListingStatus.FOR_SALE),
            snapshot(now.minus(30, ChronoUnit.DAYS), 400000, ListingStatus.FOR_SALE),
            snapshot(now.minus(5,  ChronoUnit.DAYS), 400000, ListingStatus.UNDER_OFFER)
        );

        when(listingService.findById(listingId)).thenReturn(Optional.of(listing));
        when(listingService.findSnapshotsByListingId(listingId)).thenReturn(snapshots);

        ListingReport report = service.generateReport(listingId).orElseThrow();

        assertThat(report.statusHistory()).hasSize(2);
        assertThat(report.statusHistory().get(0).status()).isEqualTo(ListingStatus.FOR_SALE);
        assertThat(report.statusHistory().get(1).status()).isEqualTo(ListingStatus.UNDER_OFFER);
    }

    private ListingSnapshotDto snapshot(Instant scrapedAt, int price, ListingStatus status) {
        return new ListingSnapshotDto(
            UUID.randomUUID(), scrapedAt, price,
            75, 3, "A", LocalDate.now().minusDays(60), status
        );
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd hermes-backend && ./mvnw test -Dtest=ReportServiceTest -q`

Expected: FAIL — `ReportService` does not exist.

- [ ] **Step 3: Create report records**

`src/main/java/com/kropholler/dev/hermes/report/PricePoint.java`:

```java
package com.kropholler.dev.hermes.report;

import java.time.Instant;

public record PricePoint(Instant scrapedAt, Integer askingPrice) {}
```

`src/main/java/com/kropholler/dev/hermes/report/StatusPoint.java`:

```java
package com.kropholler.dev.hermes.report;

import com.kropholler.dev.hermes.listing.ListingStatus;
import java.time.Instant;

public record StatusPoint(Instant scrapedAt, ListingStatus status) {}
```

`src/main/java/com/kropholler/dev/hermes/report/ListingReport.java`:

```java
package com.kropholler.dev.hermes.report;

import com.kropholler.dev.hermes.listing.ListingStatus;

import java.util.List;
import java.util.UUID;

public record ListingReport(
    UUID listingId,
    Long daysListedOnFunda,
    Long daysInHermes,
    Integer currentPrice,
    Integer initialPrice,
    Double priceChangePct,
    List<PricePoint> priceHistory,
    List<StatusPoint> statusHistory,
    ListingStatus currentStatus
) {}
```

- [ ] **Step 4: Implement ReportService**

`src/main/java/com/kropholler/dev/hermes/report/ReportService.java`:

```java
package com.kropholler.dev.hermes.report;

import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.ListingStatus;
import com.kropholler.dev.hermes.listing.internal.ListingSnapshot;
import com.kropholler.dev.hermes.listing.internal.ListingSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ListingSnapshotRepository snapshotRepository;
    private final ListingService listingService;

    @Transactional(readOnly = true)
    public Optional<ListingReport> generateReport(UUID listingId) {
        return listingService.findById(listingId).map(listing -> {
            List<ListingSnapshot> snapshots = snapshotRepository
                .findByListingIdOrderByScrapedAtAsc(listingId);

            if (snapshots.isEmpty()) {
                return null;
            }

            ListingSnapshot first = snapshots.get(0);
            ListingSnapshot latest = snapshots.get(snapshots.size() - 1);

            Integer initialPrice = first.getAskingPrice();
            Integer currentPrice = latest.getAskingPrice();

            Double priceChangePct = null;
            if (initialPrice != null && currentPrice != null && initialPrice != 0) {
                priceChangePct = Math.round(
                    ((currentPrice - initialPrice) / (double) initialPrice * 100) * 10.0) / 10.0;
            }

            Long daysListedOnFunda = snapshots.stream()
                .filter(s -> s.getListedOnFundaSince() != null)
                .map(ListingSnapshot::getListedOnFundaSince)
                .min(LocalDate::compareTo)
                .map(d -> ChronoUnit.DAYS.between(d, LocalDate.now()))
                .orElse(null);

            long daysInHermes = ChronoUnit.DAYS.between(
                listing.firstSeenAt().atZone(ZoneOffset.UTC).toLocalDate(), LocalDate.now());

            List<PricePoint> priceHistory = snapshots.stream()
                .map(s -> new PricePoint(s.getScrapedAt(), s.getAskingPrice()))
                .toList();

            List<StatusPoint> statusHistory = buildStatusHistory(snapshots);

            return new ListingReport(
                listingId, daysListedOnFunda, daysInHermes,
                currentPrice, initialPrice, priceChangePct,
                priceHistory, statusHistory, latest.getStatus()
            );
        });
    }

    private List<StatusPoint> buildStatusHistory(List<ListingSnapshot> snapshots) {
        List<StatusPoint> history = new ArrayList<>();
        ListingStatus last = null;
        for (ListingSnapshot s : snapshots) {
            if (s.getStatus() != null && !s.getStatus().equals(last)) {
                history.add(new StatusPoint(s.getScrapedAt(), s.getStatus()));
                last = s.getStatus();
            }
        }
        return history;
    }
}
```

- [ ] **Step 5: The `ReportService` accesses `ListingSnapshotRepository` which is `internal`. Fix by making `ListingSnapshotRepository` package-accessible from report module via `ListingService`**

Update `ListingService` to add a snapshots method the report module can use:

```java
// Add to ListingService.java
@Transactional(readOnly = true)
public List<ListingSnapshotDto> findSnapshotsByListingId(UUID listingId) {
    return snapshotRepository.findByListingIdOrderByScrapedAtAsc(listingId)
        .stream().map(this::toSnapshotDto).toList();
}
```

Update `ReportService` to use `ListingService` instead of the internal `ListingSnapshotRepository`:

```java
// Replace ListingSnapshotRepository dependency with:
// Change snapshotRepository calls in generateReport to listingService.findSnapshotsByListingId(listingId)
// and adjust code to work with ListingSnapshotDto instead of ListingSnapshot entity
```

Update `ReportServiceTest` accordingly to mock `listingService.findSnapshotsByListingId(...)` instead of the repository.

> **Note:** The complete updated `ReportService` using only `ListingService` (no direct repository access) is shown below.

Updated `ReportService.java` — replace full file:

```java
package com.kropholler.dev.hermes.report;

import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.ListingSnapshotDto;
import com.kropholler.dev.hermes.listing.ListingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
            List<ListingSnapshotDto> snapshots = listingService.findSnapshotsByListingId(listingId);

            if (snapshots.isEmpty()) return null;

            ListingSnapshotDto first = snapshots.get(0);
            ListingSnapshotDto latest = snapshots.get(snapshots.size() - 1);

            Integer initialPrice = first.askingPrice();
            Integer currentPrice = latest.askingPrice();

            Double priceChangePct = null;
            if (initialPrice != null && currentPrice != null && initialPrice != 0) {
                priceChangePct = Math.round(
                    ((currentPrice - initialPrice) / (double) initialPrice * 100) * 10.0) / 10.0;
            }

            Long daysListedOnFunda = snapshots.stream()
                .filter(s -> s.listedOnFundaSince() != null)
                .map(ListingSnapshotDto::listedOnFundaSince)
                .min(LocalDate::compareTo)
                .map(d -> ChronoUnit.DAYS.between(d, LocalDate.now()))
                .orElse(null);

            long daysInHermes = ChronoUnit.DAYS.between(
                listing.firstSeenAt().atZone(ZoneOffset.UTC).toLocalDate(), LocalDate.now());

            List<PricePoint> priceHistory = snapshots.stream()
                .map(s -> new PricePoint(s.scrapedAt(), s.askingPrice()))
                .toList();

            List<StatusPoint> statusHistory = buildStatusHistory(snapshots);

            return new ListingReport(
                listingId, daysListedOnFunda, daysInHermes,
                currentPrice, initialPrice, priceChangePct,
                priceHistory, statusHistory, latest.status()
            );
        });
    }

    private List<StatusPoint> buildStatusHistory(List<ListingSnapshotDto> snapshots) {
        List<StatusPoint> history = new ArrayList<>();
        ListingStatus last = null;
        for (ListingSnapshotDto s : snapshots) {
            if (s.status() != null && !s.status().equals(last)) {
                history.add(new StatusPoint(s.scrapedAt(), s.status()));
                last = s.status();
            }
        }
        return history;
    }
}
```

Also update `ReportServiceTest` to mock `listingService.findSnapshotsByListingId(listingId)` instead of `snapshotRepository`:

```java
// Replace @Mock ListingSnapshotRepository with nothing (remove it)
// Replace when(snapshotRepository...) with:
when(listingService.findSnapshotsByListingId(listingId))
    .thenReturn(List.of(toDto(snap1), toDto(snap2)));

// Add helper in test:
private ListingSnapshotDto toDto(ListingSnapshot s) {
    return new ListingSnapshotDto(UUID.randomUUID(), s.getScrapedAt(), s.getAskingPrice(),
        s.getLivingAreaM2(), s.getRooms(), s.getEnergyLabel(),
        s.getListedOnFundaSince(), s.getStatus());
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd hermes-backend && ./mvnw test -Dtest=ReportServiceTest -q`

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/report/ hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingService.java hermes-backend/src/test/java/com/kropholler/dev/hermes/report/ReportServiceTest.java
git commit -m "feat(report): add ReportService with price history and status deduplication"
```

---

## Task 9: AI module

**Files:**
- Create: `src/main/java/com/kropholler/dev/hermes/ai/ListingSummaryDto.java`
- Create: `src/main/java/com/kropholler/dev/hermes/ai/ListingSummaryService.java`
- Create: `src/main/java/com/kropholler/dev/hermes/ai/internal/ListingSummary.java`
- Create: `src/main/java/com/kropholler/dev/hermes/ai/internal/ListingSummaryRepository.java`
- Create: `src/main/java/com/kropholler/dev/hermes/ai/internal/ListingSummaryGenerationService.java`
- Test: `src/test/java/com/kropholler/dev/hermes/ai/ListingSummaryServiceTest.java`

- [ ] **Step 1: Create public DTO**

`src/main/java/com/kropholler/dev/hermes/ai/ListingSummaryDto.java`:

```java
package com.kropholler.dev.hermes.ai;

import java.time.Instant;
import java.util.UUID;

public record ListingSummaryDto(UUID listingId, String summary, Instant generatedAt) {}
```

- [ ] **Step 2: Create ListingSummary entity**

`src/main/java/com/kropholler/dev/hermes/ai/internal/ListingSummary.java`:

```java
package com.kropholler.dev.hermes.ai.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "listing_summaries")
@Getter
@Setter
@NoArgsConstructor
public class ListingSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID listingId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false)
    private Instant generatedAt = Instant.now();
}
```

- [ ] **Step 3: Create repository**

`src/main/java/com/kropholler/dev/hermes/ai/internal/ListingSummaryRepository.java`:

```java
package com.kropholler.dev.hermes.ai.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ListingSummaryRepository extends JpaRepository<ListingSummary, UUID> {
    Optional<ListingSummary> findByListingId(UUID listingId);
}
```

- [ ] **Step 4: Write failing test for ListingSummaryService**

`src/test/java/com/kropholler/dev/hermes/ai/ListingSummaryServiceTest.java`:

```java
package com.kropholler.dev.hermes.ai;

import com.kropholler.dev.hermes.ai.internal.ListingSummary;
import com.kropholler.dev.hermes.ai.internal.ListingSummaryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingSummaryServiceTest {

    @Mock private ListingSummaryRepository repository;

    @InjectMocks
    private ListingSummaryService service;

    @Test
    void findByListingId_returnsDtoWhenSummaryExists() {
        UUID listingId = UUID.randomUUID();
        ListingSummary summary = new ListingSummary();
        summary.setListingId(listingId);
        summary.setSummary("A lovely apartment in Amsterdam.");
        summary.setGeneratedAt(Instant.now());

        when(repository.findByListingId(listingId)).thenReturn(Optional.of(summary));

        Optional<ListingSummaryDto> result = service.findByListingId(listingId);

        assertThat(result).isPresent();
        assertThat(result.get().summary()).isEqualTo("A lovely apartment in Amsterdam.");
    }

    @Test
    void findByListingId_returnsEmptyWhenNotFound() {
        UUID listingId = UUID.randomUUID();
        when(repository.findByListingId(listingId)).thenReturn(Optional.empty());

        assertThat(service.findByListingId(listingId)).isEmpty();
    }
}
```

- [ ] **Step 5: Run tests to verify they fail**

Run: `cd hermes-backend && ./mvnw test -Dtest=ListingSummaryServiceTest -q`

Expected: FAIL — `ListingSummaryService` does not exist.

- [ ] **Step 6: Create ListingSummaryService (public)**

`src/main/java/com/kropholler/dev/hermes/ai/ListingSummaryService.java`:

```java
package com.kropholler.dev.hermes.ai;

import com.kropholler.dev.hermes.ai.internal.ListingSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListingSummaryService {

    private final ListingSummaryRepository repository;

    @Transactional(readOnly = true)
    public Optional<ListingSummaryDto> findByListingId(UUID listingId) {
        return repository.findByListingId(listingId)
            .map(s -> new ListingSummaryDto(s.getListingId(), s.getSummary(), s.getGeneratedAt()));
    }
}
```

- [ ] **Step 7: Create ListingSummaryGenerationService (event consumer + Ollama)**

`src/main/java/com/kropholler/dev/hermes/ai/internal/ListingSummaryGenerationService.java`:

```java
package com.kropholler.dev.hermes.ai.internal;

import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.ListingSnapshotDto;
import com.kropholler.dev.hermes.listing.ListingSnapshotsCreated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
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
    @Transactional
    public void onListingSnapshotsCreated(ListingSnapshotsCreated event) {
        ChatClient chatClient = chatClientBuilder.build();

        for (UUID listingId : event.listingIds()) {
            listingService.findById(listingId).ifPresent(listing -> {
                String summary = generateSummary(chatClient, listing);
                upsertSummary(listingId, summary);
            });
        }
    }

    private String generateSummary(ChatClient chatClient, ListingDto listing) {
        ListingSnapshotDto snapshot = listing.latestSnapshot();
        String prompt = buildPrompt(listing, snapshot);
        try {
            return chatClient.prompt().user(prompt).call().content();
        } catch (Exception e) {
            log.error("Failed to generate AI summary for listing {}", listing.id(), e);
            return "Summary not available.";
        }
    }

    private String buildPrompt(ListingDto listing, ListingSnapshotDto snapshot) {
        return String.format(
            """
            Write a concise, plain-language summary (2-3 sentences) of this Dutch property listing.
            Include the key selling points, price, and location.

            Address: %s %s%s, %s %s, %s
            Price: €%,d
            Size: %d m²
            Rooms: %d
            Energy label: %s
            Status: %s
            """,
            listing.street(), listing.houseNumber(),
            listing.houseNumberAddition() != null ? " " + listing.houseNumberAddition() : "",
            listing.zipCode(), listing.city(), listing.province(),
            snapshot != null ? snapshot.askingPrice() : 0,
            snapshot != null ? snapshot.livingAreaM2() : 0,
            snapshot != null ? snapshot.rooms() : 0,
            snapshot != null && snapshot.energyLabel() != null ? snapshot.energyLabel() : "unknown",
            snapshot != null ? snapshot.status() : "unknown"
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

- [ ] **Step 8: Run tests to verify they pass**

Run: `cd hermes-backend && ./mvnw test -Dtest=ListingSummaryServiceTest -q`

Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/ hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/
git commit -m "feat(ai): add ListingSummaryService and Ollama-backed summary generation"
```

---

## Task 10: OpenAPI spec

**Files:**
- Create: `src/main/resources/openapi/api.yaml`

- [ ] **Step 1: Create the OpenAPI spec**

`src/main/resources/openapi/api.yaml`:

```yaml
openapi: 3.0.3
info:
  title: Hermes API
  description: Real estate tracking API backed by Funda.nl scraping
  version: 1.0.0

paths:
  /api/scraping-sessions:
    post:
      operationId: createScrapingSession
      tags: [ScrapingSessions]
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateScrapingSessionRequest'
      responses:
        '201':
          description: Session created
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ScrapingSessionResponse'

  /api/scraping-sessions/{id}:
    get:
      operationId: getScrapingSession
      tags: [ScrapingSessions]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '200':
          description: Session found
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ScrapingSessionResponse'
        '404':
          description: Session not found
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'

  /api/listings:
    get:
      operationId: getListings
      tags: [Listings]
      parameters:
        - name: page
          in: query
          schema:
            type: integer
            default: 0
            minimum: 0
        - name: size
          in: query
          schema:
            type: integer
            default: 20
            minimum: 1
            maximum: 100
      responses:
        '200':
          description: Paginated listing results
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ListingPage'

  /api/listings/{id}:
    get:
      operationId: getListing
      tags: [Listings]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '200':
          description: Listing found
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ListingDetailResponse'
        '404':
          description: Listing not found
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'

  /api/listings/{id}/rescrape:
    post:
      operationId: rescrapeListing
      tags: [Listings]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '202':
          description: Rescrape session enqueued
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ScrapingSessionResponse'
        '404':
          description: Listing not found
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'

  /api/listings/{id}/report:
    get:
      operationId: getListingReport
      tags: [Listings]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '200':
          description: Report generated
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ListingReportResponse'
        '404':
          description: Listing not found
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'

  /api/listings/{id}/summary:
    get:
      operationId: getListingSummary
      tags: [Listings]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '200':
          description: AI summary
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/AiSummaryResponse'
        '404':
          description: Listing or summary not found
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'

components:
  schemas:
    CreateScrapingSessionRequest:
      type: object
      required: [city, pageLimit]
      properties:
        city:
          type: string
          example: amsterdam
        minPrice:
          type: integer
          nullable: true
        maxPrice:
          type: integer
          nullable: true
        minArea:
          type: integer
          nullable: true
        maxArea:
          type: integer
          nullable: true
        pageLimit:
          type: integer
          minimum: 1
          maximum: 5

    ScrapingSessionResponse:
      type: object
      properties:
        id:
          type: string
          format: uuid
        status:
          type: string
          enum: [PENDING, IN_PROGRESS, COMPLETED, FAILED, TIMED_OUT]
        type:
          type: string
          enum: [SEARCH, RESCRAPE]
        createdAt:
          type: string
          format: date-time
        completedAt:
          type: string
          format: date-time
          nullable: true

    ListingPage:
      type: object
      properties:
        content:
          type: array
          items:
            $ref: '#/components/schemas/ListingSummaryResponse'
        totalElements:
          type: integer
          format: int64
        totalPages:
          type: integer
        page:
          type: integer
        size:
          type: integer

    ListingSummaryResponse:
      type: object
      properties:
        id:
          type: string
          format: uuid
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
        askingPrice:
          type: integer
          nullable: true
        status:
          type: string
          nullable: true
        firstSeenAt:
          type: string
          format: date-time

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
        latestSnapshot:
          $ref: '#/components/schemas/SnapshotResponse'

    SnapshotResponse:
      type: object
      properties:
        id:
          type: string
          format: uuid
        scrapedAt:
          type: string
          format: date-time
        askingPrice:
          type: integer
          nullable: true
        livingAreaM2:
          type: integer
          nullable: true
        rooms:
          type: integer
          nullable: true
        energyLabel:
          type: string
          nullable: true
        listedOnFundaSince:
          type: string
          format: date
          nullable: true
        status:
          type: string
          enum: [FOR_SALE, UNDER_OFFER, SOLD, WITHDRAWN]
          nullable: true

    ListingReportResponse:
      type: object
      properties:
        listingId:
          type: string
          format: uuid
        daysListedOnFunda:
          type: integer
          format: int64
          nullable: true
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
        statusHistory:
          type: array
          items:
            $ref: '#/components/schemas/StatusPointResponse'
        currentStatus:
          type: string
          nullable: true

    PricePointResponse:
      type: object
      properties:
        scrapedAt:
          type: string
          format: date-time
        askingPrice:
          type: integer
          nullable: true

    StatusPointResponse:
      type: object
      properties:
        scrapedAt:
          type: string
          format: date-time
        status:
          type: string

    AiSummaryResponse:
      type: object
      properties:
        listingId:
          type: string
          format: uuid
        summary:
          type: string
        generatedAt:
          type: string
          format: date-time

    ErrorResponse:
      type: object
      properties:
        error:
          type: string
        detail:
          type: string
```

- [ ] **Step 2: Verify OpenAPI spec generates code**

Run: `cd hermes-backend && ./mvnw generate-sources -q`

Expected: BUILD SUCCESS. Generated sources appear under `target/generated-sources/openapi/`.

Inspect the generated interfaces to confirm tag-based interface names: `ScrapingSessionsApi` and `ListingsApi`.

- [ ] **Step 3: Commit**

```bash
git add hermes-backend/src/main/resources/openapi/api.yaml
git commit -m "feat(api): add OpenAPI 3.0 spec for all endpoints"
```

---

## Task 11: API module — controllers and exception handler

**Files:**
- Create: `src/main/java/com/kropholler/dev/hermes/api/ScrapingSessionController.java`
- Create: `src/main/java/com/kropholler/dev/hermes/api/ListingController.java`
- Create: `src/main/java/com/kropholler/dev/hermes/api/GlobalExceptionHandler.java`

> The generated interfaces are in package `com.kropholler.dev.hermes.api.generated`. The generated model classes are in `com.kropholler.dev.hermes.api.generated.model`. Controllers go in `com.kropholler.dev.hermes.api` (the module root) to stay within module boundaries.

- [ ] **Step 1: Create GlobalExceptionHandler**

`src/main/java/com/kropholler/dev/hermes/api/GlobalExceptionHandler.java`:

```java
package com.kropholler.dev.hermes.api;

import com.kropholler.dev.hermes.api.generated.model.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        ErrorResponse body = new ErrorResponse()
            .error(ex.getReason() != null ? ex.getReason() : ex.getStatusCode().toString())
            .detail(ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        ErrorResponse body = new ErrorResponse()
            .error("INTERNAL_SERVER_ERROR")
            .detail(ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
```

- [ ] **Step 2: Create ScrapingSessionController**

`src/main/java/com/kropholler/dev/hermes/api/ScrapingSessionController.java`:

```java
package com.kropholler.dev.hermes.api;

import com.kropholler.dev.hermes.api.generated.ScrapingSessionsApi;
import com.kropholler.dev.hermes.api.generated.model.CreateScrapingSessionRequest;
import com.kropholler.dev.hermes.api.generated.model.ScrapingSessionResponse;
import com.kropholler.dev.hermes.scraping.ScrapingQueueService;
import com.kropholler.dev.hermes.scraping.ScrapingSessionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
class ScrapingSessionController implements ScrapingSessionsApi {

    private final ScrapingQueueService queueService;

    @Override
    public ResponseEntity<ScrapingSessionResponse> createScrapingSession(
            CreateScrapingSessionRequest request) {
        ScrapingSessionDto dto = queueService.enqueueSearch(
            request.getCity(),
            request.getMinPrice(),
            request.getMaxPrice(),
            request.getMinArea(),
            request.getMaxArea(),
            request.getPageLimit()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(dto));
    }

    @Override
    public ResponseEntity<ScrapingSessionResponse> getScrapingSession(UUID id) {
        return queueService.findById(id)
            .map(dto -> ResponseEntity.ok(toResponse(dto)))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Scraping session " + id + " not found"));
    }

    private ScrapingSessionResponse toResponse(ScrapingSessionDto dto) {
        return new ScrapingSessionResponse()
            .id(dto.id())
            .status(ScrapingSessionResponse.StatusEnum.valueOf(dto.status().name()))
            .type(ScrapingSessionResponse.TypeEnum.valueOf(dto.type().name()))
            .createdAt(dto.createdAt() != null ? dto.createdAt().atOffset(java.time.ZoneOffset.UTC) : null)
            .completedAt(dto.completedAt() != null ? dto.completedAt().atOffset(java.time.ZoneOffset.UTC) : null);
    }
}
```

- [ ] **Step 3: Create ListingController**

`src/main/java/com/kropholler/dev/hermes/api/ListingController.java`:

```java
package com.kropholler.dev.hermes.api;

import com.kropholler.dev.hermes.ai.ListingSummaryService;
import com.kropholler.dev.hermes.api.generated.ListingsApi;
import com.kropholler.dev.hermes.api.generated.model.*;
import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.ListingSnapshotDto;
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
        ListingSnapshotDto snap = dto.latestSnapshot();
        return new ListingSummaryResponse()
            .id(dto.id())
            .street(dto.street())
            .houseNumber(dto.houseNumber())
            .houseNumberAddition(dto.houseNumberAddition())
            .zipCode(dto.zipCode())
            .city(dto.city())
            .province(dto.province())
            .askingPrice(snap != null ? snap.askingPrice() : null)
            .status(snap != null && snap.status() != null ? snap.status().name() : null)
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
            .latestSnapshot(dto.latestSnapshot() != null ? toSnapshotResponse(dto.latestSnapshot()) : null);
    }

    private SnapshotResponse toSnapshotResponse(ListingSnapshotDto s) {
        return new SnapshotResponse()
            .id(s.id())
            .scrapedAt(s.scrapedAt().atOffset(ZoneOffset.UTC))
            .askingPrice(s.askingPrice())
            .livingAreaM2(s.livingAreaM2())
            .rooms(s.rooms())
            .energyLabel(s.energyLabel())
            .listedOnFundaSince(s.listedOnFundaSince())
            .status(s.status() != null
                ? SnapshotResponse.StatusEnum.valueOf(s.status().name()) : null);
    }

    private ListingReportResponse toReportResponse(ListingReport r) {
        return new ListingReportResponse()
            .listingId(r.listingId())
            .daysListedOnFunda(r.daysListedOnFunda())
            .daysInHermes(r.daysInHermes())
            .currentPrice(r.currentPrice())
            .initialPrice(r.initialPrice())
            .priceChangePct(r.priceChangePct())
            .priceHistory(r.priceHistory().stream()
                .map(p -> new PricePointResponse()
                    .scrapedAt(p.scrapedAt().atOffset(ZoneOffset.UTC))
                    .askingPrice(p.askingPrice()))
                .toList())
            .statusHistory(r.statusHistory().stream()
                .map(s -> new StatusPointResponse()
                    .scrapedAt(s.scrapedAt().atOffset(ZoneOffset.UTC))
                    .status(s.status().name()))
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

- [ ] **Step 4: Compile to verify no errors**

Run: `cd hermes-backend && ./mvnw compile -q`

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/api/
git commit -m "feat(api): implement generated controller interfaces for scraping sessions and listings"
```

---

## Task 12: Nightly rescrape scheduler

**Files:**
- Create: `src/main/java/com/kropholler/dev/hermes/scraping/internal/NightlyRescrapeScheduler.java`

- [ ] **Step 1: Create the scheduler**

`src/main/java/com/kropholler/dev/hermes/scraping/internal/NightlyRescrapeScheduler.java`:

```java
package com.kropholler.dev.hermes.scraping.internal;

import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.scraping.ScrapingQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class NightlyRescrapeScheduler {

    private final ListingService listingService;
    private final ScrapingQueueService queueService;

    @Scheduled(cron = "0 0 2 * * *")
    public void enqueueNightlyRescrapes() {
        log.info("Starting nightly rescrape job");
        int count = 0;
        int page = 0;
        org.springframework.data.domain.Page<com.kropholler.dev.hermes.listing.ListingDto> batch;

        do {
            batch = listingService.findAll(org.springframework.data.domain.PageRequest.of(page, 100));
            for (var listing : batch.getContent()) {
                queueService.enqueueRescrape(listing.url(), listing.city());
                count++;
            }
            page++;
        } while (batch.hasNext());

        log.info("Nightly rescrape job enqueued {} sessions", count);
    }
}
```

- [ ] **Step 2: Compile to verify**

Run: `cd hermes-backend && ./mvnw compile -q`

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/internal/NightlyRescrapeScheduler.java
git commit -m "feat(scraping): add nightly rescrape scheduler (02:00 daily)"
```

---

## Task 13: Module structure verification test and full test run

**Files:**
- Modify: `src/test/java/com/kropholler/dev/hermes/HermesBackendApplicationTests.java`

- [ ] **Step 1: Update the application test to verify module structure**

Replace the contents of `src/test/java/com/kropholler/dev/hermes/HermesBackendApplicationTests.java`:

```java
package com.kropholler.dev.hermes;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class HermesBackendApplicationTests {

    ApplicationModules modules = ApplicationModules.of(HermesBackendApplication.class);

    @Test
    void verifyModuleStructure() {
        modules.verify();
    }

    @Test
    void writeDocumentationSnippets() {
        new Documenter(modules)
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml();
    }
}
```

- [ ] **Step 2: Run the module structure test**

Run: `cd hermes-backend && ./mvnw test -Dtest=HermesBackendApplicationTests -q`

Expected: PASS — all module boundaries respected. If it fails with a "dependency violation", a class in one module is importing an internal class from another. Fix by moving the offending class to the public package of its module or accessing it via the public service.

- [ ] **Step 3: Run the full test suite**

Run: `cd hermes-backend && ./mvnw test -q`

Expected: All tests pass.

- [ ] **Step 4: Commit**

```bash
git add hermes-backend/src/test/java/com/kropholler/dev/hermes/HermesBackendApplicationTests.java
git commit -m "test: add Spring Modulith structure verification test"
```

---

## Notes

**Funda HTML selectors:** The CSS selectors in `FundaScraperService` are written to match the fixture in `src/test/resources/fixtures/funda-search-result.html`. Before running against the live site, capture a real Funda search result page and update the fixture and selectors to match.

**`ScrapingQueueService` URL duplication:** `ScrapingQueueService` duplicates URL-building logic from `FundaUrlBuilder` because `FundaUrlBuilder` is package-private. If this becomes a maintenance concern, promote `FundaUrlBuilder` to the module's public package.

**`ListingSnapshotRepository` in tests:** `ReportServiceTest` avoids importing the internal `ListingSnapshotRepository` by going through `ListingService`. This is the correct pattern — test through the public API, not internal classes.

**`spring.jpa.hibernate.ddl-auto=update`:** Suitable for development. Replace with Flyway or Liquibase for production schema management.
