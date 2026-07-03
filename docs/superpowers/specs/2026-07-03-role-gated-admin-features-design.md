# Role-Gated Admin Features — Design Spec

**Date:** 2026-07-03

## Goal

Phase 4 of the Keycloak/roles/multi-user initiative. Restrict scraping-related actions to the `admin` realm role, and close an object-level authorization gap flagged during phase 3's review: `deleteAgentTask(id)` and `markNotificationRead(id)` currently let any authenticated user act on any resource id, not just their own.

This phase does **not** include an admin "see all users" screen — the user explicitly decided Keycloak's own admin console is sufficient for that, so no local users-mirror table or Keycloak Admin API integration is needed. Chat history management (phase 5) is untouched.

---

## Architecture Overview

Two independent, additive changes on top of the existing JWT/`CurrentUser` model — no new tables, no new external dependency.

**Role enforcement:** `SecurityConfig` gains `@EnableMethodSecurity`, and every scraping-related endpoint gets `@PreAuthorize("hasRole('ADMIN')")`: `ScrapingSessionController.createScrapingSession`/`getScrapingSession`, and `ListingController.rescrapeListing`/`backfillListingGeocoding`. Because `@PreAuthorize` denials are intercepted by Spring Security's filter chain (`ExceptionTranslationFilter`) before they ever reach `GlobalExceptionHandler`'s `@RestControllerAdvice`, a custom `AccessDeniedHandler` is registered on `SecurityConfig` so a denied request still gets the same `ProblemDetail` JSON shape (403) as every other error in this API, not Spring Security's default plain-text page.

**Ownership enforcement:** `SecurityConfig`'s `authenticated()` rule only proves *who* a caller is — it has no concept of whether a specific resource id belongs to them. Most phase-3-migrated endpoints already avoid the problem by construction (they derive their result set *from* `CurrentUser.current().id()` rather than trusting a client-supplied id, or filter mutations by `(userId, resourceId)` together). The two exceptions, found during phase 3's final review, accept a bare id and act on it with no ownership check at all: `AgentTaskController.deleteAgentTask(id)` and `NotificationController.markNotificationRead(id)`. Fixing this at the service layer (`AgentTaskService.delete`, `NotificationService.markRead`) also closes a third call site discovered during this phase's brainstorming: `ListWatchesTool`'s chat-driven "cancel this watch" path calls the exact same `AgentTaskService.delete(cancelId)` with no check today.

A denied ownership check returns 404, not 403 — consistent with this codebase's existing pattern of not confirming another resource's existence to a caller who isn't entitled to see it, and indistinguishable from a genuinely-missing id.

---

## Backend

- `SecurityConfig`:
  - Add `@EnableMethodSecurity` alongside the existing `@EnableWebSecurity`.
  - Add an `AccessDeniedHandler` bean, wired via `.exceptionHandling(ex -> ex.accessDeniedHandler(...))` in the `SecurityFilterChain`, writing a `ProblemDetail` (status 403, same shape `GlobalExceptionHandler` already produces) directly to the response.
- `ScrapingSessionController`: `@PreAuthorize("hasRole('ADMIN')")` on `createScrapingSession` and `getScrapingSession`.
- `ListingController`: `@PreAuthorize("hasRole('ADMIN')")` on `rescrapeListing` and `backfillListingGeocoding`. (`getListings`/`getListing`/`getListingSummary`/`requestListingSummaryGeneration` are unaffected — search and AI-summary viewing stay open to every authenticated user.)
- `AgentTaskService.delete(UUID taskId, UUID userId)` (parameter added to the existing method): loads the task by id; if absent or `!task.getUserId().equals(userId)`, throws `ResponseStatusException(HttpStatus.NOT_FOUND, ...)`; otherwise deletes. Both call sites updated to pass the caller's id:
  - `AgentTaskController.deleteAgentTask` passes `CurrentUser.current().id()`.
  - `ListWatchesTool.listWatches`'s cancel branch passes `this.userId` (the field already available on the `TaskTool` base class).
- `NotificationService.markRead(UUID notificationId, UUID userId)` (parameter added): loads the notification; if absent or `!notification.getUserId().equals(userId)`, throws 404; otherwise marks read and saves. `NotificationController.markNotificationRead` passes `CurrentUser.current().id()`.

---

## Frontend

- New `adminGuard` (`hermes-frontend/src/app/core/admin.guard.ts`), built via `createAuthGuard` the same way `authGuard` is: checks `authData.grantedRoles.realmRoles.includes('admin')` in addition to authentication, and returns a redirect `UrlTree` to `/listings` if the caller is authenticated but not an admin (no re-login prompt — they're already logged in, just not authorized). Applied to the `scraping` route in `app.routes.ts` in place of the plain `authGuard`.
- `AppComponent` gains an `isAdmin` getter (mirroring the existing `username` getter): reads `keycloak.tokenParsed?.['realm_access']?.['roles']` and checks for `'admin'`. The "Scraping" link in the header template is wrapped in `@if (isAdmin)`. This is a UX nicety only — the backend's role check is the actual security boundary, so hiding the link doesn't need to be airtight.
- No changes to `scraping.service.ts` — a 403 `ProblemDetail` (if it ever reaches the frontend) flows through the same `err.error?.detail` handling already used everywhere else.

---

## Testing

- **Backend:**
  - New tests on `ScrapingSessionControllerTest`/`ListingControllerTest`: authenticated-as-`user` role → 403 with a `ProblemDetail` body; authenticated-as-`admin` role → the endpoint's existing success status (201/200/202/202 respectively), using the established `.with(jwt().jwt(b -> b.claim("realm_access", Map.of("roles", List.of(...)))))` pattern, varying only the role claim.
  - `AgentTaskServiceTest`: owner calling `delete(taskId, ownerUserId)` on their own task succeeds; a different `userId` on the same `taskId` → `ResponseStatusException` 404, and `repository.deleteById(...)` is verified `never()` called.
  - `NotificationServiceTest`: same pattern for `markRead(notificationId, userId)` — owner succeeds, non-owner gets 404 and `repository.save(...)` is verified `never()` called.
  - `ListWatchesToolTest`: confirms the cancel branch passes the tool's own `userId` through to `agentTaskService.delete(cancelId, userId)`.
- **Frontend:** a focused `admin.guard.spec.ts` (mirroring `auth.guard.spec.ts`'s existing structure): admin role → guard resolves `true`; authenticated non-admin → guard resolves a `UrlTree` pointing at `/listings`, and `keycloak.login()` is never called (they're already authenticated, just redirected).
- **Manual:** log in as `testuser` — confirm the "Scraping" nav link is gone, and a direct `curl`/API call to `POST /api/scraping-sessions` and `GET /api/scraping-sessions/{id}` both return 403 with a JSON `ProblemDetail` body. Log in as `testadmin` — confirm scraping (create session, rescrape a listing, geocoding backfill) works exactly as before. As `testuser`, attempt to delete `testadmin`'s watch and mark `testadmin`'s notification read via direct API calls with a known/guessed id — confirm both return 404, and the target resource is untouched.
