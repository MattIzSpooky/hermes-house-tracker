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


def test_search_does_not_cross_match_city_names_that_are_substrings(mock_funda):
    # "Nederweert" contains "weert" — the filter must not treat that as a match.
    weert_ids = {l.global_id for l in mock_funda.search("Weert", page=0)} | {
        l.global_id for l in mock_funda.search("Weert", page=1)
    } | {l.global_id for l in mock_funda.search("Weert", page=2)} | {
        l.global_id for l in mock_funda.search("Weert", page=3)
    }
    assert mock_funda.search("Weert", page=4) == []
    nederweert = mock_funda.search("Nederweert")
    assert len(nederweert) == 15
    assert all(l.address.city == "Nederweert" for l in nederweert)
    assert weert_ids.isdisjoint({l.global_id for l in nederweert})


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
    for global_id in ("90000001", "90000600"):
        history = mock_funda.price_history(global_id)
        assert 1 <= len(history.changes) <= 3


def test_price_history_unknown_id_raises_not_found(mock_funda):
    with pytest.raises(ListingNotFound):
        mock_funda.price_history("00000000")
