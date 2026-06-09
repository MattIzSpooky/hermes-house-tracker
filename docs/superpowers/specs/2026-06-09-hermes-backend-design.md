# Hermes Backend Design Spec
Date: 2026-06-09

## Overview

Hermes is a real estate tracking application that scrapes property listings from Funda.nl and persists them for analysis and tracking over time. The backend is a modular monolith built with Spring Modulith. Modules communicate exclusively via JPA-backed application events — no direct cross-module bean dependencies.

---

## Module Structure

```
com.kropholler.dev.hermes/
├── scraping/     Queue management, Funda.nl HTML parsing, session lifecycle
├── listing/      Listing + snapshot persistence, deduplication
├── report/       On-demand report aggregation over snapshot history
├── ai/           Ollama-backed plain-language listing summaries
└── api/          REST controllers (generated from OpenAPI spec), DTOs
```

Spring Modulith's module verification test runs at build time and fails if any module imports internal classes of another module.

---

## Data Model

### `scraping` module

**`ScrapingSession`**
| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `status` | Enum | `PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED`, `TIMED_OUT` |
| `type` | Enum | `SEARCH` (new session), `RESCRAPE` (single listing) |
| `city` | String | Search filter |
| `minPrice` | Integer | nullable |
| `maxPrice` | Integer | nullable |
| `minArea` | Integer | nullable, m² |
| `maxArea` | Integer | nullable, m² |
| `pageLimit` | Integer | 1–5, hard-capped at 5 |
| `fundaUrl` | String | Built from filters at session creation |
| `targetListingUrl` | String | nullable, set when `type=RESCRAPE` |
| `createdAt` | Instant | |
| `startedAt` | Instant | nullable |
| `completedAt` | Instant | nullable |

### `listing` module

**`Listing`**
| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `fundaId` | String | Funda's internal listing identifier, unique |
| `url` | String | Canonical Funda listing URL |
| `street` | String | |
| `houseNumber` | String | |
| `houseNumberAddition` | String | nullable (e.g. "A", "bis") |
| `zipCode` | String | |
| `city` | String | |
| `province` | String | |
| `firstSeenAt` | Instant | Set on first insert, never updated |
| `lastSeenAt` | Instant | Updated on every rescrape |

**`ListingSnapshot`** (immutable — never updated after insert)
| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `listingId` | UUID | FK to `Listing` |
| `scrapedAt` | Instant | |
| `askingPrice` | Integer | In euros |
| `livingAreaM2` | Integer | |
| `rooms` | Integer | |
| `energyLabel` | String | nullable (A+++, A++, …, G) |
| `listedOnFundaSince` | LocalDate | nullable |
| `status` | Enum | `FOR_SALE`, `UNDER_OFFER`, `SOLD`, `WITHDRAWN` |

### `ai` module

**`ListingSummary`**
| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `listingId` | UUID | FK to `Listing` |
| `summary` | Text | Plain-language summary from Ollama |
| `generatedAt` | Instant | |

---

## Event Flow

### Cross-module events

| Event | Published by | Consumed by | Payload |
|---|---|---|---|
| `ScrapingSessionCompleted` | `scraping` | `listing` | `sessionId`, `List<RawListing>` |
| `ScrapingSessionFailed` | `scraping` | — (logged) | `sessionId`, `reason` |
| `ListingSnapshotsCreated` | `listing` | `ai` | `List<listingId>` |

All events are declared in the publishing module's public API package and consumed via `@ApplicationModuleListener` in the receiving module.

### Scraping session lifecycle

```
POST /api/scraping-sessions
        │
        ▼
  ScrapingSession persisted (status=PENDING)
        │
        ▼
  @Async ScrapingWorker polls every 5s for PENDING sessions
  (one session processed at a time per worker thread)
        │
   Sets status=IN_PROGRESS, records startedAt
        │
        ▼
  FundaScraperService fetches pages via RestClient
  JSoup parses HTML → extracts RawListing per property card
  (up to pageLimit pages, hard max 5)
        │
        ├── success → status=COMPLETED, publishes ScrapingSessionCompleted
        └── error   → status=FAILED,    publishes ScrapingSessionFailed
```

**Timeout watchdog:** `@Scheduled` task runs every 30 seconds. Any `IN_PROGRESS` session with `startedAt` older than 3 minutes is marked `TIMED_OUT`.

### Listing persistence (triggered by `ScrapingSessionCompleted`)

```
ScrapingSessionCompleted received
        │
        ▼
  For each RawListing:
    Upsert Listing by fundaId (insert if new, update lastSeenAt if exists)
    Insert ListingSnapshot (always new record)
        │
        ▼
  Publish ListingSnapshotsCreated(List<listingId>)
```

### AI summary (triggered by `ListingSnapshotsCreated`)

```
ListingSnapshotsCreated received
        │
        ▼
  For each listingId:
    Fetch latest ListingSnapshot
    Build prompt with listing details
    Call Ollama via Spring AI chat client
    Upsert ListingSummary (replace previous summary if exists)
```

---

## Report Generation

Reports are computed on-demand from snapshots — nothing is persisted.

`ReportService.generateReport(listingId)` produces:

| Field | Calculation |
|---|---|
| `daysListedOnFunda` | today − earliest `listedOnFundaSince` across all snapshots |
| `daysInHermes` | today − `Listing.firstSeenAt` |
| `currentPrice` | latest snapshot `askingPrice` |
| `initialPrice` | first snapshot `askingPrice` |
| `priceChangePct` | `(currentPrice − initialPrice) / initialPrice × 100` |
| `priceHistory` | ordered list of `{scrapedAt, askingPrice}` |
| `statusHistory` | ordered list of `{scrapedAt, status}`, deduplicated on status change |
| `currentStatus` | latest snapshot `status` |

---

## API Design

### Schema-first approach

The OpenAPI 3.x spec is defined in `hermes-backend/src/main/resources/openapi/api.yaml`. The `openapi-generator-maven-plugin` generates Spring controller interfaces from the spec. The `api` module implements these interfaces.

### Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/scraping-sessions` | Enqueue a new search session |
| `GET` | `/api/scraping-sessions/{id}` | Get session status |
| `GET` | `/api/listings` | List all listings (paginated) |
| `GET` | `/api/listings/{id}` | Get listing with latest snapshot |
| `POST` | `/api/listings/{id}/rescrape` | Enqueue a rescrape session |
| `GET` | `/api/listings/{id}/report` | Get computed report |
| `GET` | `/api/listings/{id}/summary` | Get AI-generated summary |

Error responses follow a consistent shape:
```json
{ "error": "NOT_FOUND", "detail": "Listing 123 does not exist" }
```

---

## Scheduling

| Job | Schedule | Action |
|---|---|---|
| Timeout watchdog | Every 30s | Mark IN_PROGRESS sessions older than 3 min as TIMED_OUT |
| Nightly rescrape | `0 2 * * *` (02:00) | Enqueue a `RESCRAPE` ScrapingSession for every known Listing |

---

## Testing Strategy

- **Module structure test** — Spring Modulith's `ApplicationModuleTest` verifies no illegal cross-module dependencies
- **Integration tests** — Testcontainers spins up PostgreSQL + Ollama; tests cover the full event chain from session creation to snapshot persistence
- **Scraper unit tests** — `FundaScraperService` tested against saved HTML fixtures to avoid live network calls
- **Report unit tests** — `ReportService` tested with known snapshot sets for deterministic output
