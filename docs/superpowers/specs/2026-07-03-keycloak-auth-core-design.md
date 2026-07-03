# Keycloak + JWT Auth Core — Design Spec

**Date:** 2026-07-03

## Goal

Introduce Keycloak as the identity provider for Hermes and wire up JWT-based authentication end to end: a `keycloak` service reproducible via a committed realm export, Spring Security validating bearer tokens on the backend, and an Angular login flow (Authorization Code + PKCE) on the frontend. Two realm roles (`admin`, `user`) and two test users are provisioned via the realm import so the stack is fully working after `docker compose up` with no manual Keycloak configuration.

This is phase 1 of a larger initiative. It deliberately does **not**:
- Replace the existing `clientId`-based scoping of favorites/chat/notifications/agent tasks (phase 3).
- Add the `user_profile`/address table (phase 2).
- Add per-endpoint role enforcement (`@PreAuthorize` for scraping etc.) beyond "must be authenticated" (phase 4).
- Add JWT auth to the `/ws/chat` STOMP handshake (phase 3, since it's tied to the clientId migration).
- Add a user-management/admin screen or chat history UI (phases 4–5).

Existing endpoints keep working exactly as they do today, but now require a valid bearer token in addition to whatever `clientId`/`sessionId` parameters they already take. No authorization-level (role) checks are added yet — just authentication.

---

## Architecture Overview

Three pieces, each independently testable:

1. **Keycloak infra** — a `keycloak` container added to `hermes-backend/docker-compose.yml`, started with `start-dev --import-realm`, importing a committed `realm-export.json` that defines the realm, client, roles, and test users.
2. **Backend resource server** — Spring Boot validates incoming JWTs against Keycloak's issuer using `spring-boot-starter-oauth2-resource-server`; a custom `JwtAuthenticationConverter` maps `realm_access.roles` to Spring authorities; a `CurrentUser` helper exposes `sub`/username/roles to later phases.
3. **Frontend login** — Angular uses `keycloak-angular` + `keycloak-js` for Authorization Code + PKCE against the SPA client, a functional route guard protects the app shell, and an `HttpInterceptorFn` attaches `Authorization: Bearer <token>` to all `/api/**` calls with silent refresh.

---

## Keycloak Infra

### docker-compose

Add to `hermes-backend/docker-compose.yml`:

```yaml
  keycloak:
    image: quay.io/keycloak/keycloak:26.0
    command: ["start-dev", "--import-realm"]
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
    ports:
      - "8081:8080"
    volumes:
      - ./keycloak/realm-export.json:/opt/keycloak/data/import/realm-export.json:ro
```

The `backend` service gains a `depends_on: keycloak: condition: service_started` and an env var for the issuer URI:

```yaml
      SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI: http://keycloak:8080/realms/hermes
```

Note the issuer URI uses the **internal** Docker network hostname/port (`keycloak:8080`), which differs from the browser-facing `http://localhost:8081` the Angular app uses. Spring's JWT decoder only needs to reach the issuer for its JWKS; it doesn't need the browser-facing URL. (If token `iss` claim validation fails because Keycloak stamps tokens with the hostname it was reached under, we set `KC_HOSTNAME_URL`/`KC_HOSTNAME` explicitly to `http://localhost:8081` so issued tokens and the configured issuer-uri agree — see Risks below.)

### Realm export (`hermes-backend/keycloak/realm-export.json`)

Defines:
- Realm `hermes`, `enabled: true`.
- Client `hermes-frontend`: public client, `standardFlowEnabled: true`, `directAccessGrantsEnabled: false`, PKCE (`pkce.code.challenge.method: S256`), redirect URIs `http://localhost:4200/*`, web origins `http://localhost:4200`.
- Realm roles: `admin`, `user`.
- Two test users:
  - `testuser` / password `password`, realm role `user`, email `testuser@hermes.local`, `emailVerified: true`, credential `temporary: false`.
  - `testadmin` / password `password`, realm role `admin`, same email pattern.
- `sslRequired: none` (dev-only realm, matches the rest of the local-dev stack which has no TLS anywhere).

The file is committed to the repo (not `.gitignore`d) so the realm is reproducible by anyone who clones the repo and runs `docker compose up`.

---

## Backend

### Dependency

Add `org.springframework.boot:spring-boot-starter-oauth2-resource-server` to `pom.xml`.

### Configuration

`application.properties`:
```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=${SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI:http://localhost:8081/realms/hermes}
```

### `SecurityConfig`

New `com.kropholler.dev.hermes.config.SecurityConfig`:
- `SecurityFilterChain` bean: stateless session (`SessionCreationPolicy.STATELESS`), CSRF disabled (pure token API, no cookies), `oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(...)))`.
- Authorization rules for this phase: `/actuator/health/**` permit-all, `/ws/chat/**` permit-all (unchanged until phase 3), everything else under `/api/**` requires authentication (`authenticated()`), no role checks yet.
- A `JwtAuthenticationConverter` bean wired with a custom `Converter<Jwt, Collection<GrantedAuthority>>` that reads `jwt.getClaimAsMap("realm_access").get("roles")` and maps each to `SimpleGrantedAuthority("ROLE_" + role.toUpperCase())`.

### `CurrentUser`

New `com.kropholler.dev.hermes.security.CurrentUser`, a small `@Component` with a request-scoped-friendly static-style API:

```java
public record CurrentUser(UUID id, String username, Set<String> roles) {
    public static CurrentUser from(Jwt jwt) { ... }
}
```

Resolved via a Spring MVC `HandlerMethodArgumentResolver` (or simply injected as `@AuthenticationPrincipal Jwt jwt` and converted inline) — later phases will use this instead of `clientId` path/query params. Not wired into any controller yet in this phase; it exists so phase 2/3 can consume it without redesigning the security layer.

### Tests

- `SecurityConfigTest` (`@SpringBootTest` + `@AutoConfigureMockMvc`): a request to an existing endpoint (e.g. `GET /api/listings`) without a token returns 401; with a valid mock JWT (`.with(jwt())` from `spring-security-test`) returns 200.
- Existing controller tests need `.with(jwt())` added to their `MockMvc` requests now that endpoints require authentication — this touches every existing `@WebMvcTest`/`@SpringBootTest` MockMvc test class. This is the main "existing test" blast radius of this phase.

---

## Frontend

### Dependencies

Add `keycloak-angular` and `keycloak-js` to `package.json`.

### Bootstrap (`app.config.ts`)

```ts
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
})
```

`onLoad: 'check-sso'` (not `login-required`) so the app shell loads and the route guard decides whether to force a redirect, rather than gating the entire app boot on auth.

### Route guard

`authGuard` (functional `CanActivateFn` from `keycloak-angular`, `createAuthGuard`) applied to the app's existing routes in `app.routes.ts`. Unauthenticated navigation redirects to Keycloak's hosted login page; on return, PKCE code exchange happens automatically via the library.

### HTTP interceptor

`authInterceptor: HttpInterceptorFn` registered via `provideHttpClient(withInterceptors([authInterceptor]))`, using `keycloak-angular`'s `AUTHORIZATION_HEADER_NAME`/`includeBearerTokenInterceptor` helper (or a thin custom wrapper) scoped to requests matching `/api/**`, attaching `Authorization: Bearer <token>` and awaiting silent token refresh when the token is near expiry.

### UI

Header component (wherever the current nav/branding lives) gains a small auth widget: shows `preferred_username` when logged in with a "Log out" action calling `keycloak.logout()`; shows nothing extra when logged out since the guard already forces login before any protected route renders.

### Existing HTTP calls

No changes to `favorites.service.ts` / `chat.service.ts` / etc. in this phase — they keep sending `clientId` as before. The interceptor adds the bearer token alongside, but nothing reads it yet on the backend for authorization purposes beyond "is this request authenticated at all."

---

## Risks / Open Questions Resolved Inline

- **Issuer URL mismatch (browser vs. Docker-internal):** flagged above; resolved by pinning Keycloak's `KC_HOSTNAME` to the browser-facing URL so token issuer claims match what both the backend's issuer-uri and the frontend expect. If this proves brittle in practice, the fallback is `issuer-uri` validation disabled in favor of an explicit `jwk-set-uri` (which has no issuer-matching requirement) — call this out during implementation if the simple approach doesn't hold up.
- **Existing MockMvc tests break:** every controller test that hits a now-protected endpoint needs `.with(jwt())`. Counted as in-scope cleanup for this phase, not a follow-up.

---

## Testing Summary

- Backend: `SecurityConfigTest` for 401/200 behavior; update existing MockMvc-based controller tests to authenticate their requests.
- Frontend: guard/interceptor are thin wrappers around `keycloak-angular` primitives — light unit coverage on the interceptor's `/api/**` matching logic; manual verification of the login/logout round trip in a browser (per this project's UI-change testing convention).
- Manual: `docker compose up`, confirm Keycloak admin console reachable at `localhost:8081`, confirm realm/roles/users present, confirm Angular app redirects to login and back, confirm an authenticated `/api/listings` call succeeds and an unauthenticated `curl` returns 401.
