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
    m.areas.plot = 85
    m.rooms.total = 5
    m.rooms.bedrooms = 3
    m.property_details.energy_label = "A"
    m.property_details.status = "beschikbaar"
    m.description = "Ruim appartement met balkon"
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


def _make_change():
    m = MagicMock()
    m.price = 350000
    m.human_price = "€ 350.000 k.k."
    m.status = "asking_price"
    m.source = "walter"
    m.date = "15 mei 2024"
    m.timestamp = "2024-05-15T00:00:00+00:00"
    return m


def test_get_price_history_returns_changes(api):
    client, mock_funda = api
    history = MagicMock()
    history.changes = [_make_change()]
    mock_funda.price_history.return_value = history
    resp = client.get("/listings/12345678/price-history")
    assert resp.status_code == 200
    data = resp.json()
    assert len(data) == 1
    assert data[0]["price"] == 350000
    assert data[0]["status"] == "asking_price"
    assert data[0]["date"] == "2024-05-15"
    assert "2024-05-15" in data[0]["timestamp"]


def test_get_price_history_not_found_returns_404(api):
    client, mock_funda = api
    from funda.exceptions import ListingNotFound
    mock_funda.price_history.side_effect = ListingNotFound("not found")
    resp = client.get("/listings/99999999/price-history")
    assert resp.status_code == 404


def test_get_price_history_funda_error_returns_502(api):
    client, mock_funda = api
    from funda.exceptions import FundaError
    mock_funda.price_history.side_effect = FundaError("upstream down")
    resp = client.get("/listings/12345678/price-history")
    assert resp.status_code == 502
