# Hermes Frontend Design Spec
Date: 2026-06-09

## Overview

A single-page Angular 22 application that provides a UI for the Hermes House Tracker backend. Users can trigger Funda.nl scraping sessions, browse saved listings, view listing detail and AI summaries, and inspect price history reports.

The app uses **Angular 22 standalone components**, **signals** for reactive state, **Tailwind CSS** for styling, and **Chart.js via ng2-charts** for the price history chart. It communicates with the Spring Boot backend exclusively via `HttpClient`, proxied through Angular's dev server during development.

---

## Tech Stack

| Concern | Choice |
|---|---|
| Framework | Angular 22, standalone components, zoneless |
| Reactivity | Angular signals (`signal`, `computed`, `effect`) |
| Styling | Tailwind CSS 4 + `@tailwindcss/forms` |
| HTTP | Angular `HttpClient` with `provideHttpClient(withFetch())` |
| Chart | Chart.js + `ng2-charts` |
| Dev proxy | `proxy.conf.json` → `http://localhost:8080` |

### Angular 22 notes

- **Zoneless by default** — `zone.js` is not included. Change detection runs via signals. `provideZonelessChangeDetection()` replaces `provideClientHydration()` + zone bootstrapping.
- **Control flow syntax** — All templates use `@if`, `@for`, `@switch` (the structural directive syntax `*ngIf`/`*ngFor` is not used).
- **Standalone only** — No NgModules. All components, pipes, and directives are standalone.
- **`provideHttpClient(withFetch())`** — Standard HTTP provider; uses the Fetch API under the hood.

---

## Routing

| Route | Component | Description |
|---|---|---|
| `/` | redirect | Redirects to `/listings` |
| `/listings` | `ListingsPageComponent` | Paginated listings table |
| `/listings/:id` | `ListingDetailPageComponent` | Detail, snapshot, AI summary, rescrape |
| `/listings/:id/report` | `ListingReportPageComponent` | Price chart, status history, stats |
| `/scraping` | `ScrapingPageComponent` | Session form + live status polling |

---

## File Structure

```
hermes-frontend/src/app/
├── core/
│   ├── api.types.ts                    — TypeScript interfaces matching backend OpenAPI models
│   ├── listings.service.ts             — Signal state + HttpClient for /api/listings
│   └── scraping.service.ts             — Signal state + HttpClient for /api/scraping-sessions
├── pages/
│   ├── listings/
│   │   └── listings-page.component.ts
│   ├── listing-detail/
│   │   └── listing-detail-page.component.ts
│   ├── listing-report/
│   │   └── listing-report-page.component.ts
│   └── scraping/
│       └── scraping-page.component.ts
├── shared/
│   ├── status-badge.component.ts       — Coloured status pill
│   └── euro-price.pipe.ts              — Formats 450000 → € 450.000
├── app.component.ts                    — Shell with top nav
├── app.routes.ts
└── app.config.ts
```

---

## Data Layer

### `api.types.ts`

TypeScript interfaces derived from the backend OpenAPI models:

- `ScrapingSessionResponse` — `id`, `status` (enum), `type` (enum), `createdAt`, `completedAt`
- `CreateScrapingSessionRequest` — `city`, `minPrice?`, `maxPrice?`, `minArea?`, `maxArea?`, `pageLimit`
- `ListingPage` — `content: ListingSummaryResponse[]`, `totalElements`, `totalPages`, `page`, `size`
- `ListingSummaryResponse` — `id`, `street`, `houseNumber`, `houseNumberAddition?`, `zipCode`, `city`, `province`, `askingPrice?`, `status?`, `firstSeenAt`
- `ListingDetailResponse` — all fields from summary + `fundaId`, `url`, `lastSeenAt`, `latestSnapshot?`
- `SnapshotResponse` — `id`, `scrapedAt`, `askingPrice?`, `livingAreaM2?`, `rooms?`, `energyLabel?`, `listedOnFundaSince?`, `status?`
- `ListingReportResponse` — `listingId`, `daysListedOnFunda?`, `daysInHermes`, `currentPrice?`, `initialPrice?`, `priceChangePct?`, `priceHistory`, `statusHistory`, `currentStatus?`
- `PricePointResponse` — `scrapedAt`, `askingPrice?`
- `StatusPointResponse` — `scrapedAt`, `status`
- `AiSummaryResponse` — `listingId`, `summary`, `generatedAt`
- `ErrorResponse` — `error`, `detail`

### `ListingsService`

`injectable({ providedIn: 'root' })`, wraps `HttpClient`.

**Signals:**
```
listings        = signal<ListingPage>(empty default)
currentListing  = signal<ListingDetailResponse | null>(null)
report          = signal<ListingReportResponse | null>(null)
summary         = signal<AiSummaryResponse | null>(null)
loading         = signal<boolean>(false)
error           = signal<string | null>(null)
```

**Methods:**
- `loadListings(page: number, size: number)` — GET `/api/listings?page=&size=`
- `loadListing(id: string)` — GET `/api/listings/:id`
- `loadReport(id: string)` — GET `/api/listings/:id/report`
- `loadSummary(id: string)` — GET `/api/listings/:id/summary`
- `rescrape(id: string): Observable<ScrapingSessionResponse>` — POST `/api/listings/:id/rescrape`

### `ScrapingService`

`injectable({ providedIn: 'root' })`, wraps `HttpClient`.

**Signals:**
```
session   = signal<ScrapingSessionResponse | null>(null)
loading   = signal<boolean>(false)
error     = signal<string | null>(null)
```

**Methods:**
- `createSession(req: CreateScrapingSessionRequest)` — POST `/api/scraping-sessions`; on success sets `session` signal and starts polling
- `pollSession(id: string)` — GET `/api/scraping-sessions/:id` every 3 seconds using `setInterval`; stops when status is `COMPLETED`, `FAILED`, or `TIMED_OUT`; updates `session` signal on each tick

---

## Pages

### Scraping Page (`/scraping`)

**Form fields:**
- City (text, required)
- Min price, max price (number, optional)
- Min area, max area (number, optional)
- Page limit (number 1–5, required, default 3)

**Behaviour:**
1. On submit: calls `ScrapingService.createSession()`, disables form
2. Shows a status card below the form: session ID, status badge, timestamps
3. Status card auto-updates every 3s via polling until terminal status
4. On terminal status: re-enables form, shows success/failure message

### Listings Page (`/listings`)

**Table columns:** Address (street + number), City, Price, Area (m²), Rooms, Energy Label, Status, First Seen

**Behaviour:**
- Loads on init with page=0, size=20
- Pagination: prev/next buttons + page info ("Page 2 of 14")
- Page size selector: 10 / 20 / 50
- Row click navigates to `/listings/:id`

### Listing Detail Page (`/listings/:id`)

Two-column layout on desktop (≥768px), single column on mobile.

**Left column:**
- Full address (street, number, addition, zip, city, province)
- Latest snapshot: price, area, rooms, energy label, status badge, listed on Funda since
- "View report →" link to `/listings/:id/report`

**Right column:**
- AI summary section: heading "AI Summary", text content or skeleton loader while fetching, `generatedAt` timestamp
- "Trigger Rescrape" button — on click calls `rescrape()`, shows inline status badge + polling until done

### Listing Report Page (`/listings/:id/report`)

**Stats row (4 cards):**
- Days on Funda
- Days in Hermes
- Price change % (green if negative/lower, red if positive/higher)
- Current price

**Price history chart:**
- Line chart using `ng2-charts` (Chart.js)
- X axis: `scrapedAt` dates, Y axis: price in euros
- Single dataset: asking price over time

**Status history:**
- Ordered list of status changes with timestamp
- Each entry: `StatusBadge` + formatted date

---

## Shared Components

### `StatusBadgeComponent`

Input: `status: string`. Renders a small pill with text and colour:

| Status | Colour |
|---|---|
| PENDING | grey |
| IN_PROGRESS | blue |
| COMPLETED | green |
| FAILED | red |
| TIMED_OUT | red |
| FOR_SALE | green |
| UNDER_OFFER | orange |
| SOLD | grey |
| WITHDRAWN | red |

### `EuroPricePipe`

Transforms `450000` → `€ 450.000` (Dutch formatting: dot as thousands separator).

---

## Dev Proxy

`hermes-frontend/proxy.conf.json`:
```json
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true
  }
}
```

Referenced in `angular.json` under `serve.options.proxyConfig`.

---

## Tailwind Setup

Tailwind 4 uses CSS-only configuration — no `tailwind.config.js`.

- Install `tailwindcss` and `@tailwindcss/vite` (or `@tailwindcss/postcss`) as dev dependencies
- `styles.scss` (or `styles.css`) contains a single `@import "tailwindcss"` directive
- `@tailwindcss/forms` plugin added via `@plugin "@tailwindcss/forms"` in the CSS file

---

## Error Handling

- All service methods catch HTTP errors and set `error` signal with the backend's `detail` message (or a generic fallback)
- Pages display an error banner when `error()` is non-null
- 404 responses on detail/report/summary pages show a "Not found" message rather than an error banner

---

## Testing Strategy

- Unit tests for `EuroPricePipe` (pure transformation, easy to test)
- Unit tests for `StatusBadgeComponent` (input → correct CSS class)
- No E2E tests in this phase
