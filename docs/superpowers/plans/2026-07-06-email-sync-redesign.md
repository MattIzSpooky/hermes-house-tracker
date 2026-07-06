# Email Sync Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the per-request `UserProfileSyncFilter` (a DB read+conditional-write on every authenticated request) with a design that only ever writes the cached email opportunistically where the JWT already has it for free, and gates task creation on the *live* JWT email instead of a cached read.

**Architecture:** A blind `INSERT ... ON CONFLICT DO UPDATE` on `UserProfileRepository` (no read-then-compare) called from the two places a live JWT email is already in hand for free (every chat message, every address update) feeds the *only* consumer that actually needs a durable copy: `EmailNotificationSender`, running from a background scheduler with no JWT in scope. Separately, the "no email → no task" requirement is enforced using the live JWT email threaded through the existing per-chat-request tool-construction pipeline (`ChatController` → `AiChatService` → `ChatToolProvider` → the four task-creating tools) — zero extra DB reads, since that path already runs inside an authenticated request.

**Tech Stack:** Spring Boot 4.1, Spring Data JPA (native queries), Spring Security 7 (JWT), Spring AI `@Tool` annotations, JUnit 5 + Mockito + AssertJ + Testcontainers.

## Global Constraints

- The upsert is a blind write: `INSERT INTO user_profiles (user_id, email, updated_at) VALUES (:userId, :email, now()) ON CONFLICT (user_id) DO UPDATE SET email = EXCLUDED.email` — no read-then-compare, and it must never touch address fields or `updated_at` on an *existing* row (only a brand-new row gets `updated_at` set, to satisfy the `NOT NULL` constraint).
- H2 (this project's default `@DataJpaTest` database) does not support `ON CONFLICT ... DO UPDATE SET ... = EXCLUDED. ...` — confirmed by running it directly; it throws `JdbcSQLSyntaxErrorException`. Any test of this query must use a real Postgres via Testcontainers, matching the existing `ListingRepositoryRadiusTest` pattern.
- The creation-time email gate always checks the *live* JWT value for the current request — never a cached DB read.
- Rejection copy across all four gated tools: `"Please make sure your account has an email address before setting up notifications."` (exact string, used identically in all four).
- `ListWatchesTool` does not get the email gate (it only lists/cancels, never creates anything that could be emailed) but still takes the same three-argument `TaskTool` constructor for consistency.
- Every reverted file must return to *exactly* its pre-filter state — no partial reverts, no leftover unused imports.

---

### Task 1: `UserProfileRepository.upsertEmail` + `UserProfileService.syncEmail`

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileRepository.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileService.java`
- Create: `hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileRepositoryUpsertEmailTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileServiceTest.java`

**Interfaces:**
- Produces: `UserProfileRepository.upsertEmail(UUID userId, String email): void` (native, `@Modifying`) and `UserProfileService.syncEmail(UUID userId, String email): void` — both used by Tasks 2 and 4.

- [ ] **Step 1: Write the failing repository test**

Create `hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileRepositoryUpsertEmailTest.java`. This needs a real Postgres (H2 doesn't support the `ON CONFLICT` syntax we need), so it follows the same Testcontainers pattern as `ListingRepositoryRadiusTest` in `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingRepositoryRadiusTest.java` (same `@DataJpaTest` + `@Import(Containers.class)` + `@ServiceConnection` + `@TestPropertySource` shape), except this table has no PostGIS dependency so a plain `postgres` image is fine:

```java
package com.kropholler.dev.hermes.profile;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(UserProfileRepositoryUpsertEmailTest.Containers.class)
@TestPropertySource(properties = {
    "spring.test.database.replace=none",
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class UserProfileRepositoryUpsertEmailTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));
        }
    }

    @Autowired UserProfileRepository repository;
    @Autowired EntityManager em;

    @Test
    void upsertEmail_noExistingRow_createsBareRowWithEmail() {
        UUID userId = UUID.randomUUID();

        repository.upsertEmail(userId, "user@hermes.local");
        em.flush();
        em.clear();

        UserProfileEntity saved = repository.findById(userId).orElseThrow();
        assertThat(saved.getEmail()).isEqualTo("user@hermes.local");
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getStreet()).isNull();
    }

    @Test
    void upsertEmail_existingRow_updatesEmailOnly() {
        UUID userId = UUID.randomUUID();
        UserProfileEntity existing = new UserProfileEntity();
        existing.setUserId(userId);
        existing.setStreet("Dorpstraat");
        existing.setEmail("old@hermes.local");
        Instant originalUpdatedAt = Instant.parse("2020-01-01T00:00:00Z");
        existing.setUpdatedAt(originalUpdatedAt);
        repository.saveAndFlush(existing);
        em.clear();

        repository.upsertEmail(userId, "new@hermes.local");
        em.flush();
        em.clear();

        UserProfileEntity reloaded = repository.findById(userId).orElseThrow();
        assertThat(reloaded.getEmail()).isEqualTo("new@hermes.local");
        assertThat(reloaded.getStreet()).isEqualTo("Dorpstraat");
        assertThat(reloaded.getUpdatedAt()).isEqualTo(originalUpdatedAt);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=UserProfileRepositoryUpsertEmailTest`
Expected: FAIL (compile error) — `UserProfileRepository.upsertEmail(...)` doesn't exist yet.

- [ ] **Step 3: Add `upsertEmail` to `UserProfileRepository`**

Change `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileRepository.java` from:

```java
package com.kropholler.dev.hermes.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfileEntity, UUID> {
}
```

to:

```java
package com.kropholler.dev.hermes.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfileEntity, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO user_profiles (user_id, email, updated_at)
            VALUES (:userId, :email, now())
            ON CONFLICT (user_id) DO UPDATE SET email = EXCLUDED.email
            """, nativeQuery = true)
    void upsertEmail(@Param("userId") UUID userId, @Param("email") String email);
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=UserProfileRepositoryUpsertEmailTest`
Expected: PASS (both tests green; this spins up a real Postgres container, so it will take longer than a typical unit test).

- [ ] **Step 5: Write the failing `UserProfileService.syncEmail` test**

Append to `hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileServiceTest.java` (add `import static org.mockito.Mockito.verifyNoInteractions;` and `import static org.mockito.Mockito.never;` alongside the existing static imports if not already present via the wildcard `import static org.mockito.Mockito.*;` — check first, this file already has that wildcard import so nothing new is needed):

```java
    @Test
    void syncEmail_delegatesToRepositoryUpsert() {
        UUID userId = UUID.randomUUID();

        service.syncEmail(userId, "user@hermes.local");

        verify(repository).upsertEmail(userId, "user@hermes.local");
    }

    @Test
    void syncEmail_nullEmail_doesNothing() {
        service.syncEmail(UUID.randomUUID(), null);

        verifyNoInteractions(repository);
    }

    @Test
    void syncEmail_blankEmail_doesNothing() {
        service.syncEmail(UUID.randomUUID(), "   ");

        verifyNoInteractions(repository);
    }
```

- [ ] **Step 6: Run it to verify it fails**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=UserProfileServiceTest`
Expected: FAIL (compile error) — `UserProfileService.syncEmail(...)` doesn't exist yet.

- [ ] **Step 7: Add `syncEmail` to `UserProfileService`**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileService.java`, add this method after `updateAddress`:

```java
    @Transactional
    public void syncEmail(UUID userId, String email) {
        if (email == null || email.isBlank()) return;
        repository.upsertEmail(userId, email);
    }
```

- [ ] **Step 8: Run it to verify it passes**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=UserProfileServiceTest`
Expected: PASS (all tests green, including the 3 new ones).

- [ ] **Step 9: Run the full backend test suite**

Run: `cd hermes-backend && ./mvnw -q test`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileRepository.java hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileService.java hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileRepositoryUpsertEmailTest.java hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileServiceTest.java
git commit -m "feat(profile): add blind-upsert email sync to UserProfileRepository/Service"
```

---

### Task 2: Sync email on address update

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileController.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileControllerTest.java`

**Interfaces:**
- Consumes: `UserProfileService.syncEmail(UUID, String)` (Task 1), `CurrentUser.current().email()` (existing).
- Produces: nothing new consumed elsewhere — this is a leaf caller.

This task also reverts this test file's `@Import` back to not needing `NoOpUserProfileSyncFilterTestConfig` (that revert is really Task 3's job, but since this file already needs editing here for the `syncEmail` test, do both edits together to avoid two separate touches to the same file — see Task 3's note about this).

- [ ] **Step 1: Write the failing test**

In `hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileControllerTest.java`, add this test (after `updateAddress_usesSubjectFromJwtNotFromRequest`):

```java
    @Test
    void updateAddress_syncsEmailFromJwt() throws Exception {
        UUID subject = UUID.randomUUID();
        AddressDto dto = new AddressDto("Dorpstraat", "10", null, "1234AB", "Utrecht", "Utrecht", 52.09, 5.12);
        when(userProfileService.updateAddress(eq(subject), eq("Dorpstraat"), eq("10"), eq(null), eq("1234AB"), eq("Utrecht"), eq("Utrecht")))
            .thenReturn(dto);
        when(userProfileApiMapper.toResponse(dto)).thenReturn(new com.kropholler.dev.hermes.profile.openapi.AddressResponse());

        mockMvc.perform(put("/api/profile/address")
                .with(jwt().jwt(builder -> builder.subject(subject.toString()).claim("email", "user@hermes.local")))
                .contentType("application/json")
                .content("""
                    {"street":"Dorpstraat","houseNumber":"10","zipCode":"1234AB","city":"Utrecht","province":"Utrecht"}
                    """))
            .andExpect(status().isOk());

        verify(userProfileService).syncEmail(subject, "user@hermes.local");
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=UserProfileControllerTest`
Expected: FAIL — `verify(userProfileService).syncEmail(...)` never happened (method doesn't exist on the controller's call path yet — it will actually fail with "Wanted but not invoked").

- [ ] **Step 3: Call `syncEmail` from `updateAddress`**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileController.java`, change:

```java
    @Override
    public ResponseEntity<AddressResponse> updateAddress(UpdateAddressRequest request) {
        AddressDto dto = userProfileService.updateAddress(
            CurrentUser.current().id(),
            request.getStreet(),
            request.getHouseNumber(),
            request.getHouseNumberAddition(),
            request.getZipCode(),
            request.getCity(),
            request.getProvince()
        );
        return ResponseEntity.ok(userProfileApiMapper.toResponse(dto));
    }
```

to:

```java
    @Override
    public ResponseEntity<AddressResponse> updateAddress(UpdateAddressRequest request) {
        CurrentUser currentUser = CurrentUser.current();
        AddressDto dto = userProfileService.updateAddress(
            currentUser.id(),
            request.getStreet(),
            request.getHouseNumber(),
            request.getHouseNumberAddition(),
            request.getZipCode(),
            request.getCity(),
            request.getProvince()
        );
        userProfileService.syncEmail(currentUser.id(), currentUser.email());
        return ResponseEntity.ok(userProfileApiMapper.toResponse(dto));
    }
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=UserProfileControllerTest`
Expected: PASS.

- [ ] **Step 5: Run the full backend test suite**

Run: `cd hermes-backend && ./mvnw -q test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileController.java hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileControllerTest.java
git commit -m "feat(profile): sync email from JWT on address update"
```

---

### Task 3: Remove the per-request `UserProfileSyncFilter`

**Files:**
- Delete: `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileSyncFilter.java`
- Delete: `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileSyncFilterConfig.java`
- Delete: `hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileSyncFilterTest.java`
- Delete: `hermes-backend/src/test/java/com/kropholler/dev/hermes/security/NoOpUserProfileSyncFilterTestConfig.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/config/SecurityConfig.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/config/SecurityConfigTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskControllerTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatHistoryControllerTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/favorites/FavoriteControllerTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingControllerTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingControllerSearchTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/notification/NotificationControllerTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/report/ReportControllerTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/scraping/ScrapingSessionControllerTest.java`

Note: `hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileControllerTest.java` also currently has the same `@Import`/import-statement pattern to revert, but Task 2 already touches that exact file for a different reason — its Step 1 test addition and this revert should be applied together there rather than as a separate touch. If you're executing tasks in order and Task 2 is already done, `UserProfileControllerTest.java`'s `@Import` will still say `@Import({SecurityConfig.class, NoOpUserProfileSyncFilterTestConfig.class})` — revert it as part of this task's Step 3 below, exactly like the other 9 files (Task 2 didn't touch the `@Import` line, only added a new test method).

**Interfaces:** None — this task only removes code, it doesn't produce anything later tasks consume.

- [ ] **Step 1: Delete the four filter-related files**

```bash
cd hermes-backend
rm src/main/java/com/kropholler/dev/hermes/profile/UserProfileSyncFilter.java
rm src/main/java/com/kropholler/dev/hermes/profile/UserProfileSyncFilterConfig.java
rm src/test/java/com/kropholler/dev/hermes/profile/UserProfileSyncFilterTest.java
rm src/test/java/com/kropholler/dev/hermes/security/NoOpUserProfileSyncFilterTestConfig.java
```

- [ ] **Step 2: Confirm the build is now broken as expected**

Run: `cd hermes-backend && ./mvnw -q compile`
Expected: FAIL — `SecurityConfig.java` still references `UserProfileSyncFilter`'s qualifier and the now-deleted types. Confirms the deletion took effect; the next steps fix the remaining references.

- [ ] **Step 3: Revert `SecurityConfig.java`**

Change `hermes-backend/src/main/java/com/kropholler/dev/hermes/config/SecurityConfig.java` from:

```java
package com.kropholler.dev.hermes.config;

import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Qualifier;
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
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(proxyTargetClass = true)
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, AccessDeniedHandler accessDeniedHandler,
            @Qualifier("userProfileSyncFilter") OncePerRequestFilter userProfileSyncFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/ws/chat/**").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(exceptionHandling -> exceptionHandling.accessDeniedHandler(accessDeniedHandler))
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
            .addFilterAfter(userProfileSyncFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }
```

to:

```java
package com.kropholler.dev.hermes.config;

import tools.jackson.databind.json.JsonMapper;
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
@EnableMethodSecurity(proxyTargetClass = true)
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
```

(The rest of the file — `accessDeniedHandler()`, `jwtAuthenticationConverter()`, `realmRoleAuthorities(...)` — is unchanged.)

- [ ] **Step 4: Revert the 10 affected test files' `@Import` lists**

For each of these 8 files, change `@Import({SecurityConfig.class, NoOpUserProfileSyncFilterTestConfig.class})` back to `@Import(SecurityConfig.class)`, and remove the now-unused `import com.kropholler.dev.hermes.security.NoOpUserProfileSyncFilterTestConfig;` line:
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/config/SecurityConfigTest.java`
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskControllerTest.java`
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatHistoryControllerTest.java`
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/favorites/FavoriteControllerTest.java`
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingControllerTest.java`
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/notification/NotificationControllerTest.java`
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileControllerTest.java`
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/scraping/ScrapingSessionControllerTest.java`

For these 2 files, change `@Import({SecurityConfig.class, SecuredMockMvcTestSupport.class, NoOpUserProfileSyncFilterTestConfig.class})` back to `@Import({SecurityConfig.class, SecuredMockMvcTestSupport.class})`, and remove the same import line:
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingControllerSearchTest.java`
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/report/ReportControllerTest.java`

- [ ] **Step 5: Run the full backend test suite**

Run: `cd hermes-backend && ./mvnw -q clean test`
Expected: PASS. This is a pure revert, so nothing here should exercise new behavior — every test file goes back to exactly its Task-6-era (from the prior plan) state, so the full suite should be green with no changes to test *logic*, only to the filter's presence.

- [ ] **Step 6: Confirm no other references to the removed filter classes remain**

Run: `cd hermes-backend && grep -rn "UserProfileSyncFilter\|NoOpUserProfileSyncFilterTestConfig" src`
Expected: no output (the exact string `UserProfileSyncFilter` — including as a prefix of `UserProfileSyncFilterConfig` — no longer appears anywhere).

- [ ] **Step 7: Commit**

```bash
git add -A hermes-backend/src/main/java/com/kropholler/dev/hermes/profile hermes-backend/src/test/java/com/kropholler/dev/hermes/profile hermes-backend/src/test/java/com/kropholler/dev/hermes/security hermes-backend/src/main/java/com/kropholler/dev/hermes/config/SecurityConfig.java hermes-backend/src/test/java/com/kropholler/dev/hermes/config/SecurityConfigTest.java hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskControllerTest.java hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatHistoryControllerTest.java hermes-backend/src/test/java/com/kropholler/dev/hermes/favorites/FavoriteControllerTest.java hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingControllerTest.java hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingControllerSearchTest.java hermes-backend/src/test/java/com/kropholler/dev/hermes/notification/NotificationControllerTest.java hermes-backend/src/test/java/com/kropholler/dev/hermes/report/ReportControllerTest.java hermes-backend/src/test/java/com/kropholler/dev/hermes/scraping/ScrapingSessionControllerTest.java
git commit -m "refactor(security): remove per-request UserProfileSyncFilter"
```

---

### Task 4: Thread the live JWT email through the chat-tool pipeline (plumbing only)

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/ChatToolProvider.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/AiChatService.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatController.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/TaskTool.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/AgentChatToolProvider.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/SaveWatchTool.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/TriggerResearchTool.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/TriggerDigestTool.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/ListWatchesTool.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/SaveAreaResearchTool.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/AiChatServiceTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatControllerTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/SaveWatchToolTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/TriggerResearchToolTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/TriggerDigestToolTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/ListWatchesToolTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/SaveAreaResearchToolTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/AgentChatToolProviderTest.java`

**Interfaces:**
- Consumes: `UserProfileService.syncEmail(UUID, String)` (Task 1).
- Produces: `TaskTool(UUID userId, AgentTaskService agentTaskService, String email)` — the new 3-arg base constructor every subclass (`SaveWatchTool`, `TriggerResearchTool`, `TriggerDigestTool`, `ListWatchesTool`) now calls via `super(userId, agentTaskService, email)`; `SaveAreaResearchTool(UUID userId, AgentTaskService agentTaskService, UserProfileRepository userProfileRepository, GeocodingService geocodingService, String email)` (email appended as the last constructor param); `TaskTool.hasEmail(): boolean` — a protected helper Task 5 uses. `ChatToolProvider.provideTools(UUID userId, String email): List<Object>`. `AiChatService.startStream(UUID sessionId, UUID userId, String email, String userMessage)`.

This task is pure plumbing — it threads the email through every layer and adds the `hasEmail()` helper, but **does not yet enforce anything**. No tool should reject a task in this task; that's Task 5. Verify at the end that behavior is unchanged (all existing assertions in the four tool test files still pass, just with an extra constructor argument).

- [ ] **Step 1: Update `ChatToolProvider` interface**

Change `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/ChatToolProvider.java` from:

```java
package com.kropholler.dev.hermes.ai;

import java.util.List;
import java.util.UUID;

/**
 * Extension point that allows other modules (e.g. agent) to contribute
 * per-request chat tools to the AI chat pipeline without creating a
 * circular dependency between the {@code ai} and {@code agent} modules.
 *
 * <p>Implementations are discovered via Spring's dependency injection
 * ({@code List<ChatToolProvider>}) and called in {@code AiChatService.startStream}.
 */
public interface ChatToolProvider {

    /**
     * Return tool instances scoped to the given user.
     * Called once per chat request — implementations may create new instances each time.
     *
     * @param userId the authenticated user's UUID for this chat session
     * @return a list of Spring AI tool objects (annotated with {@code @Tool})
     */
    List<Object> provideTools(UUID userId);
}
```

to:

```java
package com.kropholler.dev.hermes.ai;

import java.util.List;
import java.util.UUID;

/**
 * Extension point that allows other modules (e.g. agent) to contribute
 * per-request chat tools to the AI chat pipeline without creating a
 * circular dependency between the {@code ai} and {@code agent} modules.
 *
 * <p>Implementations are discovered via Spring's dependency injection
 * ({@code List<ChatToolProvider>}) and called in {@code AiChatService.startStream}.
 */
public interface ChatToolProvider {

    /**
     * Return tool instances scoped to the given user.
     * Called once per chat request — implementations may create new instances each time.
     *
     * @param userId the authenticated user's UUID for this chat session
     * @param email the authenticated user's email from their JWT, or {@code null} if the
     *              token carried none — the live value for this request, never a cached read
     * @return a list of Spring AI tool objects (annotated with {@code @Tool})
     */
    List<Object> provideTools(UUID userId, String email);
}
```

- [ ] **Step 2: Update `AiChatService.startStream` and its test**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/AiChatService.java`, change the method signature and the tool-collection loop from:

```java
    public StreamHandle startStream(UUID sessionId, UUID userId, String userMessage) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(userMessage, "userMessage must not be null");
```

to:

```java
    public StreamHandle startStream(UUID sessionId, UUID userId, String email, String userMessage) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(userMessage, "userMessage must not be null");
```

and change:

```java
        for (ChatToolProvider provider : chatToolProviders) {
            allTools.addAll(provider.provideTools(userId));
        }
```

to:

```java
        for (ChatToolProvider provider : chatToolProviders) {
            allTools.addAll(provider.provideTools(userId, email));
        }
```

In `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/AiChatServiceTest.java`, every call to `service.startStream(sessionId, userId, "...")` gains a new `email` argument in the third position. Update each of these 6 call sites (add `"user@hermes.local"` as the new 3rd argument, shifting the message string to 4th):

- `startStream_usesGivenUserId`: `service.startStream(sessionId, userId, "user@hermes.local", "hello")`
- `startStream_historyWithUserAndAssistantRoles_mapsBoth`: `service.startStream(sessionId, userId, "user@hermes.local", "more info")`
- `startStream_unknownRole_throwsIllegalStateException`: `service.startStream(sessionId, userId, "user@hermes.local", "hi")`
- `startStream_scopesHistoryToSessionAndUser`: `service.startStream(sessionId, userId, "user@hermes.local", "hello")`
- `startStream_withChatToolProvider_addsProviderTools`: change both `when(chatToolProvider.provideTools(userId)).thenReturn(...)` to `when(chatToolProvider.provideTools(userId, "user@hermes.local")).thenReturn(...)`, the `service.startStream(sessionId, userId, "hi")` call to `service.startStream(sessionId, userId, "user@hermes.local", "hi")`, and `verify(chatToolProvider).provideTools(userId)` to `verify(chatToolProvider).provideTools(userId, "user@hermes.local")`

- [ ] **Step 3: Run it to verify it fails, then passes**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=AiChatServiceTest`
Expected: first FAIL (compile error before the signature change, if you run it before Step 2's production-code edit — otherwise skip straight to running after both edits), then PASS once `AiChatService.java` and the test are both updated.

- [ ] **Step 4: Update `ChatController` to extract and thread the email, and sync it**

Change `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatController.java`'s imports and class body. Add `UserProfileService` as a constructor dependency (via `@RequiredArgsConstructor`, add the field), and update `handleMessage`:

```java
package com.kropholler.dev.hermes.ai.chat;

import com.kropholler.dev.hermes.profile.UserProfileService;
import com.kropholler.dev.hermes.security.CurrentUser;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final AiChatService aiChatService;
    private final UserProfileService userProfileService;
    private final SimpMessagingTemplate messaging;
    private final MeterRegistry meterRegistry;

    @MessageMapping("/chat")
    public void handleMessage(ChatMessageRequest request, @Header("simpUser") Principal principal) {
        if (request == null || request.sessionId() == null || request.message() == null || request.message().isBlank()) {
            log.warn("Received invalid chat request: {}", request);
            return;
        }

        CurrentUser currentUser = CurrentUser.from((Jwt) ((JwtAuthenticationToken) principal).getPrincipal());
        UUID userId = currentUser.id();
        String email = currentUser.email();

        try {
            userProfileService.syncEmail(userId, email);
        } catch (Exception e) {
            log.warn("Failed to sync email onto profile for user {}; continuing chat request", userId, e);
        }

        String destination = "/topic/chat/" + request.sessionId();
        Timer.Sample requestTimer = Timer.start(meterRegistry);
        Timer.Sample ttftTimer = Timer.start(meterRegistry);
        long startNanos = System.nanoTime();
        String outcome = "success";

        log.info("AI chat request: session={}, messageLength={}", request.sessionId(), request.message().length());

        try {
            AiChatService.StreamHandle handle = aiChatService.startStream(
                    request.sessionId(), userId, email, request.message());

            String response = streamTokens(handle, destination, request.sessionId(), ttftTimer, startNanos);

            aiChatService.saveUserMessage(request.sessionId(), userId, request.message());
            if (!response.isBlank()) {
                aiChatService.saveAssistantMessage(request.sessionId(), userId, response);
            }
            messaging.convertAndSend(destination, new ResultFrame("RESULT", handle.resultHolder().get()));

            log.info("AI chat completed: session={}, duration={}ms", request.sessionId(), elapsedMs(startNanos));

        } catch (Exception e) {
            outcome = "error";
            log.error("AI chat error: session={}, duration={}ms", request.sessionId(), elapsedMs(startNanos), e);
            messaging.convertAndSend(destination, new TokenFrame("ERROR", "Something went wrong. Please try again."));

        } finally {
            requestTimer.stop(Timer.builder("hermes.ai.chat.duration")
                    .tag("outcome", outcome)
                    .register(meterRegistry));
            meterRegistry.counter("hermes.ai.chat.requests", "outcome", outcome).increment();
        }
    }
```

(`streamTokens` and `elapsedMs` are unchanged — leave them exactly as they are.)

Note the `syncEmail` call is deliberately wrapped in its own try/catch, separate from the main streaming try/catch below it — a profile-write failure must never be reported as an "AI chat error" or count against the `hermes.ai.chat.requests` outcome metrics, and must never prevent the actual chat request from proceeding.

- [ ] **Step 5: Update `ChatControllerTest`**

In `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatControllerTest.java`:

Add `@Mock UserProfileService userProfileService;` alongside the existing `@Mock` fields, and add the import `import com.kropholler.dev.hermes.profile.UserProfileService;`.

Change the constructor call in `setUp()`:

```java
    @BeforeEach
    void setUp() {
        controller = new ChatController(aiChatService, userProfileService, messaging, new SimpleMeterRegistry());
    }
```

Change `principalFor` to include an email claim:

```java
    private Principal principalFor(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(userId.toString())
            .claim("email", "user@hermes.local")
            .claim("realm_access", Map.of("roles", List.of("user")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
        return new JwtAuthenticationToken(jwt);
    }
```

Every `when(aiChatService.startStream(sessionId, userId, request.message())).thenReturn(handle)` and matching `verify(aiChatService).startStream(sessionId, userId, request.message())` needs the new `email` argument inserted as the 3rd parameter — update these 5 call sites (in `handleMessage_streamsTokensAndSendsEmptyResult`, `handleMessage_withListings_sendsResultFrameWithCards`, `handleMessage_whitespaceOnlyResponse_doesNotSaveAssistantMessage`, `handleMessage_serviceThrows_sendsErrorTokenFrame`) to:

```java
when(aiChatService.startStream(sessionId, userId, "user@hermes.local", request.message())).thenReturn(handle);
```

and:

```java
verify(aiChatService).startStream(sessionId, userId, "user@hermes.local", request.message());
```

(only `handleMessage_streamsTokensAndSendsEmptyResult` has both a `when` and a `verify`; the others only have the `when`).

The three early-return tests (`handleMessage_nullRequest_returnsEarlyWithoutStreaming`, `handleMessage_nullSessionId_returnsEarlyWithoutStreaming`, `handleMessage_nullMessage_returnsEarlyWithoutStreaming`, `handleMessage_blankMessage_returnsEarlyWithoutStreaming`) use `verify(aiChatService, never()).startStream(any(), any(), any())` — this needs a 4th `any()` added: `verify(aiChatService, never()).startStream(any(), any(), any(), any())`.

Add one new test verifying the sync call happens:

```java
    @Test
    void handleMessage_syncsEmailFromJwt() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatMessageRequest request = new ChatMessageRequest(sessionId, "hello");
        AiChatService.StreamHandle handle = handle(Flux.just("hi"), List.of());
        when(aiChatService.startStream(sessionId, userId, "user@hermes.local", request.message())).thenReturn(handle);

        controller.handleMessage(request, principalFor(userId));

        verify(userProfileService).syncEmail(userId, "user@hermes.local");
    }
```

- [ ] **Step 6: Run it to verify it fails, then passes**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=ChatControllerTest`
Expected: PASS once both the production code (Step 4) and the test (Step 5) are in place together.

- [ ] **Step 7: Add `email` to `TaskTool`**

Change `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/TaskTool.java` from:

```java
package com.kropholler.dev.hermes.ai.agent.tool;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;

import java.util.UUID;

abstract class TaskTool {
    protected final UUID userId;
    protected final AgentTaskService agentTaskService;

    protected TaskTool(UUID userId, AgentTaskService agentTaskService) {
        this.userId = userId;
        this.agentTaskService = agentTaskService;
    }
}
```

to:

```java
package com.kropholler.dev.hermes.ai.agent.tool;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;

import java.util.UUID;

abstract class TaskTool {
    protected final UUID userId;
    protected final AgentTaskService agentTaskService;
    protected final String email;

    protected TaskTool(UUID userId, AgentTaskService agentTaskService, String email) {
        this.userId = userId;
        this.agentTaskService = agentTaskService;
        this.email = email;
    }

    /** True only if the live JWT email for this request was present and non-blank. */
    protected boolean hasEmail() {
        return email != null && !email.isBlank();
    }
}
```

- [ ] **Step 8: Update the four simple `TaskTool` subclasses' constructors (no behavior change yet)**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/SaveWatchTool.java`, change:

```java
    protected SaveWatchTool(UUID userId, AgentTaskService agentTaskService) {
        super(userId, agentTaskService);
    }
```

to:

```java
    protected SaveWatchTool(UUID userId, AgentTaskService agentTaskService, String email) {
        super(userId, agentTaskService, email);
    }
```

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/TriggerResearchTool.java`, change:

```java
    protected TriggerResearchTool(UUID userId, AgentTaskService agentTaskService) {
        super(userId, agentTaskService);
    }
```

to:

```java
    protected TriggerResearchTool(UUID userId, AgentTaskService agentTaskService, String email) {
        super(userId, agentTaskService, email);
    }
```

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/TriggerDigestTool.java`, change:

```java
    protected TriggerDigestTool(UUID userId, AgentTaskService agentTaskService) {
        super(userId, agentTaskService);
    }
```

to:

```java
    protected TriggerDigestTool(UUID userId, AgentTaskService agentTaskService, String email) {
        super(userId, agentTaskService, email);
    }
```

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/ListWatchesTool.java`, change:

```java
    protected ListWatchesTool(UUID userId, AgentTaskService agentTaskService) {
        super(userId, agentTaskService);
    }
```

to:

```java
    protected ListWatchesTool(UUID userId, AgentTaskService agentTaskService, String email) {
        super(userId, agentTaskService, email);
    }
```

- [ ] **Step 9: Update `SaveAreaResearchTool`'s constructor**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/SaveAreaResearchTool.java`, change:

```java
    protected SaveAreaResearchTool(UUID userId, AgentTaskService agentTaskService,
                                    UserProfileRepository userProfileRepository,
                                    GeocodingService geocodingService) {
        super(userId, agentTaskService);
        this.userProfileRepository = userProfileRepository;
        this.geocodingService = geocodingService;
    }
```

to:

```java
    protected SaveAreaResearchTool(UUID userId, AgentTaskService agentTaskService,
                                    UserProfileRepository userProfileRepository,
                                    GeocodingService geocodingService, String email) {
        super(userId, agentTaskService, email);
        this.userProfileRepository = userProfileRepository;
        this.geocodingService = geocodingService;
    }
```

- [ ] **Step 10: Update `AgentChatToolProvider`**

Change `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/AgentChatToolProvider.java` from:

```java
    @Override
    public List<Object> provideTools(UUID userId) {
        return List.of(
            new SaveWatchTool(userId, agentTaskService),
            new TriggerResearchTool(userId, agentTaskService),
            new TriggerDigestTool(userId, agentTaskService),
            new ListWatchesTool(userId, agentTaskService),
            new SaveAreaResearchTool(userId, agentTaskService, userProfileRepository, geocodingService)
        );
    }
```

to:

```java
    @Override
    public List<Object> provideTools(UUID userId, String email) {
        return List.of(
            new SaveWatchTool(userId, agentTaskService, email),
            new TriggerResearchTool(userId, agentTaskService, email),
            new TriggerDigestTool(userId, agentTaskService, email),
            new ListWatchesTool(userId, agentTaskService, email),
            new SaveAreaResearchTool(userId, agentTaskService, userProfileRepository, geocodingService, email)
        );
    }
```

- [ ] **Step 11: Update all five tool test files' constructor calls (mechanical, no new test cases yet)**

In `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/SaveWatchToolTest.java`, every `new SaveWatchTool(clientId, agentTaskService)` becomes `new SaveWatchTool(clientId, agentTaskService, "user@hermes.local")` (4 call sites: `createsWatchWithExtractedCriteria`, `saveWatch_nullName_buildNameFromCityBedroomsAndPrice`, `saveWatch_blankName_noFields_nameBecomesNewWatch`, `saveWatch_blankCityString_treatedAsNullInPayload`).

In `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/TriggerResearchToolTest.java`, `new TriggerResearchTool(clientId, agentTaskService)` becomes `new TriggerResearchTool(clientId, agentTaskService, "user@hermes.local")` (1 call site).

In `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/TriggerDigestToolTest.java`, both `new TriggerDigestTool(clientId, agentTaskService)` calls become `new TriggerDigestTool(clientId, agentTaskService, "user@hermes.local")` (2 call sites: `triggerDigest_delegatesToServiceAndReturnsConfirmation`, `triggerDigest_blankEntryInCitiesString_filteredOut`).

In `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/ListWatchesToolTest.java`, every `new ListWatchesTool(clientId, agentTaskService)` becomes `new ListWatchesTool(clientId, agentTaskService, "user@hermes.local")` (4 call sites: `returnsFormattedActiveWatches`, `returnsEmptyMessageWhenNoWatches`, `cancelWatchWhenCancelIdProvided`, `returnsFormattedWatchWithNullScheduleAndNonNullLastRunAt`).

In `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/SaveAreaResearchToolTest.java`, the `tool(UUID userId)` helper method currently does:

```java
    private SaveAreaResearchTool tool(UUID userId) {
        return new SaveAreaResearchTool(userId, agentTaskService, userProfileRepository, geocodingService);
    }
```

Change it to:

```java
    private SaveAreaResearchTool tool(UUID userId) {
        return new SaveAreaResearchTool(userId, agentTaskService, userProfileRepository, geocodingService, "user@hermes.local");
    }
```

(Every test in this file goes through the `tool(userId)` helper, so this one change covers all of them.)

- [ ] **Step 12: Run all five tool test files to verify they still pass unchanged**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=SaveWatchToolTest,TriggerResearchToolTest,TriggerDigestToolTest,ListWatchesToolTest,SaveAreaResearchToolTest`
Expected: PASS — this step is pure plumbing, so every existing assertion should hold exactly as before; only the constructor call sites changed.

- [ ] **Step 13: Update `AgentChatToolProviderTest`**

In `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/AgentChatToolProviderTest.java`, change:

```java
    @Test
    void provideTools_returnsListOfFiveTools() {
        List<Object> tools = provider.provideTools(UUID.randomUUID());

        assertThat(tools).hasSize(5);
```

to:

```java
    @Test
    void provideTools_returnsListOfFiveTools() {
        List<Object> tools = provider.provideTools(UUID.randomUUID(), "user@hermes.local");

        assertThat(tools).hasSize(5);
```

(the rest of the test — the `hasExactlyElementsOfTypes(...)` assertion — is unchanged).

- [ ] **Step 14: Run it to verify it passes**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=AgentChatToolProviderTest`
Expected: PASS.

- [ ] **Step 15: Run the full backend test suite**

Run: `cd hermes-backend && ./mvnw -q test`
Expected: PASS — this whole task is additive plumbing; no existing behavior should have changed.

- [ ] **Step 16: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/ChatToolProvider.java hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/AiChatService.java hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/chat/ChatController.java hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/TaskTool.java hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/AgentChatToolProvider.java hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/SaveWatchTool.java hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/TriggerResearchTool.java hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/TriggerDigestTool.java hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/ListWatchesTool.java hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/SaveAreaResearchTool.java hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/AiChatServiceTest.java hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/chat/ChatControllerTest.java hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/SaveWatchToolTest.java hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/TriggerResearchToolTest.java hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/TriggerDigestToolTest.java hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/ListWatchesToolTest.java hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/SaveAreaResearchToolTest.java hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/AgentChatToolProviderTest.java
git commit -m "feat(ai): thread live JWT email through the chat-tool pipeline"
```

---

### Task 5: Enforce "no email → no task" in the four creating tools

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/SaveWatchTool.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/TriggerResearchTool.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/TriggerDigestTool.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/SaveAreaResearchTool.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/SaveWatchToolTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/TriggerResearchToolTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/TriggerDigestToolTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/SaveAreaResearchToolTest.java`

**Interfaces:**
- Consumes: `TaskTool.hasEmail(): boolean` (Task 4).
- Produces: nothing later tasks rely on — this is the final behavioral piece of the gate.

- [ ] **Step 1: Write the failing test for `SaveWatchTool`**

Add to `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/SaveWatchToolTest.java`:

```java
    @Test
    void saveWatch_noEmail_rejectsWithoutCreatingTask() {
        AgentTaskService agentTaskService = mock(AgentTaskService.class);
        UUID clientId = UUID.randomUUID();

        SaveWatchTool tool = new SaveWatchTool(clientId, agentTaskService, null);
        String result = tool.saveWatch("Utrecht 3-bed", "Utrecht", null, null, 400000, 3, null, null, null, null, null);

        assertThat(result).contains("email address");
        verify(agentTaskService, never()).createWatch(any(), anyString(), any());
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=SaveWatchToolTest`
Expected: FAIL — `saveWatch` still creates the task regardless of email.

- [ ] **Step 3: Add the gate to `saveWatch`**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/SaveWatchTool.java`, change the start of the `saveWatch` method from:

```java
    public String saveWatch(
        @ToolParam(required = false, description = "Friendly name for this watch, e.g. 'Utrecht 3-bed under 400k'") String name,
        @ToolParam(required = false, description = "City to filter by") String city,
        @ToolParam(required = false, description = "Province to filter by") String province,
        @ToolParam(required = false, description = "Minimum asking price in euros") Integer minPrice,
        @ToolParam(required = false, description = "Maximum asking price in euros") Integer maxPrice,
        @ToolParam(required = false, description = "Minimum number of bedrooms") Integer minBedrooms,
        @ToolParam(required = false, description = "Minimum total rooms") Integer minRooms,
        @ToolParam(required = false, description = "Minimum living area in square metres") Integer minLivingAreaM2,
        @ToolParam(required = false, description = "Keywords to search in descriptions") String keywords,
        @ToolParam(required = false, description = "City to search near") String nearCity,
        @ToolParam(required = false, description = "Radius in km when nearCity is set") Integer radiusKm
    ) {
        String watchName = (name != null && !name.isBlank()) ? name : buildName(city, minBedrooms, maxPrice);
```

to:

```java
    public String saveWatch(
        @ToolParam(required = false, description = "Friendly name for this watch, e.g. 'Utrecht 3-bed under 400k'") String name,
        @ToolParam(required = false, description = "City to filter by") String city,
        @ToolParam(required = false, description = "Province to filter by") String province,
        @ToolParam(required = false, description = "Minimum asking price in euros") Integer minPrice,
        @ToolParam(required = false, description = "Maximum asking price in euros") Integer maxPrice,
        @ToolParam(required = false, description = "Minimum number of bedrooms") Integer minBedrooms,
        @ToolParam(required = false, description = "Minimum total rooms") Integer minRooms,
        @ToolParam(required = false, description = "Minimum living area in square metres") Integer minLivingAreaM2,
        @ToolParam(required = false, description = "Keywords to search in descriptions") String keywords,
        @ToolParam(required = false, description = "City to search near") String nearCity,
        @ToolParam(required = false, description = "Radius in km when nearCity is set") Integer radiusKm
    ) {
        if (!hasEmail()) {
            return "Please make sure your account has an email address before setting up notifications.";
        }
        String watchName = (name != null && !name.isBlank()) ? name : buildName(city, minBedrooms, maxPrice);
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=SaveWatchToolTest`
Expected: PASS (all tests, including the new one and the pre-existing ones — the pre-existing ones all construct the tool with `"user@hermes.local"` from Task 4 Step 11, so `hasEmail()` is true for them and behavior is unchanged).

- [ ] **Step 5: Write the failing test for `TriggerResearchTool`**

Add to `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/TriggerResearchToolTest.java`:

```java
    @Test
    void triggerResearch_noEmail_rejectsWithoutQueuingTask() {
        AgentTaskService agentTaskService = mock(AgentTaskService.class);
        UUID clientId = UUID.randomUUID();

        TriggerResearchTool tool = new TriggerResearchTool(clientId, agentTaskService, null);
        String result = tool.triggerResearch("What are the current market trends in Amsterdam?");

        assertThat(result).contains("email address");
        verify(agentTaskService, never()).createResearch(any(), anyString());
    }
```

- [ ] **Step 6: Run it to verify it fails**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=TriggerResearchToolTest`
Expected: FAIL.

- [ ] **Step 7: Add the gate to `triggerResearch`**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/TriggerResearchTool.java`, change:

```java
    public String triggerResearch(
        @ToolParam(description = "The research question or task to investigate in detail") String prompt
    ) {
        agentTaskService.createResearch(userId, prompt);
        return "Research queued — results will appear as a notification shortly.";
    }
```

to:

```java
    public String triggerResearch(
        @ToolParam(description = "The research question or task to investigate in detail") String prompt
    ) {
        if (!hasEmail()) {
            return "Please make sure your account has an email address before setting up notifications.";
        }
        agentTaskService.createResearch(userId, prompt);
        return "Research queued — results will appear as a notification shortly.";
    }
```

- [ ] **Step 8: Run it to verify it passes**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=TriggerResearchToolTest`
Expected: PASS.

- [ ] **Step 9: Write the failing test for `TriggerDigestTool`**

Add to `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/TriggerDigestToolTest.java`:

```java
    @Test
    void triggerDigest_noEmail_rejectsWithoutSchedulingTask() {
        AgentTaskService agentTaskService = mock(AgentTaskService.class);
        UUID clientId = UUID.randomUUID();

        TriggerDigestTool tool = new TriggerDigestTool(clientId, agentTaskService, null);
        String result = tool.triggerDigest("Amsterdam,Utrecht", "Weekly digest");

        assertThat(result).contains("email address");
        verify(agentTaskService, never()).createDigest(any(), anyString(), anyList());
    }
```

- [ ] **Step 10: Run it to verify it fails**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=TriggerDigestToolTest`
Expected: FAIL.

- [ ] **Step 11: Add the gate to `triggerDigest`**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/TriggerDigestTool.java`, change:

```java
    public String triggerDigest(
        @ToolParam(description = "Comma-separated list of cities to include in the digest, e.g. 'Amsterdam,Utrecht'") String cities,
        @ToolParam(description = "A short name for this digest, e.g. 'Weekly Amsterdam & Utrecht digest'") String name
    ) {
        List<String> cityList = Arrays.stream(cities.split(","))
            .map(String::strip)
            .filter(s -> !s.isBlank())
            .toList();
        agentTaskService.createDigest(userId, name, cityList);
        return "Weekly digest scheduled for " + cities + " — I'll send you a market summary every Monday morning.";
    }
```

to:

```java
    public String triggerDigest(
        @ToolParam(description = "Comma-separated list of cities to include in the digest, e.g. 'Amsterdam,Utrecht'") String cities,
        @ToolParam(description = "A short name for this digest, e.g. 'Weekly Amsterdam & Utrecht digest'") String name
    ) {
        if (!hasEmail()) {
            return "Please make sure your account has an email address before setting up notifications.";
        }
        List<String> cityList = Arrays.stream(cities.split(","))
            .map(String::strip)
            .filter(s -> !s.isBlank())
            .toList();
        agentTaskService.createDigest(userId, name, cityList);
        return "Weekly digest scheduled for " + cities + " — I'll send you a market summary every Monday morning.";
    }
```

- [ ] **Step 12: Run it to verify it passes**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=TriggerDigestToolTest`
Expected: PASS.

- [ ] **Step 13: Write the failing test for `SaveAreaResearchTool`**

Add to `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/SaveAreaResearchToolTest.java`:

```java
    @Test
    void saveAreaResearch_noEmail_rejectsBeforeAnyOtherValidation() {
        UUID userId = UUID.randomUUID();
        SaveAreaResearchTool tool = new SaveAreaResearchTool(userId, agentTaskService, userProfileRepository, geocodingService, null);

        String result = tool.saveAreaResearch(null, 15, null, null, null, null, null, null, null, null, null);

        assertThat(result).contains("email address");
        verify(agentTaskService, never()).createAreaResearch(any(), anyString(), any());
        verifyNoInteractions(userProfileRepository, geocodingService);
    }
```

(This test constructs the tool directly rather than via the file's `tool(UUID userId)` helper, since that helper always passes `"user@hermes.local"` — see Task 4 Step 11.)

- [ ] **Step 14: Run it to verify it fails**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=SaveAreaResearchToolTest`
Expected: FAIL.

- [ ] **Step 15: Add the gate to `saveAreaResearch`, before the existing address/override validation**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/SaveAreaResearchTool.java`, change the start of the `saveAreaResearch` method from:

```java
    ) {
        Double overrideLon = null;
        Double overrideLat = null;

        if (hasOverride(nearAddress, nearCity)) {
```

to:

```java
    ) {
        if (!hasEmail()) {
            return "Please make sure your account has an email address before setting up notifications.";
        }

        Double overrideLon = null;
        Double overrideLat = null;

        if (hasOverride(nearAddress, nearCity)) {
```

(Placed before the address/override checks so a user with no email gets the email message rather than an address message, matching Step 13's test asserting `verifyNoInteractions(userProfileRepository, geocodingService)` — neither should be touched if the email gate already rejected.)

- [ ] **Step 16: Run it to verify it passes**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=SaveAreaResearchToolTest`
Expected: PASS (all tests, including the new one — the file's other tests all go through the `tool(userId)` helper which passes a non-blank email, so they're unaffected).

- [ ] **Step 17: Run the full backend test suite**

Run: `cd hermes-backend && ./mvnw -q clean test`
Expected: PASS.

- [ ] **Step 18: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/SaveWatchTool.java hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/TriggerResearchTool.java hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/TriggerDigestTool.java hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/SaveAreaResearchTool.java hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/SaveWatchToolTest.java hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/TriggerResearchToolTest.java hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/TriggerDigestToolTest.java hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/SaveAreaResearchToolTest.java
git commit -m "feat(ai): reject task creation when the user has no email on file"
```

---

### Task 6: `EmailNotificationSender`: skip instead of falling back; remove dead config

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/EmailNotificationSender.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/notification/EmailNotificationSenderTest.java`
- Modify: `hermes-backend/src/main/resources/application.properties`

**Interfaces:** None — this is the terminal consumer of the cached email; nothing downstream depends on its internals.

- [ ] **Step 1: Write the failing test**

Replace the entire contents of `hermes-backend/src/test/java/com/kropholler/dev/hermes/notification/EmailNotificationSenderTest.java` with:

```java
package com.kropholler.dev.hermes.notification;

import com.kropholler.dev.hermes.profile.UserProfileEntity;
import com.kropholler.dev.hermes.profile.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailNotificationSenderTest {

    @Mock JavaMailSender mailSender;
    @Mock NotificationRepository notificationRepository;
    @Mock UserProfileRepository userProfileRepository;
    @InjectMocks EmailNotificationSender sender;

    private void setEmail(String from) {
        ReflectionTestUtils.setField(sender, "fromEmail", from);
    }

    private NotificationDto dto(UUID id) {
        return new NotificationDto(id, null, UUID.randomUUID(),
            "Price alert", "Dropped 10%", List.of(), false, null, null);
    }

    private UserProfileEntity profileWithEmail(String email) {
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(UUID.randomUUID());
        profile.setEmail(email);
        return profile;
    }

    @Test
    void sendAsync_sendsMailWithCorrectFields() {
        UUID id = UUID.randomUUID();
        NotificationEntity entity = new NotificationEntity();
        setEmail("from@hermes.nl");
        when(userProfileRepository.findById(any())).thenReturn(Optional.of(profileWithEmail("to@user.nl")));
        when(notificationRepository.findById(id)).thenReturn(Optional.of(entity));

        sender.sendAsync(dto(id));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getFrom()).isEqualTo("from@hermes.nl");
        assertThat(msg.getTo()).containsExactly("to@user.nl");
        assertThat(msg.getSubject()).isEqualTo("[Hermes] Price alert");
        assertThat(msg.getText()).isEqualTo("Dropped 10%");
    }

    @Test
    void sendAsync_updatesEmailSentAt() {
        UUID id = UUID.randomUUID();
        NotificationEntity entity = new NotificationEntity();
        setEmail("from@hermes.nl");
        when(userProfileRepository.findById(any())).thenReturn(Optional.of(profileWithEmail("to@user.nl")));
        when(notificationRepository.findById(id)).thenReturn(Optional.of(entity));

        sender.sendAsync(dto(id));

        ArgumentCaptor<NotificationEntity> saved = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getEmailSentAt()).isNotNull();
    }

    @Test
    void sendAsync_swallowsMailException() {
        UUID id = UUID.randomUUID();
        setEmail("from@hermes.nl");
        when(userProfileRepository.findById(any())).thenReturn(Optional.of(profileWithEmail("to@user.nl")));
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        sender.sendAsync(dto(id));

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void sendAsync_userHasProfileEmail_sendsToProfileEmail() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        NotificationDto dto = new NotificationDto(id, null, userId,
            "Price alert", "Dropped 10%", List.of(), false, null, null);
        setEmail("from@hermes.nl");
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(userId);
        profile.setEmail("actualuser@hermes.local");
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(notificationRepository.findById(id)).thenReturn(Optional.of(new NotificationEntity()));

        sender.sendAsync(dto);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("actualuser@hermes.local");
    }

    @Test
    void sendAsync_userHasNoProfileEmail_skipsSendingAndDoesNotThrow() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        NotificationDto dto = new NotificationDto(id, null, userId,
            "Price alert", "Dropped 10%", List.of(), false, null, null);
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());

        sender.sendAsync(dto);

        verifyNoInteractions(mailSender);
        verify(notificationRepository, never()).save(any());
    }
}
```

This: renames the `setEmails(String, String)` helper to a single-argument `setEmail(String)` (there's no more `toEmail` field to set once Step 3 removes it), adds a `profileWithEmail(String)` helper, gives the three previously-fallback-reliant tests (`sendAsync_sendsMailWithCorrectFields`, `sendAsync_updatesEmailSentAt`, `sendAsync_swallowsMailException`) an explicit profile-with-email stub instead, and replaces `sendAsync_userHasNoProfileEmail_fallsBackToConfigValue` with `sendAsync_userHasNoProfileEmail_skipsSendingAndDoesNotThrow`.

- [ ] **Step 2: Run it to verify it fails**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=EmailNotificationSenderTest`
Expected: FAIL — `setEmails(String, String)` no longer matches (compile error) until Step 3's production change removes the second field, or the assertions fail against old fallback behavior.

- [ ] **Step 3: Update `EmailNotificationSender`**

Change `hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/EmailNotificationSender.java` from:

```java
package com.kropholler.dev.hermes.notification;

import com.kropholler.dev.hermes.profile.UserProfileEntity;
import com.kropholler.dev.hermes.profile.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
class EmailNotificationSender {

    private final JavaMailSender mailSender;
    private final NotificationRepository notificationRepository;
    private final UserProfileRepository userProfileRepository;

    @Value("${hermes.notifications.from-email}")
    private String fromEmail;

    @Value("${hermes.notifications.to-email}")
    private String toEmail;

    @Async
    public void sendAsync(NotificationDto dto) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(resolveRecipient(dto.userId()));
            msg.setSubject("[Hermes] " + dto.title());
            msg.setText(dto.body());
            mailSender.send(msg);
            notificationRepository.findById(dto.id()).ifPresent(n -> {
                n.setEmailSentAt(Instant.now());
                notificationRepository.save(n);
            });
        } catch (Exception e) {
            log.error("Failed to send notification email for {}", dto.id(), e);
        }
    }

    private String resolveRecipient(UUID userId) {
        return userProfileRepository.findById(userId)
            .map(UserProfileEntity::getEmail)
            .filter(email -> email != null && !email.isBlank())
            .orElse(toEmail);
    }
}
```

to:

```java
package com.kropholler.dev.hermes.notification;

import com.kropholler.dev.hermes.profile.UserProfileEntity;
import com.kropholler.dev.hermes.profile.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
class EmailNotificationSender {

    private final JavaMailSender mailSender;
    private final NotificationRepository notificationRepository;
    private final UserProfileRepository userProfileRepository;

    @Value("${hermes.notifications.from-email}")
    private String fromEmail;

    @Async
    public void sendAsync(NotificationDto dto) {
        Optional<String> recipient = resolveRecipient(dto.userId());
        if (recipient.isEmpty()) {
            log.warn("Skipping notification email for {}: no email on file for user {}", dto.id(), dto.userId());
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(recipient.get());
            msg.setSubject("[Hermes] " + dto.title());
            msg.setText(dto.body());
            mailSender.send(msg);
            notificationRepository.findById(dto.id()).ifPresent(n -> {
                n.setEmailSentAt(Instant.now());
                notificationRepository.save(n);
            });
        } catch (Exception e) {
            log.error("Failed to send notification email for {}", dto.id(), e);
        }
    }

    private Optional<String> resolveRecipient(UUID userId) {
        return userProfileRepository.findById(userId)
            .map(UserProfileEntity::getEmail)
            .filter(email -> email != null && !email.isBlank());
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=EmailNotificationSenderTest`
Expected: PASS.

- [ ] **Step 5: Remove the now-dead config property**

In `hermes-backend/src/main/resources/application.properties`, remove this line entirely:

```
hermes.notifications.to-email=${MAIL_TO:matthijsk2000@gmail.com}
```

(Leave `hermes.notifications.from-email=${MAIL_USERNAME:noreply@hermes.local}` — that one's still used.)

- [ ] **Step 6: Run the full backend test suite**

Run: `cd hermes-backend && ./mvnw -q clean test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/EmailNotificationSender.java hermes-backend/src/test/java/com/kropholler/dev/hermes/notification/EmailNotificationSenderTest.java hermes-backend/src/main/resources/application.properties
git commit -m "fix(notification): skip sending instead of falling back to a hardcoded address"
```

---

### Task 7: Full-suite verification and design doc cross-check

**Files:** None (verification only).

- [ ] **Step 1: Run the complete backend build and test suite**

Run: `cd hermes-backend && ./mvnw -q clean verify`
Expected: `BUILD SUCCESS`, 0 failures, 0 errors.

- [ ] **Step 2: Confirm no leftover references to the removed filter**

Run: `grep -rn "UserProfileSyncFilter" hermes-backend/src`
Expected: no output.

- [ ] **Step 3: Confirm the dead config property is gone**

Run: `grep -rn "hermes.notifications.to-email\|MAIL_TO" hermes-backend/src`
Expected: no output.

- [ ] **Step 4: Confirm the Spring Modulith module structure still verifies clean**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=HermesBackendApplicationTests#verifyModuleStructure`
Expected: PASS (this task didn't introduce any new cross-package edges beyond what Task 9/10 of the prior plan already established — `ai -> profile` via `ChatController`/`AgentChatToolProvider` was already present before this plan; removing the filter only *removes* the `config -> profile` edge that used to require the earlier Modulith fix, so this should be unambiguously easier to satisfy than before, not harder).

- [ ] **Step 5: Manually verify the four creation-gate rejection messages are identical**

Run: `grep -rn "email address before setting up notifications" hermes-backend/src/main/java`
Expected: exactly 4 matches (one per creating tool: `SaveWatchTool.java`, `TriggerResearchTool.java`, `TriggerDigestTool.java`, `SaveAreaResearchTool.java`), all with the identical string specified in the Global Constraints section.
