# Area Research Agent Task — Design Spec

**Date:** 2026-07-06
**Status:** Approved

## Overview

Add a fourth agent task type, `AREA_RESEARCH`, to the existing `AgentTask` execution loop (see [2026-06-19-agentic-ai-design.md](2026-06-19-agentic-ai-design.md)). It runs daily and finds + researches the best available listings within a configurable radius of the user's home address (or an overridden location), ranked by price, size, bedrooms, and value using an LLM over a deterministically-fetched candidate set. Results are delivered as an in-app notification and, as part of this work, a **real per-user email** — today all notification emails go to one hardcoded address regardless of which user they're for, which this spec also fixes.

Two smaller, broadly-applicable fixes ride along with this feature:

1. The hardcoded `LIMIT 5` in the two chat-facing listing search queries becomes a configurable, clamped parameter, used by this feature and by the existing chat search tool.
2. Per-user email delivery, since notifications are currently emailed to a single fixed address for every user.

---

## 1. `AREA_RESEARCH` Task Type

### Payload

```java
public record AreaResearchPayload(
    Integer radiusKm,
    Integer limit,
    Integer minBedrooms,
    Integer minRooms,
    Integer minLivingAreaM2,
    Integer minPrice,
    Integer maxPrice,
    String keywords,
    Double overrideLon,
    Double overrideLat
) {}
```

`overrideLon`/`overrideLat` are both `null` for the common case (use the user's home address). When set, they hold coordinates **already resolved and validated at task-creation time** — see below. There is no `nearAddress`/`nearCity` string on the payload; by the time a task exists, any override has already been turned into coordinates.

### Schedule

Daily, same cadence as `WATCH`: `AgentTaskService.createAreaResearch(userId, name, payload)` sets `schedule = "0 0 8 * * *"` and computes `nextRunAt` the same way `createWatch` does. No DB migration needed — `agent_tasks.type` is a plain `VARCHAR(20)` with no check constraint, and `"AREA_RESEARCH"` fits.

### Creation: `SaveAreaResearchTool`

A new chat tool (`ai/agent/tool/SaveAreaResearchTool`, registered in `AgentChatToolProvider` alongside the existing four), following the `SaveWatchTool` pattern (`TaskTool` base, constructed with `userId` + `AgentTaskService`), plus two new constructor dependencies: `UserProfileRepository` and `GeocodingService`.

Validation happens **at creation time**, so failures are surfaced immediately in chat rather than silently every day:

- No `nearAddress`/`nearCity` argument given → look up the user's `UserProfileEntity`. If `latitude`/`longitude` are null (no home address on file), return an error string telling the user to set their address first. **Do not create the task.**
- `nearAddress`/`nearCity` argument given → geocode it immediately via `GeocodingService` (same call `WatchTaskHandler`/`ListingService` already make for ad-hoc locations). If geocoding fails, return an error string ("could not find that location") and **do not create the task.** If it succeeds, store the resolved `lon`/`lat` as `overrideLon`/`overrideLat` on the payload — frozen at creation, not re-resolved on every run.

This means the handler never performs a geocoding call at runtime, ever — the home-address path reads already-geocoded profile coordinates fresh each run, and the override path was frozen at creation.

`limit` is clamped the same way as everywhere else (see §3): omitted → 5, otherwise clamped to `[1, 15]`.

Tool description emphasizes: "Set up a recurring daily search that finds and researches the best available listings within a radius of your home address (or another address/city), ranked by an AI reviewing price, size, and value."

---

## 2. `AreaResearchTaskHandler`

Implements `AgentTaskHandler` (`getType() == AREA_RESEARCH`), wired like `ResearchTaskHandler`/`DigestTaskHandler` (`ChatClient` qualified `"chatClient"`, `ListingService`, `ChatListingCardMapper`, `ListingSummaryService`, `MeterRegistry`, `ObjectMapper`), plus `UserProfileRepository` for the home-address path.

### Flow

1. Deserialize `AreaResearchPayload`. On failure, log and return `Optional.empty()` (matches every other handler's invalid-payload handling).
2. Resolve center coordinates:
   - `payload.overrideLon() != null` → use `(overrideLon, overrideLat)` directly.
   - else → `userProfileRepository.findById(task.getUserId())`; if the profile or its coordinates are missing (e.g. the user cleared their address after creating the task), log a warning and return `Optional.empty()` — this is a defensive fallback; the creation-time check in §1 prevents it in the normal case.
3. **Deterministic find:** call a new `ListingService.findNearLocation(double lon, double lat, Integer minBedrooms, Integer minRooms, Integer minLivingAreaM2, String province, String keywords, Integer minPrice, Integer maxPrice, int radiusMeters, int limit)` that calls `ListingRepository.searchForChatNearLocation` **directly** — no `resolveLatLon`/geocoding step, since we already have coordinates either way. Ordering is unchanged (closest-first, from the existing query). This guarantees exactly the clamped `limit` count of correctly-filtered candidates, regardless of what an LLM might otherwise choose to do.
4. If zero candidates, return `Optional.empty()` (nothing to report this run — same quiet behavior as `WatchTaskHandler`/`DigestTaskHandler` on an empty result).
5. **Agentic research:** build a `ChatClient` prompt listing the candidate addresses/prices/sizes/bedrooms, with `GetListingSummaryTool`, `GetPriceHistoryTool`, and `CompareListingsTool` available (deliberately **not** `ListingSearchTool` or `GetFavouriteListingsTool` — the candidate set is fixed; the LLM's job is to research and rank *these*, not search elsewhere). Instruct it to reason about price, size, bedrooms, and value against the task's criteria and produce a ranked write-up.
6. Build `NotificationContent`: title e.g. `"N best listings within {radiusKm}km"`, body = the LLM's narrative, `listingIds` = the candidate IDs from step 3 (known deterministically — not scraped from `resultHolder` side effects, unlike `ResearchTaskHandler`/`DigestTaskHandler`, since here we already have the authoritative set).

---

## 3. Configurable result limit (applies globally, not just this feature)

Today `ListingRepository.searchForChat` and `searchForChatNearLocation` hardcode `LIMIT 5` in their native SQL. This changes to:

- Both queries gain a `:limit` bind parameter (`LIMIT :limit` instead of `LIMIT 5`), with a new `@Param("limit") int limit` parameter.
- `ListingService.findForChat(...)` gains an `Integer limit` parameter and a small clamp helper: `null` or non-positive → `5`; otherwise `Math.min(limit, 15)`. Applied before calling the repository.
- The new `findNearLocation` (§2) applies the same clamp helper.
- `ListingSearchTool` (the general chat "search listings" tool) gains an optional `@ToolParam limit` ("Number of listings to return, default 5, max 15"), forwarded through.
- `WatchTaskHandler`'s existing call to `findForChat` passes `null` for the new parameter — unaffected, still defaults to 5.

---

## 4. Per-user email delivery fix

Today `EmailNotificationSender` sends every notification email to one hardcoded `hermes.notifications.to-email` config value, regardless of which user it's for. This fixes it:

- `CurrentUser` gains an `email` field, extracted via `jwt.getClaimAsString("email")` (standard Keycloak claim when the `email` scope is granted, which is part of Keycloak's baked-in realm defaults).
- `UserProfileEntity` gains a nullable `email` column (migration `V14__add_email_to_user_profiles.sql`).
- A new `UserProfileSyncFilter` (`OncePerRequestFilter`), registered in `SecurityConfig` to run after JWT authentication, reads the authenticated `CurrentUser` on every request and — if their email is present and differs from what's stored (or no profile row exists yet) — upserts just the email onto their profile (address fields untouched, or left null for a brand-new bare row). This is a single choke point: whichever endpoint a user's JWT happens to hit first (profile, chat, tasks, anything), their email ends up on file, without requiring the JWT to be present at async send-time.
- `EmailNotificationSender` gains a `UserProfileRepository` dependency. Instead of always using the config value, it looks up `dto.userId()`'s profile email and sends there, falling back to the config value only if no email is on file yet (e.g. a notification fires before any request from that user has been seen — shouldn't normally happen since task creation itself requires an authenticated request, but kept as a safety net rather than dropping the email).

---

## 5. Testing

- `AgentTaskServiceTest`: `createAreaResearch` persists with the daily schedule.
- `SaveAreaResearchToolTest`: rejects when no address and no override; rejects when override fails to geocode; succeeds and freezes coordinates on a valid override; succeeds using the profile address when no override given; limit clamping.
- `AreaResearchTaskHandlerTest`: home-address vs. frozen-override center resolution; missing-profile-coordinates fallback; empty-candidates → no notification; deterministic `listingIds` match the candidate set regardless of which tools the mocked `ChatClient` "calls"; clamped limit is honored.
- `ListingServiceTest` (or repository-level test): limit clamping (`null`→5, `20`→15, `0`/negative→1) for both `findForChat` and `findNearLocation`.
- `ListingSearchToolTest`: new `limit` parameter passthrough and clamping.
- `CurrentUserTest`: email claim extraction.
- `UserProfileSyncFilterTest`: syncs email on first authenticated request; updates on change; no-op when unchanged; no-op when JWT has no email claim.
- `EmailNotificationSenderTest`: sends to the per-user profile email; falls back to config value when no profile email is on file.

---

## Out of scope

- User-configurable schedule cadence (daily only, matching `WATCH`).
- Re-geocoding an override address on every run (frozen at creation is sufficient; if the named location needs to change, the user creates a new task).
- Changing `WATCH`/`RESEARCH`/`DIGEST` behavior beyond the shared `findForChat` limit parameter threading through unchanged (`null` → default 5).
