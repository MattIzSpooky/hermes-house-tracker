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
                if l.address.city and l.address.city.casefold() == needle
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
