"""One-off generator for funda-proxy/mock_data/{listings,price_histories}.csv.

Not imported by the running service — mock_fixtures.py reads the CSVs this
script produces. Re-run by hand (`python scripts/generate_mock_data.py` from
funda-proxy/) only if the curated city/street table or generation logic
below changes; the fixed random seed makes output reproducible.
"""

import csv
import random
from pathlib import Path

random.seed(42)

# (street_name, postcode, province) per city, real Dutch streets so the
# geocoding pipeline can resolve plausible coordinates.
CITIES: dict[str, tuple[list[tuple[str, str, str]], int]] = {
    "Amsterdam": (
        [
            ("Prinsengracht", "1016GV", "Noord-Holland"),
            ("Keizersgracht", "1015CJ", "Noord-Holland"),
            ("Vondelstraat", "1054GD", "Noord-Holland"),
            ("Overtoom", "1054HN", "Noord-Holland"),
            ("Ferdinand Bolstraat", "1072LM", "Noord-Holland"),
        ],
        650000,
    ),
    "Utrecht": (
        [
            ("Oudegracht", "3511AZ", "Utrecht"),
            ("Neude", "3512JJ", "Utrecht"),
            ("Biltstraat", "3572AR", "Utrecht"),
            ("Amsterdamsestraatweg", "3513AB", "Utrecht"),
            ("Twijnstraat", "3511ZK", "Utrecht"),
        ],
        500000,
    ),
    "Rotterdam": (
        [
            ("Coolsingel", "3011AD", "Zuid-Holland"),
            ("Witte de Withstraat", "3012BM", "Zuid-Holland"),
            ("Nieuwe Binnenweg", "3014GA", "Zuid-Holland"),
            ("Meent", "3011JJ", "Zuid-Holland"),
            ("Kruiskade", "3012EE", "Zuid-Holland"),
        ],
        425000,
    ),
    "Eindhoven": (
        [
            ("Stratumseind", "5611ET", "Noord-Brabant"),
            ("Kruisstraat", "5612AJ", "Noord-Brabant"),
            ("Vestdijk", "5611CA", "Noord-Brabant"),
            ("Woenselse Markt", "5621CS", "Noord-Brabant"),
            ("Fuutlaan", "5613AB", "Noord-Brabant"),
        ],
        400000,
    ),
    "Haarlem": (
        [
            ("Grote Markt", "2011RD", "Noord-Holland"),
            ("Barteljorisstraat", "2011RA", "Noord-Holland"),
            ("Kruisstraat", "2011PV", "Noord-Holland"),
            ("Zijlstraat", "2011TL", "Noord-Holland"),
            ("Nieuwe Groenmarkt", "2011TW", "Noord-Holland"),
        ],
        475000,
    ),
    "Weert": (
        [
            ("Nieuwstraat", "6001EM", "Limburg"),
            ("Hegstraat", "6001CX", "Limburg"),
            ("Wilhelminasingel", "6001GS", "Limburg"),
            ("Beekstraat", "6001BB", "Limburg"),
            ("Emmasingel", "6001BT", "Limburg"),
        ],
        325000,
    ),
    "Groningen": (
        [
            ("Grote Markt", "9711LV", "Groningen"),
            ("Herestraat", "9711LC", "Groningen"),
            ("Oude Ebbingestraat", "9712HA", "Groningen"),
            ("Vismarkt", "9712CB", "Groningen"),
            ("Folkingestraat", "9711JW", "Groningen"),
        ],
        350000,
    ),
    "Maastricht": (
        [
            ("Vrijthof", "6211LD", "Limburg"),
            ("Grote Staat", "6211CT", "Limburg"),
            ("Wycker Brugstraat", "6221ED", "Limburg"),
            ("Stokstraat", "6211GP", "Limburg"),
            ("Rechtstraat", "6221EG", "Limburg"),
        ],
        375000,
    ),
    "Tilburg": (
        [
            ("Heuvel", "5038CS", "Noord-Brabant"),
            ("Piusstraat", "5038WP", "Noord-Brabant"),
            ("Korte Heuvel", "5038CT", "Noord-Brabant"),
            ("NS-plein", "5014DA", "Noord-Brabant"),
            ("Stationsstraat", "5038EA", "Noord-Brabant"),
        ],
        340000,
    ),
    "Nijmegen": (
        [
            ("Grote Markt", "6511KB", "Gelderland"),
            ("Lange Hezelstraat", "6511CD", "Gelderland"),
            ("Molenstraat", "6511EA", "Gelderland"),
            ("Hertogstraat", "6511TA", "Gelderland"),
            ("Bloemerstraat", "6511EM", "Gelderland"),
        ],
        390000,
    ),
}

ENERGY_LABELS = ["A", "B", "C", "D", "E", "F", "G"]
ENERGY_WEIGHTS = [25, 20, 20, 15, 10, 6, 4]
STATUSES = [
    "beschikbaar",
    "beschikbaar",
    "beschikbaar",
    "beschikbaar",
    "onder bod",
    "verkocht onder voorbehoud",
]
DESCRIPTION_TEMPLATES = [
    "Sfeervolle woning aan de {street}.",
    "Ruim appartement met balkon aan de {street}.",
    "Karakteristiek pand in het centrum, {street}.",
    "Modern herenhuis aan de {street}.",
    "Compacte woning dichtbij het centrum, {street}.",
    "Lichte bovenwoning aan de {street}.",
]
MONTHS = [
    "januari", "februari", "maart", "april", "mei", "juni",
    "juli", "augustus", "september", "oktober", "november", "december",
]

OUTPUT_DIR = Path(__file__).resolve().parent.parent / "mock_data"

LISTING_FIELDS = [
    "global_id", "street_name", "house_number", "house_number_suffix",
    "postcode", "city", "province", "price", "living_area", "plot_area",
    "rooms", "bedrooms", "energy_label", "status", "description",
    "offering_type", "publication_date",
]
PRICE_HISTORY_FIELDS = [
    "global_id", "price", "human_price", "source", "status", "date", "timestamp",
]


def _format_price(price: int) -> str:
    return f"€ {price:,}".replace(",", ".") + " k.k."


def _price_history_rows(global_id: int, asking_price: int) -> list[dict]:
    count = random.randint(1, 3)
    prices = {asking_price}
    while len(prices) < count:
        prices.add(asking_price + random.choice([10000, 20000, 30000, 40000]))
    ordered_prices = sorted(prices, reverse=True)[:count]
    ordered_prices[-1] = asking_price

    rows = []
    for price in ordered_prices:
        month_index = random.randint(0, 11)
        day = random.randint(1, 28)
        rows.append(
            {
                "global_id": global_id,
                "price": price,
                "human_price": _format_price(price),
                "source": "funda",
                "status": "asking_price",
                "date": f"{day} {MONTHS[month_index]} 2024",
                "timestamp": f"2024-{month_index + 1:02d}-{day:02d}T00:00:00+00:00",
            }
        )
    return rows


def generate() -> tuple[list[dict], list[dict]]:
    listing_rows: list[dict] = []
    price_history_rows: list[dict] = []
    global_id = 90000001

    for city, (streets, base_price) in CITIES.items():
        for street_name, postcode, province in streets:
            for i in range(10):
                house_number = str(1 + i * 2)
                living_area = random.randint(45, 180)
                plot_area = random.randint(30, 250) if random.random() < 0.5 else None
                rooms = max(2, living_area // 30)
                bedrooms = max(1, rooms - 1)
                energy_label = random.choices(ENERGY_LABELS, weights=ENERGY_WEIGHTS, k=1)[0]
                status = random.choice(STATUSES)
                price = base_price + random.randint(-75000, 150000)
                description = random.choice(DESCRIPTION_TEMPLATES).format(street=street_name)

                listing_rows.append(
                    {
                        "global_id": global_id,
                        "street_name": street_name,
                        "house_number": house_number,
                        "house_number_suffix": "",
                        "postcode": postcode,
                        "city": city,
                        "province": province,
                        "price": price,
                        "living_area": living_area,
                        "plot_area": plot_area if plot_area is not None else "",
                        "rooms": rooms,
                        "bedrooms": bedrooms,
                        "energy_label": energy_label,
                        "status": status,
                        "description": description,
                        "offering_type": "koop",
                        "publication_date": "2024-01-15",
                    }
                )
                price_history_rows.extend(_price_history_rows(global_id, price))
                global_id += 1

    return listing_rows, price_history_rows


def write_csv(path: Path, fieldnames: list[str], rows: list[dict]) -> None:
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


if __name__ == "__main__":
    listings, price_histories = generate()
    OUTPUT_DIR.mkdir(exist_ok=True)
    write_csv(OUTPUT_DIR / "listings.csv", LISTING_FIELDS, listings)
    write_csv(OUTPUT_DIR / "price_histories.csv", PRICE_HISTORY_FIELDS, price_histories)
    print(f"Wrote {len(listings)} listings and {len(price_histories)} price-history rows to {OUTPUT_DIR}")
