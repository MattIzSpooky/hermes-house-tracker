# Mock listing data: CSV-backed fixtures, city filtering, real pagination

## Context

This supersedes parts of `2026-07-02-mock-listing-data-design.md`, which shipped
a minimal mock mode (5 hardcoded fixture listings in Python, no city
filtering, and a dedicated "Seed mock listings" admin button that ignored
location). That was enough to prove the pipeline works end to end. This spec
expands it into something usable for real manual testing: 500 listings,
loaded from CSV, filterable by city like the real Funda API, with real
pagination — so the existing scraping admin form (not a special button) is
the way to seed and browse mock data.

## Goal

Let a developer search any of a curated set of mock Dutch cities through the
normal scraping UI and get back a realistic, paginated, city-scoped result
set — indistinguishable in shape from a real Funda search — without any
request reaching Funda.

## Design

### 1. Fixture data as CSV

Two new files, checked into git as static data:

- `funda-proxy/mock_data/listings.csv` — one row per listing. Columns:
  `global_id, street_name, house_number, house_number_suffix, postcode,
  city, province, price, living_area, plot_area, rooms, bedrooms,
  energy_label, status, description, offering_type, publication_date`.
- `funda-proxy/mock_data/price_histories.csv` — one row per price-history
  entry, 1-3 rows per `global_id`. Columns: `global_id, price, human_price,
  source, status, date, timestamp`.

500 listings across 10 real Dutch cities (Amsterdam, Utrecht, Rotterdam,
Eindhoven, Haarlem, Weert, Groningen, Maastricht, Tilburg, Nijmegen), 5 real
streets per city, 10 listings per street (varying house number) — 50
listings/city × 10 cities = 500. `global_id` values are sequential,
90000001-90000500, assigned in city → street → house-number order for
reproducibility. `house_number_suffix` is empty for all rows (kept simple;
not load-bearing for any test).

Real street names and approximate real postcodes are used per city (as in
the original 5 fixtures) so the existing Nominatim-based geocoding pipeline
can still resolve plausible coordinates.

### 2. One-off generator script

`funda-proxy/scripts/generate_mock_data.py` — not part of the running
service, not imported by any runtime code. A developer re-runs it by hand
only if the curated city/street table or generation logic changes; its
output (the two CSVs) is what's checked in and what the service actually
reads.

- A hardcoded table of the 10 cities, each with 5 `(street_name, postcode,
  province)` tuples.
- `random.Random(42)` (fixed seed) drives all synthetic values — price,
  living/plot area, rooms, bedrooms, energy label, status, price-history
  count and amounts/dates, description template choice — so re-running the
  script with unchanged inputs reproduces byte-identical CSVs.
- Price ranges are loosely scaled per city (e.g. Amsterdam/Utrecht higher
  than Weert/Nijmegen) for plausibility; not a hard requirement, just avoids
  obviously-wrong data during manual review.
- Descriptions are drawn from a small pool of Dutch template strings with
  the street name interpolated in, not 500 unique texts.
- Each listing gets 1-3 price-history rows (random, seeded), each with a
  plausible sequential price and a Dutch date string in the same format the
  original 5 fixtures used (`"15 januari 2024"`), so `dateparser` in
  `models.py` keeps working unchanged.

### 3. `mock_fixtures.py`: load from CSV

Rewritten to read the two CSVs with the stdlib `csv` module at import time
(no new dependency) and build the same `MOCK_LISTINGS: list[Listing]` /
`MOCK_PRICE_HISTORIES: dict[str, PriceHistory]` shapes as before, using the
same `funda.listing` dataclasses. `mock_client.py`'s data-access contract
doesn't change for this part.

### 4. `mock_client.py`: real city filtering + real pagination

- `search()` gains city filtering: normalize `location` and each fixture's
  `address.city` (casefold, strip) and match via case-insensitive substring
  containment (either direction), so `"weert"` matches `"Weert"`. If
  `location` is falsy, all cities match (keeps the endpoint usable without a
  location for ad hoc testing, mirroring today's permissive behavior).
- `search()` gains real pagination: `PAGE_SIZE = 15` (matching pyfunda's own
  constant), and pagination is **strictly 0-based**, exactly like the real
  API: `page=0` returns the first 15 matches, `page=1` the next 15, etc.
  Price/area filters continue to apply before pagination, as today.
- This intentionally reproduces an existing, unrelated production quirk:
  `ScrapingWorker` (Java, untouched) loops pages 1-based
  (`for (page = 1; page <= limit; page++)`), so a real (or now mock) scrape
  always skips the true first 15 results of a city. This is a known
  pre-existing latent bug in production scraping, out of scope to fix here
  — mock mode now faithfully reproduces it instead of masking it, which is
  a deliberate choice: "re-create funda API behavior" takes priority over
  making mock-seeded data maximally convenient.
- `listing()` / `price_history()` behavior (lookup by `global_id`,
  `ListingNotFound` for unknown IDs) is unchanged.

### 5. hermes-backend: configurable page-limit cap

Today, `pageLimit` is capped at 5 in two redundant places: a static OpenAPI
`maximum: 5` (enforced via generated Jakarta Bean Validation before the
request reaches any service code) and a hardcoded
`ScrapingQueueService.MAX_PAGE_LIMIT = 5` constant used to clamp (not
reject) the value.

- `hermes-backend/src/main/resources/openapi/scraping.yaml`: drop
  `maximum: 5` from `pageLimit` (keep `minimum: 1`), so arbitrarily large
  values pass bean validation and reach the service layer.
- `ScrapingQueueService`: replace the hardcoded constant with
  `@Value("${scraping.page-limit.max:5}") private int maxPageLimit = 5;`
  (the `= 5` field initializer is a deliberate fallback so the existing
  Mockito `@InjectMocks`-based unit tests, which run without a Spring
  context and can't resolve `@Value`, keep working unchanged at the current
  default). `enqueueSearch` keeps clamping (not rejecting) requests above
  the configured max, same as today.
- Default behavior is unchanged (max 5) unless the `scraping.page-limit.max`
  property (or a `SCRAPING_PAGE_LIMIT_MAX` env var, via Spring's relaxed
  binding) is explicitly set — e.g. in `docker-compose.yml` for local/mock
  use, left unset by default in this change (10 cities × 50 listings ÷ 15
  per page = 4 pages needed to cover a full mock city, already within the
  unchanged default of 5).

### 6. hermes-frontend: drop the dedicated seed button, add a hint

- Remove the "Seed mock listings" card/button and its `seedMockData()`
  method added by the previous iteration of this feature — with real city
  filtering, the existing "Start scraping" form already does the job: type
  a mock city name, submit, get real (mock) results back.
- Add a short static hint under the city field naming the 10 available mock
  cities, so a developer knows what to type when `FUNDA_MOCK_MODE=true`:
  "In mock mode, try: Amsterdam, Utrecht, Rotterdam, Eindhoven, Haarlem,
  Weert, Groningen, Maastricht, Tilburg, Nijmegen." Always visible (harmless
  when mock mode is off — it just describes what mock mode does).

## Testing

- `funda-proxy/tests/test_mock_client.py` — rewritten/extended: city
  filtering (matching city returns only that city's listings; non-matching
  city returns empty; no location returns all); pagination (`page=0`
  returns first 15, `page=1` the next 15 — distinct from page 0 — last
  partial page for a 50-listing city, page beyond the last returns empty);
  existing price/area filter and `listing()`/`price_history()` tests
  updated for the new dataset size (500 global_ids) but keep the same
  assertions in spirit.
- `funda-proxy/tests/test_mock_mode.py` — updated to reflect the larger
  dataset and city-scoped search (e.g. `/search?location=weert` returns
  only Weert listings, paginated).
- `hermes-backend`: `ScrapingQueueServiceTest` gains a case asserting the
  configured `maxPageLimit` (default 5, or a value set via
  `ReflectionTestUtils.setField`) is what requests get clamped to, so the
  configurability is actually exercised, not just typechecked.
- `hermes-frontend`: no automated test (no existing convention for this
  component, as established in the previous spec).
- Manual verification (still owed from the previous spec, expanded): with
  `FUNDA_MOCK_MODE=true`, search "Weert" in the admin form and confirm only
  Weert addresses come back, across multiple pages if `pageLimit > 1`.

## Out of scope

- Fixing the pre-existing 1-based `ScrapingWorker` pagination quirk in
  production Java code — mock mode reproduces it faithfully rather than
  fixing it.
- Raising the docker-compose default `SCRAPING_PAGE_LIMIT_MAX` — the
  property is introduced but left at its current default (5) in this
  change; an operator can override it later if needed.
- `house_number_suffix` variety in generated fixtures (left empty for all
  500 rows).
