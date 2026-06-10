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
