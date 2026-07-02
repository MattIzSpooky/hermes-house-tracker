import csv
from collections import Counter
from pathlib import Path

MOCK_DATA_DIR = Path(__file__).resolve().parent.parent / "mock_data"

EXPECTED_CITIES = {
    "Amsterdam", "Utrecht", "Rotterdam", "Eindhoven", "Haarlem",
    "Weert", "Groningen", "Maastricht", "Tilburg", "Nijmegen",
    "Nederweert", "Stramproy",
}


def _read_listings():
    with (MOCK_DATA_DIR / "listings.csv").open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def _read_price_histories():
    with (MOCK_DATA_DIR / "price_histories.csv").open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def test_listings_csv_has_600_rows():
    assert len(_read_listings()) == 600


def test_listings_csv_global_ids_are_unique_and_sequential():
    ids = [int(row["global_id"]) for row in _read_listings()]
    assert sorted(ids) == list(range(90000001, 90000601))


def test_listings_csv_only_contains_expected_cities():
    cities = {row["city"] for row in _read_listings()}
    assert cities == EXPECTED_CITIES


def test_listings_csv_has_50_listings_per_city():
    counts = Counter(row["city"] for row in _read_listings())
    assert all(count == 50 for count in counts.values())


def test_price_histories_reference_only_known_listing_ids():
    listing_ids = {row["global_id"] for row in _read_listings()}
    history_ids = {row["global_id"] for row in _read_price_histories()}
    assert history_ids <= listing_ids


def test_every_listing_has_one_to_three_price_history_rows():
    counts = Counter(row["global_id"] for row in _read_price_histories())
    listing_ids = {row["global_id"] for row in _read_listings()}
    assert set(counts.keys()) == listing_ids
    assert all(1 <= n <= 3 for n in counts.values())
