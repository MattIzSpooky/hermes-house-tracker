# funda-proxy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

## Progress

| Task | Status | Commit |
|------|--------|--------|
| Task 1: Project scaffold | ✅ Done | 5132809 |
| Task 2: Pydantic response model | ✅ Done | 3708f94 |
| Task 3: Funda client module | ✅ Done | 3545743 |
| Task 4: FastAPI app and endpoints | ✅ Done | f14c055 |
| Task 5: Docker-compose integration | ✅ Done | efd6721 |

**Goal:** Create a FastAPI microservice (`funda-proxy`) that wraps the `pyfunda` library and exposes two HTTP endpoints — search and listing detail — so the Java `hermes-backend` can fetch Funda data without running a Python runtime itself.

**Architecture:** Single Python package with four focused files: `models.py` (Pydantic response shape + mapping from pyfunda's Listing), `client.py` (singleton Funda lifecycle tied to FastAPI startup/shutdown), `main.py` (endpoints), plus container config. Tests live in `funda-proxy/tests/`.

**Tech Stack:** Python 3.12, FastAPI, uvicorn, pyfunda, pytest, httpx (for TestClient)

---

## File Map

| File | Purpose |
|------|---------|
| `funda-proxy/pyproject.toml` | Project metadata and pytest config |
| `funda-proxy/requirements.txt` | Runtime dependencies |
| `funda-proxy/requirements-dev.txt` | Dev/test dependencies |
| `funda-proxy/Dockerfile` | Container image |
| `funda-proxy/models.py` | `ListingResponse` Pydantic model + `from_listing()` classmethod |
| `funda-proxy/client.py` | Singleton `Funda` client + `get_client()` + `lifespan` |
| `funda-proxy/main.py` | FastAPI app, `/health`, `/search`, `/listings/{id}` |
| `funda-proxy/tests/__init__.py` | Empty, makes tests a package |
| `funda-proxy/tests/test_models.py` | Unit tests for `ListingResponse.from_listing()` |
| `funda-proxy/tests/test_client.py` | Unit test for `get_client()` guard |
| `funda-proxy/tests/test_endpoints.py` | Endpoint tests with mocked Funda client |
| `docker-compose.yml` (root) | Add `funda-proxy` service |

---

## Task 1: Project scaffold

**Files:**
- Create: `funda-proxy/pyproject.toml`
- Create: `funda-proxy/requirements.txt`
- Create: `funda-proxy/requirements-dev.txt`
- Create: `funda-proxy/Dockerfile`
- Create: `funda-proxy/tests/__init__.py`

- [ ] **Step 1: Create the funda-proxy directory and empty tests package**

```bash
mkdir funda-proxy
mkdir funda-proxy/tests
touch funda-proxy/tests/__init__.py
```

- [ ] **Step 2: Write `funda-proxy/requirements.txt`**

```
fastapi>=0.115.0
uvicorn[standard]>=0.34.0
pyfunda
```

- [ ] **Step 3: Write `funda-proxy/requirements-dev.txt`**

```
-r requirements.txt
pytest>=8.0.0
httpx>=0.27.0
```

- [ ] **Step 4: Write `funda-proxy/pyproject.toml`**

```toml
[project]
name = "funda-proxy"
version = "0.1.0"
requires-python = ">=3.11"

[tool.pytest.ini_options]
testpaths = ["tests"]
```

- [ ] **Step 5: Write `funda-proxy/Dockerfile`**

```dockerfile
FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
```

- [ ] **Step 6: Commit**

```bash
git add funda-proxy/
git commit -m "feat(funda-proxy): add project scaffold"
```

---

## Task 2: Pydantic response model

**Files:**
- Create: `funda-proxy/models.py`
- Create: `funda-proxy/tests/test_models.py`

pyfunda's `Listing` is a frozen dataclass with nested objects. `address.street_name` is set by the search parser; the detail parser may set `address.title` instead. The mapping uses `getattr` with fallback to handle both cases safely.

Relevant pyfunda attribute paths:
- `listing.global_id` → `int | None`
- `listing.tiny_id` → `str | None`
- `listing.offering_type` → `str | None`
- `listing.publication_date` → `str | None`
- `listing.urls.full` → URL string
- `listing.address.street_name` → street (search result) or `listing.address.title` (detail)
- `listing.address.house_number` → `str | None`
- `listing.address.house_number_suffix` → `str | None`
- `listing.address.postcode` → zip code
- `listing.address.city` → `str | None`
- `listing.address.province` → `str | None`
- `listing.price.amount` → `int | None`
- `listing.areas.living` → living area m² as `int | None`
- `listing.rooms.total` → `int | None`
- `listing.rooms.bedrooms` → `int | None`
- `listing.property_details.energy_label` → `str | None`
- `listing.property_details.status` → `str | None`

- [ ] **Step 1: Write the failing tests in `funda-proxy/tests/test_models.py`**

```python
from unittest.mock import MagicMock
from models import ListingResponse


def _make_listing(**overrides):
    m = MagicMock()
    m.global_id = 12345678
    m.tiny_id = "abc123"
    m.offering_type = "koop"
    m.publication_date = "2024-01-15"
    m.urls.full = "https://www.funda.nl/koop/amsterdam/huis-12345678/"
    m.address.street_name = "Herengracht"
    m.address.house_number = "1"
    m.address.house_number_suffix = None
    m.address.postcode = "1015BZ"
    m.address.city = "Amsterdam"
    m.address.province = "Noord-Holland"
    m.price.amount = 850000
    m.areas.living = 120
    m.rooms.total = 5
    m.rooms.bedrooms = 3
    m.property_details.energy_label = "A"
    m.property_details.status = "beschikbaar"
    for k, v in overrides.items():
        setattr(m, k, v)
    return m


def test_from_listing_maps_all_fields():
    result = ListingResponse.from_listing(_make_listing())
    assert result.global_id == 12345678
    assert result.tiny_id == "abc123"
    assert result.url == "https://www.funda.nl/koop/amsterdam/huis-12345678/"
    assert result.street == "Herengracht"
    assert result.house_number == "1"
    assert result.house_number_suffix is None
    assert result.zip_code == "1015BZ"
    assert result.city == "Amsterdam"
    assert result.province == "Noord-Holland"
    assert result.asking_price == 850000
    assert result.living_area_m2 == 120
    assert result.rooms == 5
    assert result.bedrooms == 3
    assert result.energy_label == "A"
    assert result.publication_date == "2024-01-15"
    assert result.status == "beschikbaar"
    assert result.offering_type == "koop"


def test_from_listing_nullable_fields_become_none():
    m = _make_listing()
    m.global_id = None
    m.price.amount = None
    m.areas.living = None
    m.rooms.bedrooms = None
    result = ListingResponse.from_listing(m)
    assert result.global_id is None
    assert result.asking_price is None
    assert result.living_area_m2 is None
    assert result.bedrooms is None


def test_from_listing_falls_back_to_title_when_no_street_name():
    m = _make_listing()
    # Simulate detail-parser listing that uses title instead of street_name
    del m.address.street_name  # remove so getattr returns default None
    m.address.title = "Keizersgracht"
    result = ListingResponse.from_listing(m)
    assert result.street == "Keizersgracht"
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cd funda-proxy
pip install -r requirements-dev.txt
pytest tests/test_models.py -v
```

Expected: `ImportError: No module named 'models'`

- [ ] **Step 3: Write `funda-proxy/models.py`**

```python
from pydantic import BaseModel


class ListingResponse(BaseModel):
    global_id: int | None = None
    tiny_id: str | None = None
    url: str | None = None
    street: str | None = None
    house_number: str | None = None
    house_number_suffix: str | None = None
    zip_code: str | None = None
    city: str | None = None
    province: str | None = None
    asking_price: int | None = None
    living_area_m2: int | None = None
    rooms: int | None = None
    bedrooms: int | None = None
    energy_label: str | None = None
    publication_date: str | None = None
    status: str | None = None
    offering_type: str | None = None

    @classmethod
    def from_listing(cls, listing) -> "ListingResponse":
        addr = listing.address
        # search parser sets street_name; detail parser sets title
        street = getattr(addr, "street_name", None) or getattr(addr, "title", None)
        return cls(
            global_id=listing.global_id,
            tiny_id=listing.tiny_id,
            url=getattr(listing.urls, "full", None),
            street=street,
            house_number=getattr(addr, "house_number", None),
            house_number_suffix=getattr(addr, "house_number_suffix", None),
            zip_code=getattr(addr, "postcode", None),
            city=getattr(addr, "city", None),
            province=getattr(addr, "province", None),
            asking_price=getattr(listing.price, "amount", None),
            living_area_m2=getattr(listing.areas, "living", None),
            rooms=getattr(listing.rooms, "total", None),
            bedrooms=getattr(listing.rooms, "bedrooms", None),
            energy_label=getattr(listing.property_details, "energy_label", None),
            publication_date=listing.publication_date,
            status=getattr(listing.property_details, "status", None),
            offering_type=listing.offering_type,
        )
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
pytest tests/test_models.py -v
```

Expected: 3 tests PASSED

- [ ] **Step 5: Commit**

```bash
git add funda-proxy/models.py funda-proxy/tests/test_models.py
git commit -m "feat(funda-proxy): add ListingResponse model with pyfunda mapping"
```

---

## Task 3: Funda client module

**Files:**
- Create: `funda-proxy/client.py`
- Create: `funda-proxy/tests/test_client.py`

`client.py` owns the singleton `Funda` instance. `get_client()` is called by the endpoints; it raises if called before startup (guards against misconfiguration). The `lifespan` async context manager is passed to FastAPI's app constructor.

- [ ] **Step 1: Write the failing test in `funda-proxy/tests/test_client.py`**

```python
import pytest
import client as client_module


def test_get_client_raises_before_initialization():
    saved = client_module._client
    client_module._client = None
    try:
        with pytest.raises(RuntimeError, match="not initialized"):
            client_module.get_client()
    finally:
        client_module._client = saved
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
pytest tests/test_client.py -v
```

Expected: `ImportError: No module named 'client'`

- [ ] **Step 3: Write `funda-proxy/client.py`**

```python
from contextlib import asynccontextmanager
from funda import Funda

_client: Funda | None = None


def get_client() -> Funda:
    if _client is None:
        raise RuntimeError("Funda client not initialized")
    return _client


@asynccontextmanager
async def lifespan(app):
    global _client
    _client = Funda()
    yield
    _client.close()
    _client = None
```

- [ ] **Step 4: Run test to confirm it passes**

```bash
pytest tests/test_client.py -v
```

Expected: 1 test PASSED

- [ ] **Step 5: Commit**

```bash
git add funda-proxy/client.py funda-proxy/tests/test_client.py
git commit -m "feat(funda-proxy): add Funda singleton client with lifespan"
```

---

## Task 4: FastAPI app and endpoints

**Files:**
- Create: `funda-proxy/main.py`
- Create: `funda-proxy/tests/test_endpoints.py`

Three endpoints:
- `GET /health` — liveness probe, no client needed
- `GET /search` — calls `client.search(location, **filters)`, returns `list[ListingResponse]`
- `GET /listings/{listing_id}` — calls `client.listing(listing_id)`, returns `ListingResponse`

Error mapping:
- `ListingNotFound` → 404
- Any other `FundaError` → 502

Tests patch two things: `client.Funda` (prevents real network call during lifespan startup) and `main.get_client` (controls what the endpoints see).

- [ ] **Step 1: Write failing tests in `funda-proxy/tests/test_endpoints.py`**

```python
import pytest
from unittest.mock import MagicMock, patch
from fastapi.testclient import TestClient
from funda.exceptions import ListingNotFound, FundaError


def _make_listing():
    m = MagicMock()
    m.global_id = 12345678
    m.tiny_id = "abc123"
    m.offering_type = "koop"
    m.publication_date = "2024-01-15"
    m.urls.full = "https://www.funda.nl/koop/amsterdam/huis-12345678/"
    m.address.street_name = "Herengracht"
    m.address.house_number = "1"
    m.address.house_number_suffix = None
    m.address.postcode = "1015BZ"
    m.address.city = "Amsterdam"
    m.address.province = "Noord-Holland"
    m.price.amount = 850000
    m.areas.living = 120
    m.rooms.total = 5
    m.rooms.bedrooms = 3
    m.property_details.energy_label = "A"
    m.property_details.status = "beschikbaar"
    return m


@pytest.fixture
def mock_funda():
    return MagicMock()


@pytest.fixture
def api(mock_funda):
    from main import app
    with patch("client.Funda", return_value=mock_funda):
        with patch("main.get_client", return_value=mock_funda):
            with TestClient(app) as c:
                yield c, mock_funda


def test_health(api):
    client, _ = api
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json() == {"status": "ok"}


def test_search_returns_listings(api):
    client, mock_funda = api
    mock_funda.search.return_value = [_make_listing()]
    resp = client.get("/search?location=amsterdam")
    assert resp.status_code == 200
    data = resp.json()
    assert len(data) == 1
    assert data[0]["city"] == "Amsterdam"
    assert data[0]["asking_price"] == 850000


def test_search_passes_all_filters_to_pyfunda(api):
    client, mock_funda = api
    mock_funda.search.return_value = []
    client.get("/search?location=amsterdam&min_price=200000&max_price=500000&min_area=50&max_area=120&page=2")
    mock_funda.search.assert_called_once_with(
        "amsterdam",
        min_price=200000,
        max_price=500000,
        min_area=50,
        max_area=120,
        page=2,
    )


def test_search_missing_location_returns_422(api):
    client, _ = api
    resp = client.get("/search")
    assert resp.status_code == 422


def test_search_funda_error_returns_502(api):
    client, mock_funda = api
    mock_funda.search.side_effect = FundaError("upstream failure")
    resp = client.get("/search?location=amsterdam")
    assert resp.status_code == 502
    assert "upstream failure" in resp.json()["detail"]


def test_get_listing_returns_listing(api):
    client, mock_funda = api
    mock_funda.listing.return_value = _make_listing()
    resp = client.get("/listings/12345678")
    assert resp.status_code == 200
    assert resp.json()["global_id"] == 12345678


def test_get_listing_not_found_returns_404(api):
    client, mock_funda = api
    mock_funda.listing.side_effect = ListingNotFound("not found")
    resp = client.get("/listings/99999999")
    assert resp.status_code == 404


def test_get_listing_funda_error_returns_502(api):
    client, mock_funda = api
    mock_funda.listing.side_effect = FundaError("api down")
    resp = client.get("/listings/12345678")
    assert resp.status_code == 502
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
pytest tests/test_endpoints.py -v
```

Expected: `ImportError: No module named 'main'`

- [ ] **Step 3: Write `funda-proxy/main.py`**

```python
from fastapi import FastAPI, HTTPException, Query
from funda.exceptions import FundaError, ListingNotFound
from client import lifespan, get_client
from models import ListingResponse

app = FastAPI(title="funda-proxy", lifespan=lifespan)


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/search", response_model=list[ListingResponse])
def search(
    location: str = Query(...),
    min_price: int | None = Query(None),
    max_price: int | None = Query(None),
    min_area: int | None = Query(None),
    max_area: int | None = Query(None),
    page: int = Query(0, ge=0),
):
    try:
        listings = get_client().search(
            location,
            min_price=min_price,
            max_price=max_price,
            min_area=min_area,
            max_area=max_area,
            page=page,
        )
        return [ListingResponse.from_listing(l) for l in listings]
    except FundaError as e:
        raise HTTPException(status_code=502, detail=str(e))


@app.get("/listings/{listing_id}", response_model=ListingResponse)
def get_listing(listing_id: str):
    try:
        listing = get_client().listing(listing_id)
    except ListingNotFound as e:
        raise HTTPException(status_code=404, detail=str(e))
    except FundaError as e:
        raise HTTPException(status_code=502, detail=str(e))
    if listing is None:
        raise HTTPException(status_code=404, detail="Listing not found")
    return ListingResponse.from_listing(listing)
```

- [ ] **Step 4: Run all tests to confirm they pass**

```bash
pytest tests/ -v
```

Expected: 12 tests PASSED (3 model + 1 client + 8 endpoint)

- [ ] **Step 5: Commit**

```bash
git add funda-proxy/main.py funda-proxy/tests/test_endpoints.py
git commit -m "feat(funda-proxy): add FastAPI app with /health, /search, /listings endpoints"
```

---

## Task 5: Docker-compose integration

**Files:**
- Modify: `docker-compose.yml` (root)

Add `funda-proxy` as a service. It has no dependencies on other services — the Java backend calls it, not the other way around.

- [ ] **Step 1: Add the `funda-proxy` service to `docker-compose.yml`**

Current content:
```yaml
include:
  - hermes-backend/docker-compose.yml

services:
  frontend:
    build: ./hermes-frontend
    ports:
      - "4200:80"
    environment:
      BACKEND_URL: http://backend:8080
    depends_on:
      - backend
```

New content (add `funda-proxy` service):
```yaml
include:
  - hermes-backend/docker-compose.yml

services:
  frontend:
    build: ./hermes-frontend
    ports:
      - "4200:80"
    environment:
      BACKEND_URL: http://backend:8080
    depends_on:
      - backend

  funda-proxy:
    build: ./funda-proxy
    ports:
      - "8001:8000"
    restart: unless-stopped
```

- [ ] **Step 2: Verify the compose file parses without errors**

```bash
docker compose config --quiet
```

Expected: no output (silent success)

- [ ] **Step 3: Commit**

```bash
git add docker-compose.yml
git commit -m "feat(funda-proxy): add funda-proxy service to docker-compose"
```

---

## Self-Review

**Spec coverage:**
- ✅ Two endpoints (`/search`, `/listings/{id}`) — Tasks 4
- ✅ `/health` endpoint — Task 4
- ✅ `ListingResponse` flat Pydantic model with all specified fields — Task 2
- ✅ Singleton `Funda` client with startup/shutdown lifecycle — Task 3
- ✅ `ListingNotFound` → 404, `FundaError` → 502, missing `location` → 422 — Task 4
- ✅ `Dockerfile` with slim Python base image — Task 1
- ✅ docker-compose service on port 8001 — Task 5
- ✅ `street_name` / `title` fallback for address — Task 2

**Placeholder scan:** None found. All steps contain complete code.

**Type consistency:** `ListingResponse.from_listing()` defined in Task 2 and used in Task 4 (`main.py`). `get_client()` defined in Task 3 and imported in Task 4. `lifespan` defined in Task 3 and imported in Task 4. All consistent.
