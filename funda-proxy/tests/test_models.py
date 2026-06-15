from datetime import date, datetime, timezone
from unittest.mock import MagicMock

from models import ListingResponse, PriceChangeResponse


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
    m.areas.plot = 85
    m.rooms.total = 5
    m.rooms.bedrooms = 3
    m.property_details.energy_label = "A"
    m.property_details.status = "beschikbaar"
    m.description = "Ruim appartement met balkon"
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
    assert result.description == "Ruim appartement met balkon"
    assert result.plot_area_m2 == 85
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


def _make_change(**overrides):
    m = MagicMock()
    m.price = 350000
    m.human_price = "€ 350.000 k.k."
    m.status = "asking_price"
    m.source = "walter"
    m.date = "15 mei 2024"
    m.timestamp = "2024-05-15T00:00:00+00:00"
    for k, v in overrides.items():
        setattr(m, k, v)
    return m


def test_price_change_response_maps_all_fields():
    result = PriceChangeResponse.from_change(_make_change())
    assert result.price == 350000
    assert result.human_price == "€ 350.000 k.k."
    assert result.status == "asking_price"
    assert result.source == "walter"
    assert result.date == date(2024, 5, 15)
    assert result.timestamp == datetime(2024, 5, 15, 0, 0, 0, tzinfo=timezone.utc)


def test_price_change_response_null_date_becomes_none():
    result = PriceChangeResponse.from_change(_make_change(date=None))
    assert result.date is None


def test_price_change_response_null_timestamp_becomes_none():
    result = PriceChangeResponse.from_change(_make_change(timestamp=None))
    assert result.timestamp is None


def test_price_change_response_unparseable_date_becomes_none():
    result = PriceChangeResponse.from_change(_make_change(date="not a date"))
    assert result.date is None


def test_price_change_response_unparseable_timestamp_becomes_none():
    result = PriceChangeResponse.from_change(_make_change(timestamp="not a timestamp"))
    assert result.timestamp is None
