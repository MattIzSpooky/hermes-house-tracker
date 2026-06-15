# AI House-Finding Chat — Design Spec

**Date:** 2026-06-15

## Goal

Add a globally visible chat bubble to the frontend that lets users describe what they are looking for in natural language. The AI assistant maintains conversation memory per session, calls a structured search tool when the user expresses a housing preference, and streams its reply token by token. Matched listings are returned as clickable cards alongside the conversational text.

---

## Architecture Overview

Three layers:

1. **WebSocket transport (STOMP)** — bidirectional, streaming-friendly channel between the Angular app and the Spring Boot backend.
2. **AI chat service (backend)** — orchestrates conversation history, model invocation with tool calling, and result delivery.
3. **Chat UI (frontend)** — a global floating bubble + panel built as two standalone Angular components with a signal-based service.

---

## Backend

### WebSocket / STOMP Configuration

A new `WebSocketConfig` in the `config` package enables `@EnableWebSocketMessageBroker`.

- **Endpoint:** `/ws/chat` with SockJS fallback.
- **Application destination prefix:** `/app` (inbound from client).
- **Broker prefixes:** `/topic` (outbound to client).
- **Session channel:** client subscribes to `/topic/chat/{sessionId}` where `sessionId` is a UUID the client generates.
- No Spring Security user principal required — sessions are anonymous and identified by `sessionId`.

### Chat History — Database

Flyway **V5** migration creates `chat_messages`:

```sql
CREATE TABLE chat_messages (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID        NOT NULL,
    role       VARCHAR(16) NOT NULL,  -- 'USER' or 'ASSISTANT'
    content    TEXT        NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_messages_session_id ON chat_messages (session_id, created_at);
```

**JPA entity** `ChatMessage` and **repository** `ChatMessageRepository` live in `ai/internal`. Before calling the model, the service loads all prior messages for the session ordered by `created_at` and feeds them into the `ChatClient` as conversation history.

### Listing Search Tool

`ListingSearchTool` (in `ai/internal`) registers a Spring AI `@Tool` method named `searchListings`. The model calls this automatically when it determines a search is needed.

**Tool parameters:**

| Parameter | Type | Purpose |
|---|---|---|
| `minPrice` | Integer | Minimum asking price |
| `maxPrice` | Integer | Maximum asking price |
| `minBedrooms` | Integer | Minimum number of bedrooms |
| `minRooms` | Integer | Minimum total rooms |
| `minLivingAreaM2` | Integer | Minimum living area |
| `province` | String | Province filter |
| `city` | String | City filter |
| `keywords` | String | Free-text keywords for description search |

**Search logic:** combines the existing `Specification`-based JPA filters with a new PostgreSQL full-text predicate on the `description` column when `keywords` is non-blank:

```sql
to_tsvector('dutch', coalesce(description, '')) @@ plainto_tsquery('dutch', :keywords)
```

Returns at most **5** matching `ListingDto` objects. Only active (non-deleted) listings are considered.

The tool stores its results in a per-request holder (`AtomicReference` passed into the tool on construction) so the controller can read them after streaming completes.

### Ollama Models

| Purpose | Model |
|---|---|
| Chat (this feature) | `llama3.2:3b` |
| Listing summaries (existing) | `qwen3.5:0.8b` |

Two named `ChatClient` beans are configured in `AiConfig` using Spring AI's builder, one per model.

### System Prompt

```
You are a helpful Dutch real-estate assistant for the Hermes property tracker.
When the user expresses a preference for a property (location, size, price, features, etc.),
always call the searchListings tool to find matching listings.
You may ask clarifying questions before searching if the intent is unclear.
Respond in the same language the user writes in.
Keep replies concise and friendly.
```

### Message Flow

1. Client sends `{ sessionId, message }` to `/app/chat`.
2. `ChatController` (`@Controller`, `@MessageMapping("/chat")`) receives the payload.
3. User message is saved to `chat_messages`.
4. Service loads full session history from DB.
5. `ChatClient` is built with: system prompt, conversation history, `ListingSearchTool` instance (with fresh result holder).
6. `.stream().content()` returns a `Flux<String>`.
7. Each token is forwarded to `/topic/chat/{sessionId}` as a `TokenFrame`:
   ```json
   { "type": "TOKEN", "content": "..." }
   ```
8. On stream completion, the full assembled response is saved to `chat_messages` as role `ASSISTANT`.
9. If the tool was called, a `ResultFrame` is sent:
   ```json
   { "type": "RESULT", "listings": [ ... ] }
   ```
   Each listing entry contains: `id`, `street`, `houseNumber`, `houseNumberAddition`, `city`, `currentPrice`, `bedrooms`, `livingAreaM2`, `energyLabel`, `status`.

### New Backend Files

| File | Responsibility |
|---|---|
| `config/WebSocketConfig.java` | STOMP broker + endpoint registration |
| `ai/ChatController.java` | `@MessageMapping("/chat")` handler |
| `ai/internal/ChatMessage.java` | JPA entity |
| `ai/internal/ChatMessageRepository.java` | Spring Data repo |
| `ai/internal/ListingSearchTool.java` | Spring AI `@Tool` — delegates to `ListingService` + full-text query |
| `ai/internal/TokenFrame.java` | Record: `type`, `content` |
| `ai/internal/ResultFrame.java` | Record: `type`, `listings` |
| `ai/AiConfig.java` | Named `ChatClient` beans for chat and summary models |
| `resources/db/migration/V5__add_chat_messages.sql` | Schema migration |

### Modified Backend Files

| File | Change |
|---|---|
| `listing/internal/ListingRepository.java` | Add native full-text search query method |
| `pom.xml` | Add `spring-boot-starter-websocket` dependency |
| `application.properties` | Add named Ollama model properties for the chat `ChatClient` bean |
| `openapi/api.yaml` | No change — WebSocket is not described in OpenAPI |

---

## Frontend

### New Dependency

`@stomp/stompjs` added to `package.json` for the STOMP WebSocket client.

### ChatService

`src/app/core/chat.service.ts` — injectable service (provided in root).

**Responsibilities:**
- On construction: load or generate `sessionId` UUID from `localStorage`.
- Open STOMP connection to `/ws/chat`; subscribe to `/topic/chat/{sessionId}`.
- Expose signals:
  - `messages: Signal<ChatMessage[]>` — full conversation history (role + content).
  - `isStreaming: Signal<boolean>` — true while tokens are arriving.
  - `pendingListings: Signal<ListingSummaryResponse[]>` — listings from the latest `RESULT` frame (cleared when a new user message is sent).
  - `isOpen: Signal<boolean>` — panel visibility.
- On `TOKEN` frame: append content to the last assistant message in-place (streaming effect).
- On `RESULT` frame: set `pendingListings`.
- `sendMessage(text: string)`: save user message locally, clear pending listings, publish to `/app/chat`.
- `toggle()`: flip `isOpen`.
- Handle reconnection automatically via `@stomp/stompjs` retry config.

### ChatBubbleComponent

`src/app/shared/chat-bubble.component.ts` + `chat-bubble.component.html`

- Fixed position, bottom-right corner (`fixed bottom-6 right-6`).
- Circular button that calls `chatService.toggle()`.
- Shows a pulsing dot indicator while `chatService.isStreaming()` is true.
- Conditionally renders `<app-chat-panel>` when `chatService.isOpen()` is true.
- Added to `app.component.html` alongside `<router-outlet>` — one line, always present.

### ChatPanelComponent

`src/app/shared/chat-panel.component.ts` + `chat-panel.component.html`

- Absolute/fixed panel anchored above the bubble (`fixed bottom-20 right-6`), fixed width and max height with overflow scroll.
- Wrapped in `SectionCardComponent`.
- Renders each message from `chatService.messages()`:
  - User messages: right-aligned.
  - Assistant messages: left-aligned, streaming content displayed character-by-character with a blinking cursor while `isStreaming` is true.
- After each completed assistant message, if `pendingListings()` is non-empty, renders listing mini-cards below it. Each card uses `StatCardComponent` and shows: full address, price, bedrooms, living area, energy label, and a `routerLink` to `/listings/{id}`.
- Input field at the bottom: sends on Enter, disabled while `isStreaming` is true.

### Modified Frontend Files

| File | Change |
|---|---|
| `src/app/app.component.html` | Add `<app-chat-bubble>` |
| `src/app/app.component.ts` | Import `ChatBubbleComponent` |
| `src/app/core/api.types.ts` | Add `ChatMessageRequest`, `TokenFrame`, `ResultFrame` interfaces |
| `package.json` | Add `@stomp/stompjs` |

---

## Error Handling

- If the STOMP connection drops, `@stomp/stompjs` retries automatically. `isStreaming` is reset to false on disconnect.
- If the model fails mid-stream, the backend sends an error `TokenFrame` (`{ type: "ERROR", content: "..." }`) and the frontend displays it as a system message.
- If `searchListings` returns no results, the tool returns an empty list and the model responds conversationally ("I couldn't find any listings matching your criteria").
- Chat history older than 30 days can be pruned by a scheduled task (future work, not in this spec).

---

## Testing

**Backend:**
- Unit test `ListingSearchTool` with mocked `ListingService` — verify parameter mapping and result capping at 5.
- Unit test `ChatController` with mocked `ChatService` — verify messages are saved and frames are forwarded.
- Integration test for the full-text search query against H2 or Testcontainers PostgreSQL.

**Frontend:**
- `ChatService` spec: mock STOMP client, verify `TOKEN` frames append to last message, `RESULT` frames set `pendingListings`.
- `ChatBubbleComponent` spec: verify toggle behaviour and streaming indicator.
- `ChatPanelComponent` spec: verify message rendering and listing card display.
