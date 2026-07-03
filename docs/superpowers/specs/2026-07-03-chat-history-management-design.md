# Chat History Management — Design Spec

**Date:** 2026-07-03

## Goal

Phase 5 (final phase) of the Keycloak/roles/multi-user initiative. Let users start new conversations, see a list of their past conversations, switch between them, and delete ones they no longer want — turning the current "one ever-growing session per browser" model into real chat history management.

## Current State

`chat_messages` (id, session_id, user_id, role, content, created_at) already persists every message, scoped by both `session_id` and `user_id` (fixed in phase 3 to stop history leaking across users sharing a browser). But there is no REST endpoint to list or retrieve past messages, and the frontend (`ChatService` in `hermes-frontend/src/app/core/chat.service.ts`) keeps exactly one `sessionId` per browser in `localStorage['hermes-chat-session']`, generated once and reused forever. Refreshing the page loses the visible transcript even though it's still in the database — it's just never fetched back.

## Out of Scope

- No admin visibility into other users' conversations — scoped to owner only, consistent with phase 4's decision that Keycloak's own console covers admin-facing user management. Chat content is more sensitive than watches/notifications, so this is a stricter default than phase 4's original plan, not a looser one.
- No renaming/editing conversation titles — titles are derived, not stored or user-editable.
- No pagination beyond a simple recency cap (matches the existing `findTop50...` pattern used for notifications).

---

## Architecture Overview

No new table. A "conversation" is simply the set of `chat_messages` rows sharing one `session_id` for one `user_id` — this already exists today, phase 5 just exposes it. Three new REST endpoints, backed by a new `ChatHistoryService` (kept separate from `AiChatService`, which owns LLM orchestration — listing/deleting conversations is a distinct responsibility):

- `GET /api/chat/sessions` — lists the caller's conversations, most recent first, capped at 50. Each entry: `sessionId`, a title snippet derived from the conversation's first `USER` message (truncated), and `lastMessageAt`.
- `GET /api/chat/sessions/{sessionId}/messages` — the full transcript for one conversation (role, content, createdAt per message), ordered oldest-first.
- `DELETE /api/chat/sessions/{sessionId}` — deletes all messages for that session belonging to the caller.

All three query/mutate scoped to `(session_id, user_id)` together at the database level — there is no separate "look up by id, then check ownership" step the way phase 4's `AgentTaskService.delete`/`NotificationService.markRead` needed. Because of that, requesting or deleting another user's `session_id` isn't a 403 case: the query itself only ever touches rows matching the caller's own `user_id`, so it silently returns an empty list / no-op delete (200 with `[]`, or 204 with nothing changed) — behaviorally identical to a session that never existed. This matches the security posture already documented in `AiChatService`'s class comment (a session id was never trusted as an identity mechanism on its own) and needs no new enforcement mechanism.

The listing query is a native SQL query grouped by `session_id` (JPQL doesn't cleanly express "first message per group" the way Postgres can): `MAX(created_at)` per session for recency, and a correlated subquery for the earliest `USER`-role message's content as the title source. Capped with `LIMIT 50` in the query itself, same shape as the existing `findTop50ByUserIdOrderByCreatedAtDesc` notifications query.

A conversation with zero messages never appears in the list, because it doesn't exist in the database until the first message is saved — "New chat" is purely a frontend action (generate a new UUID, same mechanism `ChatService.initSession()` already uses today) and needs no backend call or special-casing until the user actually sends something.

---

## Backend

- New OpenAPI spec `hermes-backend/src/main/resources/openapi/chat-history.yaml` defining the three endpoints above, generating a `ChatHistoryApi` interface (same codegen pattern as `agent-tasks.yaml`/`notifications.yaml`).
- `ChatHistoryController implements ChatHistoryApi`, deriving `userId` from `CurrentUser.current().id()` for every operation — no client-supplied identity, consistent with every other controller since phase 3.
- `ChatHistoryService`: `listSessions(UUID userId)`, `getMessages(UUID userId, UUID sessionId)`, `deleteSession(UUID userId, UUID sessionId)`. Backed by new `ChatMessageRepository` methods: a native `@Query` for the grouped listing, `findBySessionIdAndUserIdOrderByCreatedAtAsc` (already exists, reused for the transcript endpoint), and a new `deleteBySessionIdAndUserId`.
- Title truncation: first `USER` message content, truncated to 60 characters with an ellipsis if longer (matches typical chat-app title lengths; no configuration needed).

## Frontend

- `ChatService` gains: a `sessions` signal (list from `GET /api/chat/sessions`), refreshed when the history panel is opened, after a delete, and after the *first* message of a brand-new conversation completes streaming (so the new conversation appears in the list with its derived title) — not after every message, to avoid a network round-trip on each send for no visible benefit. A `switchSession(sessionId)` method unsubscribes the current STOMP topic, sets the new active `sessionId`, persists it to `localStorage['hermes-chat-session']`, fetches that session's messages via REST to repopulate the `messages` signal, and resubscribes to `/topic/chat/{sessionId}`. A `startNewChat()` method does the same but with a freshly generated UUID and an empty `messages` list — no REST call needed since nothing exists yet.
- A new history panel/dropdown (extending `chat-panel.component`) lists conversations by title snippet + relative timestamp, with a delete action per entry and a "New chat" button. Deleting the currently-active conversation calls `startNewChat()` immediately after the delete succeeds.
- No changes to the STOMP message-sending path (`sendMessage`) or the token-streaming subscription logic itself — only which `sessionId` it's pointed at changes.

## Testing

- **Backend:** `@WebMvcTest` slices for `ChatHistoryController` covering list/get-messages/delete, using the same `.with(jwt().jwt(builder -> builder.subject(...)))` pattern established since phase 3 to control the caller identity; a repository test for the grouped listing query against a real test database (matching how existing radius/geo queries in `ListingRepositoryGeoTest` are tested) verifying correct grouping, title-source selection, and recency ordering with multiple sessions and multiple users; a service test confirming a `session_id` belonging to a different user genuinely returns empty/no-op rather than leaking or erroring.
- **Frontend:** unit tests for the new history panel component (renders list, delete triggers `startNewChat()` when deleting the active session) and for `ChatService`'s `switchSession`/`startNewChat` methods (correct STOMP unsubscribe/resubscribe, correct `localStorage` update, correct message-list repopulation).
- **Manual:** create two real conversations as the same user, confirm both appear in the list with sensible titles, switch between them and confirm the correct transcript loads and new messages land in the right STOMP topic, delete the non-active one and confirm it disappears without affecting the active conversation, then delete the active one and confirm a new empty conversation starts automatically. Confirm a second test user's conversations never appear in the first user's list.
