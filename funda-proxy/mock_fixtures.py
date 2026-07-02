"""Loads the fixed set of mock listings from funda-proxy/mock_data/*.csv.

See funda-proxy/scripts/generate_mock_data.py for how the CSVs are
produced; this module only reads them.
"""

import csv
from pathlib import Path

from funda.listing import (
    Address,
    Areas,
    Listing,
    Price,
    PriceChange,
    PriceHistory,
    PropertyDetails,
    Rooms,
    Urls,
)

_MOCK_DATA_DIR = Path(__file__).resolve().parent / "mock_data"
_LISTINGS_CSV = _MOCK_DATA_DIR / "listings.csv"
_PRICE_HISTORIES_CSV = _MOCK_DATA_DIR / "price_histories.csv"


def _int_or_none(value: str) -> int | None:
    return int(value) if value else None


def _load_listings() -> list[Listing]:
    listings = []
    with _LISTINGS_CSV.open(newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            global_id = int(row["global_id"])
            slug = row["street_name"].lower().replace(" ", "-")
            listings.append(
                Listing(
                    global_id=global_id,
                    offering_type=row["offering_type"],
                    address=Address(
                        street_name=row["street_name"],
                        house_number=row["house_number"],
                        house_number_suffix=row["house_number_suffix"] or None,
                        postcode=row["postcode"],
                        city=row["city"],
                        province=row["province"],
                    ),
                    price=Price(amount=int(row["price"]), offering_type=row["offering_type"]),
                    areas=Areas(
                        living=_int_or_none(row["living_area"]),
                        plot=_int_or_none(row["plot_area"]),
                    ),
                    rooms=Rooms(total=int(row["rooms"]), bedrooms=int(row["bedrooms"])),
                    property_details=PropertyDetails(
                        energy_label=row["energy_label"], status=row["status"]
                    ),
                    urls=Urls(
                        full=f"https://www.funda.nl/koop/{row['city'].lower()}/huis-{global_id}-{slug}/"
                    ),
                    description=row["description"],
                    publication_date=row["publication_date"],
                )
            )
    return listings


def _load_price_histories() -> dict[str, PriceHistory]:
    changes_by_id: dict[str, list[PriceChange]] = {}
    with _PRICE_HISTORIES_CSV.open(newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            changes_by_id.setdefault(row["global_id"], []).append(
                PriceChange(
                    price=int(row["price"]),
                    human_price=row["human_price"],
                    source=row["source"],
                    status=row["status"],
                    date=row["date"],
                    timestamp=row["timestamp"],
                )
            )
    return {
        global_id: PriceHistory(changes=tuple(changes))
        for global_id, changes in changes_by_id.items()
    }


MOCK_LISTINGS: list[Listing] = _load_listings()
MOCK_PRICE_HISTORIES: dict[str, PriceHistory] = _load_price_histories()
