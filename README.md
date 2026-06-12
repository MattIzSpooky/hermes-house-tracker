# Hermes House Tracker

Hermes is a real estate tracking application that scrapes property listings from [Funda.nl](https://www.funda.nl) and persists them for analysis and tracking over time. Users can initiate scraping sessions, search and filter tracked listings, view price history charts, and re-scrape individual listings on demand.

## Architecture

The backend is a **modular monolith** built with [Spring Modulith](https://spring.io/projects/spring-modulith). Modules communicate via Spring's application event system — no direct cross-module bean dependencies. This enforces clear boundaries while keeping deployment simple.

### Modules

| Module | Package | Responsibility |
|---|---|---|
| `scraping` | `com.kropholler.dev.hermes.scraping` | Scraping session management, queue processing, Funda.nl HTML parsing via JSoup |
| `listing` | `com.kropholler.dev.hermes.listing` | Real estate listing data model, persistence, deduplication, price history fetching |
| `report` | `com.kropholler.dev.hermes.report` | Report generation: days tracked, price trend, price change % |
| `ai` | `com.kropholler.dev.hermes.ai` | Ollama-backed LLM integration for listing summaries |
| `api` | `com.kropholler.dev.hermes.api` | REST controllers and OpenAPI-generated interfaces |

### Scraping Flow

```
User submits scrape request
        │
        ▼
  Scraping session queued (Spring Modulith JPA-backed events)
        │
        ▼
  Background worker processes session asynchronously
        │
        ▼
  Funda proxy fetches listing data
        │
        ▼
  ScrapingCompleted event published
        │
        ▼
  Listing module persists/updates listing records
        │
        ▼
  FetchPriceHistoryCommand sent to ActiveMQ Artemis queue
        │
        ▼
  Price history consumer (5 concurrent, max 10/min) fetches and stores price data
```

Scraping is fully asynchronous — it never blocks API responses. Scraping sessions are persisted so in-progress work survives restarts. Price history fetching is rate-limited via a shared Guava `RateLimiter` to avoid hammering the proxy.

### Re-scraping & Reports

Individual listings can be re-scraped at any time from the detail page. The report module aggregates price history entries to produce:

- Days tracked in Hermes
- Current and initial price
- Price change percentage
- Full price history chart

## Technology Stack

### Backend

| Layer | Technology |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.0.x |
| Architecture | Spring Modulith 2.0.x |
| Persistence | Spring Data JPA + PostgreSQL |
| Messaging | ActiveMQ Artemis (external broker) |
| Web scraping | Spring AI JSoup Document Reader |
| AI/LLM | Spring AI + Ollama (local) |
| HTTP client | Spring RestClient |
| Rate limiting | Guava RateLimiter |
| Observability | Micrometer + OpenTelemetry + Grafana LGTM |
| API contract | OpenAPI 3 (code-generated interfaces via openapi-generator-maven-plugin) |
| Build | Maven |
| Testing | JUnit 5 + Mockito + Testcontainers |

### Frontend

| Layer | Technology |
|---|---|
| Framework | Angular 22 (standalone components) |
| Language | TypeScript 6.0 |
| Styling | Tailwind CSS 4 |
| Charts | Chart.js + ng2-charts |

### Infrastructure (local dev via Docker Compose)

| Service | Purpose |
|---|---|
| PostgreSQL | Primary database |
| ActiveMQ Artemis | JMS message broker for async price history fetching |
| Ollama | Local LLM server |
| Grafana LGTM | Logs, traces, and metrics (OpenTelemetry) |

## Project Structure

```
hermes-house-tracker/
├── hermes-backend/                 Spring Boot application
│   ├── src/main/java/com/kropholler/dev/hermes/
│   │   ├── HermesBackendApplication.java
│   │   ├── scraping/               Scraping module
│   │   ├── listing/                Listing module (+ price history)
│   │   ├── report/                 Report module
│   │   ├── ai/                     AI module
│   │   └── api/                    REST controllers
│   ├── src/main/resources/
│   │   ├── openapi/api.yaml        API contract (source of truth)
│   │   └── application.properties
│   ├── docker-compose.yml          Local Docker services
│   └── pom.xml
└── hermes-frontend/                Angular application
    └── src/app/
        ├── core/                   Services and API types
        ├── pages/                  Route-level components
        └── shared/                 Reusable components and pipes
```

## Getting Started

### Prerequisites

- Java 25+
- Maven 3.9+
- Docker + Docker Compose
- Node.js 20+

### Run locally

**Start infrastructure:**
```bash
cd hermes-backend
docker compose up -d
```

**Start backend:**
```bash
./mvnw spring-boot:run
```

**Start frontend (development):**
```bash
cd hermes-frontend
npm install
npm start
```

The frontend dev server proxies `/api` to `localhost:8080`.

**Run backend with Testcontainers (no Docker Compose needed):**
```bash
./mvnw spring-boot:test-run
```

### Run tests

```bash
cd hermes-backend
./mvnw test
```

Spring Modulith's module structure verification is part of the test suite — module dependency violations fail the build.

## Key Design Decisions

**Modulith over microservices** — a single deployable unit reduces operational complexity while Modulith enforces module boundaries at compile/test time, preventing architectural drift.

**ActiveMQ Artemis for price history fetching** — when a new listing is created, a `FetchPriceHistoryCommand` is sent to a JMS queue. Five concurrent consumers process commands at a capped rate of 10 fetches per minute, preventing HikariCP connection pool exhaustion that occurred with the previous event-driven fan-out approach.

**OpenAPI contract-first** — `api.yaml` is the single source of truth. The Maven build generates the `ListingsApi` interface; the controller implements it. Frontend types in `api.types.ts` are kept manually in sync.

**JPA Specification for search** — listing search uses Spring Data's `JpaSpecificationExecutor` with dynamically composed `LIKE` predicates, keeping the query logic out of the controller and testable in isolation.

**Async scraping** — scraping sessions are queued and processed by a background worker. The API returns immediately after enqueuing, keeping the application responsive.

**Local LLM via Ollama** — AI-powered listing summaries run entirely on local infrastructure, avoiding external API costs and keeping listing data private.
