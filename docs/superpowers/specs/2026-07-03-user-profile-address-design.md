# User Profile & Address — Design Spec

**Date:** 2026-07-03

## Goal

Give every authenticated user a profile where they can view and update their own home address, laying the groundwork for future features (e.g. defaulting radius search to "near me"). This is phase 2 of the larger Keycloak/roles/multi-user initiative (phase 1, "auth core", is implemented and unmerged on branch `worktree-keycloak-auth-core`; this phase builds directly on top of it — `SecurityConfig` and `CurrentUser` from phase 1 are prerequisites, not reproduced here).

This phase does **not**: migrate favorites/chat/notifications off `clientId` (phase 3), add role-based authorization (phase 4), or add chat history management (phase 5).

---

## Architecture Overview

A new `profile` backend module (mirroring the existing `favorites`/`notification` module shape) owns a `user_profiles` table keyed directly by the JWT `sub` — no surrogate key, no local `users` mirror table, consistent with phase 1's decision that identity lives entirely in the JWT. Two endpoints: `GET /api/profile` (returns the caller's profile, defaulting to all-null address fields if no row exists yet) and `PUT /api/profile/address` (validates and upserts the address). The user id always comes from the authenticated JWT's `sub` claim via `CurrentUser.from(jwt)` — never from the request body or path — so there is no way to read or write another user's profile.

An address is only ever persisted if it can be geocoded. `PUT /api/profile/address` synchronously calls the existing `GeocodingService.geocodeAddress(houseNumber, street, city)` (the same Nominatim-backed service listings use); a successful geocode upserts the row with both the raw address fields and the resolved latitude/longitude, while an empty/failed geocode throws `ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, ...)` before any write happens — the previously-saved address (if any) is left untouched. This reuses the existing `GlobalExceptionHandler`, which already converts `ResponseStatusException` into a `ProblemDetail` response, so no new exception-handling infrastructure is needed.

Synchronous (not async/JMS-queued) geocoding is appropriate here since profile updates are rare, single-item, user-initiated actions with no bulk/rate-limit pressure like scraping has.

On the frontend, a new `/profile` page (linked from the username in the header, which currently is static text) has a simple reactive-form address editor talking to these two endpoints, and shows an inline error if the backend rejects an ungeocodable address.

---

## Database

New Flyway migration `hermes-backend/src/main/resources/db/migration/V11__create_user_profiles.sql`:

```sql
CREATE TABLE user_profiles (
    user_id               UUID PRIMARY KEY,
    street                VARCHAR(255),
    house_number          VARCHAR(50),
    house_number_addition VARCHAR(50),
    zip_code              VARCHAR(20),
    city                  VARCHAR(255),
    province              VARCHAR(255),
    latitude              DOUBLE PRECISION,
    longitude             DOUBLE PRECISION,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL
);
```

`user_id` is the Keycloak `sub` claim — no foreign key to a local `users` table since phase 1 deliberately has none. All address/geo columns are nullable since a user may only have partially filled in their address, or none at all yet (the "no row exists" case).

---

## Backend

New package `com.kropholler.dev.hermes.profile`:

### `UserProfileEntity`

JPA entity mapped to `user_profiles`. `@Id` is a plain `UUID userId` (no `@GeneratedValue` — the id is always supplied, either from an existing row or from `CurrentUser`). Fields: `street`, `houseNumber`, `houseNumberAddition`, `zipCode`, `city`, `province` (all `String`), `latitude`, `longitude` (both `Double`), `updatedAt` (`Instant`).

### DTOs

- `AddressDto` (record): `street`, `houseNumber`, `houseNumberAddition`, `zipCode`, `city`, `province`, `latitude`, `longitude` — the full read shape returned by `GET /api/profile`.
- `UpdateAddressRequest` (record): `street`, `houseNumber`, `houseNumberAddition`, `zipCode`, `city`, `province` — the write shape for `PUT /api/profile/address`; no `latitude`/`longitude`, since those are always server-computed from a successful geocode.

### `UserProfileRepository`

`extends JpaRepository<UserProfileEntity, UUID>` — no custom queries needed beyond what `JpaRepository` provides (`findById`, `save`).

### `UserProfileService`

- `getProfile(UUID userId)` — returns an `AddressDto`. If no `UserProfileEntity` row exists for `userId`, returns an `AddressDto` with every field `null` (never throws/404s — "no address set yet" is a valid, common state, not an error).
- `updateAddress(UUID userId, UpdateAddressRequest request)` — calls `geocodingService.geocodeAddress(request.houseNumber(), request.street(), request.city())`. If the result is `Optional.empty()`, throws `new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Address could not be geocoded")` immediately, before touching the repository. If the result is present, loads the existing `UserProfileEntity` for `userId` (or constructs a new one with that id if absent), sets all address fields plus the resolved `latitude`/`longitude` from the `GeocodeResult`, sets `updatedAt = Instant.now()`, and saves. Returns the resulting `AddressDto`.

### `UserProfileController`

- `GET /api/profile` — takes `@AuthenticationPrincipal Jwt jwt`, computes `UUID userId = CurrentUser.from(jwt).id()`, delegates to `userProfileService.getProfile(userId)`, returns 200 with the `AddressDto`.
- `PUT /api/profile/address` — takes `@AuthenticationPrincipal Jwt jwt` and `@RequestBody @Valid UpdateAddressRequest request`, computes `userId` the same way, delegates to `userProfileService.updateAddress(userId, request)`, returns 200 with the resulting `AddressDto`.

Both endpoints require authentication only (inherited from phase 1's `SecurityConfig`, which already requires auth on all of `/api/**`) — no role check, since "view/update your own address" is available to every authenticated user per the original requirements (`user` role can search + manage their own address; `admin` can do everything, which trivially includes this).

---

## Frontend

- **`hermes-frontend/src/app/core/profile.service.ts`** (new) — `getProfile(): Observable<AddressDto>` (GET `/api/profile`) and `updateAddress(address: UpdateAddressRequest): Observable<AddressDto>` (PUT `/api/profile/address`), following the existing flat-service style used by `favorites.service.ts`/`notifications.service.ts` (plain `HttpClient`, no extra state management beyond what the page component holds).
- **`hermes-frontend/src/app/pages/profile/profile-page.component.ts`** (new) — a reactive form (`FormGroup`) with controls for `street`, `houseNumber`, `houseNumberAddition`, `zipCode`, `city`, `province`. On init, calls `getProfile()` and patches the form. On submit, calls `updateAddress(...)`; on success, shows a brief confirmation; on a 422 error, surfaces the message via the existing `ErrorAlertComponent` pattern (used elsewhere in the app for inline errors) without clearing the form, so the user can correct the address and retry.
- **`hermes-frontend/src/app/app.routes.ts`** — add `{ path: 'profile', canActivate: [authGuard], loadComponent: () => import('./pages/profile/profile-page.component').then(m => m.ProfilePageComponent) }`.
- **`hermes-frontend/src/app/app.component.html`** — the username `<span>` becomes `<a routerLink="/profile">{{ username }}</a>`, styled consistently with the existing header links.

---

## Testing

- **Backend:**
  - `UserProfileServiceTest` (unit, `UserProfileRepository` and `GeocodingService` mocked): no existing row → `getProfile` returns an all-null `AddressDto`; successful geocode → `updateAddress` upserts a row with address fields + lat/lon and returns them; empty/failed geocode → `updateAddress` throws `ResponseStatusException` with status 422 and `repository.save(...)` is never called (verified via `verifyNoInteractions`/`verify(never())`).
  - `UserProfileControllerTest` (`@WebMvcTest(UserProfileController.class)` importing `SecurityConfig` + `SecuredMockMvcTestSupport`, matching the phase 1 pattern exactly): confirms the controller derives `userId` from the JWT's `sub` claim (via a custom `jwt()` postprocessor setting a known subject) rather than from any request parameter, and that the service is invoked with that exact id — demonstrating one user's token cannot act on another user's data.
- **Frontend:** `npm run build` for compile verification, plus a small `profile-page.component.spec.ts` / `profile.service.spec.ts` with a couple of focused assertions (loads and displays an existing address; shows an error message on a 422 response), matching the minimal style used for the phase 1 guard/interceptor specs.
- **Manual:** log in as `testuser`, visit `/profile`, save a real, geocodable address, confirm it persists across a page reload; then try an unrecognizable/garbage address and confirm the UI shows an error and the previously-saved address is unchanged.
