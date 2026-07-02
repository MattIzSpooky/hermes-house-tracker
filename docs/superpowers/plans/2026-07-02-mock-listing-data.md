# Mock Listing Data Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a developer populate the app with realistic fake listing data through the real scraping pipeline, without any request reaching Funda.

**Architecture:** `funda-proxy` gains an env-var-gated mock mode that swaps the real `pyfunda` client for a `MockFunda` serving a fixed set of fixture `Listing` objects (built from pyfunda's own dataclasses). The scraping admin page in `hermes-frontend` gets a button that triggers a normal scraping session against it, reusing the existing search → queue → persist → geocode pipeline unchanged.

**Tech Stack:** Python/FastAPI (`funda-proxy`), Angular (`hermes-frontend`); no changes to the Java backend (`hermes-backend`).

## Global Constraints

- Mock mode must not change behavior when `FUNDA_MOCK_MODE` is unset/`false` — existing tests in `funda-proxy/tests/test_endpoints.py` and `funda-proxy/tests/test_client.py` must keep passing unmodified.
- Fixture listings must use real Dutch street addresses (so the existing Nominatim-based geocoding pipeline can resolve real coordinates) but fabricated prices/areas/etc.
- No parallel response schema for mock data — `MockFunda` must return real `funda.listing.Listing` / `funda.listing.PriceHistory` instances so `models.py`'s `ListingResponse.from_listing()` / `PriceChangeResponse.from_change()` work unchanged.
- No new hermes-backend (Java) endpoint — the frontend must reuse the existing `POST /api/scraping-sessions` endpoint.

## Progress

| Task | Status | Commit |
|------|--------|--------|
| Task 1: `MockFunda` client and fixture data | ⬜ Pending | — |
| Task 2: Wire mock mode into `client.py` and `docker-compose.yml` | ⬜ Pending | — |
| Task 3: "Seed mock listings" button on the scraping admin page | ⬜ Pending | — |

**Resuming after an interruption:** re-read this table. Pick up at the first `⬜ Pending` row — every task before it is done and its commit is on the branch. Each task's own section below is self-contained (files, code, exact commands), so you don't need prior tasks' sections in context to execute it, only their *outputs* (types/signatures), which are listed under each task's "Interfaces" block.

---

### Task 1: `MockFunda` client and fixture data

**Files:**
- Create: `funda-proxy/mock_fixtures.py`
- Create: `funda-proxy/mock_client.py`
- Test: `funda-proxy/tests/test_mock_client.py`

**Interfaces:**
- Consumes: `funda.listing.{Address,Areas,Listing,Price,PriceChange,PriceHistory,PropertyDetails,Rooms,Urls}` (pyfunda, already a dependency), `funda.exceptions.ListingNotFound` (already a dependency).
- Produces: `mock_fixtures.MOCK_LISTINGS: list[Listing]` (5 fixtures, `global_id` values `90000001`-`90000005`, `tiny_id` left `None` so `Listing.id` returns `str(global_id)`), `mock_fixtures.MOCK_PRICE_HISTORIES: dict[str, PriceHistory]` (keyed by `str(global_id)`). `mock_client.MockFunda` class with `search(location=None, *, min_price=None, max_price=None, min_area=None, max_area=None, page=0, **_ignored) -> list[Listing]`, `listing(listing_id: int | str) -> Listing`, `price_history(listing: Listing | str) -> PriceHistory`, `close() -> None`. These are consumed by Task 2's `client.py`.

- [ ] **Step 1: Write the failing tests for `MockFunda`**

Create `funda-proxy/tests/test_mock_client.py`:

```python
import pytest
from funda.exceptions import ListingNotFound

from mock_client import MockFunda


@pytest.fixture
def mock_funda():
    return MockFunda()


def test_search_returns_all_fixtures_by_default(mock_funda):
    results = mock_funda.search("anything")
    assert len(results) == 5
    assert {l.global_id for l in results} == {
        90000001, 90000002, 90000003, 90000004, 90000005,
    }


def test_search_page_beyond_first_returns_empty(mock_funda):
    assert mock_funda.search("anything", page=1) == []


def test_search_filters_by_price(mock_funda):
    results = mock_funda.search("anything", min_price=600000, max_price=800000)
    assert {l.global_id for l in results} == {90000001, 90000003}


def test_search_filters_by_area(mock_funda):
    results = mock_funda.search("anything", min_area=100)
    assert {l.global_id for l in results} == {90000003, 90000005}


def test_listing_returns_matching_fixture(mock_funda):
    listing = mock_funda.listing("90000002")
    assert listing.global_id == 90000002
    assert listing.address.city == "Utrecht"


def test_listing_accepts_int_id(mock_funda):
    listing = mock_funda.listing(90000002)
    assert listing.global_id == 90000002


def test_listing_unknown_id_raises_not_found(mock_funda):
    with pytest.raises(ListingNotFound):
        mock_funda.listing("00000000")


def test_price_history_returns_changes_for_known_listing(mock_funda):
    history = mock_funda.price_history("90000001")
    assert len(history.changes) == 2
    assert history.changes[0].price == 795000


def test_price_history_returns_empty_for_listing_without_changes(mock_funda):
    history = mock_funda.price_history("90000002")
    assert history.changes == ()


def test_price_history_unknown_id_raises_not_found(mock_funda):
    with pytest.raises(ListingNotFound):
        mock_funda.price_history("00000000")
```

- [ ] **Step 2: Run the tests to verify they fail on import**

Run (from `funda-proxy/`): `pytest tests/test_mock_client.py -v`
Expected: FAIL/ERROR — `ModuleNotFoundError: No module named 'mock_client'`

- [ ] **Step 3: Create the fixture data**

Create `funda-proxy/mock_fixtures.py`:

```python
"""Fixed set of realistic Dutch listings served by MockFunda.

Addresses are real (so the geocoding pipeline can resolve real
coordinates); everything else is fabricated. Global IDs live in the
90000000 range, well outside any real Funda listing ID, so they can't
collide with real data.
"""

from funda.listing import (
    Address,
    Areas,
    Listing,
    Price,
    PriceChange,
    PriceHistory,
    PropertyDetails,
    Rooms,
    Urls,
)


def _listing(
    global_id: int,
    *,
    street_name: str,
    house_number: str,
    postcode: str,
    city: str,
    province: str,
    price: int,
    living: int,
    plot: int | None,
    rooms: int,
    bedrooms: int,
    energy_label: str,
    status: str,
    description: str,
) -> Listing:
    slug = street_name.lower().replace(" ", "-")
    return Listing(
        global_id=global_id,
        offering_type="koop",
        address=Address(
            street_name=street_name,
            house_number=house_number,
            house_number_suffix=None,
            postcode=postcode,
            city=city,
            province=province,
        ),
        price=Price(amount=price, offering_type="koop"),
        areas=Areas(living=living, plot=plot),
        rooms=Rooms(total=rooms, bedrooms=bedrooms),
        property_details=PropertyDetails(energy_label=energy_label, status=status),
        urls=Urls(
            full=f"https://www.funda.nl/koop/{city.lower()}/huis-{global_id}-{slug}/"
        ),
        description=description,
        publication_date="2024-01-15",
    )


MOCK_LISTINGS: list[Listing] = [
    _listing(
        90000001,
        street_name="Prinsengracht",
        house_number="263",
        postcode="1016GV",
        city="Amsterdam",
        province="Noord-Holland",
        price=750000,
        living=95,
        plot=None,
        rooms=4,
        bedrooms=2,
        energy_label="B",
        status="beschikbaar",
        description="Karakteristiek grachtenpand appartement met balkon.",
    ),
    _listing(
        90000002,
        street_name="Oudegracht",
        house_number="158",
        postcode="3511AZ",
        city="Utrecht",
        province="Utrecht",
        price=495000,
        living=78,
        plot=None,
        rooms=3,
        bedrooms=2,
        energy_label="C",
        status="beschikbaar",
        description="Sfeervolle woning aan de Oudegracht.",
    ),
    _listing(
        90000003,
        street_name="Coolsingel",
        house_number="40",
        postcode="3011AD",
        city="Rotterdam",
        province="Zuid-Holland",
        price=625000,
        living=110,
        plot=60,
        rooms=5,
        bedrooms=3,
        energy_label="A",
        status="beschikbaar",
        description="Modern herenhuis in het centrum.",
    ),
    _listing(
        90000004,
        street_name="Stratumseind",
        house_number="10",
        postcode="5611ET",
        city="Eindhoven",
        province="Noord-Brabant",
        price=385000,
        living=65,
        plot=None,
        rooms=3,
        bedrooms=1,
        energy_label="D",
        status="beschikbaar",
        description="Compact appartement dichtbij het centrum.",
    ),
    _listing(
        90000005,
        street_name="Grote Markt",
        house_number="1",
        postcode="2011RD",
        city="Haarlem",
        province="Noord-Holland",
        price=899000,
        living=150,
        plot=120,
        rooms=6,
        bedrooms=4,
        energy_label="A",
        status="onder bod",
        description="Ruime eengezinswoning aan de Grote Markt.",
    ),
]


MOCK_PRICE_HISTORIES: dict[str, PriceHistory] = {
    "90000001": PriceHistory(
        changes=(
            PriceChange(
                price=795000,
                human_price="€ 795.000 k.k.",
                source="funda",
                status="asking_price",
                date="15 januari 2024",
                timestamp="2024-01-15T00:00:00+00:00",
            ),
            PriceChange(
                price=750000,
                human_price="€ 750.000 k.k.",
                source="funda",
                status="asking_price",
                date="1 maart 2024",
                timestamp="2024-03-01T00:00:00+00:00",
            ),
        )
    ),
    "90000003": PriceHistory(
        changes=(
            PriceChange(
                price=625000,
                human_price="€ 625.000 k.k.",
                source="funda",
                status="asking_price",
                date="10 februari 2024",
                timestamp="2024-02-10T00:00:00+00:00",
            ),
        )
    ),
}
```

- [ ] **Step 4: Create `MockFunda`**

Create `funda-proxy/mock_client.py`:

```python
"""Stand-in for funda.Funda that serves fixture data instead of the real API."""

from funda.exceptions import ListingNotFound
from funda.listing import Listing, PriceHistory

from mock_fixtures import MOCK_LISTINGS, MOCK_PRICE_HISTORIES


class MockFunda:
    """Implements the subset of Funda's interface that main.py calls."""

    def close(self) -> None:
        pass

    def search(
        self,
        location=None,
        *,
        min_price=None,
        max_price=None,
        min_area=None,
        max_area=None,
        page=0,
        **_ignored,
    ) -> list[Listing]:
        if page > 0:
            return []
        results = list(MOCK_LISTINGS)
        if min_price is not None:
            results = [
                l for l in results
                if l.price.amount is not None and l.price.amount >= min_price
            ]
        if max_price is not None:
            results = [
                l for l in results
                if l.price.amount is not None and l.price.amount <= max_price
            ]
        if min_area is not None:
            results = [
                l for l in results
                if l.areas.living is not None and l.areas.living >= min_area
            ]
        if max_area is not None:
            results = [
                l for l in results
                if l.areas.living is not None and l.areas.living <= max_area
            ]
        return results

    def listing(self, listing_id: int | str) -> Listing:
        match = _find(str(listing_id))
        if match is None:
            raise ListingNotFound(f"Mock listing {listing_id} not found")
        return match

    def price_history(self, listing: "Listing | str") -> PriceHistory:
        listing_id = listing.id if isinstance(listing, Listing) else str(listing)
        if _find(listing_id) is None:
            raise ListingNotFound(f"Mock listing {listing_id} not found")
        return MOCK_PRICE_HISTORIES.get(listing_id, PriceHistory(changes=()))


def _find(listing_id: str) -> Listing | None:
    for listing in MOCK_LISTINGS:
        if listing.id == listing_id:
            return listing
    return None
```

- [ ] **Step 5: Run the tests to verify they pass**

Run (from `funda-proxy/`): `pytest tests/test_mock_client.py -v`
Expected: PASS — 10 passed

- [ ] **Step 6: Commit**

```bash
git add funda-proxy/mock_fixtures.py funda-proxy/mock_client.py funda-proxy/tests/test_mock_client.py
git commit -m "feat(funda-proxy): add MockFunda client with fixture listings"
```

---

### Task 2: Wire mock mode into `client.py` and `docker-compose.yml`

**Files:**
- Modify: `funda-proxy/client.py`
- Modify: `hermes-backend/docker-compose.yml`
- Test: `funda-proxy/tests/test_mock_mode.py`

**Interfaces:**
- Consumes: `mock_client.MockFunda` (Task 1).
- Produces: `client.get_client() -> Funda | MockFunda` (unchanged return-type contract for `main.py`, now polymorphic). Env var `FUNDA_MOCK_MODE` (`"true"`/`"false"`, case-insensitive, default `"false"`).

- [ ] **Step 1: Write the failing integration test**

Create `funda-proxy/tests/test_mock_mode.py`:

```python
import pytest
from fastapi.testclient import TestClient


@pytest.fixture
def mock_mode_api(monkeypatch):
    monkeypatch.setenv("FUNDA_MOCK_MODE", "true")
    from main import app
    with TestClient(app) as c:
        yield c


def test_search_returns_fixture_listings(mock_mode_api):
    resp = mock_mode_api.get("/search?location=anything")
    assert resp.status_code == 200
    data = resp.json()
    assert len(data) == 5
    assert all(item["city"] for item in data)


def test_get_listing_returns_fixture(mock_mode_api):
    resp = mock_mode_api.get("/listings/90000001")
    assert resp.status_code == 200
    assert resp.json()["global_id"] == 90000001


def test_get_listing_unknown_returns_404(mock_mode_api):
    resp = mock_mode_api.get("/listings/00000000")
    assert resp.status_code == 404


def test_get_price_history_for_fixture_with_changes(mock_mode_api):
    resp = mock_mode_api.get("/listings/90000001/price-history")
    assert resp.status_code == 200
    assert len(resp.json()) == 2


def test_get_price_history_for_fixture_without_changes(mock_mode_api):
    resp = mock_mode_api.get("/listings/90000002/price-history")
    assert resp.status_code == 200
    assert resp.json() == []
```

- [ ] **Step 2: Run the tests to verify they fail**

Run (from `funda-proxy/`): `pytest tests/test_mock_mode.py -v`
Expected: FAIL — real `Funda()` is constructed (mock mode not wired up yet) and the requests either hang, error, or hit the real network, since `FUNDA_MOCK_MODE` is ignored by `client.py`.

- [ ] **Step 3: Wire mock mode into `client.py`**

Replace the full contents of `funda-proxy/client.py`:

```python
import logging
import os
from contextlib import asynccontextmanager

from funda import Funda
from mock_client import MockFunda
from telemetry import setup_logging

logger = logging.getLogger(__name__)

_client: "Funda | MockFunda | None" = None


def get_client() -> "Funda | MockFunda":
    if _client is None:
        raise RuntimeError("Funda client not initialized")
    return _client


def _mock_mode_enabled() -> bool:
    return os.getenv("FUNDA_MOCK_MODE", "false").strip().lower() == "true"


@asynccontextmanager
async def lifespan(app):
    global _client
    # Must run here — uvicorn overwrites root logger handlers during startup,
    # so we re-apply our JSON + OTel bridge after it finishes.
    setup_logging()
    if _mock_mode_enabled():
        logger.warning(
            "FUNDA_MOCK_MODE is enabled - serving fixture listings, not calling Funda"
        )
        _client = MockFunda()
    else:
        _client = Funda()
    yield
    _client.close()
    _client = None
```

- [ ] **Step 4: Run the new tests to verify they pass**

Run (from `funda-proxy/`): `pytest tests/test_mock_mode.py -v`
Expected: PASS — 5 passed

- [ ] **Step 5: Run the full funda-proxy test suite to confirm no regressions**

Run (from `funda-proxy/`): `pytest -v`
Expected: PASS — all tests pass, including the pre-existing `tests/test_client.py` and `tests/test_endpoints.py` (which don't set `FUNDA_MOCK_MODE`, so they exercise the real-`Funda` branch as before).

- [ ] **Step 6: Add the opt-in env var to docker-compose**

In `hermes-backend/docker-compose.yml`, add `FUNDA_MOCK_MODE` to the `funda-proxy` service's `environment` block:

```yaml
  funda-proxy:
    build: ../funda-proxy
    ports:
      - "8001:8000"
    environment:
      OTEL_SERVICE_NAME: funda-proxy
      OTEL_EXPORTER_OTLP_ENDPOINT: http://grafana-lgtm:4318
      FUNDA_MOCK_MODE: ${FUNDA_MOCK_MODE:-false}
    depends_on:
      grafana-lgtm:
        condition: service_started
```

- [ ] **Step 7: Commit**

```bash
git add funda-proxy/client.py funda-proxy/tests/test_mock_mode.py hermes-backend/docker-compose.yml
git commit -m "feat(funda-proxy): add FUNDA_MOCK_MODE toggle to serve fixture listings"
```

---

### Task 3: "Seed mock listings" button on the scraping admin page

**Files:**
- Modify: `hermes-frontend/src/app/pages/scraping/scraping-page.component.ts`
- Modify: `hermes-frontend/src/app/pages/scraping/scraping-page.component.html`

**Interfaces:**
- Consumes: `ScrapingService.createSession(req: CreateScrapingSessionRequest): void` (existing, `hermes-frontend/src/app/core/scraping.service.ts:26`), `ScrapingService.loading: Signal<boolean>` (existing), `CreateScrapingSessionRequest { city: string; pageLimit: number; minPrice?: number; maxPrice?: number; minArea?: number; maxArea?: number }` (existing, `hermes-frontend/src/app/core/api.types.ts:27-34`).
- Produces: `ScrapingPageComponent.seedMockData(): void`, used only by this task's template.

This task has no automated test — the existing `scraping-page` component and `scraping.service` have no `.spec.ts` files in this codebase (no established frontend test convention to follow here), so verification is manual per Step 3 below.

- [ ] **Step 1: Add the `seedMockData` method to the component**

In `hermes-frontend/src/app/pages/scraping/scraping-page.component.ts`, add a new method inside the `ScrapingPageComponent` class, after `submit()`:

```typescript
  seedMockData(): void {
    this.svc.createSession({ city: 'Mock data', pageLimit: 1 });
  }
```

- [ ] **Step 2: Add the button to the template**

In `hermes-frontend/src/app/pages/scraping/scraping-page.component.html`, insert a new `<app-section-card>` block right before the existing `Geocoding backfill` card (i.e. right before the line `<app-section-card class="mt-6" extraClass="max-w-lg space-y-3">` that contains `Geocoding backfill`):

```html
<app-section-card class="mt-6" extraClass="max-w-lg space-y-3">
  <h2 class="text-xs font-semibold text-slate-400 uppercase tracking-wider">Mock data</h2>
  <p class="text-xs text-slate-500">
    Seed the app with fixture listings through the normal scraping pipeline, without contacting Funda.
    Only returns fixture data when funda-proxy is running with <code class="font-mono">FUNDA_MOCK_MODE=true</code>;
    otherwise this performs a harmless real search that returns nothing.
  </p>
  <button (click)="seedMockData()" [disabled]="svc.loading() || isPolling()"
    class="rounded-lg bg-slate-800 px-4 py-2.5 text-sm font-semibold text-white
           hover:bg-slate-700 disabled:opacity-50 transition-colors">
    @if (svc.loading()) {
      <app-spinner color="white" label="Starting..." />
    } @else {
      Seed mock listings
    }
  </button>
</app-section-card>
```

- [ ] **Step 3: Manually verify end to end**

1. In the repo root, run: `FUNDA_MOCK_MODE=true docker compose up --build`
2. Open the frontend, navigate to the Scraping admin page.
3. Click "Seed mock listings".
4. Confirm the session card appears and reaches `COMPLETED` status.
5. Navigate to the listings search page and confirm 5 new listings appear (Amsterdam, Utrecht, Rotterdam, Eindhoven, Haarlem addresses), each with resolved map coordinates after the geocoding pipeline runs.
6. Open the Amsterdam (Prinsengracht) and Rotterdam (Coolsingel) listing detail pages and confirm each shows a 2-entry / 1-entry price history respectively.
7. Stop the stack (`docker compose down`), restart without `FUNDA_MOCK_MODE` set, click "Seed mock listings" again, and confirm it creates a session that completes with zero listings found (proving the button is inert against the real Funda API when mock mode is off).

- [ ] **Step 4: Commit**

```bash
git add hermes-frontend/src/app/pages/scraping/scraping-page.component.ts hermes-frontend/src/app/pages/scraping/scraping-page.component.html
git commit -m "feat(frontend): add mock listing seed button to scraping admin page"
```
