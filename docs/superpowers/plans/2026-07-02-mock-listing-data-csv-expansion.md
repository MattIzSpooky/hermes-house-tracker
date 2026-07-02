# Mock Listing Data CSV Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 5 hardcoded mock listings with 500 CSV-backed fixtures across 10 real Dutch cities, add real city filtering and real pagination to `MockFunda`, make the backend's scraping page-limit cap configurable, and let the existing scraping admin form (not a dedicated button) drive mock-mode testing.

**Architecture:** A one-off Python generator script produces two checked-in CSVs (`listings.csv`, `price_histories.csv`); `mock_fixtures.py` loads them at import time into the same `funda.listing.Listing`/`PriceHistory` shapes as before; `mock_client.py`'s `MockFunda.search()` gains city filtering and page-size-15 pagination that strictly mirrors the real (0-based) pyfunda API. On the Java side, the previously-hardcoded `pageLimit` cap of 5 becomes a Spring property. The frontend's dedicated "Seed mock listings" button is removed since the existing city-search form now works directly against mock cities.

**Tech Stack:** Python/FastAPI (`funda-proxy`), Java/Spring Boot (`hermes-backend`), Angular (`hermes-frontend`).

## Global Constraints

- The generator script must use `random.seed(42)` and produce byte-identical output on every run given unchanged inputs.
- Exactly 500 listings across 10 named real Dutch cities (Amsterdam, Utrecht, Rotterdam, Eindhoven, Haarlem, Weert, Groningen, Maastricht, Tilburg, Nijmegen), 50 listings per city, `global_id` values `90000001`-`90000500` assigned in city → street → house-number order.
- Every listing gets 1-3 price-history rows (all 500, not a subset).
- `MockFunda.search()` pagination is strictly 0-based with `PAGE_SIZE = 15`, matching real pyfunda exactly — it does NOT special-case page 1, so it reproduces the existing Java `ScrapingWorker`'s 1-based-caller quirk rather than working around it.
- `MockFunda.search()` city filtering: case-insensitive substring match (either direction) between the `location` argument and each fixture's `address.city`; a falsy/empty `location` matches all cities.
- `ScrapingQueueService`'s page-limit cap becomes `@Value("${scraping.page-limit.max:5}") private int maxPageLimit = 5;` (default unchanged at 5); `scraping.yaml`'s static `maximum: 5` on `pageLimit` is removed (keep `minimum: 1`).
- The "Seed mock listings" button/card and `seedMockData()` method (added by the previous iteration of this feature) are removed from the frontend.
- `ScrapingWorker.scrapeAllPages()`'s page loop becomes 0-based (`for (page = 0; page < limit; page++)`), matching the real pyfunda API exactly, and its own separate hardcoded `Math.min(pageLimit, 5)` clamp is removed in favor of trusting `session.getPageLimit()`, which is already clamped once at creation time by `ScrapingQueueService` (Task 4) — a single source of truth for the cap, not two.

## Progress

| Task | Status | Commit |
|------|--------|--------|
| Task 1: Generator script + CSV fixture data | ⬜ Pending | — |
| Task 2: `mock_fixtures.py` loads from CSV | ⬜ Pending | — |
| Task 3: `MockFunda` city filtering + real pagination | ⬜ Pending | — |
| Task 4: Configurable scraping page-limit cap (hermes-backend) | ⬜ Pending | — |
| Task 5: Remove the mock-seed button, add a city hint (hermes-frontend) | ⬜ Pending | — |
| Task 6: Fix `ScrapingWorker`'s page numbering and redundant clamp | ⬜ Pending | — |

**Resuming after an interruption:** re-read this table. Pick up at the first `⬜ Pending` row — every task before it is done and its commit is on the branch. Each task's own section below is self-contained (files, code, exact commands), so you don't need prior tasks' sections in context to execute it, only their *outputs* (types/signatures), which are listed under each task's "Interfaces" block.

---

### Task 1: Generator script + CSV fixture data

**Files:**
- Create: `funda-proxy/scripts/generate_mock_data.py`
- Create: `funda-proxy/mock_data/listings.csv` (generated output, not hand-written)
- Create: `funda-proxy/mock_data/price_histories.csv` (generated output, not hand-written)
- Test: `funda-proxy/tests/test_mock_data_csv.py`

**Interfaces:**
- Produces: two CSV files at `funda-proxy/mock_data/listings.csv` and `funda-proxy/mock_data/price_histories.csv`, with the exact header rows given in Step 3 below. Task 2 reads these files directly by path — it does not import anything from this task's script.

- [ ] **Step 1: Write the failing tests**

Create `funda-proxy/tests/test_mock_data_csv.py`:

```python
import csv
from collections import Counter
from pathlib import Path

MOCK_DATA_DIR = Path(__file__).resolve().parent.parent / "mock_data"

EXPECTED_CITIES = {
    "Amsterdam", "Utrecht", "Rotterdam", "Eindhoven", "Haarlem",
    "Weert", "Groningen", "Maastricht", "Tilburg", "Nijmegen",
}


def _read_listings():
    with (MOCK_DATA_DIR / "listings.csv").open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def _read_price_histories():
    with (MOCK_DATA_DIR / "price_histories.csv").open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def test_listings_csv_has_500_rows():
    assert len(_read_listings()) == 500


def test_listings_csv_global_ids_are_unique_and_sequential():
    ids = [int(row["global_id"]) for row in _read_listings()]
    assert sorted(ids) == list(range(90000001, 90000501))


def test_listings_csv_only_contains_expected_cities():
    cities = {row["city"] for row in _read_listings()}
    assert cities == EXPECTED_CITIES


def test_listings_csv_has_50_listings_per_city():
    counts = Counter(row["city"] for row in _read_listings())
    assert all(count == 50 for count in counts.values())


def test_price_histories_reference_only_known_listing_ids():
    listing_ids = {row["global_id"] for row in _read_listings()}
    history_ids = {row["global_id"] for row in _read_price_histories()}
    assert history_ids <= listing_ids


def test_every_listing_has_one_to_three_price_history_rows():
    counts = Counter(row["global_id"] for row in _read_price_histories())
    listing_ids = {row["global_id"] for row in _read_listings()}
    assert set(counts.keys()) == listing_ids
    assert all(1 <= n <= 3 for n in counts.values())
```

- [ ] **Step 2: Run the tests to verify they fail**

Run (from `funda-proxy/`): `pytest tests/test_mock_data_csv.py -v`
Expected: FAIL — `FileNotFoundError` (no `mock_data/listings.csv` yet).

- [ ] **Step 3: Write the generator script**

Create `funda-proxy/scripts/generate_mock_data.py`:

```python
"""One-off generator for funda-proxy/mock_data/{listings,price_histories}.csv.

Not imported by the running service — mock_fixtures.py reads the CSVs this
script produces. Re-run by hand (`python scripts/generate_mock_data.py` from
funda-proxy/) only if the curated city/street table or generation logic
below changes; the fixed random seed makes output reproducible.
"""

import csv
import random
from pathlib import Path

random.seed(42)

# (street_name, postcode, province) per city, real Dutch streets so the
# geocoding pipeline can resolve plausible coordinates.
CITIES: dict[str, tuple[list[tuple[str, str, str]], int]] = {
    "Amsterdam": (
        [
            ("Prinsengracht", "1016GV", "Noord-Holland"),
            ("Keizersgracht", "1015CJ", "Noord-Holland"),
            ("Vondelstraat", "1054GD", "Noord-Holland"),
            ("Overtoom", "1054HN", "Noord-Holland"),
            ("Ferdinand Bolstraat", "1072LM", "Noord-Holland"),
        ],
        650000,
    ),
    "Utrecht": (
        [
            ("Oudegracht", "3511AZ", "Utrecht"),
            ("Neude", "3512JJ", "Utrecht"),
            ("Biltstraat", "3572AR", "Utrecht"),
            ("Amsterdamsestraatweg", "3513AB", "Utrecht"),
            ("Twijnstraat", "3511ZK", "Utrecht"),
        ],
        500000,
    ),
    "Rotterdam": (
        [
            ("Coolsingel", "3011AD", "Zuid-Holland"),
            ("Witte de Withstraat", "3012BM", "Zuid-Holland"),
            ("Nieuwe Binnenweg", "3014GA", "Zuid-Holland"),
            ("Meent", "3011JJ", "Zuid-Holland"),
            ("Kruiskade", "3012EE", "Zuid-Holland"),
        ],
        425000,
    ),
    "Eindhoven": (
        [
            ("Stratumseind", "5611ET", "Noord-Brabant"),
            ("Kruisstraat", "5612AJ", "Noord-Brabant"),
            ("Vestdijk", "5611CA", "Noord-Brabant"),
            ("Woenselse Markt", "5621CS", "Noord-Brabant"),
            ("Fuutlaan", "5613AB", "Noord-Brabant"),
        ],
        400000,
    ),
    "Haarlem": (
        [
            ("Grote Markt", "2011RD", "Noord-Holland"),
            ("Barteljorisstraat", "2011RA", "Noord-Holland"),
            ("Kruisstraat", "2011PV", "Noord-Holland"),
            ("Zijlstraat", "2011TL", "Noord-Holland"),
            ("Nieuwe Groenmarkt", "2011TW", "Noord-Holland"),
        ],
        475000,
    ),
    "Weert": (
        [
            ("Nieuwstraat", "6001EM", "Limburg"),
            ("Hegstraat", "6001CX", "Limburg"),
            ("Wilhelminasingel", "6001GS", "Limburg"),
            ("Beekstraat", "6001BB", "Limburg"),
            ("Emmasingel", "6001BT", "Limburg"),
        ],
        325000,
    ),
    "Groningen": (
        [
            ("Grote Markt", "9711LV", "Groningen"),
            ("Herestraat", "9711LC", "Groningen"),
            ("Oude Ebbingestraat", "9712HA", "Groningen"),
            ("Vismarkt", "9712CB", "Groningen"),
            ("Folkingestraat", "9711JW", "Groningen"),
        ],
        350000,
    ),
    "Maastricht": (
        [
            ("Vrijthof", "6211LD", "Limburg"),
            ("Grote Staat", "6211CT", "Limburg"),
            ("Wycker Brugstraat", "6221ED", "Limburg"),
            ("Stokstraat", "6211GP", "Limburg"),
            ("Rechtstraat", "6221EG", "Limburg"),
        ],
        375000,
    ),
    "Tilburg": (
        [
            ("Heuvel", "5038CS", "Noord-Brabant"),
            ("Piusstraat", "5038WP", "Noord-Brabant"),
            ("Korte Heuvel", "5038CT", "Noord-Brabant"),
            ("NS-plein", "5014DA", "Noord-Brabant"),
            ("Stationsstraat", "5038EA", "Noord-Brabant"),
        ],
        340000,
    ),
    "Nijmegen": (
        [
            ("Grote Markt", "6511KB", "Gelderland"),
            ("Lange Hezelstraat", "6511CD", "Gelderland"),
            ("Molenstraat", "6511EA", "Gelderland"),
            ("Hertogstraat", "6511TA", "Gelderland"),
            ("Bloemerstraat", "6511EM", "Gelderland"),
        ],
        390000,
    ),
}

ENERGY_LABELS = ["A", "B", "C", "D", "E", "F", "G"]
ENERGY_WEIGHTS = [25, 20, 20, 15, 10, 6, 4]
STATUSES = [
    "beschikbaar",
    "beschikbaar",
    "beschikbaar",
    "beschikbaar",
    "onder bod",
    "verkocht onder voorbehoud",
]
DESCRIPTION_TEMPLATES = [
    "Sfeervolle woning aan de {street}.",
    "Ruim appartement met balkon aan de {street}.",
    "Karakteristiek pand in het centrum, {street}.",
    "Modern herenhuis aan de {street}.",
    "Compacte woning dichtbij het centrum, {street}.",
    "Lichte bovenwoning aan de {street}.",
]
MONTHS = [
    "januari", "februari", "maart", "april", "mei", "juni",
    "juli", "augustus", "september", "oktober", "november", "december",
]

OUTPUT_DIR = Path(__file__).resolve().parent.parent / "mock_data"

LISTING_FIELDS = [
    "global_id", "street_name", "house_number", "house_number_suffix",
    "postcode", "city", "province", "price", "living_area", "plot_area",
    "rooms", "bedrooms", "energy_label", "status", "description",
    "offering_type", "publication_date",
]
PRICE_HISTORY_FIELDS = [
    "global_id", "price", "human_price", "source", "status", "date", "timestamp",
]


def _format_price(price: int) -> str:
    return f"€ {price:,}".replace(",", ".") + " k.k."


def _price_history_rows(global_id: int, asking_price: int) -> list[dict]:
    count = random.randint(1, 3)
    prices = {asking_price}
    while len(prices) < count:
        prices.add(asking_price + random.choice([10000, 20000, 30000, 40000]))
    ordered_prices = sorted(prices, reverse=True)[:count]
    ordered_prices[-1] = asking_price

    rows = []
    for price in ordered_prices:
        month_index = random.randint(0, 11)
        day = random.randint(1, 28)
        rows.append(
            {
                "global_id": global_id,
                "price": price,
                "human_price": _format_price(price),
                "source": "funda",
                "status": "asking_price",
                "date": f"{day} {MONTHS[month_index]} 2024",
                "timestamp": f"2024-{month_index + 1:02d}-{day:02d}T00:00:00+00:00",
            }
        )
    return rows


def generate() -> tuple[list[dict], list[dict]]:
    listing_rows: list[dict] = []
    price_history_rows: list[dict] = []
    global_id = 90000001

    for city, (streets, base_price) in CITIES.items():
        for street_name, postcode, province in streets:
            for i in range(10):
                house_number = str(1 + i * 2)
                living_area = random.randint(45, 180)
                plot_area = random.randint(30, 250) if random.random() < 0.5 else None
                rooms = max(2, living_area // 30)
                bedrooms = max(1, rooms - 1)
                energy_label = random.choices(ENERGY_LABELS, weights=ENERGY_WEIGHTS, k=1)[0]
                status = random.choice(STATUSES)
                price = base_price + random.randint(-75000, 150000)
                description = random.choice(DESCRIPTION_TEMPLATES).format(street=street_name)

                listing_rows.append(
                    {
                        "global_id": global_id,
                        "street_name": street_name,
                        "house_number": house_number,
                        "house_number_suffix": "",
                        "postcode": postcode,
                        "city": city,
                        "province": province,
                        "price": price,
                        "living_area": living_area,
                        "plot_area": plot_area if plot_area is not None else "",
                        "rooms": rooms,
                        "bedrooms": bedrooms,
                        "energy_label": energy_label,
                        "status": status,
                        "description": description,
                        "offering_type": "koop",
                        "publication_date": "2024-01-15",
                    }
                )
                price_history_rows.extend(_price_history_rows(global_id, price))
                global_id += 1

    return listing_rows, price_history_rows


def write_csv(path: Path, fieldnames: list[str], rows: list[dict]) -> None:
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


if __name__ == "__main__":
    listings, price_histories = generate()
    OUTPUT_DIR.mkdir(exist_ok=True)
    write_csv(OUTPUT_DIR / "listings.csv", LISTING_FIELDS, listings)
    write_csv(OUTPUT_DIR / "price_histories.csv", PRICE_HISTORY_FIELDS, price_histories)
    print(f"Wrote {len(listings)} listings and {len(price_histories)} price-history rows to {OUTPUT_DIR}")
```

- [ ] **Step 4: Run the generator**

Run (from `funda-proxy/`): `python scripts/generate_mock_data.py`
Expected output: `Wrote 500 listings and <N> price-history rows to .../funda-proxy/mock_data` where `<N>` is between 500 and 1500.

- [ ] **Step 5: Run the tests to verify they pass**

Run (from `funda-proxy/`): `pytest tests/test_mock_data_csv.py -v`
Expected: PASS — 6 passed

- [ ] **Step 6: Commit**

```bash
git add funda-proxy/scripts/generate_mock_data.py funda-proxy/mock_data/listings.csv funda-proxy/mock_data/price_histories.csv funda-proxy/tests/test_mock_data_csv.py
git commit -m "feat(funda-proxy): generate 500 mock listings across 10 cities as CSV fixtures"
```

---

### Task 2: `mock_fixtures.py` loads from CSV

**Files:**
- Modify: `funda-proxy/mock_fixtures.py` (full rewrite — replace the hardcoded 5-listing version)
- Test: `funda-proxy/tests/test_mock_fixtures.py`

**Interfaces:**
- Consumes: `funda-proxy/mock_data/listings.csv`, `funda-proxy/mock_data/price_histories.csv` (Task 1, read by file path, no import dependency).
- Produces: `mock_fixtures.MOCK_LISTINGS: list[Listing]` (500 entries), `mock_fixtures.MOCK_PRICE_HISTORIES: dict[str, PriceHistory]` (500 entries, keyed by `str(global_id)`) — same names and shapes the old version exposed. Task 3's `mock_client.py` imports these two names unchanged.

- [ ] **Step 1: Write the failing tests**

Create `funda-proxy/tests/test_mock_fixtures.py`:

```python
from funda.listing import Listing, PriceHistory

import mock_fixtures


def test_loads_500_listings():
    assert len(mock_fixtures.MOCK_LISTINGS) == 500
    assert all(isinstance(l, Listing) for l in mock_fixtures.MOCK_LISTINGS)


def test_global_ids_match_expected_range():
    ids = {l.global_id for l in mock_fixtures.MOCK_LISTINGS}
    assert ids == set(range(90000001, 90000501))


def test_every_listing_has_a_price_history_entry():
    listing_ids = {l.id for l in mock_fixtures.MOCK_LISTINGS}
    assert set(mock_fixtures.MOCK_PRICE_HISTORIES.keys()) == listing_ids
    assert all(
        isinstance(h, PriceHistory) and 1 <= len(h.changes) <= 3
        for h in mock_fixtures.MOCK_PRICE_HISTORIES.values()
    )


def test_a_known_listing_has_expected_address():
    first = next(l for l in mock_fixtures.MOCK_LISTINGS if l.global_id == 90000001)
    assert first.address.city == "Amsterdam"
    assert first.address.street_name == "Prinsengracht"
    assert first.address.house_number == "1"
```

- [ ] **Step 2: Run the tests to verify they fail**

Run (from `funda-proxy/`): `pytest tests/test_mock_fixtures.py -v`
Expected: FAIL — the old `mock_fixtures.py` only defines 5 listings, so `test_loads_500_listings` and `test_global_ids_match_expected_range` fail.

- [ ] **Step 3: Rewrite `mock_fixtures.py`**

Replace the full contents of `funda-proxy/mock_fixtures.py`:

```python
"""Loads the fixed set of mock listings from funda-proxy/mock_data/*.csv.

See funda-proxy/scripts/generate_mock_data.py for how the CSVs are
produced; this module only reads them.
"""

import csv
from pathlib import Path

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

_MOCK_DATA_DIR = Path(__file__).resolve().parent / "mock_data"
_LISTINGS_CSV = _MOCK_DATA_DIR / "listings.csv"
_PRICE_HISTORIES_CSV = _MOCK_DATA_DIR / "price_histories.csv"


def _int_or_none(value: str) -> int | None:
    return int(value) if value else None


def _load_listings() -> list[Listing]:
    listings = []
    with _LISTINGS_CSV.open(newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            global_id = int(row["global_id"])
            slug = row["street_name"].lower().replace(" ", "-")
            listings.append(
                Listing(
                    global_id=global_id,
                    offering_type=row["offering_type"],
                    address=Address(
                        street_name=row["street_name"],
                        house_number=row["house_number"],
                        house_number_suffix=row["house_number_suffix"] or None,
                        postcode=row["postcode"],
                        city=row["city"],
                        province=row["province"],
                    ),
                    price=Price(amount=int(row["price"]), offering_type=row["offering_type"]),
                    areas=Areas(
                        living=_int_or_none(row["living_area"]),
                        plot=_int_or_none(row["plot_area"]),
                    ),
                    rooms=Rooms(total=int(row["rooms"]), bedrooms=int(row["bedrooms"])),
                    property_details=PropertyDetails(
                        energy_label=row["energy_label"], status=row["status"]
                    ),
                    urls=Urls(
                        full=f"https://www.funda.nl/koop/{row['city'].lower()}/huis-{global_id}-{slug}/"
                    ),
                    description=row["description"],
                    publication_date=row["publication_date"],
                )
            )
    return listings


def _load_price_histories() -> dict[str, PriceHistory]:
    changes_by_id: dict[str, list[PriceChange]] = {}
    with _PRICE_HISTORIES_CSV.open(newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            changes_by_id.setdefault(row["global_id"], []).append(
                PriceChange(
                    price=int(row["price"]),
                    human_price=row["human_price"],
                    source=row["source"],
                    status=row["status"],
                    date=row["date"],
                    timestamp=row["timestamp"],
                )
            )
    return {
        global_id: PriceHistory(changes=tuple(changes))
        for global_id, changes in changes_by_id.items()
    }


MOCK_LISTINGS: list[Listing] = _load_listings()
MOCK_PRICE_HISTORIES: dict[str, PriceHistory] = _load_price_histories()
```

- [ ] **Step 4: Run the tests to verify they pass**

Run (from `funda-proxy/`): `pytest tests/test_mock_fixtures.py -v`
Expected: PASS — 4 passed

- [ ] **Step 5: Run the full funda-proxy suite to confirm no regressions**

Run (from `funda-proxy/`): `pytest -v`
Expected: `tests/test_mock_client.py` and `tests/test_mock_mode.py` (from the earlier iteration of this feature, still referencing the old 5-listing dataset) will now FAIL — this is expected and is what Task 3 fixes. Confirm every OTHER test file passes: `test_client.py`, `test_endpoints.py`, `test_mock_data_csv.py`, `test_mock_fixtures.py`, `test_models.py`.

- [ ] **Step 6: Commit**

```bash
git add funda-proxy/mock_fixtures.py funda-proxy/tests/test_mock_fixtures.py
git commit -m "feat(funda-proxy): load mock fixtures from CSV instead of hardcoded data"
```

---

### Task 3: `MockFunda` city filtering + real pagination

**Files:**
- Modify: `funda-proxy/mock_client.py` (full rewrite)
- Modify: `funda-proxy/tests/test_mock_client.py` (full rewrite — replaces the 5-listing-based tests)
- Modify: `funda-proxy/tests/test_mock_mode.py` (full rewrite — replaces the 5-listing-based tests)

**Interfaces:**
- Consumes: `mock_fixtures.MOCK_LISTINGS: list[Listing]`, `mock_fixtures.MOCK_PRICE_HISTORIES: dict[str, PriceHistory]` (Task 2).
- Produces: `mock_client.MockFunda` with `search(location=None, *, min_price=None, max_price=None, min_area=None, max_area=None, page=0, **_ignored) -> list[Listing]`, `listing(listing_id: int | str) -> Listing`, `price_history(listing: Listing | str) -> PriceHistory`, `close() -> None`, and `mock_client.PAGE_SIZE = 15` — same public names as before; `client.py` (already wired up in a prior task, not touched by this plan) needs no changes.

- [ ] **Step 1: Write the failing tests**

Replace the full contents of `funda-proxy/tests/test_mock_client.py`:

```python
import pytest
from funda.exceptions import ListingNotFound

from mock_client import MockFunda


@pytest.fixture
def mock_funda():
    return MockFunda()


def test_search_without_location_returns_first_page_of_all_cities(mock_funda):
    results = mock_funda.search()
    assert len(results) == 15


def test_search_filters_by_city_exact_match(mock_funda):
    results = mock_funda.search("Weert")
    assert len(results) == 15
    assert all(l.address.city == "Weert" for l in results)


def test_search_filters_by_city_case_insensitive(mock_funda):
    results = mock_funda.search("weert")
    assert len(results) == 15
    assert all(l.address.city == "Weert" for l in results)


def test_search_unknown_city_returns_empty(mock_funda):
    assert mock_funda.search("Nowhereville") == []


def test_search_pagination_within_a_city_covers_all_50_listings(mock_funda):
    page0 = mock_funda.search("Weert", page=0)
    page1 = mock_funda.search("Weert", page=1)
    page2 = mock_funda.search("Weert", page=2)
    page3 = mock_funda.search("Weert", page=3)
    assert len(page0) == 15
    assert len(page1) == 15
    assert len(page2) == 15
    assert len(page3) == 5
    assert mock_funda.search("Weert", page=4) == []
    ids = {l.global_id for l in page0 + page1 + page2 + page3}
    assert len(ids) == 50


def test_search_page_zero_and_page_one_return_disjoint_results(mock_funda):
    page0 = mock_funda.search("Weert", page=0)
    page1 = mock_funda.search("Weert", page=1)
    assert {l.global_id for l in page0}.isdisjoint({l.global_id for l in page1})


def test_search_filters_by_price(mock_funda):
    wide = mock_funda.search("Weert", min_price=1, max_price=10_000_000)
    assert len(wide) == 15
    narrow = mock_funda.search("Weert", min_price=10_000_000)
    assert narrow == []


def test_search_filters_by_area(mock_funda):
    wide = mock_funda.search("Weert", min_area=0, max_area=1000)
    assert len(wide) == 15
    narrow = mock_funda.search("Weert", min_area=10_000)
    assert narrow == []


def test_listing_returns_matching_fixture(mock_funda):
    listing = mock_funda.listing("90000001")
    assert listing.global_id == 90000001


def test_listing_accepts_int_id(mock_funda):
    listing = mock_funda.listing(90000001)
    assert listing.global_id == 90000001


def test_listing_unknown_id_raises_not_found(mock_funda):
    with pytest.raises(ListingNotFound):
        mock_funda.listing("00000000")


def test_price_history_returns_one_to_three_changes_for_any_listing(mock_funda):
    for global_id in ("90000001", "90000500"):
        history = mock_funda.price_history(global_id)
        assert 1 <= len(history.changes) <= 3


def test_price_history_unknown_id_raises_not_found(mock_funda):
    with pytest.raises(ListingNotFound):
        mock_funda.price_history("00000000")
```

Replace the full contents of `funda-proxy/tests/test_mock_mode.py`:

```python
import pytest
from fastapi.testclient import TestClient


@pytest.fixture
def mock_mode_api(monkeypatch):
    monkeypatch.setenv("FUNDA_MOCK_MODE", "true")
    from main import app
    with TestClient(app) as c:
        yield c


def test_search_filters_by_city(mock_mode_api):
    resp = mock_mode_api.get("/search?location=Weert")
    assert resp.status_code == 200
    data = resp.json()
    assert len(data) == 15
    assert all(item["city"] == "Weert" for item in data)


def test_search_pagination_returns_disjoint_slices(mock_mode_api):
    page0 = mock_mode_api.get("/search?location=Weert&page=0").json()
    page1 = mock_mode_api.get("/search?location=Weert&page=1").json()
    assert {item["global_id"] for item in page0}.isdisjoint(
        {item["global_id"] for item in page1}
    )


def test_search_unknown_city_returns_empty_list(mock_mode_api):
    resp = mock_mode_api.get("/search?location=Nowhereville")
    assert resp.status_code == 200
    assert resp.json() == []


def test_get_listing_returns_fixture(mock_mode_api):
    resp = mock_mode_api.get("/listings/90000001")
    assert resp.status_code == 200
    assert resp.json()["global_id"] == 90000001


def test_get_listing_unknown_returns_404(mock_mode_api):
    resp = mock_mode_api.get("/listings/00000000")
    assert resp.status_code == 404


def test_get_price_history_returns_one_to_three_changes(mock_mode_api):
    resp = mock_mode_api.get("/listings/90000001/price-history")
    assert resp.status_code == 200
    data = resp.json()
    assert 1 <= len(data) <= 3
```

- [ ] **Step 2: Run the tests to verify they fail**

Run (from `funda-proxy/`): `pytest tests/test_mock_client.py tests/test_mock_mode.py -v`
Expected: FAIL — the current `mock_client.py` has no city filtering and its `page > 1` guard doesn't implement real slicing, so most of the new assertions fail (e.g. `search()` with no args currently returns all 500 rather than 15).

- [ ] **Step 3: Rewrite `mock_client.py`**

Replace the full contents of `funda-proxy/mock_client.py`:

```python
"""Stand-in for funda.Funda that serves fixture data instead of the real API."""

from funda.exceptions import ListingNotFound
from funda.listing import Listing, PriceHistory

from mock_fixtures import MOCK_LISTINGS, MOCK_PRICE_HISTORIES

PAGE_SIZE = 15


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
        results = list(MOCK_LISTINGS)
        if location:
            needle = location.strip().casefold()
            results = [
                l for l in results
                if l.address.city and (
                    needle in l.address.city.casefold()
                    or l.address.city.casefold() in needle
                )
            ]
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
        start = page * PAGE_SIZE
        return results[start:start + PAGE_SIZE]

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

- [ ] **Step 4: Run the tests to verify they pass**

Run (from `funda-proxy/`): `pytest tests/test_mock_client.py tests/test_mock_mode.py -v`
Expected: PASS — 13 passed in `test_mock_client.py`, 6 passed in `test_mock_mode.py`

- [ ] **Step 5: Run the full funda-proxy suite to confirm no regressions**

Run (from `funda-proxy/`): `pytest -v`
Expected: PASS — all tests pass (`test_client.py`, `test_endpoints.py`, `test_mock_data_csv.py`, `test_mock_fixtures.py`, `test_mock_client.py`, `test_mock_mode.py`, `test_models.py`), output pristine aside from the pre-existing unrelated `StarletteDeprecationWarning`.

- [ ] **Step 6: Commit**

```bash
git add funda-proxy/mock_client.py funda-proxy/tests/test_mock_client.py funda-proxy/tests/test_mock_mode.py
git commit -m "feat(funda-proxy): add real city filtering and pagination to MockFunda"
```

---

### Task 4: Configurable scraping page-limit cap (hermes-backend)

**Files:**
- Modify: `hermes-backend/src/main/resources/openapi/scraping.yaml`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/ScrapingQueueService.java`
- Test: `hermes-backend/src/test/java/com/kropholler/dev/hermes/scraping/ScrapingQueueServiceTest.java`

**Interfaces:**
- Produces: `ScrapingQueueService.maxPageLimit` (package-private-visible via reflection for tests), driven by the `scraping.page-limit.max` Spring property (default `5`). No other class in the codebase reads this field or property.

- [ ] **Step 1: Write the failing test**

In `hermes-backend/src/test/java/com/kropholler/dev/hermes/scraping/ScrapingQueueServiceTest.java`, add this test method inside the `ScrapingQueueServiceTest` class, after `enqueueSearch_clampsPageLimitToFive`:

```java
    @Test
    void enqueueSearch_clampsPageLimitToConfiguredMax() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "maxPageLimit", 8);
        ScrapingSessionEntity session = new ScrapingSessionEntity();
        session.setType(ScrapingSessionType.SEARCH);
        session.setCity("amsterdam");
        session.setPageLimit(8);
        when(repository.save(any())).thenReturn(session);

        service.enqueueSearch("amsterdam", null, null, null, null, 20);

        org.mockito.ArgumentCaptor<ScrapingSessionEntity> captor =
            org.mockito.ArgumentCaptor.forClass(ScrapingSessionEntity.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPageLimit()).isEqualTo(8);
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run (from `hermes-backend/`): `mvnw.cmd test -Dtest=ScrapingQueueServiceTest#enqueueSearch_clampsPageLimitToConfiguredMax`
Expected: FAIL — `NoSuchFieldException` or similar, since `ScrapingQueueService` has no `maxPageLimit` field yet (it currently uses a hardcoded `private static final int MAX_PAGE_LIMIT = 5;` constant).

- [ ] **Step 3: Remove the static OpenAPI maximum**

In `hermes-backend/src/main/resources/openapi/scraping.yaml`, change:

```yaml
        pageLimit:
          type: integer
          minimum: 1
          maximum: 5
```

to:

```yaml
        pageLimit:
          type: integer
          minimum: 1
```

- [ ] **Step 4: Make the cap configurable**

Replace the full contents of `hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/ScrapingQueueService.java`:

```java
package com.kropholler.dev.hermes.scraping;

import com.kropholler.dev.hermes.scraping.schedule.session.ScrapingSessionEntity;
import com.kropholler.dev.hermes.scraping.schedule.session.ScrapingSessionMapper;
import com.kropholler.dev.hermes.scraping.schedule.session.ScrapingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScrapingQueueService {

    private final ScrapingSessionRepository sessionRepository;
    private final ScrapingSessionMapper mapper;

    @Value("${scraping.page-limit.max:5}")
    private int maxPageLimit = 5;

    @Transactional
    public ScrapingSessionDto enqueueSearch(String city, Integer minPrice, Integer maxPrice,
                                            Integer minArea, Integer maxArea, int pageLimit) {
        int clampedLimit = Math.min(pageLimit, maxPageLimit);
        String url = buildSearchUrl(city, minPrice, maxPrice, minArea, maxArea, 1);

        ScrapingSessionEntity session = new ScrapingSessionEntity();
        session.setType(ScrapingSessionType.SEARCH);
        session.setCity(city);
        session.setMinPrice(minPrice);
        session.setMaxPrice(maxPrice);
        session.setMinArea(minArea);
        session.setMaxArea(maxArea);
        session.setPageLimit(clampedLimit);
        session.setFundaUrl(url);

        return mapper.toDto(sessionRepository.save(session));
    }

    @Transactional
    public ScrapingSessionDto enqueueRescrape(String listingUrl, String city) {
        ScrapingSessionEntity session = new ScrapingSessionEntity();
        session.setType(ScrapingSessionType.RESCRAPE);
        session.setCity(city);
        session.setPageLimit(1);
        session.setFundaUrl(listingUrl);
        session.setTargetListingUrl(listingUrl);

        return mapper.toDto(sessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public Optional<ScrapingSessionDto> findById(UUID id) {
        return sessionRepository.findById(id).map(mapper::toDto);
    }

    private String buildSearchUrl(String city, Integer minPrice, Integer maxPrice,
                                  Integer minArea, Integer maxArea, int page) {
        UriComponentsBuilder uri = UriComponentsBuilder
            .fromUriString("https://www.funda.nl/zoeken/koop")
            .queryParam("selected_area", "[\"" + city.toLowerCase() + "\"]")
            .queryParam("search_result", page);
        if (minPrice != null) uri.queryParam("price_min", minPrice);
        if (maxPrice != null) uri.queryParam("price_max", maxPrice);
        if (minArea != null)  uri.queryParam("floor_area_min", minArea);
        if (maxArea != null)  uri.queryParam("floor_area_max", maxArea);
        return uri.build().encode().toUriString();
    }

}
```

The field initializer (`= 5`) is deliberate: it's the fallback value used when the class is constructed without a Spring context (e.g. `@InjectMocks` in tests), matching today's default behavior exactly.

- [ ] **Step 5: Run the new test to verify it passes**

Run (from `hermes-backend/`): `mvnw.cmd test -Dtest=ScrapingQueueServiceTest`
Expected: PASS — all tests in `ScrapingQueueServiceTest` pass, including `enqueueSearch_clampsPageLimitToFive` (still passes unchanged, since the field initializer keeps the default at 5) and the new `enqueueSearch_clampsPageLimitToConfiguredMax`.

- [ ] **Step 6: Run the full backend suite to confirm no regressions**

Run (from `hermes-backend/`): `mvnw.cmd test`
Expected: `BUILD SUCCESS`, 0 failures, 0 errors. The OpenAPI schema change regenerates `CreateScrapingSessionRequest` without the `@Max(5)` annotation; confirmed during planning that no existing test asserts a 400 response for `pageLimit > 5` (the only other `pageLimit`-referencing test, `ScrapingSessionControllerTest.java:48`, uses `pageLimit: 5`, which stays valid), so no other test file should need updating.

- [ ] **Step 7: Commit**

```bash
git add hermes-backend/src/main/resources/openapi/scraping.yaml hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/ScrapingQueueService.java hermes-backend/src/test/java/com/kropholler/dev/hermes/scraping/ScrapingQueueServiceTest.java
git commit -m "feat(backend): make scraping page-limit cap configurable via scraping.page-limit.max"
```

---

### Task 5: Remove the mock-seed button, add a city hint (hermes-frontend)

**Files:**
- Modify: `hermes-frontend/src/app/pages/scraping/scraping-page.component.ts`
- Modify: `hermes-frontend/src/app/pages/scraping/scraping-page.component.html`

**Interfaces:**
- Consumes: nothing new.
- Produces: nothing new — this task only removes the `seedMockData()` method and its template card, and adds static text. No other file references either.

This task has no automated test (no existing `.spec.ts` convention for this component, as established previously) — verification is the typecheck in Step 3 plus the still-pending manual browser check.

- [ ] **Step 1: Remove `seedMockData()` from the component**

In `hermes-frontend/src/app/pages/scraping/scraping-page.component.ts`, remove this method (currently the last method in the class, after `submit()`):

```typescript
  seedMockData(): void {
    this.svc.createSession({ city: 'Mock data', pageLimit: 1 });
  }
```

So the class body ends with `submit()`'s closing brace followed directly by the class's closing brace.

- [ ] **Step 2: Update the template**

In `hermes-frontend/src/app/pages/scraping/scraping-page.component.html`:

1. Add a hint paragraph inside the City field's `<div>` (currently lines 12-21), right after the closing `/>` of the `<input>`:

```html
    <div>
      <label class="block text-sm font-medium text-slate-700 mb-1.5">
        City <span class="text-cyan-500">*</span>
      </label>
      <input type="text" [(ngModel)]="city" name="city" required [disabled]="isPolling()"
        placeholder="e.g. Amsterdam"
        class="block w-full rounded-lg border-slate-200 shadow-sm text-sm
               focus:border-cyan-500 focus:ring-cyan-500
               disabled:bg-slate-50 disabled:text-slate-400" />
      <p class="mt-1.5 text-xs text-slate-400">
        In mock mode (funda-proxy running with FUNDA_MOCK_MODE=true), try: Amsterdam, Utrecht,
        Rotterdam, Eindhoven, Haarlem, Weert, Groningen, Maastricht, Tilburg, Nijmegen.
      </p>
    </div>
```

2. Remove the entire "Mock data" card block (currently the section between the session-status block and the "Geocoding backfill" card):

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

Delete this block entirely, leaving the "Geocoding backfill" card immediately following the `@if (svc.session(); as session) { ... }` block.

- [ ] **Step 3: Verify it compiles**

Run (from `hermes-frontend/`): `npx tsc -p tsconfig.app.json --noEmit`
Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add hermes-frontend/src/app/pages/scraping/scraping-page.component.ts hermes-frontend/src/app/pages/scraping/scraping-page.component.html
git commit -m "feat(frontend): drop dedicated mock-seed button, hint mock city names in the search form"
```

- [ ] **Step 5: Manual verification (owed, expand on the previous plan's pending check)**

1. In the repo root, run: `FUNDA_MOCK_MODE=true docker compose up --build`
2. Open the frontend, navigate to the Scraping admin page.
3. Search city "Weert", page limit 4.
4. Confirm the session completes and the listings search page shows ~50 Weert-only addresses (real street names, varying house numbers), each geocoded.
5. Open a Weert listing's detail page and confirm it shows 1-3 price-history entries.
6. Search an unrelated city name (e.g. "Nowhereville") and confirm the session completes with zero listings.
7. Stop the stack, restart without `FUNDA_MOCK_MODE` set, and confirm a normal city search still attempts a real Funda search (no mock leakage).

---

### Task 6: Fix `ScrapingWorker`'s page numbering and redundant clamp

**Context:** Discovered while planning Task 4: `ScrapingWorker.scrapeAllPages()` has its own hardcoded `Math.min(session.getPageLimit(), 5)` clamp, independent of `ScrapingQueueService`'s cap — so Task 4's configurable `scraping.page-limit.max` wouldn't actually take effect at scrape time without this fix. Separately, `pyfunda`'s real `search()` API is 0-based (`page=0` is the first 15 results), but the worker's loop starts at `page=1`, so every real (and, after Task 3, every mock) scrape has always skipped the true first page of results. This task fixes both: the worker now trusts the already-clamped `session.getPageLimit()` (set once, in `ScrapingQueueService`) instead of re-clamping, and the loop is 0-based to match the real API.

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/schedule/session/ScrapingWorker.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/scraping/schedule/session/ScrapingWorkerTest.java`

**Interfaces:**
- Consumes: `ScrapingSessionEntity.getPageLimit()` (existing, already clamped by `ScrapingQueueService.enqueueSearch` from Task 4 before the entity is ever persisted).
- Produces: nothing new — `scrapeAllPages()` stays a private method; only its internal page-numbering and clamping logic changes. No other class calls it or needs to know about this change.

- [ ] **Step 1: Update the failing tests**

In `hermes-backend/src/test/java/com/kropholler/dev/hermes/scraping/schedule/session/ScrapingWorkerTest.java`, replace the two existing `search_*` test methods and add a third:

```java
    @Test
    void search_multiplePages_stopsWhenPageReturnsEmpty() {
        ScrapingSessionEntity session = new ScrapingSessionEntity();
        session.setType(ScrapingSessionType.SEARCH);
        session.setCity("amsterdam");
        session.setPageLimit(3);
        session.setFundaUrl("https://funda.nl/...");
        RawListing listing = mock(RawListing.class);
        when(proxyClient.search("amsterdam", null, null, null, null, 0)).thenReturn(List.of(listing));
        when(proxyClient.search("amsterdam", null, null, null, null, 1)).thenReturn(List.of());

        worker.process(session);

        verify(proxyClient, times(2)).search(any(), any(), any(), any(), any(), anyInt());
        verify(sessionStore).complete(any(), argThat(list -> list.size() == 1));
    }

    @Test
    void search_singlePageWithResults_returnsAllListings() {
        ScrapingSessionEntity session = new ScrapingSessionEntity();
        session.setType(ScrapingSessionType.SEARCH);
        session.setCity("rotterdam");
        session.setPageLimit(1);
        session.setFundaUrl("https://funda.nl/...");
        RawListing listing = mock(RawListing.class);
        when(proxyClient.search("rotterdam", null, null, null, null, 0)).thenReturn(List.of(listing));

        worker.process(session);

        verify(sessionStore).complete(any(), argThat(list -> list.size() == 1));
    }

    @Test
    void search_respectsSessionPageLimitWithoutAdditionalClamping() {
        ScrapingSessionEntity session = new ScrapingSessionEntity();
        session.setType(ScrapingSessionType.SEARCH);
        session.setCity("amsterdam");
        session.setPageLimit(8);
        session.setFundaUrl("https://funda.nl/...");
        RawListing listing = mock(RawListing.class);
        when(proxyClient.search(eq("amsterdam"), any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(listing));

        worker.process(session);

        verify(proxyClient, times(8)).search(any(), any(), any(), any(), any(), anyInt());
    }
```

(`eq` and `anyInt` are already available via the file's existing `import static org.mockito.Mockito.*;` — no new imports needed.)

- [ ] **Step 2: Run the tests to verify they fail**

Run (from `hermes-backend/`): `mvnw.cmd test -Dtest=ScrapingWorkerTest`
Expected: FAIL — `search_multiplePages_stopsWhenPageReturnsEmpty` and `search_singlePageWithResults_returnsAllListings` fail because the current code calls `proxyClient.search(..., 1)` first, not `..., 0`; `search_respectsSessionPageLimitWithoutAdditionalClamping` fails because the current code clamps `pageLimit=8` down to 5 calls, not 8.

- [ ] **Step 3: Fix `ScrapingWorker`**

In `hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/schedule/session/ScrapingWorker.java`, replace:

```java
        List<RawListing> all = new ArrayList<>();
        int limit = Math.min(session.getPageLimit(), 5);
        for (int page = 1; page <= limit; page++) {
            List<RawListing> pageResults = proxyClient.search(
                session.getCity(), session.getMinPrice(), session.getMaxPrice(),
                session.getMinArea(), session.getMaxArea(), page);
            all.addAll(pageResults);
            if (pageResults.isEmpty()) break;
        }
        return all;
```

with:

```java
        List<RawListing> all = new ArrayList<>();
        int limit = session.getPageLimit();
        for (int page = 0; page < limit; page++) {
            List<RawListing> pageResults = proxyClient.search(
                session.getCity(), session.getMinPrice(), session.getMaxPrice(),
                session.getMinArea(), session.getMaxArea(), page);
            all.addAll(pageResults);
            if (pageResults.isEmpty()) break;
        }
        return all;
```

- [ ] **Step 4: Run the tests to verify they pass**

Run (from `hermes-backend/`): `mvnw.cmd test -Dtest=ScrapingWorkerTest`
Expected: PASS — all 6 tests in `ScrapingWorkerTest` pass.

- [ ] **Step 5: Run the full backend suite to confirm no regressions**

Run (from `hermes-backend/`): `mvnw.cmd test`
Expected: `BUILD SUCCESS`, 0 failures, 0 errors.

- [ ] **Step 6: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/scraping/schedule/session/ScrapingWorker.java hermes-backend/src/test/java/com/kropholler/dev/hermes/scraping/schedule/session/ScrapingWorkerTest.java
git commit -m "fix(backend): scrape pages 0-based to match the real Funda API, trust the already-clamped page limit"
```

- [ ] **Step 7: Update the manual verification from Task 5**

If Task 5's manual verification (Step 5 in that task) hasn't been performed yet, do it after this task instead of after Task 5, so the manually-observed page counts already reflect the 0-based fix. Concretely: Weert has 50 listings (indices 0-49, pages of 15: page 0 = 0-14, page 1 = 15-29, page 2 = 30-44, page 3 = 45-49). A search with `pageLimit: 4` should now return all 50 (pages 0-3, the last partial). Before this fix, the same request would have fetched pages 1-4 instead (skipping page 0 entirely) and returned only 35.
