# Correlation IDs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Propagate `X-Correlation-ID` through every log line, async thread, downstream HTTP call, domain event, and error response in hermes-backend and funda-proxy.

**Architecture:** A `CorrelationIdFilter` reads or generates the ID on every HTTP request and writes it to MDC. An `MdcTaskDecorator` on the async executor snapshots and restores MDC across `@Async` thread boundaries. Domain event records carry the correlation ID as a field so `@ApplicationModuleListener` handlers can restore MDC when processing. A `RestClient` interceptor forwards the header to funda-proxy, which picks it up via a FastAPI middleware backed by a `contextvars.ContextVar`.

**Tech Stack:** Spring Boot 4.0.6 (Java 25), SLF4J MDC, `OncePerRequestFilter`, `AsyncConfigurer`, `ClientHttpRequestInterceptor`, FastAPI / Starlette `BaseHTTPMiddleware`, Python `contextvars`

---

## File Map

**New files — hermes-backend:**
- `src/main/java/com/kropholler/dev/hermes/config/CorrelationIdFilter.java` — reads/generates correlation ID, sets MDC and response header
- `src/main/java/com/kropholler/dev/hermes/config/MdcTaskDecorator.java` — snapshots/restores MDC across async thread boundaries
- `src/test/java/com/kropholler/dev/hermes/config/CorrelationIdFilterTest.java`
- `src/test/java/com/kropholler/dev/hermes/config/MdcTaskDecoratorTest.java`
- `src/test/java/com/kropholler/dev/hermes/scraping/internal/ScrapingWorkerTest.java`
- `src/test/java/com/kropholler/dev/hermes/scraping/internal/FundaProxyClientTest.java`
- `src/test/java/com/kropholler/dev/hermes/api/GlobalExceptionHandlerTest.java`

**New files — funda-proxy:**
- `funda-proxy/correlation.py` — `ContextVar` + `CorrelationIdMiddleware`
- `funda-proxy/tests/test_correlation.py`

**Modified files — hermes-backend:**
- `src/main/java/com/kropholler/dev/hermes/config/AsyncConfig.java` — implement `AsyncConfigurer`, apply decorator
- `src/main/java/com/kropholler/dev/hermes/scraping/ScrapingSessionCompleted.java` — add `correlationId` field
- `src/main/java/com/kropholler/dev/hermes/scraping/ScrapingSessionFailed.java` — add `correlationId` field
- `src/main/java/com/kropholler/dev/hermes/listing/ListingSnapshotsCreated.java` — add `correlationId` field
- `src/main/java/com/kropholler/dev/hermes/scraping/internal/ScrapingWorker.java` — pass MDC value when publishing events
- `src/main/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceService.java` — restore MDC in listener, chain correlationId to `ListingSnapshotsCreated`
- `src/main/java/com/kropholler/dev/hermes/ai/internal/ListingSummaryGenerationService.java` — restore MDC in listener
- `src/main/java/com/kropholler/dev/hermes/scraping/internal/FundaProxyClient.java` — add `X-Correlation-ID` interceptor
- `src/main/java/com/kropholler/dev/hermes/api/GlobalExceptionHandler.java` — include correlationId in error body
- `src/main/resources/openapi/api.yaml` — add `correlationId` to `ErrorResponse`
- `src/test/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceServiceTest.java` — fix broken constructors + add MDC tests

**Modified files — funda-proxy:**
- `funda-proxy/telemetry.py` — enrich JSON log records with `correlation_id`
- `funda-proxy/main.py` — register `CorrelationIdMiddleware`

---

## Task 1: CorrelationIdFilter

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/config/CorrelationIdFilter.java`
- Create: `hermes-backend/src/test/java/com/kropholler/dev/hermes/config/CorrelationIdFilterTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.kropholler.dev.hermes.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void cleanup() {
        MDC.clear();
    }

    @Test
    void usesProvidedCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", "my-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> capturedMdc = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> capturedMdc.set(MDC.get("correlationId")));

        assertThat(capturedMdc.get()).isEqualTo("my-id");
        assertThat(response.getHeader("X-Correlation-ID")).isEqualTo("my-id");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void generatesCorrelationIdWhenAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> capturedMdc = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> capturedMdc.set(MDC.get("correlationId")));

        assertThat(capturedMdc.get()).isNotNull().isNotEmpty();
        assertThat(response.getHeader("X-Correlation-ID")).isEqualTo(capturedMdc.get());
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void clearsMdcEvenOnFilterChainException() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", "ex-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, (req, res) -> { throw new RuntimeException("boom"); });
        } catch (RuntimeException ignored) {}

        assertThat(MDC.get("correlationId")).isNull();
    }
}
```

- [ ] **Step 2: Run the test to see it fail**

```
cd hermes-backend
./mvnw test -pl . -Dtest=CorrelationIdFilterTest -q
```

Expected: compilation error — `CorrelationIdFilter` does not exist.

- [ ] **Step 3: Implement `CorrelationIdFilter`**

```java
package com.kropholler.dev.hermes.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Correlation-ID";
    static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String id = request.getHeader(HEADER);
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
```

- [ ] **Step 4: Run tests and verify they pass**

```
cd hermes-backend
./mvnw test -pl . -Dtest=CorrelationIdFilterTest -q
```

Expected: BUILD SUCCESS, 3 tests passed.

- [ ] **Step 5: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/config/CorrelationIdFilter.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/config/CorrelationIdFilterTest.java
git commit -m "feat(hermes-backend): add CorrelationIdFilter"
```

---

## Task 2: MdcTaskDecorator + AsyncConfig

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/config/MdcTaskDecorator.java`
- Create: `hermes-backend/src/test/java/com/kropholler/dev/hermes/config/MdcTaskDecoratorTest.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/config/AsyncConfig.java`

- [ ] **Step 1: Write the failing test**

```java
package com.kropholler.dev.hermes.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @AfterEach
    void cleanup() {
        MDC.clear();
    }

    @Test
    void propagatesMdcSnapshotIntoDecoratedRunnable() {
        MDC.put("correlationId", "ctx-id");
        AtomicReference<String> captured = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> captured.set(MDC.get("correlationId")));
        MDC.clear(); // simulate thread switch

        decorated.run();

        assertThat(captured.get()).isEqualTo("ctx-id");
    }

    @Test
    void clearsMdcAfterDecoratedRunnableCompletes() {
        MDC.put("correlationId", "ctx-id");
        Runnable decorated = decorator.decorate(() -> {});
        MDC.clear();

        decorated.run();

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void worksWhenNoMdcAtSubmission() {
        MDC.clear();
        AtomicReference<String> captured = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> captured.set(MDC.get("correlationId")));
        decorated.run();

        assertThat(captured.get()).isNull();
        assertThat(MDC.get("correlationId")).isNull();
    }
}
```

- [ ] **Step 2: Run the test to see it fail**

```
cd hermes-backend
./mvnw test -pl . -Dtest=MdcTaskDecoratorTest -q
```

Expected: compilation error — `MdcTaskDecorator` does not exist.

- [ ] **Step 3: Implement `MdcTaskDecorator`**

```java
package com.kropholler.dev.hermes.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> snapshot = MDC.getCopyOfContextMap();
        return () -> {
            if (snapshot != null) {
                MDC.setContextMap(snapshot);
            }
            try {
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
```

- [ ] **Step 4: Run tests and verify they pass**

```
cd hermes-backend
./mvnw test -pl . -Dtest=MdcTaskDecoratorTest -q
```

Expected: BUILD SUCCESS, 3 tests passed.

- [ ] **Step 5: Update `AsyncConfig` to wire the decorator**

Replace the entire contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/config/AsyncConfig.java`:

```java
package com.kropholler.dev.hermes.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setThreadNamePrefix("hermes-async-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
        return executor;
    }
}
```

- [ ] **Step 6: Compile to verify no breakage**

```
cd hermes-backend
./mvnw compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/config/MdcTaskDecorator.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/config/AsyncConfig.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/config/MdcTaskDecoratorTest.java
git commit -m "feat(hermes-backend): add MdcTaskDecorator and wire into AsyncConfig"
```

---

## Task 3: Domain event records + all publication sites

Event records need a new `correlationId` field. This is a breaking change: all existing instantiation sites must be updated in the same task so the code compiles.

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/ScrapingSessionCompleted.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/ScrapingSessionFailed.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingSnapshotsCreated.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/internal/ScrapingWorker.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceService.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceServiceTest.java`
- Create: `hermes-backend/src/test/java/com/kropholler/dev/hermes/scraping/internal/ScrapingWorkerTest.java`

- [ ] **Step 1: Write the failing test for ScrapingWorker**

```java
package com.kropholler.dev.hermes.scraping.internal;

import com.kropholler.dev.hermes.scraping.ScrapingSessionCompleted;
import com.kropholler.dev.hermes.scraping.ScrapingSessionFailed;
import com.kropholler.dev.hermes.scraping.ScrapingSessionStatus;
import com.kropholler.dev.hermes.scraping.ScrapingSessionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScrapingWorkerTest {

    @Mock ScrapingSessionRepository sessionRepository;
    @Mock FundaProxyClient proxyClient;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks ScrapingWorker worker;

    @AfterEach
    void cleanup() {
        MDC.clear();
    }

    @Test
    void process_publishesCompletedEventWithCorrelationIdFromMdc() {
        MDC.put("correlationId", "test-corr");
        ScrapingSession session = new ScrapingSession();
        session.setId(UUID.randomUUID());
        session.setType(ScrapingSessionType.SEARCH);
        session.setCity("amsterdam");
        session.setPageLimit(1);
        when(sessionRepository.save(any())).thenReturn(session);
        when(proxyClient.search(any(), any(), any(), any(), any(), anyInt())).thenReturn(List.of());

        worker.process(session);

        ArgumentCaptor<ScrapingSessionCompleted> captor = ArgumentCaptor.forClass(ScrapingSessionCompleted.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().correlationId()).isEqualTo("test-corr");
    }

    @Test
    void process_publishesFailedEventWithCorrelationIdFromMdc() {
        MDC.put("correlationId", "fail-corr");
        ScrapingSession session = new ScrapingSession();
        session.setId(UUID.randomUUID());
        session.setType(ScrapingSessionType.SEARCH);
        session.setCity("amsterdam");
        session.setPageLimit(1);
        when(sessionRepository.save(any())).thenReturn(session);
        when(proxyClient.search(any(), any(), any(), any(), any(), anyInt()))
            .thenThrow(new RuntimeException("network error"));

        worker.process(session);

        ArgumentCaptor<ScrapingSessionFailed> captor = ArgumentCaptor.forClass(ScrapingSessionFailed.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().correlationId()).isEqualTo("fail-corr");
    }
}
```

- [ ] **Step 2: Run to confirm it fails**

```
cd hermes-backend
./mvnw test -pl . -Dtest=ScrapingWorkerTest -q
```

Expected: compilation error — `correlationId()` does not exist on `ScrapingSessionCompleted`.

- [ ] **Step 3: Update all three event records**

`hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/ScrapingSessionCompleted.java`:
```java
package com.kropholler.dev.hermes.scraping;

import java.util.List;
import java.util.UUID;

public record ScrapingSessionCompleted(UUID sessionId, List<RawListing> listings, String correlationId) {}
```

`hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/ScrapingSessionFailed.java`:
```java
package com.kropholler.dev.hermes.scraping;

import java.util.UUID;

public record ScrapingSessionFailed(UUID sessionId, String reason, String correlationId) {}
```

`hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingSnapshotsCreated.java`:
```java
package com.kropholler.dev.hermes.listing;

import java.util.List;
import java.util.UUID;

public record ListingSnapshotsCreated(List<UUID> listingIds, String correlationId) {}
```

- [ ] **Step 4: Fix broken constructors in `ListingPersistenceServiceTest`**

The existing tests use the old 2-arg constructor for `ScrapingSessionCompleted`. Add `null` as the third argument in both test methods. Open `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceServiceTest.java` and change both occurrences:

```java
// Line 38 — was:
ScrapingSessionCompleted event = new ScrapingSessionCompleted(UUID.randomUUID(), List.of(raw));
// Change to:
ScrapingSessionCompleted event = new ScrapingSessionCompleted(UUID.randomUUID(), List.of(raw), null);
```

```java
// Line 65 — was:
ScrapingSessionCompleted event = new ScrapingSessionCompleted(UUID.randomUUID(), List.of(raw));
// Change to:
ScrapingSessionCompleted event = new ScrapingSessionCompleted(UUID.randomUUID(), List.of(raw), null);
```

- [ ] **Step 5: Update `ScrapingWorker` to pass MDC value when publishing**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/internal/ScrapingWorker.java`, add the MDC import and update both `publishEvent` calls:

Add import at the top of the file:
```java
import org.slf4j.MDC;
```

Change the completed event publication (was line 40):
```java
eventPublisher.publishEvent(new ScrapingSessionCompleted(session.getId(), listings, MDC.get("correlationId")));
```

Change the failed event publication (was line 46):
```java
eventPublisher.publishEvent(new ScrapingSessionFailed(session.getId(), e.getMessage(), MDC.get("correlationId")));
```

- [ ] **Step 6: Update `ListingPersistenceService` to chain correlationId forward**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceService.java`, change the `ListingSnapshotsCreated` publication (was line 45):

```java
eventPublisher.publishEvent(new ListingSnapshotsCreated(affectedListingIds, event.correlationId()));
```

- [ ] **Step 7: Run all tests to verify they compile and pass**

```
cd hermes-backend
./mvnw test -pl . -Dtest="ScrapingWorkerTest,ListingPersistenceServiceTest" -q
```

Expected: BUILD SUCCESS, 4 tests passed.

- [ ] **Step 8: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/ScrapingSessionCompleted.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/ScrapingSessionFailed.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingSnapshotsCreated.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/internal/ScrapingWorker.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceService.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceServiceTest.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/scraping/internal/ScrapingWorkerTest.java
git commit -m "feat(hermes-backend): propagate correlationId through domain events"
```

---

## Task 4: Listener MDC restore

`@ApplicationModuleListener` methods run on a managed thread where MDC is otherwise empty. Each listener must restore MDC from the event's `correlationId` field and clear it in a `finally` block.

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceService.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/internal/ListingSummaryGenerationService.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceServiceTest.java`

- [ ] **Step 1: Write failing tests for `ListingPersistenceService` MDC behaviour**

Add the following two tests to `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceServiceTest.java`.

Add imports at the top of the file:
```java
import org.junit.jupiter.api.AfterEach;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import java.util.concurrent.atomic.AtomicReference;
import static org.mockito.Mockito.verify;
```

Add tests inside the class:
```java
@AfterEach
void cleanupMdc() {
    MDC.clear();
}

@Test
void onScrapingCompleted_restoresMdcCorrelationIdDuringExecution() {
    AtomicReference<String> capturedMdc = new AtomicReference<>();
    Listing existingListing = new Listing();
    existingListing.setFundaId("12345678");

    when(listingRepository.findByFundaId("12345678")).thenAnswer(inv -> {
        capturedMdc.set(MDC.get("correlationId"));
        return Optional.of(existingListing);
    });
    when(listingRepository.save(any())).thenReturn(existingListing);
    when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    RawListing raw = new RawListing(
        "12345678", "https://f.nl/1", "Street", "1", null,
        "1234AB", "Amsterdam", "Noord-Holland", null, null, null, null, null, "FOR_SALE");
    ScrapingSessionCompleted event = new ScrapingSessionCompleted(
        UUID.randomUUID(), List.of(raw), "chain-corr");

    service.onScrapingSessionCompleted(event);

    assertThat(capturedMdc.get()).isEqualTo("chain-corr");
    assertThat(MDC.get("correlationId")).isNull();
}

@Test
void onScrapingCompleted_publishesListingSnapshotsCreatedWithCorrelationId() {
    Listing existingListing = new Listing();
    existingListing.setFundaId("12345678");
    when(listingRepository.findByFundaId("12345678")).thenReturn(Optional.of(existingListing));
    when(listingRepository.save(any())).thenReturn(existingListing);
    when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    RawListing raw = new RawListing(
        "12345678", "https://f.nl/1", "Street", "1", null,
        "1234AB", "Amsterdam", "Noord-Holland", null, null, null, null, null, "FOR_SALE");
    ScrapingSessionCompleted event = new ScrapingSessionCompleted(
        UUID.randomUUID(), List.of(raw), "chain-corr");

    service.onScrapingSessionCompleted(event);

    ArgumentCaptor<ListingSnapshotsCreated> captor = ArgumentCaptor.forClass(ListingSnapshotsCreated.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().correlationId()).isEqualTo("chain-corr");
}
```

- [ ] **Step 2: Run to confirm the new tests fail**

```
cd hermes-backend
./mvnw test -pl . -Dtest=ListingPersistenceServiceTest -q
```

Expected: 2 of the new tests fail — MDC is null during execution and correlationId on event is null.

- [ ] **Step 3: Update `ListingPersistenceService` to restore MDC**

Replace the `onScrapingSessionCompleted` method in `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceService.java`. Also add the MDC import.

Add import:
```java
import org.slf4j.MDC;
```

Replace the method:
```java
@ApplicationModuleListener
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void onScrapingSessionCompleted(ScrapingSessionCompleted event) {
    if (event.correlationId() != null) {
        MDC.put("correlationId", event.correlationId());
    }
    try {
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
            eventPublisher.publishEvent(new ListingSnapshotsCreated(affectedListingIds, event.correlationId()));
        }
    } finally {
        MDC.remove("correlationId");
    }
}
```

- [ ] **Step 4: Update `ListingSummaryGenerationService` to restore MDC**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/internal/ListingSummaryGenerationService.java`, add the MDC import and wrap the handler body:

Add import:
```java
import org.slf4j.MDC;
```

Replace the `onListingSnapshotsCreated` method:
```java
@ApplicationModuleListener
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void onListingSnapshotsCreated(ListingSnapshotsCreated event) {
    if (event.correlationId() != null) {
        MDC.put("correlationId", event.correlationId());
    }
    try {
        ChatClient chatClient = chatClientBuilder.build();

        for (UUID listingId : event.listingIds()) {
            listingService.findById(listingId).ifPresent(listing -> {
                String summary = generateSummary(chatClient, listing);
                upsertSummary(listingId, summary);
            });
        }
    } finally {
        MDC.remove("correlationId");
    }
}
```

- [ ] **Step 5: Run all tests to verify they pass**

```
cd hermes-backend
./mvnw test -pl . -Dtest=ListingPersistenceServiceTest -q
```

Expected: BUILD SUCCESS, all 4 tests passed.

- [ ] **Step 6: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceService.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/internal/ListingSummaryGenerationService.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/internal/ListingPersistenceServiceTest.java
git commit -m "feat(hermes-backend): restore MDC correlationId in event listeners"
```

---

## Task 5: FundaProxyClient outbound header

**Files:**
- Create: `hermes-backend/src/test/java/com/kropholler/dev/hermes/scraping/internal/FundaProxyClientTest.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/internal/FundaProxyClient.java`

- [ ] **Step 1: Write the failing test**

```java
package com.kropholler.dev.hermes.scraping.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FundaProxyClientTest {

    private MockRestServiceServer server;
    private FundaProxyClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FundaProxyClient(builder, "http://test");
    }

    @AfterEach
    void cleanup() {
        MDC.clear();
    }

    @Test
    void search_forwardsCorrelationIdHeaderFromMdc() {
        MDC.put("correlationId", "req-abc");
        server.expect(requestTo(containsString("/search")))
              .andExpect(header("X-Correlation-ID", "req-abc"))
              .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        client.search("amsterdam", null, null, null, null, 1);

        server.verify();
    }

    @Test
    void search_omitsCorrelationIdHeaderWhenMdcEmpty() {
        server.expect(requestTo(containsString("/search")))
              .andExpect(headerDoesNotExist("X-Correlation-ID"))
              .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        client.search("amsterdam", null, null, null, null, 1);

        server.verify();
    }
}
```

- [ ] **Step 2: Run to confirm it fails**

```
cd hermes-backend
./mvnw test -pl . -Dtest=FundaProxyClientTest -q
```

Expected: `search_forwardsCorrelationIdHeaderFromMdc` fails — header not sent.

- [ ] **Step 3: Add the request interceptor to `FundaProxyClient`**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/internal/FundaProxyClient.java`, add the MDC import and update the constructor:

Add import:
```java
import org.slf4j.MDC;
```

Replace the constructor body:
```java
FundaProxyClient(RestClient.Builder builder,
                 @Value("${funda.proxy.url:http://funda-proxy:8001}") String baseUrl) {
    this.restClient = builder
        .baseUrl(baseUrl)
        .requestInterceptor((request, body, execution) -> {
            String correlationId = MDC.get("correlationId");
            if (correlationId != null) {
                request.getHeaders().set("X-Correlation-ID", correlationId);
            }
            return execution.execute(request, body);
        })
        .build();
}
```

- [ ] **Step 4: Run tests and verify they pass**

```
cd hermes-backend
./mvnw test -pl . -Dtest=FundaProxyClientTest -q
```

Expected: BUILD SUCCESS, 2 tests passed.

- [ ] **Step 5: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/internal/FundaProxyClient.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/scraping/internal/FundaProxyClientTest.java
git commit -m "feat(hermes-backend): forward X-Correlation-ID to funda-proxy"
```

---

## Task 6: api.yaml + GlobalExceptionHandler

The `ErrorResponse` OpenAPI schema needs a `correlationId` field. This is a code-generation step: `api.yaml` is updated first, `mvn generate-sources` regenerates the model, then `GlobalExceptionHandler` is updated to use the new field.

**Files:**
- Modify: `hermes-backend/src/main/resources/openapi/api.yaml`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/api/GlobalExceptionHandler.java`
- Create: `hermes-backend/src/test/java/com/kropholler/dev/hermes/api/GlobalExceptionHandlerTest.java`

- [ ] **Step 1: Update `ErrorResponse` in `api.yaml`**

In `hermes-backend/src/main/resources/openapi/api.yaml`, find the `ErrorResponse` schema and add the `correlationId` property:

```yaml
    ErrorResponse:
      type: object
      properties:
        error:
          type: string
        detail:
          type: string
        correlationId:
          type: string
          nullable: true
```

- [ ] **Step 2: Regenerate the OpenAPI model**

```
cd hermes-backend
./mvnw generate-sources -q
```

Expected: BUILD SUCCESS. The generated `ErrorResponse` class at `target/generated-sources/openapi/src/main/java/com/kropholler/dev/hermes/api/generated/model/ErrorResponse.java` now has a `correlationId(String)` builder method.

- [ ] **Step 3: Write the failing test for `GlobalExceptionHandler`**

```java
package com.kropholler.dev.hermes.api;

import com.kropholler.dev.hermes.api.generated.model.ErrorResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void cleanup() {
        MDC.clear();
    }

    @Test
    void handleResponseStatus_includesCorrelationIdFromMdc() {
        MDC.put("correlationId", "err-corr");
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "not found");

        ResponseEntity<ErrorResponse> response = handler.handleResponseStatus(ex);

        assertThat(response.getBody().getCorrelationId()).isEqualTo("err-corr");
    }

    @Test
    void handleResponseStatus_correlationIdIsNullWhenMdcEmpty() {
        MDC.clear();
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "not found");

        ResponseEntity<ErrorResponse> response = handler.handleResponseStatus(ex);

        assertThat(response.getBody().getCorrelationId()).isNull();
    }

    @Test
    void handleGeneral_includesCorrelationIdFromMdc() {
        MDC.put("correlationId", "gen-corr");

        ResponseEntity<ErrorResponse> response = handler.handleGeneral(new RuntimeException("oops"));

        assertThat(response.getBody().getCorrelationId()).isEqualTo("gen-corr");
    }
}
```

- [ ] **Step 4: Run to confirm the tests fail**

```
cd hermes-backend
./mvnw test -pl . -Dtest=GlobalExceptionHandlerTest -q
```

Expected: `getCorrelationId()` returns null — handler does not yet set the field.

- [ ] **Step 5: Update `GlobalExceptionHandler` to set `correlationId`**

Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/api/GlobalExceptionHandler.java`:

```java
package com.kropholler.dev.hermes.api;

import com.kropholler.dev.hermes.api.generated.model.ErrorResponse;
import org.slf4j.MDC;
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
            .detail(ex.getMessage())
            .correlationId(MDC.get("correlationId"));
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        ErrorResponse body = new ErrorResponse()
            .error("INTERNAL_SERVER_ERROR")
            .detail(ex.getMessage())
            .correlationId(MDC.get("correlationId"));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
```

- [ ] **Step 6: Run tests and verify they pass**

```
cd hermes-backend
./mvnw test -pl . -Dtest=GlobalExceptionHandlerTest -q
```

Expected: BUILD SUCCESS, 3 tests passed.

- [ ] **Step 7: Commit**

```bash
git add hermes-backend/src/main/resources/openapi/api.yaml \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/api/GlobalExceptionHandler.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/api/GlobalExceptionHandlerTest.java
git commit -m "feat(hermes-backend): include correlationId in error responses"
```

---

## Task 7: funda-proxy correlation middleware + log enrichment

**Files:**
- Create: `funda-proxy/correlation.py`
- Create: `funda-proxy/tests/test_correlation.py`
- Modify: `funda-proxy/telemetry.py`
- Modify: `funda-proxy/main.py`

- [ ] **Step 1: Write the failing test**

```python
# funda-proxy/tests/test_correlation.py
import pytest
from starlette.applications import Starlette
from starlette.responses import JSONResponse
from starlette.routing import Route
from starlette.testclient import TestClient

from correlation import CorrelationIdMiddleware, _correlation_id_var


def _read_endpoint(request):
    return JSONResponse({"correlation_id": _correlation_id_var.get("")})


_app = Starlette(routes=[Route("/", _read_endpoint)])
_app.add_middleware(CorrelationIdMiddleware)
_client = TestClient(_app)


def test_sets_correlation_id_from_header():
    resp = _client.get("/", headers={"X-Correlation-ID": "my-id"})
    assert resp.json()["correlation_id"] == "my-id"


def test_empty_string_when_header_absent():
    resp = _client.get("/")
    assert resp.json()["correlation_id"] == ""


def test_correlation_id_cleared_after_request():
    _client.get("/", headers={"X-Correlation-ID": "my-id"})
    assert _correlation_id_var.get("") == ""
```

- [ ] **Step 2: Run to confirm the tests fail**

```
cd funda-proxy
python -m pytest tests/test_correlation.py -v
```

Expected: `ModuleNotFoundError: No module named 'correlation'`.

- [ ] **Step 3: Create `correlation.py`**

```python
# funda-proxy/correlation.py
from contextvars import ContextVar

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request

_correlation_id_var: ContextVar[str] = ContextVar("correlation_id", default="")

_HEADER = "X-Correlation-ID"


class CorrelationIdMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        correlation_id = request.headers.get(_HEADER, "")
        token = _correlation_id_var.set(correlation_id)
        try:
            return await call_next(request)
        finally:
            _correlation_id_var.reset(token)
```

- [ ] **Step 4: Run the tests and verify they pass**

```
cd funda-proxy
python -m pytest tests/test_correlation.py -v
```

Expected: 3 tests passed.

- [ ] **Step 5: Extend `_OtelJsonFormatter` in `telemetry.py` to include `correlation_id`**

In `funda-proxy/telemetry.py`, add the import for `_correlation_id_var` and extend `add_fields`:

Add import at the top of the file (after existing imports):
```python
from correlation import _correlation_id_var
```

Replace `_OtelJsonFormatter.add_fields`:
```python
    def add_fields(self, log_record, record, message_dict):
        super().add_fields(log_record, record, message_dict)
        span = trace.get_current_span()
        ctx = span.get_span_context()
        if ctx.is_valid:
            log_record["trace_id"] = format(ctx.trace_id, "032x")
            log_record["span_id"] = format(ctx.span_id, "016x")
        else:
            log_record["trace_id"] = ""
            log_record["span_id"] = ""
        log_record["correlation_id"] = _correlation_id_var.get("")
```

- [ ] **Step 6: Register `CorrelationIdMiddleware` in `main.py`**

In `funda-proxy/main.py`, add the import and register the middleware. Add after the `configure_telemetry()` call and before `app = FastAPI(...)`:

Add import:
```python
from correlation import CorrelationIdMiddleware
```

Add after `FastAPIInstrumentor.instrument_app(app)`:
```python
app.add_middleware(CorrelationIdMiddleware)
```

The final `main.py` relevant section:
```python
configure_telemetry()

logger = logging.getLogger(__name__)

app = FastAPI(title="funda-proxy", lifespan=lifespan)
FastAPIInstrumentor.instrument_app(app)
app.add_middleware(CorrelationIdMiddleware)
```

- [ ] **Step 7: Run all funda-proxy tests**

```
cd funda-proxy
python -m pytest tests/ -v
```

Expected: all tests pass (existing endpoint tests + 3 new correlation tests).

- [ ] **Step 8: Commit**

```bash
git add funda-proxy/correlation.py \
        funda-proxy/telemetry.py \
        funda-proxy/main.py \
        funda-proxy/tests/test_correlation.py
git commit -m "feat(funda-proxy): add CorrelationIdMiddleware and log enrichment"
```

---

## Task 8: Final full test run

- [ ] **Step 1: Run the full hermes-backend test suite**

```
cd hermes-backend
./mvnw test -q
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 2: Run the full funda-proxy test suite**

```
cd funda-proxy
python -m pytest tests/ -v
```

Expected: all tests pass.

- [ ] **Step 3: Commit if any fixups were needed**

If no fixups needed, no commit required — all work is in prior commits.
