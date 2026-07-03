# Role-Gated Admin Features Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restrict scraping-related endpoints to the `admin` realm role, and close the object-level ownership gap on `deleteAgentTask`/`markNotificationRead` flagged during phase 3's review.

**Architecture:** `SecurityConfig` gains `@EnableMethodSecurity` and a custom `AccessDeniedHandler` (so `@PreAuthorize` denials return the same `ProblemDetail` JSON shape as every other error). `ScrapingSessionController` and `ListingController`'s scraping-related methods get `@PreAuthorize("hasRole('ADMIN')")`. `AgentTaskService.delete`/`NotificationService.markRead` gain a `userId` parameter and 404 if the resource isn't found or isn't owned by that user. Frontend gets an `adminGuard` for the `/scraping` route and hides the Scraping nav link for non-admins (UX only — the backend is the real boundary).

**Tech Stack:** Spring Security method security (`@PreAuthorize`), Spring Boot 4.0.6, Angular 22, `keycloak-angular`.

## Global Constraints

- A denied ownership check returns 404, not 403 — consistent with this codebase's existing pattern of not confirming another resource's existence to a caller not entitled to see it.
- `@PreAuthorize` denials must return the same `ProblemDetail` JSON shape (403) as every other error in this API, not Spring Security's default plain-text page.
- No admin "see all users" screen or Keycloak Admin API integration — explicitly out of scope, dropped from the original phase 4 plan.
- Search-related endpoints (`getListings`, `getListing`, `getListingSummary`, `requestListingSummaryGeneration`) stay open to every authenticated user — only scraping-triggering/status endpoints are admin-gated.

---

## Task 1: `@EnableMethodSecurity` + `ProblemDetailAccessDeniedHandler`

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/config/ProblemDetailAccessDeniedHandler.java`
- Test: `hermes-backend/src/test/java/com/kropholler/dev/hermes/config/ProblemDetailAccessDeniedHandlerTest.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/config/SecurityConfig.java`

**Interfaces:**
- Produces: `ProblemDetailAccessDeniedHandler(ObjectMapper objectMapper)`, a plain (non-`@Component`) `AccessDeniedHandler` implementation, wired as a `@Bean` inside `SecurityConfig` (not a separate `@Component`) so every existing test that already does `@Import(SecurityConfig.class)` picks it up automatically with no changes to those files.

- [ ] **Step 1: Write the failing test**

Create `hermes-backend/src/test/java/com/kropholler/dev/hermes/config/ProblemDetailAccessDeniedHandlerTest.java`:

```java
package com.kropholler.dev.hermes.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemDetailAccessDeniedHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProblemDetailAccessDeniedHandler handler = new ProblemDetailAccessDeniedHandler(objectMapper);

    @Test
    void handle_writes403ProblemDetailJson() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("Access is denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString())
            .contains("\"status\":403")
            .contains("\"detail\":\"Access is denied\"");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ProblemDetailAccessDeniedHandlerTest -f hermes-backend/pom.xml`
Expected: FAIL — compilation error, `ProblemDetailAccessDeniedHandler` does not exist.

- [ ] **Step 3: Write `ProblemDetailAccessDeniedHandler`**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/config/ProblemDetailAccessDeniedHandler.java`:

```java
package com.kropholler.dev.hermes.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    ProblemDetailAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        ProblemDetail body = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        body.setTitle("FORBIDDEN");
        body.setDetail(accessDeniedException.getMessage());
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ProblemDetailAccessDeniedHandlerTest -f hermes-backend/pom.xml`
Expected: PASS, 1 test, 0 failures.

- [ ] **Step 5: Wire `@EnableMethodSecurity` and the handler into `SecurityConfig`**

Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/config/SecurityConfig.java`:

```java
package com.kropholler.dev.hermes.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, AccessDeniedHandler accessDeniedHandler) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/ws/chat/**").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(exceptionHandling -> exceptionHandling.accessDeniedHandler(accessDeniedHandler))
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return new ProblemDetailAccessDeniedHandler(objectMapper);
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::realmRoleAuthorities);
        return converter;
    }

    @SuppressWarnings("unchecked")
    static Collection<GrantedAuthority> realmRoleAuthorities(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof List<?> roles)) {
            return Set.of();
        }
        return roles.stream()
            .map(Object::toString)
            .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
            .collect(Collectors.toSet());
    }
}
```

- [ ] **Step 6: Run the full backend suite to confirm nothing else broke**

Run: `mvn test -f hermes-backend/pom.xml`
Expected: BUILD SUCCESS. Every existing test that does `@Import(SecurityConfig.class)` should still pass unchanged, since `AccessDeniedHandler` is now a bean produced by that same imported configuration class (no other test file needs touching for this step — `@EnableMethodSecurity` has no effect yet since no `@PreAuthorize` annotation exists anywhere in the codebase until Task 2).

- [ ] **Step 7: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/config/ProblemDetailAccessDeniedHandler.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/config/SecurityConfig.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/config/ProblemDetailAccessDeniedHandlerTest.java
git commit -m "feat(backend): enable method security with a JSON-shaped 403 for access-denied responses"
```

---

## Task 2: `@PreAuthorize` on scraping endpoints

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/ScrapingSessionController.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingController.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/scraping/ScrapingSessionControllerTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingControllerTest.java`

**Interfaces:**
- Consumes: `@EnableMethodSecurity` and `AccessDeniedHandler` (Task 1).

**Important:** these two test classes currently import `SecuredMockMvcTestSupport`, which applies a single default JWT (no realm role claim) to every request in the class. Since `@PreAuthorize("hasRole('ADMIN')")` needs a per-request-controllable role, both files drop that import entirely and instead explicitly control authorities per request via `.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))` / `ROLE_USER`, matching the pattern `FavoriteControllerTest`/`NotificationControllerTest`/`AgentTaskControllerTest` already use for per-request JWT control (those control the *subject*; these control *authorities* instead, since `SecurityMockMvcRequestPostProcessors.jwt()` sets authorities directly rather than by parsing a `realm_access` claim through the real converter).

- [ ] **Step 1: Add `@PreAuthorize` to `ScrapingSessionController`**

Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/ScrapingSessionController.java`:

```java
package com.kropholler.dev.hermes.scraping;

import com.kropholler.dev.hermes.scraping.openapi.CreateScrapingSessionRequest;
import com.kropholler.dev.hermes.scraping.openapi.ScrapingSessionResponse;
import com.kropholler.dev.hermes.scraping.openapi.ScrapingSessionsApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ScrapingSessionController implements ScrapingSessionsApi {

    private final ScrapingQueueService queueService;
    private final ScrapingSessionApiMapper scrapingSessionApiMapper;

    @Override
    @PreAuthorize("hasRole('ADMIN')")
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
        return ResponseEntity.status(HttpStatus.CREATED).body(scrapingSessionApiMapper.toResponse(dto));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ScrapingSessionResponse> getScrapingSession(UUID id) {
        return queueService.findById(id)
            .map(dto -> ResponseEntity.ok(scrapingSessionApiMapper.toResponse(dto)))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Scraping session " + id + " not found"));
    }

}
```

- [ ] **Step 2: Add `@PreAuthorize` to `ListingController`'s scraping-related methods**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingController.java`, add the import `import org.springframework.security.access.prepost.PreAuthorize;`, and add `@PreAuthorize("hasRole('ADMIN')")` directly above the `@Override` for `rescrapeListing` and `backfillListingGeocoding` only (`getListings`, `getListing`, `getListingSummary`, `requestListingSummaryGeneration` are unchanged):

```java
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ScrapingSessionResponse> rescrapeListing(UUID id) {
```

```java
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GeocodingBackfillResponse> backfillListingGeocoding() {
```

- [ ] **Step 3: Rewrite `ScrapingSessionControllerTest`**

Replace the full contents of `hermes-backend/src/test/java/com/kropholler/dev/hermes/scraping/ScrapingSessionControllerTest.java`:

```java
package com.kropholler.dev.hermes.scraping;

import com.kropholler.dev.hermes.config.SecurityConfig;
import com.kropholler.dev.hermes.scraping.openapi.ScrapingSessionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScrapingSessionController.class)
@Import(SecurityConfig.class)
class ScrapingSessionControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean ScrapingQueueService queueService;
    @MockitoBean ScrapingSessionApiMapper scrapingSessionApiMapper;

    @Test
    void createScrapingSession_asAdmin_returns201WithBody() throws Exception {
        UUID sessionId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-01T08:00:00Z");

        ScrapingSessionDto dto = new ScrapingSessionDto(
            sessionId, ScrapingSessionStatus.PENDING, ScrapingSessionType.SEARCH, createdAt, null);

        ScrapingSessionResponse response = new ScrapingSessionResponse();
        response.setId(sessionId);
        response.setStatus(ScrapingSessionResponse.StatusEnum.PENDING);
        response.setCreatedAt(OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));

        when(queueService.enqueueSearch(any(), nullable(Integer.class), nullable(Integer.class),
                nullable(Integer.class), nullable(Integer.class), anyInt())).thenReturn(dto);
        when(scrapingSessionApiMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(post("/api/scraping-sessions")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"city\":\"Amsterdam\",\"pageLimit\":5}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(sessionId.toString()))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createScrapingSession_asUser_returns403() throws Exception {
        mockMvc.perform(post("/api/scraping-sessions")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"city\":\"Amsterdam\",\"pageLimit\":5}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void getScrapingSession_asAdmin_returnsOkWhenFound() throws Exception {
        UUID sessionId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-01T08:00:00Z");

        ScrapingSessionDto dto = new ScrapingSessionDto(
            sessionId, ScrapingSessionStatus.COMPLETED, ScrapingSessionType.SEARCH, createdAt, createdAt.plusSeconds(60));

        ScrapingSessionResponse response = new ScrapingSessionResponse();
        response.setId(sessionId);
        response.setStatus(ScrapingSessionResponse.StatusEnum.COMPLETED);

        when(queueService.findById(sessionId)).thenReturn(Optional.of(dto));
        when(scrapingSessionApiMapper.toResponse(dto)).thenReturn(response);

        mockMvc.perform(get("/api/scraping-sessions/{id}", sessionId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(sessionId.toString()))
            .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void getScrapingSession_asAdmin_returns404WhenNotFound() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(queueService.findById(sessionId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/scraping-sessions/{id}", sessionId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void getScrapingSession_asUser_returns403() throws Exception {
        UUID sessionId = UUID.randomUUID();

        mockMvc.perform(get("/api/scraping-sessions/{id}", sessionId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
            .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `mvn test -Dtest=ScrapingSessionControllerTest -f hermes-backend/pom.xml`
Expected: PASS, 5 tests, 0 failures.

- [ ] **Step 5: Rewrite `ListingControllerTest`**

Replace the full contents of `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingControllerTest.java`:

```java
package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.openapi.ListingDetailResponse;
import com.kropholler.dev.hermes.listing.openapi.ScrapingSessionResponse;
import com.kropholler.dev.hermes.listing.summary.ListingSummaryDto;
import com.kropholler.dev.hermes.listing.summary.ListingSummaryService;
import com.kropholler.dev.hermes.scraping.ScrapingQueueService;
import com.kropholler.dev.hermes.scraping.ScrapingSessionDto;
import com.kropholler.dev.hermes.scraping.ScrapingSessionStatus;
import com.kropholler.dev.hermes.scraping.ScrapingSessionType;
import com.kropholler.dev.hermes.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ListingController.class)
@Import(SecurityConfig.class)
class ListingControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean ListingService listingService;
    @MockitoBean ScrapingQueueService queueService;
    @MockitoBean ListingSummaryService summaryService;
    @MockitoBean ListingApiMapper listingApiMapper;
    @MockitoBean RescrapeMapper rescrapeMapper;
    @MockitoBean com.kropholler.dev.hermes.listing.geocoding.ListingGeocodingBackfillService backfillService;

    private ListingDto minimalDto(UUID id) {
        return new ListingDto(id, "funda-1", "https://funda.nl/1",
            "Dorpstraat", "10", null, "1234AB", "Utrecht", "Utrecht",
            Instant.now(), Instant.now(), 300000, ListingStatus.FOR_SALE,
            null, 80, 4, 2, "A", null, null);
    }

    @Test
    void getListing_returnsOkWithMappedDetail() throws Exception {
        UUID id = UUID.randomUUID();
        ListingDto dto = minimalDto(id);
        ListingDetailResponse response = new ListingDetailResponse();
        response.setId(id);
        response.setStreet("Dorpstraat");

        when(listingService.findById(id)).thenReturn(Optional.of(dto));
        when(listingApiMapper.toDetailResponse(dto)).thenReturn(response);

        mockMvc.perform(get("/api/listings/{id}", id).with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.street").value("Dorpstraat"));
    }

    @Test
    void getListing_returns404WhenNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(listingService.findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/listings/{id}", id).with(jwt()))
            .andExpect(status().isNotFound());
    }

    @Test
    void rescrapeListing_asAdmin_returns202WithSession() throws Exception {
        UUID id = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ListingDto dto = minimalDto(id);
        ScrapingSessionDto sessionDto = new ScrapingSessionDto(
            sessionId, ScrapingSessionStatus.PENDING, ScrapingSessionType.RESCRAPE, Instant.now(), null);

        ScrapingSessionResponse sessionResponse = new ScrapingSessionResponse();
        sessionResponse.setId(sessionId);
        sessionResponse.setStatus(ScrapingSessionResponse.StatusEnum.PENDING);

        when(listingService.findById(id)).thenReturn(Optional.of(dto));
        when(queueService.enqueueRescrape(any(), any())).thenReturn(sessionDto);
        when(rescrapeMapper.toResponse(sessionDto)).thenReturn(sessionResponse);

        mockMvc.perform(post("/api/listings/{id}/rescrape", id)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.id").value(sessionId.toString()));
    }

    @Test
    void rescrapeListing_asAdmin_returns404WhenListingNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(listingService.findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/listings/{id}/rescrape", id)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void rescrapeListing_asUser_returns403() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/listings/{id}/rescrape", id)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void getListingSummary_returnsOkWhenSummaryExists() throws Exception {
        UUID id = UUID.randomUUID();
        Instant generatedAt = Instant.parse("2026-05-01T10:00:00Z");
        ListingSummaryDto summaryDto = new ListingSummaryDto(id, "A great house.", generatedAt);

        when(summaryService.findByListingId(id)).thenReturn(Optional.of(summaryDto));

        mockMvc.perform(get("/api/listings/{id}/summary", id).with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.listingId").value(id.toString()))
            .andExpect(jsonPath("$.summary").value("A great house."));
    }

    @Test
    void getListingSummary_returns404WhenNoSummary() throws Exception {
        UUID id = UUID.randomUUID();
        when(summaryService.findByListingId(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/listings/{id}/summary", id).with(jwt()))
            .andExpect(status().isNotFound());
    }

    @Test
    void requestListingSummaryGeneration_returns202WhenListingExists() throws Exception {
        UUID id = UUID.randomUUID();
        when(listingService.findById(id)).thenReturn(Optional.of(minimalDto(id)));

        mockMvc.perform(post("/api/listings/{id}/summary/generate", id).with(jwt()))
            .andExpect(status().isAccepted());

        verify(summaryService).requestGeneration(id);
    }

    @Test
    void requestListingSummaryGeneration_returns404WhenListingNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(listingService.findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/listings/{id}/summary/generate", id).with(jwt()))
            .andExpect(status().isNotFound());
    }

    @Test
    void backfillListingGeocoding_asAdmin_returns202WithQueuedCount() throws Exception {
        when(backfillService.queueMissingGeocoding()).thenReturn(7);

        mockMvc.perform(post("/api/listings/geocoding/backfill")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.queuedCount").value(7));
    }

    @Test
    void backfillListingGeocoding_asUser_returns403() throws Exception {
        mockMvc.perform(post("/api/listings/geocoding/backfill")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
            .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 6: Run the tests**

Run: `mvn test -Dtest=ListingControllerTest -f hermes-backend/pom.xml`
Expected: PASS, 11 tests, 0 failures.

- [ ] **Step 7: Run the full backend suite**

Run: `mvn test -f hermes-backend/pom.xml`
Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/ScrapingSessionController.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingController.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/scraping/ScrapingSessionControllerTest.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingControllerTest.java
git commit -m "feat(backend): restrict scraping-related endpoints to the admin role"
```

---

## Task 3: Object-level ownership checks on delete/mark-read

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskService.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskController.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/ListWatchesTool.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/NotificationService.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/NotificationController.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskServiceTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskControllerTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/ListWatchesToolTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/notification/NotificationServiceTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/notification/NotificationControllerTest.java`

**Interfaces:**
- Produces: `AgentTaskService.delete(UUID taskId, UUID userId)` (signature changed — was `delete(UUID taskId)`), throwing `ResponseStatusException(HttpStatus.NOT_FOUND, ...)` if the task doesn't exist or isn't owned by `userId`. `NotificationService.markRead(UUID notificationId, UUID userId)` (signature changed — was `markRead(UUID notificationId)`), same 404 behavior.

- [ ] **Step 1: Write the failing tests for `AgentTaskService.delete`**

In `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskServiceTest.java`, replace the existing `delete_invokesRepositoryDeleteById` test with:

```java
    @Test
    void delete_ownerDeletesSuccessfully() {
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        AgentTaskEntity task = new AgentTaskEntity();
        task.setUserId(userId);
        when(repo.findById(taskId)).thenReturn(java.util.Optional.of(task));

        service.delete(taskId, userId);

        verify(repo).delete(task);
    }

    @Test
    void delete_throws404WhenNotFound() {
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(repo.findById(taskId)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.delete(taskId, userId))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("Agent task " + taskId + " not found");

        verify(repo, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    void delete_throws404WhenNotOwnedByCaller() {
        UUID ownerId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        AgentTaskEntity task = new AgentTaskEntity();
        task.setUserId(ownerId);
        when(repo.findById(taskId)).thenReturn(java.util.Optional.of(task));

        assertThatThrownBy(() -> service.delete(taskId, callerId))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("Agent task " + taskId + " not found");

        verify(repo, org.mockito.Mockito.never()).delete(any());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=AgentTaskServiceTest -f hermes-backend/pom.xml`
Expected: FAIL — compilation error, `service.delete(taskId, userId)` doesn't match the existing `delete(UUID)` signature.

- [ ] **Step 3: Update `AgentTaskService.delete`**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskService.java`, add imports `import org.springframework.http.HttpStatus;` and `import org.springframework.web.server.ResponseStatusException;`, then replace:

```java
    @Transactional
    public void delete(UUID taskId) {
        agentTaskRepository.deleteById(taskId);
    }
```

with:

```java
    @Transactional
    public void delete(UUID taskId, UUID userId) {
        AgentTaskEntity task = agentTaskRepository.findById(taskId)
            .filter(t -> t.getUserId().equals(userId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Agent task " + taskId + " not found"));
        agentTaskRepository.delete(task);
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=AgentTaskServiceTest -f hermes-backend/pom.xml`
Expected: PASS, 9 tests, 0 failures.

- [ ] **Step 5: Update `AgentTaskController.deleteAgentTask`**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskController.java`, replace:

```java
    @Override
    public ResponseEntity<Void> deleteAgentTask(UUID id) {
        agentTaskService.delete(id);
        return ResponseEntity.noContent().build();
    }
```

with:

```java
    @Override
    public ResponseEntity<Void> deleteAgentTask(UUID id) {
        agentTaskService.delete(id, CurrentUser.current().id());
        return ResponseEntity.noContent().build();
    }
```

- [ ] **Step 6: Update `ListWatchesTool`'s cancel branch**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/ListWatchesTool.java`, replace:

```java
        if (cancelId != null) {
            agentTaskService.delete(cancelId);
            return "Watch " + cancelId + " cancelled.";
        }
```

with:

```java
        if (cancelId != null) {
            agentTaskService.delete(cancelId, userId);
            return "Watch " + cancelId + " cancelled.";
        }
```

- [ ] **Step 7: Update `ListWatchesToolTest`'s cancel test**

In `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/ListWatchesToolTest.java`, replace:

```java
    @Test
    void cancelWatchWhenCancelIdProvided() {
        AgentTaskService agentTaskService = mock(AgentTaskService.class);
        UUID clientId = UUID.randomUUID();
        UUID cancelId = UUID.randomUUID();

        ListWatchesTool tool = new ListWatchesTool(clientId, agentTaskService);
        String result = tool.listWatches(cancelId);

        verify(agentTaskService).delete(cancelId);
        assertThat(result).contains("cancelled");
        assertThat(result).contains(cancelId.toString());
    }
```

with:

```java
    @Test
    void cancelWatchWhenCancelIdProvided() {
        AgentTaskService agentTaskService = mock(AgentTaskService.class);
        UUID clientId = UUID.randomUUID();
        UUID cancelId = UUID.randomUUID();

        ListWatchesTool tool = new ListWatchesTool(clientId, agentTaskService);
        String result = tool.listWatches(cancelId);

        verify(agentTaskService).delete(cancelId, clientId);
        assertThat(result).contains("cancelled");
        assertThat(result).contains(cancelId.toString());
    }
```

- [ ] **Step 8: Update `AgentTaskControllerTest`'s delete test**

In `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskControllerTest.java`, replace:

```java
    @Test
    void deleteAgentTask_callsServiceAndReturns204() throws Exception {
        UUID taskId = UUID.randomUUID();

        mockMvc.perform(delete("/api/agent-tasks/{id}", taskId)
                .with(jwt()))
            .andExpect(status().isNoContent());

        verify(agentTaskService).delete(taskId);
    }
```

with:

```java
    @Test
    void deleteAgentTask_usesSubjectFromJwt() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        mockMvc.perform(delete("/api/agent-tasks/{id}", taskId)
                .with(jwt().jwt(builder -> builder.subject(callerId.toString()))))
            .andExpect(status().isNoContent());

        verify(agentTaskService).delete(eq(taskId), eq(callerId));
    }
```

- [ ] **Step 9: Run the agent-task tests**

Run: `mvn test -Dtest=AgentTaskServiceTest,AgentTaskControllerTest,ListWatchesToolTest -f hermes-backend/pom.xml`
Expected: PASS, 0 failures.

- [ ] **Step 10: Write the failing tests for `NotificationService.markRead`**

In `hermes-backend/src/test/java/com/kropholler/dev/hermes/notification/NotificationServiceTest.java`, replace the existing `markRead_whenFound_setsReadAndSaves` and `markRead_whenNotFound_doesNothing` tests with:

```java
    @Test
    void markRead_ownerMarksReadSuccessfully() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        NotificationEntity entity = new NotificationEntity();
        entity.setId(notificationId);
        entity.setUserId(userId);
        when(repo.findById(notificationId)).thenReturn(Optional.of(entity));

        service.markRead(notificationId, userId);

        assertThat(entity.isRead()).isTrue();
        verify(repo).save(entity);
    }

    @Test
    void markRead_throws404WhenNotFound() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        when(repo.findById(notificationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(notificationId, userId))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("Notification " + notificationId + " not found");

        verify(repo, never()).save(any());
    }

    @Test
    void markRead_throws404WhenNotOwnedByCaller() {
        UUID ownerId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        NotificationEntity entity = new NotificationEntity();
        entity.setId(notificationId);
        entity.setUserId(ownerId);
        when(repo.findById(notificationId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.markRead(notificationId, callerId))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("Notification " + notificationId + " not found");

        verify(repo, never()).save(any());
    }
```

Add `import static org.assertj.core.api.Assertions.assertThatThrownBy;` to this file's imports alongside the existing `assertThat` import.

- [ ] **Step 11: Update `NotificationService.markRead`**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/NotificationService.java`, add imports `import org.springframework.http.HttpStatus;` and `import org.springframework.web.server.ResponseStatusException;`, then replace:

```java
    @Transactional
    public void markRead(UUID notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
    }
```

with:

```java
    @Transactional
    public void markRead(UUID notificationId, UUID userId) {
        NotificationEntity notification = notificationRepository.findById(notificationId)
            .filter(n -> n.getUserId().equals(userId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Notification " + notificationId + " not found"));
        notification.setRead(true);
        notificationRepository.save(notification);
    }
```

- [ ] **Step 12: Update `NotificationController.markNotificationRead`**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/NotificationController.java`, replace:

```java
    @Override
    public ResponseEntity<Void> markNotificationRead(UUID id) {
        notificationService.markRead(id);
        return ResponseEntity.noContent().build();
    }
```

with:

```java
    @Override
    public ResponseEntity<Void> markNotificationRead(UUID id) {
        notificationService.markRead(id, CurrentUser.current().id());
        return ResponseEntity.noContent().build();
    }
```

Add the import `import com.kropholler.dev.hermes.security.CurrentUser;` (this file didn't need it before since `markNotificationRead` was the only method not already using `CurrentUser`).

- [ ] **Step 13: Update `NotificationControllerTest`'s mark-read test**

In `hermes-backend/src/test/java/com/kropholler/dev/hermes/notification/NotificationControllerTest.java`, replace:

```java
    @Test
    void markNotificationRead_callsServiceAndReturns204() throws Exception {
        UUID notifId = UUID.randomUUID();

        mockMvc.perform(patch("/api/notifications/{id}/read", notifId)
                .with(jwt()))
            .andExpect(status().isNoContent());

        verify(notificationService).markRead(notifId);
    }
```

with:

```java
    @Test
    void markNotificationRead_usesSubjectFromJwt() throws Exception {
        UUID notifId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        mockMvc.perform(patch("/api/notifications/{id}/read", notifId)
                .with(jwt().jwt(builder -> builder.subject(callerId.toString()))))
            .andExpect(status().isNoContent());

        verify(notificationService).markRead(eq(notifId), eq(callerId));
    }
```

- [ ] **Step 14: Run the notification tests**

Run: `mvn test -Dtest=NotificationServiceTest,NotificationControllerTest -f hermes-backend/pom.xml`
Expected: PASS, 0 failures.

- [ ] **Step 15: Run the full backend suite**

Run: `mvn test -f hermes-backend/pom.xml`
Expected: BUILD SUCCESS.

- [ ] **Step 16: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskService.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskController.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/ListWatchesTool.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/NotificationService.java \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/NotificationController.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskServiceTest.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskControllerTest.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/ListWatchesToolTest.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/notification/NotificationServiceTest.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/notification/NotificationControllerTest.java
git commit -m "fix(backend): enforce ownership on deleteAgentTask/markNotificationRead, closing a cross-user gap"
```

---

## Task 4: Frontend — admin route guard and nav visibility

**Files:**
- Create: `hermes-frontend/src/app/core/admin.guard.ts`
- Test: `hermes-frontend/src/app/core/admin.guard.spec.ts`
- Modify: `hermes-frontend/src/app/app.routes.ts`
- Modify: `hermes-frontend/src/app/app.component.ts`
- Modify: `hermes-frontend/src/app/app.component.html`
- Modify: `hermes-frontend/src/app/app.component.spec.ts`

**Interfaces:**
- Produces: `adminGuard: CanActivateFn`, `AppComponent.isAdmin: boolean` getter.

- [ ] **Step 1: Write the failing test for `adminGuard`**

Create `hermes-frontend/src/app/core/admin.guard.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import Keycloak from 'keycloak-js';
import { adminGuard } from './admin.guard';

describe('adminGuard', () => {
  let keycloakStub: { authenticated: boolean; realmAccess?: { roles: string[] }; login: jasmine.Spy };
  let route: ActivatedRouteSnapshot;
  let state: RouterStateSnapshot;

  beforeEach(() => {
    keycloakStub = {
      authenticated: true,
      realmAccess: { roles: [] },
      login: jasmine.createSpy('login').and.resolveTo(undefined),
    };

    route = {} as ActivatedRouteSnapshot;
    state = { url: '/scraping' } as RouterStateSnapshot;

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: Keycloak, useValue: keycloakStub },
      ],
    });
  });

  it('allows activation when the caller has the admin realm role', async () => {
    keycloakStub.realmAccess = { roles: ['admin'] };

    const result = await TestBed.runInInjectionContext(() => adminGuard(route, state));

    expect(result).toBeTruthy();
    expect(keycloakStub.login).not.toHaveBeenCalled();
  });

  it('redirects an authenticated non-admin to /listings without prompting login', async () => {
    keycloakStub.realmAccess = { roles: ['user'] };

    const result = await TestBed.runInInjectionContext(() => adminGuard(route, state));

    expect(result).not.toBe(true);
    expect(keycloakStub.login).not.toHaveBeenCalled();
  });
});
```

Note: if the installed `keycloak-angular` version's `AuthGuardData.grantedRoles` derives from a differently-named `Keycloak` instance field than `realmAccess`, check `hermes-frontend/node_modules/keycloak-angular`'s type declarations for exactly how `createAuthGuard` builds `grantedRoles` from the injected `Keycloak` instance, and adjust the stub's field name to match — the intent (an authenticated caller with vs. without the `admin` realm role) must stay the same either way.

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test --prefix hermes-frontend -- --watch=false --browsers=ChromeHeadless`
Expected: FAIL — `admin.guard.ts` does not exist yet (compilation error for this spec file).

- [ ] **Step 3: Write `adminGuard`**

Create `hermes-frontend/src/app/core/admin.guard.ts`:

```ts
import { AuthGuardData, createAuthGuard } from 'keycloak-angular';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { inject } from '@angular/core';

const isAdminUser = async (
  _route: unknown,
  _state: unknown,
  authData: AuthGuardData,
): Promise<boolean | UrlTree> => {
  const { grantedRoles } = authData;

  if (grantedRoles.realmRoles.includes('admin')) {
    return true;
  }

  return inject(Router).parseUrl('/listings');
};

export const adminGuard: CanActivateFn = createAuthGuard(isAdminUser);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test --prefix hermes-frontend -- --watch=false --browsers=ChromeHeadless`
Expected: `adminGuard`'s 2 tests pass (pre-existing unrelated chat-component failures are unchanged).

- [ ] **Step 5: Apply `adminGuard` to the `/scraping` route**

In `hermes-frontend/src/app/app.routes.ts`, add the import `import { adminGuard } from './core/admin.guard';`, and change the `scraping` route's `canActivate`:

```ts
  {
    path: 'scraping',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./pages/scraping/scraping-page.component').then(
        m => m.ScrapingPageComponent
      ),
  },
```

(replacing `canActivate: [authGuard]` — `adminGuard` already requires authentication as a prerequisite to checking the role, per `createAuthGuard`'s `AuthGuardData.authenticated`/`grantedRoles` semantics, so `authGuard` is redundant here, not stacked alongside it.)

- [ ] **Step 6: Add `isAdmin` to `AppComponent` and hide the Scraping nav link**

In `hermes-frontend/src/app/app.component.ts`, add the `isAdmin` getter alongside the existing `username` getter:

```ts
  get isAdmin(): boolean {
    const roles = this.keycloak.tokenParsed?.['realm_access'] as { roles?: string[] } | undefined;
    return roles?.roles?.includes('admin') ?? false;
  }
```

In `hermes-frontend/src/app/app.component.html`, wrap the Scraping link in `@if (isAdmin)`:

```html
      @if (isAdmin) {
        <a
          routerLink="/scraping"
          routerLinkActive="bg-slate-700/60 text-white"
          class="text-slate-300 hover:text-white hover:bg-slate-700/40 px-3 py-1.5 rounded-md text-sm font-medium transition-colors"
        >Scraping</a>
      }
```

(replacing the existing unconditional `<a routerLink="/scraping" ...>Scraping</a>`.)

- [ ] **Step 7: Add tests for `isAdmin` to `AppComponent`'s spec**

In `hermes-frontend/src/app/app.component.spec.ts`, add these two tests alongside the existing ones:

```ts
  it('should report isAdmin false when tokenParsed has no admin realm role', () => {
    keycloakStub.tokenParsed = { realm_access: { roles: ['user'] } };
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance.isAdmin).toBeFalse();
  });

  it('should report isAdmin true when tokenParsed has the admin realm role', () => {
    keycloakStub.tokenParsed = { realm_access: { roles: ['admin'] } };
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance.isAdmin).toBeTrue();
  });
```

- [ ] **Step 8: Run the frontend build and test suite**

Run: `npm run build --prefix hermes-frontend`
Expected: BUILD SUCCESS, no compilation errors.

Run: `npm test --prefix hermes-frontend -- --watch=false --browsers=ChromeHeadless`
Expected: the new `adminGuard` and `AppComponent.isAdmin` specs pass; total pass count increases by 4 (2 + 2) over the prior baseline, with the same pre-existing unrelated chat-component failures.

- [ ] **Step 9: Commit**

```bash
git add hermes-frontend/src/app/core/admin.guard.ts \
        hermes-frontend/src/app/core/admin.guard.spec.ts \
        hermes-frontend/src/app/app.routes.ts \
        hermes-frontend/src/app/app.component.ts \
        hermes-frontend/src/app/app.component.html \
        hermes-frontend/src/app/app.component.spec.ts
git commit -m "feat(frontend): gate the scraping page behind the admin realm role"
```

---

## Task 5: End-to-end manual verification

**Files:** none (verification only).

- [ ] **Step 1: Bring up the full stack**

```bash
docker compose up -d --build
```

Wait for all services healthy: `docker compose ps`.

- [ ] **Step 2: Confirm role-based restriction on scraping**

Log in as `testuser` / `password`. Confirm the "Scraping" nav link is not visible. Confirm direct API calls are rejected with a JSON `ProblemDetail` body:

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/scraping-sessions \
  -H "Authorization: Bearer <testuser-token>" -H "Content-Type: application/json" \
  -d '{"city":"Amsterdam","pageLimit":1}'
```

Expected: `403`, with a JSON body containing `"status":403`.

Log in as `testadmin` / `password`. Confirm the "Scraping" nav link is visible, and triggering a scrape, rescraping a listing, and the geocoding backfill all work exactly as before.

- [ ] **Step 3: Confirm ownership enforcement**

As `testuser`, create a watch via chat (e.g. "save a watch for 3-bed houses in Utrecht under 400k") and note its id from "what are my watches". As `testadmin`, attempt to delete that same watch id via a direct API call:

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X DELETE http://localhost:8080/api/agent-tasks/{taskId} \
  -H "Authorization: Bearer <testadmin-token>"
```

Expected: `404`. Confirm the watch still appears when `testuser` lists their watches afterward (untouched). Repeat the same check for a notification: as `testuser`, trigger a notification, note its id, then as `testadmin` attempt `PATCH /api/notifications/{id}/read` — expect `404`, and confirm the notification is still unread for `testuser`.
