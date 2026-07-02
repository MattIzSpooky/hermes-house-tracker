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


def test_search_page_one_returns_all_fixtures(mock_funda):
    # The real Java ScrapingWorker caller is 1-based and only ever requests
    # page=1, so this must return the same fixture set as the default page.
    results = mock_funda.search("anything", page=1)
    assert len(results) == 5
    assert {l.global_id for l in results} == {
        90000001, 90000002, 90000003, 90000004, 90000005,
    }


def test_search_page_beyond_first_returns_empty(mock_funda):
    assert mock_funda.search("anything", page=2) == []


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
