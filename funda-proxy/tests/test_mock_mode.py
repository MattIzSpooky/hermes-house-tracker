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
