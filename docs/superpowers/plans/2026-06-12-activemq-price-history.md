# ActiveMQ Price History Queue Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

## Progress

| Task | Status | Commit |
|------|--------|--------|
| Task 1: Add infrastructure — dependencies, broker, properties | ✅ Done | 72b8a0d |
| Task 2: Create `FetchPriceHistoryCommand` | ✅ Done | b137503 |
| Task 3: Create `PriceHistoryConsumer` with test | ✅ Done | 93bc4c9 |
| Task 4: Refactor `PriceHistoryService` — remove event listener, add JMS send | ✅ Done | a77116b |
| Task 5: Refactor `ListingPersistenceService` and remove `ListingCreated` | ✅ Done | 982de8f |

**Goal:** Replace the `@ApplicationModuleListener` approach for price history fetching with an ActiveMQ Artemis queue that limits processing to 5 concurrent consumers and a maximum of 10 items per minute, eliminating HikariCP connection pool exhaustion.

**Architecture:** `ListingPersistenceService` sends a `FetchPriceHistoryCommand` JMS message to a `price.history.fetch` queue when a new listing is created (replacing the `ListingCreated` Spring event). A new `PriceHistoryConsumer` with `concurrency=5` and a shared Guava `RateLimiter` (10/min) reads from the queue and calls `PriceHistoryService.fetchAndStore`. `PriceHistoryService.refreshAll` is also updated to enqueue JMS messages instead of calling `fetchAndStore` inline.

**Tech Stack:** Spring Boot 4.0.6, Spring JMS, Apache ActiveMQ Artemis, Guava RateLimiter, Mockito

---

## File Structure

**Create:**
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/FetchPriceHistoryCommand.java` — Serializable JMS message record
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/PriceHistoryConsumer.java` — `@JmsListener` with rate limiting
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/internal/PriceHistoryConsumerTest.java` — unit test

**Modify:**
- `hermes-backend/pom.xml` — add `spring-boot-starter-artemis` + `guava`
- `hermes-backend/docker-compose.yml` — add `activemq` service + backend env vars
- `hermes-backend/src/main/resources/application.properties` — Artemis + JMS concurrency config
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/PriceHistoryService.java` — remove `onListingCreated`, update `refreshAll`, make `fetchAndStore` public, inject `JmsTemplate`
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceService.java` — replace `ListingCreated` event with JMS send, remove `ApplicationEventPublisher`
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/PriceHistoryServiceTest.java` — remove `onListingCreated` test, update `refreshAll` test
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceServiceTest.java` — swap `ApplicationEventPublisher` mock for `JmsTemplate`

**Delete:**
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingCreated.java` — no longer published or consumed

---

### Task 1: Add infrastructure — dependencies, broker, properties

**Files:**
- Modify: `hermes-backend/pom.xml`
- Modify: `hermes-backend/docker-compose.yml`
- Modify: `hermes-backend/src/main/resources/application.properties`

- [ ] **Step 1: Add Maven dependencies**

In `hermes-backend/pom.xml`, inside `<dependencies>`, add after the `spring-modulith-observability` dependency:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-artemis</artifactId>
</dependency>
<dependency>
    <groupId>com.google.guava</groupId>
    <artifactId>guava</artifactId>
    <version>33.4.8-jre</version>
</dependency>
```

- [ ] **Step 2: Add ActiveMQ Artemis service to docker-compose**

In `hermes-backend/docker-compose.yml`, add the `activemq` service and wire the `backend` service to it. The full updated file:

```yaml
services:
  backend:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/mydatabase
      SPRING_DATASOURCE_USERNAME: myuser
      SPRING_DATASOURCE_PASSWORD: secret
      SPRING_AI_OLLAMA_BASE_URL: http://ollama:11434
      SPRING_DOCKER_COMPOSE_ENABLED: "false"
      MANAGEMENT_OTLP_METRICS_EXPORT_URL: http://grafana-lgtm:4318/v1/metrics
      MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT: http://grafana-lgtm:4318/v1/traces
      MANAGEMENT_OPENTELEMETRY_LOGGING_EXPORT_OTLP_ENDPOINT: http://grafana-lgtm:4318/v1/logs
      FUNDA_PROXY_URL: http://funda-proxy:8000
      SPRING_ARTEMIS_BROKER_URL: tcp://activemq:61616
      SPRING_ARTEMIS_USER: artemis
      SPRING_ARTEMIS_PASSWORD: artemis
    depends_on:
      postgres:
        condition: service_healthy
      ollama:
        condition: service_started
      funda-proxy:
        condition: service_started
      grafana-lgtm:
        condition: service_started
      activemq:
        condition: service_healthy
  activemq:
    image: apache/activemq-artemis:latest
    ports:
      - '61616:61616'
      - '8161:8161'
    environment:
      ARTEMIS_USER: artemis
      ARTEMIS_PASSWORD: artemis
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8161/console/ || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 10
  funda-proxy:
    build: ../funda-proxy
    ports:
      - "8001:8000"
    environment:
      OTEL_SERVICE_NAME: funda-proxy
      OTEL_EXPORTER_OTLP_ENDPOINT: http://grafana-lgtm:4318
    depends_on:
      grafana-lgtm:
        condition: service_started
  grafana-lgtm:
    image: 'grafana/otel-lgtm:latest'
    ports:
      - '3000:3000'
      - '4317'
      - '4318'
    volumes:
      - ./grafana/dashboards.yaml:/otel-lgtm/grafana/conf/provisioning/dashboards/hermes.yaml:ro
      - ./grafana/dashboards:/var/lib/grafana/dashboards/hermes:ro
  ollama:
    image: 'ollama/ollama:latest'
    ports:
      - '11434'
    volumes:
      - ollama_data:/root/.ollama
    entrypoint: ["/bin/sh", "-c", "ollama serve & sleep 5 && ollama pull qwen3.5:0.8b && wait"]
  postgres:
    image: 'postgres:latest'
    environment:
      - 'POSTGRES_DB=mydatabase'
      - 'POSTGRES_PASSWORD=secret'
      - 'POSTGRES_USER=myuser'
    ports:
      - '5432'
    volumes:
      - postgres_data:/var/lib/postgresql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U myuser -d mydatabase"]
      interval: 5s
      timeout: 5s
      retries: 10

volumes:
  ollama_data:
  postgres_data:
```

- [ ] **Step 3: Add Artemis + JMS properties to application.properties**

In `hermes-backend/src/main/resources/application.properties`, add after the `# Async executor` block:

```properties
# ActiveMQ Artemis
spring.artemis.broker-url=tcp://localhost:61616
spring.artemis.user=artemis
spring.artemis.password=artemis

# JMS listener
spring.jms.listener.concurrency=5
spring.jms.listener.max-concurrency=5
```

- [ ] **Step 4: Verify compilation**

Run:
```
cd hermes-backend && mvn compile -q
```
Expected: BUILD SUCCESS with no errors.

- [ ] **Step 5: Commit**

```
git add hermes-backend/pom.xml hermes-backend/docker-compose.yml hermes-backend/src/main/resources/application.properties
git commit -m "feat(hermes-backend): add ActiveMQ Artemis dependency and infrastructure"
```

---

### Task 2: Create `FetchPriceHistoryCommand`

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/FetchPriceHistoryCommand.java`

No test needed — this is a pure data record with no logic.

- [ ] **Step 1: Create the record**

`public` is required because `PriceHistoryService` (in the parent `listing` package) instantiates this record in `refreshAll`. Spring Modulith still prevents external modules from importing it.

```java
package com.kropholler.dev.hermes.listing.internal;

import java.io.Serializable;
import java.util.UUID;

public record FetchPriceHistoryCommand(UUID listingId, String fundaId) implements Serializable {}
```

- [ ] **Step 2: Verify compilation**

```
cd hermes-backend && mvn compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/FetchPriceHistoryCommand.java
git commit -m "feat(hermes-backend): add FetchPriceHistoryCommand JMS message record"
```

---

### Task 3: Create `PriceHistoryConsumer` with test

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/PriceHistoryConsumer.java`
- Create: `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/internal/PriceHistoryConsumerTest.java`

**Context:** `PriceHistoryService` is in the `listing` package (one level up). `PriceHistoryConsumer` is in `listing.internal`. The consumer calls `priceHistoryService.fetchAndStore(listingId, fundaId)` — this method will be made `public` in Task 4. A shared static `RateLimiter` (Guava) limits all 5 consumer threads collectively to 10 fetches per minute. After a successful fetch, the consumer publishes a `PriceHistoryUpdated` event.

- [ ] **Step 1: Write the failing test**

```java
package com.kropholler.dev.hermes.listing.internal;

import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryService;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryUpdated;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PriceHistoryConsumerTest {

    @Mock private PriceHistoryService priceHistoryService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PriceHistoryConsumer consumer;

    @Test
    void onMessage_callsFetchAndStoreAndPublishesEvent() {
        UUID listingId = UUID.randomUUID();
        FetchPriceHistoryCommand command = new FetchPriceHistoryCommand(listingId, "12345678");

        consumer.onMessage(command);

        verify(priceHistoryService).fetchAndStore(listingId, "12345678");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(PriceHistoryUpdated.class);
        assertThat(((PriceHistoryUpdated) captor.getValue()).listingIds()).containsExactly(listingId);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```
cd hermes-backend && mvn test -pl . -Dtest=PriceHistoryConsumerTest -q
```
Expected: FAIL — `PriceHistoryConsumer` does not exist yet.

- [ ] **Step 3: Implement `PriceHistoryConsumer`**

```java
package com.kropholler.dev.hermes.listing.internal;

import com.google.common.util.concurrent.RateLimiter;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryService;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryUpdated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
class PriceHistoryConsumer {

    // 10 fetches per minute shared across all 5 consumer threads
    @SuppressWarnings("UnstableApiUsage")
    private static final RateLimiter RATE_LIMITER = RateLimiter.create(10.0 / 60.0);

    private final PriceHistoryService priceHistoryService;
    private final ApplicationEventPublisher eventPublisher;

    @JmsListener(destination = "price.history.fetch")
    public void onMessage(FetchPriceHistoryCommand command) {
        RATE_LIMITER.acquire();
        log.debug("Fetching price history for listing {}", command.listingId());
        priceHistoryService.fetchAndStore(command.listingId(), command.fundaId());
        eventPublisher.publishEvent(new PriceHistoryUpdated(List.of(command.listingId())));
    }
}
```

Note: the `@SuppressWarnings("UnstableApiUsage")` suppresses the Guava beta-API warning. `RateLimiter` is stable in practice despite the annotation.

- [ ] **Step 4: Run the test to verify it passes**

```
cd hermes-backend && mvn test -pl . -Dtest=PriceHistoryConsumerTest -q
```
Expected: BUILD SUCCESS, 1 test passed.

- [ ] **Step 5: Commit**

```
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/PriceHistoryConsumer.java
git add hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/internal/PriceHistoryConsumerTest.java
git commit -m "feat(hermes-backend): add PriceHistoryConsumer with 5-concurrency JMS listener and 10/min rate limit"
```

---

### Task 4: Refactor `PriceHistoryService` — remove event listener, add JMS send

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/PriceHistoryService.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/PriceHistoryServiceTest.java`

**Context:** Remove `onListingCreated` (price history is now triggered via JMS, not Spring events). Change `refreshAll` to send `FetchPriceHistoryCommand` messages to the queue instead of calling `fetchAndStore` inline. Make `fetchAndStore` `public` so `PriceHistoryConsumer` (in `listing.internal`) can call it. Add `JmsTemplate` injection.

- [ ] **Step 1: Update `PriceHistoryServiceTest`**

Replace the entire file content:

```java
package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.async.command.FetchPriceHistoryCommand;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jms.core.JmsTemplate;

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
    @Mock private JmsTemplate jmsTemplate;

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
    void refreshAll_sendsJmsMessagePerListing() {
        Listing listing = new Listing();
        listing.setFundaId("12345678");
        UUID listingId = UUID.randomUUID();
        listing.setId(listingId);

        when(listingRepository.findAllByDeletedAtIsNull(any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(listing)));

        service.refreshAll();

        verify(jmsTemplate).convertAndSend(eq("price.history.fetch"),
            argThat(cmd -> cmd instanceof FetchPriceHistoryCommand c
                && listingId.equals(c.listingId())
                && "12345678".equals(c.fundaId())));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```
cd hermes-backend && mvn test -pl . -Dtest=PriceHistoryServiceTest -q
```
Expected: FAIL — `PriceHistoryService` still has the old signature.

- [ ] **Step 3: Rewrite `PriceHistoryService`**

Replace the entire file:

```java
package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.async.command.FetchPriceHistoryCommand;
import com.kropholler.dev.hermes.listing.data.Listing;
import com.kropholler.dev.hermes.listing.data.ListingRepository;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryEntry;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryEntryRepository;
import com.kropholler.dev.hermes.scraping.funda.FundaProxyFacade;
import com.kropholler.dev.hermes.scraping.funda.RawPriceChange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceHistoryService {

    private final ListingRepository listingRepository;
    private final PriceHistoryEntryRepository priceHistoryRepository;
    private final FundaProxyFacade proxyFacade;
    private final JmsTemplate jmsTemplate;

    public void refreshAll() {
        int page = 0;
        Page<Listing> batch;
        do {
            batch = listingRepository.findAllByDeletedAtIsNull(PageRequest.of(page, 100));
            for (Listing listing : batch.getContent()) {
                jmsTemplate.convertAndSend("price.history.fetch",
                    new FetchPriceHistoryCommand(listing.getId(), listing.getFundaId()));
            }
            page++;
        } while (batch.hasNext());
    }

    @Transactional
    public void fetchAndStore(UUID listingId, String fundaId) {
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

- [ ] **Step 4: Run all listing tests**

```
cd hermes-backend && mvn test -pl . -Dtest="PriceHistoryServiceTest,PriceHistoryConsumerTest" -q
```
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 5: Commit**

```
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/PriceHistoryService.java
git add hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/PriceHistoryServiceTest.java
git commit -m "refactor(hermes-backend): replace ApplicationModuleListener with JMS send in PriceHistoryService"
```

---

### Task 5: Refactor `ListingPersistenceService` and remove `ListingCreated`

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceService.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceServiceTest.java`
- Delete: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingCreated.java`

**Context:** `ListingPersistenceService` currently publishes a `ListingCreated` Spring Modulith event when a new listing is created, which triggers `PriceHistoryService.onListingCreated` (now removed). Replace this with a direct `JmsTemplate.convertAndSend` call to the `price.history.fetch` queue. Remove `ApplicationEventPublisher` since it's no longer used.

- [ ] **Step 1: Update `ListingPersistenceServiceTest`**

Replace the entire file:

```java
package com.kropholler.dev.hermes.listing.internal;

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

    @Test
    void newListing_setsStatusAndSendsFetchCommand() {
        RawListing raw = new RawListing(
            "12345678", "https://www.funda.nl/koop/amsterdam/huis-12345678/",
            "Teststraat", "10", null, "1234AB", "amsterdam", "Noord-Holland",
            450000, "FOR_SALE"
        );
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

        verify(jmsTemplate).convertAndSend(eq("price.history.fetch"),
            argThat(cmd -> cmd instanceof FetchPriceHistoryCommand c
                && "12345678".equals(c.fundaId())));
    }

    @Test
    void existingListing_updatesStatusAndDoesNotSendFetchCommand() {
        Listing existing = new Listing();
        existing.setFundaId("12345678");

        RawListing raw = new RawListing(
            "12345678", "https://www.funda.nl/koop/amsterdam/huis-12345678/",
            "Teststraat", "10", null, "1234AB", "amsterdam", "Noord-Holland",
            460000, "UNDER_OFFER"
        );
        ScrapingSessionCompleted event = new ScrapingSessionCompleted(UUID.randomUUID(), List.of(raw));

        when(listingRepository.findByFundaId("12345678")).thenReturn(Optional.of(existing));
        when(listingRepository.save(any())).thenReturn(existing);

        service.onScrapingSessionCompleted(event);

        assertThat(existing.getStatus()).isEqualTo(ListingStatus.UNDER_OFFER);
        verify(jmsTemplate, never()).convertAndSend(any(String.class), any(Object.class));
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

- [ ] **Step 2: Run the test to verify it fails**

```
cd hermes-backend && mvn test -pl . -Dtest=ListingPersistenceServiceTest -q
```
Expected: FAIL — `ListingPersistenceService` still uses `ApplicationEventPublisher`.

- [ ] **Step 3: Rewrite `ListingPersistenceService`**

Replace the entire file:

```java
package com.kropholler.dev.hermes.listing.internal;

import com.kropholler.dev.hermes.listing.ListingStatus;
import com.kropholler.dev.hermes.scraping.funda.ListingNotFound;
import com.kropholler.dev.hermes.scraping.funda.RawListing;
import com.kropholler.dev.hermes.scraping.ScrapingSessionCompleted;
import lombok.RequiredArgsConstructor;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ListingPersistenceService {

    private final ListingRepository listingRepository;
    private final JmsTemplate jmsTemplate;

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

            if (isNew) {
                jmsTemplate.convertAndSend("price.history.fetch",
                    new FetchPriceHistoryCommand(saved.getId(), saved.getFundaId()));
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

- [ ] **Step 4: Delete `ListingCreated.java`**

```
git rm hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingCreated.java
```

- [ ] **Step 5: Run all tests to verify everything compiles and passes**

```
cd hermes-backend && mvn test -q
```
Expected: BUILD SUCCESS, all tests pass. (If any test still references `ListingCreated`, fix that import too.)

- [ ] **Step 6: Commit**

```
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceService.java
git add hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceServiceTest.java
git commit -m "refactor(hermes-backend): replace ListingCreated event with JMS FetchPriceHistoryCommand in ListingPersistenceService"
```
