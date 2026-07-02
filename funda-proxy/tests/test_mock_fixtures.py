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
