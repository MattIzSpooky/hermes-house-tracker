# Keycloak + JWT Auth Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up Keycloak as Hermes's identity provider — a reproducible realm (roles `admin`/`user`, 2 test users), Spring Security JWT validation on the backend, and an Angular Authorization-Code+PKCE login flow on the frontend — without yet touching the existing `clientId` scoping of favorites/chat/notifications/agent-tasks (that's a later phase).

**Architecture:** A `keycloak` container is added to the existing `hermes-backend/docker-compose.yml`, importing a committed `realm-export.json` on startup. The backend adds `spring-boot-starter-oauth2-resource-server` and a `SecurityConfig` that requires authentication on `/api/**` (no role checks yet) and maps Keycloak's `realm_access.roles` claim to Spring `ROLE_*` authorities. The Angular frontend adds `keycloak-angular`/`keycloak-js`, a route guard, and an HTTP interceptor that attaches the bearer token to `/api/**` calls.

**Tech Stack:** Keycloak 26 (Docker), Spring Boot 4.0.6 `spring-boot-starter-oauth2-resource-server`, Spring Security, Angular 22, `keycloak-angular` + `keycloak-js`.

## Global Constraints

- Existing endpoints must keep their current request/response shape — this phase only adds an authentication requirement, not new authorization logic or new fields.
- No change to `clientId`/`sessionId` usage anywhere in this phase (favorites, chat, notifications, agent tasks are explicitly out of scope — see spec).
- `realm-export.json` must be committed to the repo (not gitignored) so `docker compose up` reproduces the realm with zero manual Keycloak configuration.
- Two test users must exist after realm import: `testuser` (role `user`) and `testadmin` (role `admin`), both password `password`.
- Backend: stateless sessions, CSRF disabled (token API, no cookies).
- All new/modified backend tests must pass with `mvn test` (offline, no live Keycloak required — see Task 4 for why this matters).

---

## Task 1: Keycloak docker-compose service + realm export

**Files:**
- Create: `hermes-backend/keycloak/realm-export.json`
- Modify: `hermes-backend/docker-compose.yml`

**Interfaces:**
- Produces: a realm reachable at `http://localhost:8081/realms/hermes` (browser-facing) / `http://keycloak:8080/realms/hermes` (Docker-internal), with realm roles `admin`/`user`, client `hermes-frontend`, and users `testuser`/`testadmin`.

- [ ] **Step 1: Write the realm export file**

Create `hermes-backend/keycloak/realm-export.json`:

```json
{
  "realm": "hermes",
  "enabled": true,
  "sslRequired": "none",
  "registrationAllowed": false,
  "roles": {
    "realm": [
      { "name": "admin", "description": "Full access, including scraping and user management" },
      { "name": "user", "description": "Search and manage own address, favorites, and chat" }
    ]
  },
  "clients": [
    {
      "clientId": "hermes-frontend",
      "enabled": true,
      "publicClient": true,
      "protocol": "openid-connect",
      "standardFlowEnabled": true,
      "implicitFlowEnabled": false,
      "directAccessGrantsEnabled": false,
      "serviceAccountsEnabled": false,
      "redirectUris": [
        "http://localhost:4200/*",
        "http://localhost:4200"
      ],
      "webOrigins": [
        "http://localhost:4200"
      ],
      "attributes": {
        "pkce.code.challenge.method": "S256"
      }
    }
  ],
  "users": [
    {
      "username": "testuser",
      "enabled": true,
      "email": "testuser@hermes.local",
      "emailVerified": true,
      "firstName": "Test",
      "lastName": "User",
      "credentials": [
        { "type": "password", "value": "password", "temporary": false }
      ],
      "realmRoles": ["user"]
    },
    {
      "username": "testadmin",
      "enabled": true,
      "email": "testadmin@hermes.local",
      "emailVerified": true,
      "firstName": "Test",
      "lastName": "Admin",
      "credentials": [
        { "type": "password", "value": "password", "temporary": false }
      ],
      "realmRoles": ["admin"]
    }
  ]
}
```

- [ ] **Step 2: Add the Keycloak service to docker-compose**

In `hermes-backend/docker-compose.yml`, add a new `keycloak` service (top-level, alongside `activemq`, `funda-proxy`, etc.):

```yaml
  keycloak:
    image: quay.io/keycloak/keycloak:26.0
    command: ["start-dev", "--import-realm"]
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
      KC_HOSTNAME: http://localhost:8081
    ports:
      - "8081:8080"
    volumes:
      - ./keycloak/realm-export.json:/opt/keycloak/data/import/realm-export.json:ro
    healthcheck:
      test: ["CMD-SHELL", "exec 3<>/dev/tcp/localhost/8080 && echo -e 'GET /realms/hermes HTTP/1.1\\r\\nhost: localhost\\r\\n\\r\\n' >&3 && grep -q 'hermes' <&3"]
      interval: 10s
      timeout: 5s
      retries: 15
      start_period: 30s
```

`KC_HOSTNAME` is pinned to the browser-facing URL so tokens Keycloak issues carry `http://localhost:8081/realms/hermes` as the `iss` claim, matching what both the Angular app and (via the issuer-uri override below) the backend expect.

- [ ] **Step 3: Wire the backend to depend on Keycloak and know its issuer**

In the `backend` service of `hermes-backend/docker-compose.yml`, add to `environment`:

```yaml
      SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI: http://localhost:8081/realms/hermes
```

and to `depends_on`:

```yaml
      keycloak:
        condition: service_healthy
```

Note: the issuer-uri here uses `localhost:8081`, not the Docker-internal `keycloak:8080` hostname — this is required so it matches the `iss` claim in tokens (see Step 2). The backend container can still resolve `localhost:8081` back to the Keycloak container as long as `ports: ["8081:8080"]` is published on the host network Docker Compose sets up between containers on the same bridge is NOT how this resolves — **this only works if the backend container can reach `localhost:8081`, which requires `network_mode: host` or a published port from the compose network's perspective is not "localhost" for other containers.** To avoid this pitfall, use the Docker Compose service alias for the JWKS/discovery fetch and only rely on the issuer claim matching via a decoder built from `jwk-set-uri` instead of `issuer-uri`. Concretely, replace the `issuer-uri` env var above with:

```yaml
      SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI: http://keycloak:8080/realms/hermes/protocol/openid-connect/certs
      SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI: http://localhost:8081/realms/hermes
```

Spring Boot's `OAuth2ResourceServerAutoConfiguration` prefers `jwk-set-uri` when both are set, using it purely for key retrieval (reachable via the internal Docker network) while still validating the `iss` claim against `issuer-uri` (reachable conceptually — Spring validates the claim string equality only, it does not need to dereference `issuer-uri` over the network when `jwk-set-uri` is present).

- [ ] **Step 4: Verify manually**

```bash
docker compose up -d keycloak
```

Wait for it to become healthy (`docker compose ps`), then:

```bash
curl -s http://localhost:8081/realms/hermes/.well-known/openid-configuration | grep issuer
```

Expected: `"issuer":"http://localhost:8081/realms/hermes"`.

```bash
curl -s -X POST http://localhost:8081/realms/hermes/protocol/openid-connect/token \
  -d 'client_id=hermes-frontend' -d 'grant_type=password' \
  -d 'username=testuser' -d 'password=password' | grep -o '"access_token":"[^"]*"' | head -c 50
```

Expected: a JSON fragment starting with `"access_token":"eyJ...` (this direct grant only works for this manual curl check because `directAccessGrantsEnabled` would need to be `true` — if the above returns an `unauthorized_client` error instead, that's expected given `directAccessGrantsEnabled: false` in the realm export; in that case just confirm the discovery document call above succeeded and move on — the actual login path is exercised via the browser in Task 9).

- [ ] **Step 5: Commit**

```bash
git add hermes-backend/keycloak/realm-export.json hermes-backend/docker-compose.yml
git commit -m "infra: add Keycloak service and reproducible realm export"
```

---

## Task 2: Backend — Spring Security resource server + role mapping

**Files:**
- Modify: `hermes-backend/pom.xml`
- Modify: `hermes-backend/src/main/resources/application.properties`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/config/SecurityConfig.java`
- Test: `hermes-backend/src/test/java/com/kropholler/dev/hermes/config/SecurityConfigTest.java`

**Interfaces:**
- Produces: `SecurityConfig.jwtAuthenticationConverter()` returning a `JwtAuthenticationConverter` that maps `realm_access.roles` to `ROLE_<UPPERCASE ROLE>` `GrantedAuthority`s; a `SecurityFilterChain` requiring authentication on everything except `/actuator/health/**` and `/ws/chat/**`.
- Consumes: none (first security artifact in the codebase).

- [ ] **Step 1: Add the resource-server dependency**

In `hermes-backend/pom.xml`, add inside `<dependencies>` (after the `spring-boot-starter-validation` dependency at line 103):

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
        </dependency>
```

Also add the test-support artifact for `SecurityMockMvcRequestPostProcessors` inside the test-scoped dependencies block (after the `spring-boot-starter-webmvc-test` dependency at line 217):

```xml
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: Add the issuer/JWK config with local-dev defaults**

In `hermes-backend/src/main/resources/application.properties`, add a new section (after the "Funda proxy" section):

```properties
# Keycloak / OAuth2 resource server
spring.security.oauth2.resourceserver.jwt.issuer-uri=${SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI:http://localhost:8081/realms/hermes}
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=${SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI:http://localhost:8081/realms/hermes/protocol/openid-connect/certs}
```

- [ ] **Step 3: Write `SecurityConfig`**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/config/SecurityConfig.java`:

```java
package com.kropholler.dev.hermes.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/ws/chat/**").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
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

- [ ] **Step 4: Write `SecurityConfigTest`**

Create `hermes-backend/src/test/java/com/kropholler/dev/hermes/config/SecurityConfigTest.java`:

```java
package com.kropholler.dev.hermes.config;

import com.kropholler.dev.hermes.favorites.FavoriteApiMapper;
import com.kropholler.dev.hermes.favorites.FavoriteController;
import com.kropholler.dev.hermes.favorites.FavoriteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FavoriteController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean FavoriteService favoriteService;
    @MockitoBean FavoriteApiMapper favoriteApiMapper;

    @Test
    void unauthenticatedRequest_isRejectedWith401() throws Exception {
        UUID clientId = UUID.randomUUID();

        mockMvc.perform(get("/api/favorites/{clientId}", clientId))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequest_isAllowedThrough() throws Exception {
        UUID clientId = UUID.randomUUID();
        when(favoriteService.findByClientId(clientId)).thenReturn(List.of());

        mockMvc.perform(get("/api/favorites/{clientId}", clientId).with(jwt()))
            .andExpect(status().isOk());
    }

    @Test
    void jwtAuthenticationConverter_mapsRealmRolesToAuthorities() {
        SecurityConfig config = new SecurityConfig();
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(UUID.randomUUID().toString())
            .claim("realm_access", Map.of("roles", List.of("admin", "user")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();

        var authentication = config.jwtAuthenticationConverter().convert(jwt);

        assertThat(authentication.getAuthorities())
            .extracting(GrantedAuthority::getAuthority)
            .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
    }
}
```

- [ ] **Step 5: Run the new test to verify it passes**

Run: `mvn -pl . -am test -Dtest=SecurityConfigTest -f hermes-backend/pom.xml`
Expected: 3 tests run, 0 failures. (This runs without a live Keycloak because `jwtDecoder` is a `@MockitoBean` — the auto-configured decoder from `issuer-uri`/`jwk-set-uri` is never constructed in this slice test.)

- [ ] **Step 6: Commit**

```bash
git add hermes-backend/pom.xml hermes-backend/src/main/resources/application.properties \
        hermes-backend/src/main/java/com/kropholler/dev/hermes/config/SecurityConfig.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/config/SecurityConfigTest.java
git commit -m "feat(backend): require JWT authentication on /api/** via Spring Security resource server"
```

---

## Task 3: Backend — `CurrentUser` helper for later phases

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/security/CurrentUser.java`
- Test: `hermes-backend/src/test/java/com/kropholler/dev/hermes/security/CurrentUserTest.java`

**Interfaces:**
- Produces: `CurrentUser` record (`id: UUID`, `username: String`, `roles: Set<String>`) and `CurrentUser.from(Jwt jwt)` static factory. Not wired into any controller in this phase — phases 2/3 will inject `@AuthenticationPrincipal Jwt` and call `CurrentUser.from(jwt)`.
- Consumes: `org.springframework.security.oauth2.jwt.Jwt` (from Task 2's dependency).

- [ ] **Step 1: Write the failing test**

Create `hermes-backend/src/test/java/com/kropholler/dev/hermes/security/CurrentUserTest.java`:

```java
package com.kropholler.dev.hermes.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserTest {

    @Test
    void from_extractsIdUsernameAndRoles() {
        UUID subject = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(subject.toString())
            .claim("preferred_username", "testuser")
            .claim("realm_access", Map.of("roles", List.of("user")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();

        CurrentUser currentUser = CurrentUser.from(jwt);

        assertThat(currentUser.id()).isEqualTo(subject);
        assertThat(currentUser.username()).isEqualTo("testuser");
        assertThat(currentUser.roles()).containsExactly("user");
    }

    @Test
    void from_missingRealmAccess_returnsEmptyRoles() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(UUID.randomUUID().toString())
            .claim("preferred_username", "testuser")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();

        CurrentUser currentUser = CurrentUser.from(jwt);

        assertThat(currentUser.roles()).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=CurrentUserTest -f hermes-backend/pom.xml`
Expected: FAIL — compilation error, `CurrentUser` does not exist.

- [ ] **Step 3: Write `CurrentUser`**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/security/CurrentUser.java`:

```java
package com.kropholler.dev.hermes.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record CurrentUser(UUID id, String username, Set<String> roles) {

    public static CurrentUser from(Jwt jwt) {
        UUID id = UUID.fromString(jwt.getSubject());
        String username = jwt.getClaimAsString("preferred_username");
        return new CurrentUser(id, username, extractRoles(jwt));
    }

    @SuppressWarnings("unchecked")
    private static Set<String> extractRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof List<?> roles)) {
            return Set.of();
        }
        return roles.stream().map(Object::toString).collect(Collectors.toUnmodifiableSet());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=CurrentUserTest -f hermes-backend/pom.xml`
Expected: PASS, 2 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/security/CurrentUser.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/security/CurrentUserTest.java
git commit -m "feat(backend): add CurrentUser helper for extracting identity/roles from a JWT"
```

---

## Task 4: Backend — make existing MockMvc tests authenticate

**Context:** Task 2 makes every `/api/**` endpoint require authentication. All existing `@WebMvcTest` classes will now fail with 401s unless their requests carry a JWT. Rather than editing all 23 `mockMvc.perform(...)` calls across 7 files individually, this task adds one shared `@TestConfiguration` that (a) supplies a mock `JwtDecoder` bean so no real network call to Keycloak happens during the test's context startup, and (b) registers a `MockMvcBuilderCustomizer` that attaches `SecurityMockMvcRequestPostProcessors.jwt()` as the default request post-processor, so every `mockMvc.perform(...)` call in an importing test class is authenticated automatically.

**Files:**
- Create: `hermes-backend/src/test/java/com/kropholler/dev/hermes/security/SecuredMockMvcTestSupport.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/favorites/FavoriteControllerTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingControllerTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingControllerSearchTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/notification/NotificationControllerTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/report/ReportControllerTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/scraping/ScrapingSessionControllerTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskControllerTest.java`

**Interfaces:**
- Consumes: `com.kropholler.dev.hermes.config.SecurityConfig` (Task 2).
- Produces: `SecuredMockMvcTestSupport`, a `@TestConfiguration` any `@WebMvcTest` can `@Import` alongside `SecurityConfig` to make all of its `mockMvc.perform(...)` calls pass authentication automatically.

- [ ] **Step 1: Write `SecuredMockMvcTestSupport`**

Create `hermes-backend/src/test/java/com/kropholler/dev/hermes/security/SecuredMockMvcTestSupport.java`:

```java
package com.kropholler.dev.hermes.security;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@TestConfiguration
public class SecuredMockMvcTestSupport {

    @Bean
    JwtDecoder jwtDecoder() {
        return Mockito.mock(JwtDecoder.class);
    }

    @Bean
    MockMvcBuilderCustomizer authenticatedRequestCustomizer() {
        return builder -> builder.defaultRequest(get("/").with(jwt()));
    }
}
```

- [ ] **Step 2: Import it into `FavoriteControllerTest` and run**

In `hermes-backend/src/test/java/com/kropholler/dev/hermes/favorites/FavoriteControllerTest.java`, add imports and the `@Import` annotation:

```java
import com.kropholler.dev.hermes.config.SecurityConfig;
import com.kropholler.dev.hermes.security.SecuredMockMvcTestSupport;
import org.springframework.context.annotation.Import;
```

and change the class annotation from:

```java
@WebMvcTest(FavoriteController.class)
class FavoriteControllerTest {
```

to:

```java
@WebMvcTest(FavoriteController.class)
@Import({SecurityConfig.class, SecuredMockMvcTestSupport.class})
class FavoriteControllerTest {
```

Run: `mvn test -Dtest=FavoriteControllerTest -f hermes-backend/pom.xml`
Expected: PASS, 3 tests, 0 failures.

- [ ] **Step 3: Apply the same import to the remaining 6 test classes**

For each of the following files, add the same three imports and the same `@Import({SecurityConfig.class, SecuredMockMvcTestSupport.class})` line directly above the class declaration (adjust only the package-relative import paths, which are identical across all of them since `SecurityConfig` and `SecuredMockMvcTestSupport` are fixed packages):

- `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingControllerTest.java` — change `@WebMvcTest(ListingController.class)` line to add `@Import(...)` below it.
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingControllerSearchTest.java`
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/notification/NotificationControllerTest.java`
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/report/ReportControllerTest.java`
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/scraping/ScrapingSessionControllerTest.java`
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskControllerTest.java`

Example for `ListingControllerTest.java` — add imports after the existing `com.kropholler.dev.hermes.scraping.ScrapingSessionType;` import:

```java
import com.kropholler.dev.hermes.config.SecurityConfig;
import com.kropholler.dev.hermes.security.SecuredMockMvcTestSupport;
```

and add `import org.springframework.context.annotation.Import;` next to the existing `org.springframework.beans.factory.annotation.Autowired;` import. Change:

```java
@WebMvcTest(ListingController.class)
class ListingControllerTest {
```

to:

```java
@WebMvcTest(ListingController.class)
@Import({SecurityConfig.class, SecuredMockMvcTestSupport.class})
class ListingControllerTest {
```

Repeat the identical pattern (same two new imports, same `import org.springframework.context.annotation.Import;`, same `@Import({SecurityConfig.class, SecuredMockMvcTestSupport.class})` line inserted directly above `class <Name> {`) for `ListingControllerSearchTest`, `NotificationControllerTest`, `ReportControllerTest`, `ScrapingSessionControllerTest`, and `AgentTaskControllerTest`.

- [ ] **Step 4: Run the full backend test suite**

Run: `mvn test -f hermes-backend/pom.xml`
Expected: BUILD SUCCESS, all previously-passing tests still pass (now authenticated by default via `SecuredMockMvcTestSupport`).

- [ ] **Step 5: Commit**

```bash
git add hermes-backend/src/test/java/com/kropholler/dev/hermes/security/SecuredMockMvcTestSupport.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/favorites/FavoriteControllerTest.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingControllerTest.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingControllerSearchTest.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/notification/NotificationControllerTest.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/report/ReportControllerTest.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/scraping/ScrapingSessionControllerTest.java \
        hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskControllerTest.java
git commit -m "test(backend): authenticate existing MockMvc slice tests now that /api/** requires a JWT"
```

---

## Task 5: Frontend — Keycloak bootstrap

**Files:**
- Modify: `hermes-frontend/package.json`
- Modify: `hermes-frontend/src/app/app.config.ts`

**Interfaces:**
- Produces: an Angular `Keycloak` DI token (from `keycloak-angular`) available app-wide via `inject(Keycloak)`, initialized against realm `hermes`, client `hermes-frontend`.

- [ ] **Step 1: Install dependencies**

Run: `npm install keycloak-angular keycloak-js --save --prefix hermes-frontend`
Expected: `package.json` gains `keycloak-angular` and `keycloak-js` entries under `dependencies`.

- [ ] **Step 2: Add `provideKeycloak` to `app.config.ts`**

Modify `hermes-frontend/src/app/app.config.ts`:

```ts
import { ApplicationConfig, provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withFetch } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { provideKeycloak } from 'keycloak-angular';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    provideRouter(routes),
    provideKeycloak({
      config: {
        url: 'http://localhost:8081',
        realm: 'hermes',
        clientId: 'hermes-frontend',
      },
      initOptions: {
        onLoad: 'check-sso',
        pkceMethod: 'S256',
      },
    }),
    provideHttpClient(withFetch()),
    provideAnimationsAsync(),
    provideCharts(withDefaultRegisterables()),
  ]
};
```

`provideHttpClient` is unchanged from today — the bearer-token interceptor is added on top of it in Task 7.

- [ ] **Step 3: Verify the app still boots**

Run: `npm run build --prefix hermes-frontend`
Expected: build succeeds with no compilation errors (Keycloak won't be reachable yet since nothing forces a login — `check-sso` only silently checks for an existing session and does not redirect).

- [ ] **Step 4: Commit**

```bash
git add hermes-frontend/package.json hermes-frontend/package-lock.json hermes-frontend/src/app/app.config.ts
git commit -m "feat(frontend): bootstrap keycloak-angular against the hermes realm"
```

---

## Task 6: Frontend — route guard

**Files:**
- Create: `hermes-frontend/src/app/core/auth.guard.ts`
- Modify: `hermes-frontend/src/app/app.routes.ts`

**Interfaces:**
- Consumes: `provideKeycloak` config from Task 5.
- Produces: `authGuard: CanActivateFn`, applied to every route so unauthenticated navigation redirects to Keycloak's hosted login.

- [ ] **Step 1: Write the guard**

Create `hermes-frontend/src/app/core/auth.guard.ts`:

```ts
import { AuthGuardData, createAuthGuard } from 'keycloak-angular';
import { CanActivateFn } from '@angular/router';

const isAuthenticated = async (
  _route: unknown,
  _state: unknown,
  authData: AuthGuardData,
): Promise<boolean> => authData.authenticated;

export const authGuard: CanActivateFn = createAuthGuard(isAuthenticated);
```

- [ ] **Step 2: Apply it to every route**

Modify `hermes-frontend/src/app/app.routes.ts`:

```ts
import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: '/listings', pathMatch: 'full' },
  {
    path: 'listings',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/listings/listings-page.component').then(
        m => m.ListingsPageComponent
      ),
  },
  {
    path: 'listings/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/listing-detail/listing-detail-page.component').then(
        m => m.ListingDetailPageComponent
      ),
  },
  {
    path: 'scraping',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/scraping/scraping-page.component').then(
        m => m.ScrapingPageComponent
      ),
  },
  {
    path: 'watches',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/watches/watches-page.component').then(
        m => m.WatchesPageComponent
      ),
  },
];
```

- [ ] **Step 3: Verify the build still succeeds**

Run: `npm run build --prefix hermes-frontend`
Expected: build succeeds with no compilation errors.

- [ ] **Step 4: Commit**

```bash
git add hermes-frontend/src/app/core/auth.guard.ts hermes-frontend/src/app/app.routes.ts
git commit -m "feat(frontend): guard all routes behind Keycloak authentication"
```

---

## Task 7: Frontend — bearer token interceptor

**Files:**
- Modify: `hermes-frontend/src/app/app.config.ts`

**Interfaces:**
- Consumes: `provideKeycloak` config from Task 5.
- Produces: every HTTP request to `/api/**` carries `Authorization: Bearer <token>`, with the token silently refreshed by `keycloak-angular` when near expiry.

- [ ] **Step 1: Wire up `includeBearerTokenInterceptor`**

Modify `hermes-frontend/src/app/app.config.ts`:

```ts
import { ApplicationConfig, provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideCharts, withDefaultRegisterables } from 'ng2-charts';
import {
  provideKeycloak,
  includeBearerTokenInterceptor,
  INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
} from 'keycloak-angular';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    provideRouter(routes),
    provideKeycloak({
      config: {
        url: 'http://localhost:8081',
        realm: 'hermes',
        clientId: 'hermes-frontend',
      },
      initOptions: {
        onLoad: 'check-sso',
        pkceMethod: 'S256',
      },
    }),
    provideHttpClient(withFetch(), withInterceptors([includeBearerTokenInterceptor])),
    {
      provide: INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
      useValue: {
        bearerPrefix: 'Bearer',
        shouldAddToken: (request: { url: string }) => request.url.startsWith('/api'),
      },
    },
    provideAnimationsAsync(),
    provideCharts(withDefaultRegisterables()),
  ]
};
```

- [ ] **Step 2: Verify the build still succeeds**

Run: `npm run build --prefix hermes-frontend`
Expected: build succeeds with no compilation errors.

- [ ] **Step 3: Commit**

```bash
git add hermes-frontend/src/app/app.config.ts
git commit -m "feat(frontend): attach bearer token to /api requests via keycloak-angular interceptor"
```

---

## Task 8: Frontend — header login/logout widget

**Files:**
- Modify: `hermes-frontend/src/app/app.component.ts`
- Modify: `hermes-frontend/src/app/app.component.html`

**Interfaces:**
- Consumes: `Keycloak` DI token from `keycloak-angular` (provided by Task 5's `provideKeycloak`).
- Produces: visible username + logout control in the existing header nav.

- [ ] **Step 1: Inject Keycloak in the app component**

Modify `hermes-frontend/src/app/app.component.ts`:

```ts
import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import Keycloak from 'keycloak-js';
import { ChatBubbleComponent } from './shared/chat-bubble.component';
import { NotificationBellComponent } from './shared/notification-bell.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ChatBubbleComponent, NotificationBellComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  private readonly keycloak = inject(Keycloak);

  get username(): string | undefined {
    return this.keycloak.tokenParsed?.['preferred_username'];
  }

  logout(): void {
    this.keycloak.logout({ redirectUri: window.location.origin });
  }
}
```

- [ ] **Step 2: Add the widget to the header template**

Modify `hermes-frontend/src/app/app.component.html` — replace the `<div class="ml-auto">` block:

```html
      <div class="ml-auto flex items-center gap-3">
        <app-notification-bell />
        @if (username) {
          <span class="text-slate-300 text-sm">{{ username }}</span>
          <button
            (click)="logout()"
            class="text-slate-300 hover:text-white hover:bg-slate-700/40 px-3 py-1.5 rounded-md text-sm font-medium transition-colors"
          >Log out</button>
        }
      </div>
```

- [ ] **Step 3: Verify the build still succeeds**

Run: `npm run build --prefix hermes-frontend`
Expected: build succeeds with no compilation errors.

- [ ] **Step 4: Commit**

```bash
git add hermes-frontend/src/app/app.component.ts hermes-frontend/src/app/app.component.html
git commit -m "feat(frontend): show logged-in username and a logout control in the header"
```

---

## Task 9: End-to-end manual verification

**Files:** none (verification only).

- [ ] **Step 1: Bring up the full stack**

```bash
docker compose up -d --build
```

Wait for all services healthy: `docker compose ps`.

- [ ] **Step 2: Confirm an unauthenticated API call is rejected**

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/listings
```

Expected: `401`.

- [ ] **Step 3: Confirm the Angular app forces login**

Open `http://localhost:4200` in a browser. Expected: redirected to Keycloak's hosted login page for realm `hermes`.

- [ ] **Step 4: Log in as each test user and confirm the round trip**

Log in as `testuser` / `password`. Expected: redirected back to `http://localhost:4200/listings`, header shows `testuser`, listings load normally (the existing `clientId` behavior for favorites is unaffected). Click "Log out" and confirm you're returned to the Keycloak login page.

Repeat with `testadmin` / `password`. Expected: same behavior — this phase does not yet grant admins anything extra.

- [ ] **Step 5: Confirm the admin console and realm are reproducible from a clean state**

```bash
docker compose down -v
docker compose up -d --build
```

Repeat Steps 2–4. Expected: identical behavior with no manual Keycloak setup, confirming the realm export fully reproduces the auth setup.
