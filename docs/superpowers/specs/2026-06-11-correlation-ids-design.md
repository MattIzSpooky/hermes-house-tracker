# Correlation IDs Design

**Date:** 2026-06-11  
**Status:** Approved

## Overview

Add `X-Correlation-ID` support across hermes-backend (Spring Boot) and funda-proxy (FastAPI). Each HTTP request gets a stable correlation ID — accepted from callers or generated server-side — that flows through logs, async threads, downstream service calls, and error responses.

OTel distributed tracing already provides `trace_id`/`span_id` in logs. Correlation IDs complement this by giving callers a handle they can supply themselves and reference in error messages.

## Components

### 1. `CorrelationIdFilter` (hermes-backend)

`OncePerRequestFilter` placed at the top of the filter chain.

- Reads `X-Correlation-ID` from the incoming request header.
- If absent, generates `UUID.randomUUID().toString()`.
- Stores it under `MDC["correlationId"]`.
- Adds `X-Correlation-ID` to the response header.
- Clears `MDC["correlationId"]` in a `finally` block after the filter chain completes.

Logback already captures all MDC keys via `captureMdcAttributes>*` (OTLP appender) and the `LogstashEncoder` (console), so `correlationId` appears in every log line for the request at no extra config.

### 2. `MdcTaskDecorator` (hermes-backend)

`TaskDecorator` that propagates MDC across `@Async` thread boundaries.

- At submission time (request thread): captures `MDC.getCopyOfContextMap()`.
- In the decorated `Runnable`: restores the snapshot via `MDC.setContextMap()` before execution; clears MDC in a `finally` block after.

### 3. `AsyncConfig` update (hermes-backend)

Replace the auto-configured async executor with an explicit `ThreadPoolTaskExecutor` `@Bean("taskExecutor")`:

- Applies `MdcTaskDecorator`.
- Mirrors existing `spring.task.execution.*` property values (core size 4, max 8, prefix `hermes-async-`).

`@Async` resolves the default executor by name `taskExecutor`, so no call-site changes are needed.

### 4. `FundaProxyClient` interceptor (hermes-backend)

A `RestClient` request interceptor added once in the `FundaProxyClient` constructor:

- Reads `MDC.get("correlationId")`.
- If non-null, sets the `X-Correlation-ID` request header on every outbound call to funda-proxy.

No changes to individual call sites.

### 5. Correlation ID middleware (funda-proxy)

A new FastAPI middleware (Starlette `BaseHTTPMiddleware`):

- Reads `X-Correlation-ID` from the incoming request headers.
- Stores the value in a `contextvars.ContextVar[str | None]` (e.g., `_correlation_id_var`).
- Resets the context var after the response is dispatched.

### 6. Log enrichment (funda-proxy)

`_OtelJsonFormatter.add_fields()` in `telemetry.py` is extended to read `_correlation_id_var.get(None)` and include it as `correlation_id` in every JSON log record (empty string when absent, consistent with `trace_id`/`span_id` behaviour).

### 7. `GlobalExceptionHandler` update (hermes-backend)

Both `@ExceptionHandler` methods read `MDC.get("correlationId")` and set it on the `ErrorResponse` body as a `correlationId` field.

### 8. `api.yaml` update

`ErrorResponse` schema gains an optional `correlationId: string` property so the generated model includes the field.

## Data Flow

```
Client
  │  X-Correlation-ID: abc (or absent)
  ▼
CorrelationIdFilter          → MDC["correlationId"] = abc (or generated UUID)
  │                          → Response header X-Correlation-ID: abc
  ▼
Controller / Service (sync)  → logs include correlationId=abc
  │
  ▼ @Async (ScrapingWorker)
MdcTaskDecorator             → snapshot restored on worker thread
  │
  ▼
FundaProxyClient             → X-Correlation-ID: abc forwarded to funda-proxy
  │
  ▼
funda-proxy middleware       → _correlation_id_var = abc
  │
  ▼
funda-proxy log lines        → JSON field correlation_id=abc
```

## Error Response

```json
{
  "error": "NOT_FOUND",
  "detail": "Scraping session ... not found",
  "correlationId": "abc-123-..."
}
```

## Out of Scope

- Propagating correlation IDs through Spring Modulith domain events (event payloads are not modified).
- Nightly rescrape scheduler — scheduler-triggered jobs have no inbound HTTP request; they run without a correlation ID. OTel trace context covers those flows.
- Frontend changes.

## Testing

- Unit test `CorrelationIdFilter`: verify MDC is populated, response header is set, MDC is cleared after the request.
- Unit test `MdcTaskDecorator`: verify MDC snapshot is restored in the decorated runnable and cleared afterwards.
- Integration test (existing `ScrapingQueueServiceTest` or new): verify that a correlation ID set before `@Async` dispatch appears in the worker thread's MDC.
- funda-proxy: unit test the middleware using FastAPI `TestClient` — verify `correlation_id` appears in log output and that a missing header results in an empty string.
