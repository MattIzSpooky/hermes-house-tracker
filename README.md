# Hermes House Tracker

Hermes is a real estate tracking application that scrapes property listings from [Funda.nl](https://www.funda.nl) and persists them for analysis and tracking over time. Users can initiate scraping sessions and later re-scrape individual listings to generate reports on price fluctuations, listing duration, and other trends.

## Architecture

The backend is a **modular monolith** built with [Spring Modulith](https://spring.io/projects/spring-modulith). Modules communicate via Spring's application event system — no direct cross-module bean dependencies. This enforces clear boundaries while keeping deployment simple.

### Modules

| Module | Package | Responsibility |
|---|---|---|
| `scraping` | `com.kropholler.dev.hermes.scraping` | Scraping session management, queue processing, Funda.nl HTML parsing via JSoup |
| `listing` | `com.kropholler.dev.hermes.listing` | Real estate listing data model, persistence, deduplication |
| `report` | `com.kropholler.dev.hermes.report` | Report generation based on listing history (price trends, listing age, etc.) |
| `ai` | `com.kropholler.dev.hermes.ai` | Ollama-backed LLM integration for listing summaries and analysis |
| `api` | `com.kropholler.dev.hermes.api` | REST controllers and DTOs exposed to the frontend |

### Scraping Flow

```
User submits scrape request
        │
        ▼
  Scraping queue (Spring Modulith JPA-backed events)
        │
        ▼
  Background worker processes queue item asynchronously
        │
        ▼
  JSoup parses Funda.nl HTML → extracts listing data
        │
        ▼
  ScrapingCompleted event published
        │
        ▼
  Listing module receives event → persists/updates listing records
```

Scraping is fully asynchronous — it never blocks API responses to other users. Queue items are persisted so in-progress sessions survive application restarts.

### Re-scraping & Reports

Individual listings can be re-scraped at any time. Each scrape creates a new snapshot in the database. The report module aggregates snapshots to produce:

- Days listed on Funda
- Days tracked in Hermes
- Price history and fluctuations
- Status changes (available → sold/withdrawn)

## Technology Stack

### Backend

| Layer | Technology |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.0.x |
| Architecture | Spring Modulith 2.0.x |
| Persistence | Spring Data JPA + PostgreSQL |
| Web scraping | Spring AI JSoup Document Reader |
| AI/LLM | Spring AI + Ollama (local) |
| HTTP client | Spring RestClient |
| Observability | Micrometer + OpenTelemetry + Prometheus + Grafana LGTM |
| Build | Maven |
| Testing | JUnit 5 + Testcontainers |

### Frontend

| Layer | Technology |
|---|---|
| Framework | Angular 17 (standalone components) |
| Rendering | Angular SSR + Express |
| Language | TypeScript 5.4 |
| Styling | SCSS |

### Infrastructure (local dev via Docker Compose)

| Service | Purpose |
|---|---|
| PostgreSQL | Primary database |
| Ollama | Local LLM server |
| Grafana LGTM | Logs, traces, metrics stack |

## Project Structure

```
hermes-house-tracker/
├── hermes-backend/                 Spring Boot application
│   ├── src/main/java/com/kropholler/dev/hermes/
│   │   ├── HermesBackendApplication.java
│   │   ├── scraping/               Scraping module
│   │   ├── listing/                Listing module
│   │   ├── report/                 Report module
│   │   ├── ai/                     AI module
│   │   └── api/                    API module
│   ├── src/main/resources/
│   │   └── application.properties
│   ├── compose.yaml                Local Docker services
│   └── pom.xml
└── hermes-frontend/                Angular application
    └── src/app/
```

## Getting Started

### Prerequisites

- Java 25+
- Maven 3.9+
- Docker + Docker Compose
- Node.js 18+ (for frontend)

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

**Run backend with Testcontainers (no Docker Compose needed):**
```bash
./mvnw spring-boot:test-run
```

### Run tests

```bash
./mvnw test
```

Spring Modulith's module structure verification is part of the test suite — module dependency violations fail the build.

## Key Design Decisions

**Modulith over microservices** — a single deployable unit reduces operational complexity while Modulith enforces module boundaries at compile/test time, preventing architectural drift.

**JPA-backed event bus** — Spring Modulith persists inter-module events to the database before publishing them. This means a crashed application will re-deliver events on restart, giving at-least-once delivery without a message broker.

**Async scraping** — scraping sessions are placed on a queue and processed by a background worker. The API returns immediately after enqueuing, keeping the application responsive.

**Snapshot-based listing history** — each scrape creates an immutable snapshot rather than updating in place. This preserves the full history needed for trend reports.

**Local LLM via Ollama** — AI-powered features (listing summaries, analysis) run entirely on local infrastructure, avoiding external API costs and keeping listing data private.
