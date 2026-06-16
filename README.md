# Hermes House Tracker

Hermes is a real estate tracking application that scrapes property listings from [Funda.nl](https://www.funda.nl) and persists them for analysis and tracking over time. Users can initiate scraping sessions, search and filter tracked listings, view price history charts, and re-scrape individual listings on demand.

## Architecture

The backend is a **modular monolith** built with [Spring Modulith](https://spring.io/projects/spring-modulith). Modules communicate via Spring's application event system — no direct cross-module bean dependencies. This enforces clear boundaries while keeping deployment simple.

### Modules

| Module | Package | Responsibility |
|---|---|---|
| `scraping` | `com.kropholler.dev.hermes.scraping` | Scraping session management, queue processing, delegates fetching to funda-proxy |
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

### Funda Proxy

A small Python service that sits between the backend and Funda.nl, abstracting the scraping logic away from the JVM.

| Layer | Technology |
|---|---|
| Framework | FastAPI |
| Language | Python 3.12+ |
| Observability | OpenTelemetry (FastAPI instrumentation) |

**Endpoints:**
- `GET /search?location=...` — search listings by location with optional price/area filters
- `GET /listings/{id}` — fetch a single listing by Funda ID
- `GET /listings/{id}/price-history` — fetch the full price history for a listing

The backend configures the proxy URL via `funda.proxy.url` (defaults to `http://localhost:8001` in dev).

### Infrastructure (local dev via Docker Compose)

| Service | Purpose |
|---|---|
| PostgreSQL | Primary database |
| ActiveMQ Artemis | JMS message broker for async price history fetching |
| Ollama | Local LLM server (optional — see [Ollama setup](#ollama-setup)) |
| Funda Proxy | Python/FastAPI sidecar that scrapes Funda.nl |
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
├── funda-proxy/                    Python/FastAPI proxy for Funda.nl
│   ├── main.py                     FastAPI app (search, listing, price-history endpoints)
│   ├── client.py                   Funda.nl HTTP client with lifespan management
│   ├── models.py                   Pydantic response models
│   └── tests/
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
- [Ollama](https://ollama.com) (recommended on Windows — see [Ollama setup](#ollama-setup))

### Ollama setup

The AI module uses [Ollama](https://ollama.com) to run `llama3.2:3b` locally for listing summaries.

#### Option 1: Local Ollama (recommended on Windows, especially AMD GPU)

GPU acceleration for Ollama does not work in a Docker Compose container on Windows, including with AMD GPUs (tested on AMD Radeon RX 7900 XT). For GPU-accelerated inference on Windows, install Ollama natively instead.

1. [Download and install Ollama](https://ollama.com/download)
2. Pull the model:
   ```bash
   ollama pull llama3.2:3b
   ```
3. Ollama starts automatically on `http://localhost:11434`. The backend connects to it via `host.docker.internal:11434` from inside Docker.

No extra flags are needed — local Ollama is the default when you run `docker compose up -d`.

#### Option 2: Dockerized Ollama (Linux / CPU-only)

On Linux or when GPU acceleration is not required, you can run Ollama inside Docker Compose using the `ollama` profile. The container will pull the model on first start.

```bash
cd hermes-backend
SPRING_AI_OLLAMA_BASE_URL=http://ollama:11434 docker compose --profile ollama up -d
```

### Run locally

**Start infrastructure (with local Ollama — default):**
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
