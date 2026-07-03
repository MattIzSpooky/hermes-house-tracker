# ClientId → JWT Identity Migration — Design Spec

**Date:** 2026-07-03

## Goal

Phase 3 of the Keycloak/roles/multi-user initiative. Replace the browser-generated `clientId` UUID (currently threaded through favorites, notifications, agent tasks, and chat via `localStorage['hermes-chat-session']`) with the authenticated user's real identity, derived server-side from the JWT `sub` claim — the same pattern phase 2 established for the user profile endpoints. This also means giving `/ws/chat`'s STOMP WebSocket connection real authentication for the first time, since chat identity currently flows through that channel unauthenticated.

This phase does **not**: add role-based authorization (phase 4), or build a chat-history UI (phase 5) — though it lays groundwork for phase 5 by adding a `user_id` ownership column to `chat_messages` alongside the existing `session_id` (conversation-thread) column.

---

## Architecture Overview

Everywhere the app currently threads a client-generated `clientId` UUID through URLs, query params, or WebSocket payloads, it instead derives identity server-side from the authenticated JWT via `CurrentUser.current()` — the same helper phase 1/2 already built. Three tables (`favorites`, `notifications`, `agent_tasks`) get their `client_id` column renamed to `user_id`. `chat_messages` gains a *new*, separate `user_id` column; its existing `session_id` column is untouched and keeps meaning "one conversation thread," not identity.

The `/ws/chat` STOMP endpoint gets real authentication: a new `ChannelInterceptor` validates a bearer token sent as a STOMP `CONNECT` header (via the existing `JwtDecoder` bean) and attaches a JWT-backed `Principal` to the STOMP session, so `ChatController`'s `@MessageMapping` handler can derive the user's identity the same way HTTP controllers do via `@Header("simpUser")`.

Notifications switch from a manually UUID-keyed topic (`/topic/notifications/{clientId}`) to Spring's built-in per-user destination convention (`convertAndSendToUser(userId, "/queue/notifications", dto)` on the backend, a fixed `/user/queue/notifications` subscription on the frontend) — now that the STOMP connection carries a real authenticated principal, this is the correct, idiomatic delivery mechanism rather than a manually-keyed broadcast topic that anyone who guessed the UUID could theoretically subscribe to.

On the frontend, every service (`favorites.service.ts`, `notifications.service.ts`, `agent-task.service.ts`) drops its own `clientId`/random-UUID generation and stops sending any identity parameter at all — the existing bearer-token HTTP interceptor and the new STOMP auth header supply identity instead. Only chat's `sessionId` (conversation-thread id, `localStorage['hermes-chat-session']`) survives as a client-held value; its meaning does not change in this phase.

---

## Database

Pre-launch data isn't worth preserving here — every existing row's identity column holds a meaningless anonymous browser UUID anyway. Both migrations truncate first, so `user_id` can be declared `NOT NULL` everywhere rather than nullable-with-orphaned-data.

### `V12__rename_client_id_to_user_id.sql`

```sql
TRUNCATE TABLE notifications, agent_tasks, favorites CASCADE;

ALTER TABLE favorites RENAME COLUMN client_id TO user_id;
ALTER INDEX idx_favorites_client_id RENAME TO idx_favorites_user_id;
ALTER TABLE favorites RENAME CONSTRAINT uq_favorites_client_listing TO uq_favorites_user_listing;

ALTER TABLE notifications RENAME COLUMN client_id TO user_id;
ALTER INDEX idx_notifications_client_id_created RENAME TO idx_notifications_user_id_created;

ALTER TABLE agent_tasks RENAME COLUMN client_id TO user_id;
```

`notifications.task_id` has an `ON DELETE CASCADE` FK to `agent_tasks(id)`, so `agent_tasks` must be truncated together with (or `CASCADE`d into) `notifications` — listing `notifications` first and using `CASCADE` handles this in one statement regardless of ordering. `user_id` stays `NOT NULL` on all three tables (it already was before the rename).

### `V13__add_user_id_to_chat_messages.sql`

```sql
TRUNCATE TABLE chat_messages;

ALTER TABLE chat_messages ADD COLUMN user_id UUID NOT NULL;
CREATE INDEX idx_chat_messages_user_id ON chat_messages(user_id);
```

Truncating first lets `user_id` be `NOT NULL` from the start rather than nullable — every message from this point on has a real owner. `session_id` is untouched.

---

## Backend

### WebSocket authentication (new)

- `WsAuthChannelInterceptor implements ChannelInterceptor` (new, `com.kropholler.dev.hermes.config`): on `preSend` for STOMP `CONNECT` frames, reads the `Authorization` STOMP header, strips the `Bearer ` prefix, decodes/validates it via the existing `JwtDecoder` bean, maps roles via the same `JwtAuthenticationConverter` logic `SecurityConfig` already uses (reused, not duplicated), and calls `accessor.setUser(jwtAuthenticationToken)` so the STOMP session carries a real authenticated principal for its whole lifetime. A missing/invalid token throws, which STOMP surfaces as a connection error — `/ws/chat` is no longer anonymous.
- `WebSocketConfig.configureClientInboundChannel(ChannelRegistration registration)` registers this interceptor.
- `ChatController.handleMessage` gains a `@Header("simpUser") Principal principal` parameter and derives identity via `CurrentUser.from((Jwt) ((JwtAuthenticationToken) principal).getPrincipal())` — `CurrentUser.current()` (which reads `SecurityContextHolder`) isn't usable here since STOMP message handling doesn't populate that per-message the way the HTTP filter chain does.

### Favorites, Notifications, Agent Tasks — identical mechanical shape across all three

- Entity field rename `clientId` → `userId` in `FavoriteEntity`, `NotificationEntity`, `AgentTaskEntity`; repository method renames (`findByClientId` → `findByUserId`, `existsByClientIdAndListingId` → `existsByUserIdAndListingId`, `countByClientIdAndReadFalse` → `countByUserIdAndReadFalse`, etc., mirroring each existing method 1:1).
- Each `openapi/*.yaml` (`favorites.yaml`, `notifications.yaml`, `agent-tasks.yaml`) drops the `clientId` parameter from every operation — a real, intentional breaking API change, not additive. `FavoriteController`/`NotificationController`/`AgentTaskController` all call `CurrentUser.current().id()` instead, exactly like `UserProfileController` already does.
- `ChatToolProvider.provideTools(UUID clientId)` becomes `provideTools(UUID userId)`; `GetFavouriteListingsTool`, `SaveWatchTool`, `ListWatchesTool` are constructed with that id, now sourced from the STOMP principal in `ChatController` rather than a request DTO field.
- `NotificationService.save(UUID taskId, UUID userId, NotificationContent content)` (renamed parameter) switches its publish call from `messaging.convertAndSend("/topic/notifications/" + clientId, dto)` to `messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/notifications", dto)`.

### Chat

- `ChatMessageEntity` gains `userId` (`UUID`, non-null — the table is truncated in `V13` specifically so this can be a hard requirement from day one).
- `ChatMessageRequest` drops its `clientId` field entirely.
- `AiChatService.startStream(UUID sessionId, String userMessage, UUID userId)` — drops the `clientId` parameter and the `effectiveClientId` fallback (`clientId != null ? clientId : sessionId`) entirely; identity always comes from the STOMP principal now, never from the request.
- `AiChatService.saveUserMessage`/`saveAssistantMessage` gain a `userId` parameter to stamp onto each saved message.
- `session_id` is untouched — still client-generated, still just groups messages into one conversation thread.

---

## Frontend

- **`favorites.service.ts`**: drops `clientId` entirely. URLs become `/api/favorites` (GET) and `/api/favorites/{listingId}` (PUT/DELETE). The existing bearer-token HTTP interceptor supplies identity; `isFavorite`/`toggle`'s public API is unchanged.
- **`notifications.service.ts`**: drops the `clientId` query param on `GET /api/notifications`. STOMP subscription changes from `/topic/notifications/${clientId}` to the fixed string `/user/queue/notifications` — no id needed client-side, Spring resolves it from the authenticated STOMP session.
- **`agent-task.service.ts`**: drops the `clientId` query param the same way.
- **`chat.service.ts`**: stops sending `clientId` in the WebSocket payload (the field no longer exists on the backend's `ChatMessageRequest`); keeps generating/reusing `sessionId` from `localStorage['hermes-chat-session']` exactly as today. The `Client` construction gains a `beforeConnect` callback that fetches a fresh token via `inject(Keycloak)` (handles reconnects/token expiry correctly, rather than a header fixed once at construction time) and sets it as the STOMP `connectHeaders.Authorization` before each connection attempt.
- **`notification-bell.component.ts`**: subscribe call updated to the fixed `/user/queue/notifications` string.
- No frontend service generates a `clientId`-flavored UUID after this phase — `localStorage['hermes-chat-session']` continues to exist solely as chat's conversation-thread id.

---

## Testing

- **Backend**: `FavoriteControllerTest`/`FavoriteServiceTest`, `NotificationControllerTest`/`NotificationServiceTest`, `AgentTaskControllerTest`/`AgentTaskServiceTest` get their `clientId` path/query-param tests replaced with JWT-subject-based tests matching `UserProfileControllerTest`'s pattern (explicit per-request `.with(jwt().jwt(b -> b.subject(...)))`, asserting the service is invoked with that exact id — proving one user's token can't touch another's data). New `WsAuthChannelInterceptorTest`: valid bearer token on CONNECT → principal set with correctly-mapped authorities; missing/invalid token → connection rejected. `NotificationServiceTest` updated to verify `convertAndSendToUser(userId.toString(), "/queue/notifications", dto)` replaces the old topic-string `convertAndSend`. `AiChatServiceTest` updated to verify `userId` is stamped on saved messages and tools are provisioned with the STOMP principal's id.
- **Frontend**: each service spec updated to assert the new clientId-free URLs/topics. A focused spec on `chat.service.ts`'s `beforeConnect` header-setting logic, without needing a real STOMP server.
- **Manual**: log in as `testuser`, add a favorite / trigger a notification (e.g. via a completed agent task) / create a watch via chat / send a chat message — confirm everything persists correctly scoped to `testuser`. Log in as `testadmin` and confirm none of `testuser`'s data is visible. Confirm an unauthenticated WebSocket CONNECT attempt to `/ws/chat` (auth header stripped/omitted) is rejected. Confirm a live notification arrives over `/user/queue/notifications` in the browser in real time.
