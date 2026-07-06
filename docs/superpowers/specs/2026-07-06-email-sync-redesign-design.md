# Email Sync Redesign — Design Spec

**Date:** 2026-07-06
**Status:** Approved

## Overview

Replaces the per-request `UserProfileSyncFilter` (added in the [area research agent](2026-07-06-area-research-agent-design.md) work) with a design that never reads the database on ordinary requests. The filter ran a `findById` + conditional `save` on *every single authenticated request*, which is disproportionate to what it's for: keeping one cached column fresh for a background job that fires at most once a day per task.

Two problems drove this:

1. **Cost.** A DB round trip per request, purely to maybe-update one column, is wasteful at any real request volume.
2. **No creation-time guarantee.** Nothing stopped a user with no email on file from creating a `WATCH`/`RESEARCH`/`DIGEST`/`AREA_RESEARCH` task — it would just silently never get emailed.

The fix has two independent halves:

- **Creation-time gate**, using the live JWT email already available at the exact moment a task is created — no DB read needed, since the tool call already runs inside an authenticated request.
- **Background-send cache**, refreshed opportunistically wherever the JWT's email is already in hand for free (every chat message, every address update) via a blind upsert — no read-then-compare, just one `INSERT ... ON CONFLICT DO UPDATE`.

These are separate concerns solving separate problems: the gate is about correctness *now*; the cache is about correctness *later*, when a recurring task fires from a background scheduler with no JWT in scope at all.

## Why the cache can't be eliminated entirely

`EmailNotificationSender` runs from `AgentTaskExecutor`, triggered by `AgentTaskScheduler` — a cron-driven background process with no HTTP request, no JWT, nothing but the `userId` stored on the `AgentTaskEntity`/`NotificationEntity`. The only way to resolve an email from just a `userId` in that context is either (a) a locally cached copy, or (b) calling out to Keycloak's Admin API using `userId` as the lookup key. Option (b) was considered and rejected for this project: it requires provisioning a new confidential client with a service-account role in the realm, an admin-token acquisition/caching flow in the backend, and makes Keycloak a hard dependency for every notification send. Given no such integration exists yet, the local cache (accepted staleness: as fresh as the user's last live interaction with the app) is the pragmatic choice.

---

## 1. Remove the per-request filter entirely

Delete:
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileSyncFilter.java`
- `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileSyncFilterConfig.java`
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/profile/UserProfileSyncFilterTest.java`
- `hermes-backend/src/test/java/com/kropholler/dev/hermes/security/NoOpUserProfileSyncFilterTestConfig.java`

Revert `SecurityConfig.filterChain(...)` to its pre-filter signature (drop the `@Qualifier("userProfileSyncFilter") OncePerRequestFilter` parameter and the `.addFilterAfter(...)` call, and the now-unused `Qualifier`/`BearerTokenAuthenticationFilter`/`OncePerRequestFilter` imports).

Revert all 9 affected `@WebMvcTest` slice test files' `@Import` lists back to not referencing `NoOpUserProfileSyncFilterTestConfig` (drop the import statement and the list entry): `SecurityConfigTest`, `AgentTaskControllerTest`, `ChatHistoryControllerTest`, `FavoriteControllerTest`, `ListingControllerTest`, `ListingControllerSearchTest`, `NotificationControllerTest`, `UserProfileControllerTest`, `ReportControllerTest`, `ScrapingSessionControllerTest`.

## 2. Blind-upsert email wherever the JWT already has it for free

New method on `UserProfileRepository`:

```java
@Modifying
@Query(value = """
        INSERT INTO user_profiles (user_id, email, updated_at)
        VALUES (:userId, :email, now())
        ON CONFLICT (user_id) DO UPDATE SET email = EXCLUDED.email
        """, nativeQuery = true)
void upsertEmail(@Param("userId") UUID userId, @Param("email") String email);
```

One write, no read, no comparison. Leaves `updated_at` and address fields on an *existing* row untouched (matches the same "don't stomp on the address-last-updated semantic" decision made for the original filter); a brand-new row gets `updated_at = now()` to satisfy the column's `NOT NULL` constraint, with every other field `NULL`.

New method on `UserProfileService`:

```java
@Transactional
public void syncEmail(UUID userId, String email) {
    if (email == null || email.isBlank()) return;
    userProfileRepository.upsertEmail(userId, email);
}
```

Called from two places, both of which are already-authenticated, already-JWT-bearing requests that would otherwise discard the email claim:

- **`ChatController.handleMessage`**: every chat message already does `CurrentUser.from(jwt)` to get `userId`; call `userProfileService.syncEmail(currentUser.id(), currentUser.email())` right there, once per message.
- **`UserProfileController.updateAddress`**: already a write; call `userProfileService.syncEmail(CurrentUser.current().id(), CurrentUser.current().email())` alongside the existing `updateAddress(...)` call.

## 3. Creation-time "no email → no task" gate, using the live JWT value

The email needs to reach the four task-creation tools (`SaveWatchTool`, `TriggerResearchTool`, `TriggerDigestTool`, `SaveAreaResearchTool`) the same way `userId` already does — threaded through the existing per-chat-request tool-construction pipeline, not re-fetched from the database:

`ChatController` (already has `CurrentUser.from(jwt)`, so `.email()` is free)
→ `AiChatService.startStream(sessionId, userId, email, userMessage)` (new `email` parameter)
→ `ChatToolProvider.provideTools(userId, email)` (interface signature change)
→ `AgentChatToolProvider.provideTools(userId, email)` → passed into each tool's constructor.

`TaskTool` (the shared base class for all five agent chat tools) gains a third constructor parameter:

```java
abstract class TaskTool {
    protected final UUID userId;
    protected final AgentTaskService agentTaskService;
    protected final String email;

    protected TaskTool(UUID userId, AgentTaskService agentTaskService, String email) {
        this.userId = userId;
        this.agentTaskService = agentTaskService;
        this.email = email;
    }

    protected boolean hasEmail() {
        return email != null && !email.isBlank();
    }
}
```

Each of the four *creating* tools checks `hasEmail()` as the first thing its `@Tool`-annotated method does, returning a rejection string (not creating anything) if absent:

```java
if (!hasEmail()) {
    return "Please make sure your account has an email address before setting up notifications.";
}
```

`ListWatchesTool` (list/cancel only, never creates anything that could be emailed) does not need this check, but still takes the same three-arg `TaskTool` constructor for consistency.

This check uses the *live* JWT value for the current request — never a cached read — so it's always correct for the request it's guarding, with zero added DB cost. (The only way this could ever reject someone who "really" has an email is if Keycloak's token for that request genuinely omitted the `email` claim, in which case rejecting is the correct call — there's no better signal available in that moment anyway.)

## 4. `EmailNotificationSender`: skip instead of falling back

Today, if no cached profile email is found at send time, `EmailNotificationSender` falls back to a hardcoded `hermes.notifications.to-email` config value — meaning a stranger's notification content could land in an unrelated inbox. With the creation-time gate now preventing email-less tasks from ever being created, the only way this path is reached is if a user's email later disappears from their cached profile row after the task was created (extremely unlikely given the row is never cleared, only ever updated) — so falling back to a fixed address is no longer the right behavior. Skip sending and log a warning instead:

```java
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
```

The in-app notification (`NotificationEntity`/websocket push) is unaffected either way — only the email leg is skipped.

The `hermes.notifications.to-email` config property (and the `fromEmail`/`toEmail` field pair it fed) becomes dead once the fallback is removed; drop the config line from `application.properties` and the now-unused `toEmail` field/`@Value` from `EmailNotificationSender`.

## Out of scope

- Querying Keycloak's Admin API (rejected — see "Why the cache can't be eliminated entirely").
- Any change to how the JWT's `email` claim itself is extracted (`CurrentUser.from(jwt)` is unchanged).
- Any change to `AreaResearchTaskHandler`'s runtime behavior (it already never touches the JWT or does live geocoding; unaffected by this redesign).
