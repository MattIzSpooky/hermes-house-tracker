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
