# Agentic AI — Design Spec

**Date:** 2026-06-19  
**Status:** Approved

## Overview

Extend Hermes with a proactive agentic layer on top of the existing reactive chat. The system gains three agentic capabilities — listing watches, on-demand research, and periodic market digests — unified under a single `AgentTask` execution loop with in-app and email notification delivery.

---

## Data Model

### `AgentTask`

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `type` | enum `WATCH \| RESEARCH \| DIGEST` | determines which handler runs |
| `status` | enum `ACTIVE \| PAUSED \| COMPLETED` | |
| `clientId` | UUID | ties to existing favourites client identity |
| `name` | String | user-facing label, e.g. "3-bed Utrecht under €350k" |
| `payload` | JSONB | type-specific config (see per-type payloads below) |
| `schedule` | String (nullable) | cron expression, e.g. `0 8 * * *`; null for one-shot tasks (RESEARCH) |
| `lastRunAt` | Instant | when it last executed |
| `nextRunAt` | Instant | computed after each run |
| `createdAt` | Instant | |

**WATCH payload:** serialised search criteria matching `ListingService.findForChat` parameters (city, province, minPrice, maxPrice, minBedrooms, minRooms, minLivingAreaM2, keywords, nearCity, radiusKm).

**RESEARCH payload:** `{ "prompt": "<user research request>" }`.

**DIGEST payload:** `{ "cities": ["Amsterdam", "Utrecht"] }`.

### `Notification`

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `taskId` | UUID | FK to `AgentTask` |
| `clientId` | UUID | for in-app delivery |
| `title` | String | short summary |
| `body` | String (TEXT) | AI-generated content |
| `listingIds` | JSONB (UUID[]) | listings to render as cards (WATCH notifications); stored as JSON array |
| `read` | boolean | for in-app badge/dismiss |
| `createdAt` | Instant | |
| `emailSentAt` | Instant | null if not yet emailed |

---

## Agent Task Executor

### Scheduler

`AgentTaskScheduler` — a Spring `@Scheduled` component ticking every minute. Queries `AgentTask` where `status = ACTIVE AND next_run_at <= now()`. Dispatches each due task to `AgentTaskExecutor`.

### Executor

`AgentTaskExecutor` holds a `Map<AgentTaskType, AgentTaskHandler>` strategy map. Calls the matching handler, receives a `NotificationDto`, delegates to `NotificationService`.

### Handlers

**`WatchTaskHandler`**
- Deserialises search criteria from `payload`.
- Calls `ListingService.findForChat(...)`.
- Compares results against `lastRunAt` (filters to listings `firstSeenAt > lastRunAt` OR price changed since `lastRunAt`).
- Produces a notification only when there are new or changed listings.

**`ResearchTaskHandler`**
- Takes `prompt` from `payload`.
- Invokes the AI `ChatClient` in non-streaming mode with all existing tools available.
- Full AI response becomes the notification body.
- One-shot tasks (`RESEARCH` with no repeating schedule) are marked `COMPLETED` after running.

**`DigestTaskHandler`**
- Takes `cities` from `payload`.
- Gathers stats per city via `ListingService`: new listing count since last run, average current price, top 3 price drops.
- Passes structured stats to the AI to narrate into a friendly digest.
- Notification body is the AI-generated narrative.

### Notification Delivery

`NotificationService`:
1. Persists `Notification` row.
2. Publishes to `/topic/notifications/{clientId}` via STOMP `SimpMessagingTemplate` (reuses existing `WebSocketConfig`).
3. Submits async email task via existing `@Async` executor — uses Spring `JavaMailSender` with a simple HTML template.

---

## Chat Integration

Three new tools registered in `AiChatService.startStream(...)`:

### `SaveWatchTool`
- **Trigger:** user asks to be alerted/notified/monitored for listings.
- **Behaviour:** AI extracts search criteria it would pass to `searchListings`, calls this tool to persist an `ACTIVE AgentTask` of type `WATCH` with a daily schedule (`0 8 * * *` default).
- **Returns:** confirmation string, e.g. `"Watch 'Utrecht 3-bed' saved — I'll alert you when matching listings appear or prices change."`

### `TriggerResearchTool`
- **Trigger:** user asks for deep analysis, a full report, or "research my favourites".
- **Behaviour:** AI composes a research prompt, calls this tool which persists a one-shot `RESEARCH AgentTask` with `nextRunAt = now()`.
- **Returns:** `"Research queued — results will appear as a notification shortly."` Does not block the chat stream.

### `ListWatchesTool`
- **Trigger:** user asks what alerts/watches are active, or asks to cancel one.
- **Behaviour:** returns active `AgentTask` records for the `clientId`. Accepts an optional `deleteId` to cancel a watch.
- **Returns:** formatted list of active watches with names and schedules.

### System Prompt Additions

```
- Call saveWatch when the user asks to be alerted, notified, or monitored for listings.
- Call triggerResearch when the user wants a deep analysis or report run in the background.
- Call listWatches when the user asks what alerts or watches they have set up.
- Never run research inline in the chat — always queue it via triggerResearch.
```

---

## Frontend

### Notification Bell
- Badge icon in app header showing unread `Notification` count.
- Clicking opens a slide-out panel: title, body preview, timestamp, read/unread indicator per notification.
- `PATCH /api/notifications/{id}/read` marks a notification read.

### WebSocket Subscription
- New `NotificationsService` subscribes to `/topic/notifications/{clientId}` on app init.
- Incoming messages increment badge counter and prepend to notification list in real time.

### Watches Page (`/watches`)
- New route listing active `AgentTask` records for the current client.
- Columns: name, type, schedule, last run, status.
- Delete button calls `DELETE /api/agent-tasks/{id}`.
- No create form — watches are created exclusively through chat.

### Notification Detail
- Clicking a notification in the panel expands the full AI-generated body inline.
- `WATCH` notifications render matched listing IDs as `ChatListingCard` components (existing component reuse).

---

## API Endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/notifications` | list notifications for clientId (query param) |
| `PATCH` | `/api/notifications/{id}/read` | mark read |
| `GET` | `/api/agent-tasks` | list agent tasks for clientId |
| `DELETE` | `/api/agent-tasks/{id}` | cancel/delete a task |

---

## Out of Scope

- User authentication / multi-user support (existing limitation, unchanged).
- Push notifications (browser/mobile) — in-app + email is sufficient.
- Editing a watch after creation — delete and recreate via chat.
