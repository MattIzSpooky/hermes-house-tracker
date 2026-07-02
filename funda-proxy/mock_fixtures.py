"""Fixed set of realistic Dutch listings served by MockFunda.

Addresses are real (so the geocoding pipeline can resolve real
coordinates); everything else is fabricated. Global IDs live in the
90000000 range, well outside any real Funda listing ID, so they can't
collide with real data.
"""

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


def _listing(
    global_id: int,
    *,
    street_name: str,
    house_number: str,
    postcode: str,
    city: str,
    province: str,
    price: int,
    living: int,
    plot: int | None,
    rooms: int,
    bedrooms: int,
    energy_label: str,
    status: str,
    description: str,
) -> Listing:
    slug = street_name.lower().replace(" ", "-")
    return Listing(
        global_id=global_id,
        offering_type="koop",
        address=Address(
            street_name=street_name,
            house_number=house_number,
            house_number_suffix=None,
            postcode=postcode,
            city=city,
            province=province,
        ),
        price=Price(amount=price, offering_type="koop"),
        areas=Areas(living=living, plot=plot),
        rooms=Rooms(total=rooms, bedrooms=bedrooms),
        property_details=PropertyDetails(energy_label=energy_label, status=status),
        urls=Urls(
            full=f"https://www.funda.nl/koop/{city.lower()}/huis-{global_id}-{slug}/"
        ),
        description=description,
        publication_date="2024-01-15",
    )


MOCK_LISTINGS: list[Listing] = [
    _listing(
        90000001,
        street_name="Prinsengracht",
        house_number="263",
        postcode="1016GV",
        city="Amsterdam",
        province="Noord-Holland",
        price=750000,
        living=95,
        plot=None,
        rooms=4,
        bedrooms=2,
        energy_label="B",
        status="beschikbaar",
        description="Karakteristiek grachtenpand appartement met balkon.",
    ),
    _listing(
        90000002,
        street_name="Oudegracht",
        house_number="158",
        postcode="3511AZ",
        city="Utrecht",
        province="Utrecht",
        price=495000,
        living=78,
        plot=None,
        rooms=3,
        bedrooms=2,
        energy_label="C",
        status="beschikbaar",
        description="Sfeervolle woning aan de Oudegracht.",
    ),
    _listing(
        90000003,
        street_name="Coolsingel",
        house_number="40",
        postcode="3011AD",
        city="Rotterdam",
        province="Zuid-Holland",
        price=625000,
        living=110,
        plot=60,
        rooms=5,
        bedrooms=3,
        energy_label="A",
        status="beschikbaar",
        description="Modern herenhuis in het centrum.",
    ),
    _listing(
        90000004,
        street_name="Stratumseind",
        house_number="10",
        postcode="5611ET",
        city="Eindhoven",
        province="Noord-Brabant",
        price=385000,
        living=65,
        plot=None,
        rooms=3,
        bedrooms=1,
        energy_label="D",
        status="beschikbaar",
        description="Compact appartement dichtbij het centrum.",
    ),
    _listing(
        90000005,
        street_name="Grote Markt",
        house_number="1",
        postcode="2011RD",
        city="Haarlem",
        province="Noord-Holland",
        price=899000,
        living=150,
        plot=120,
        rooms=6,
        bedrooms=4,
        energy_label="A",
        status="onder bod",
        description="Ruime eengezinswoning aan de Grote Markt.",
    ),
]


MOCK_PRICE_HISTORIES: dict[str, PriceHistory] = {
    "90000001": PriceHistory(
        changes=(
            PriceChange(
                price=795000,
                human_price="€ 795.000 k.k.",
                source="funda",
                status="asking_price",
                date="15 januari 2024",
                timestamp="2024-01-15T00:00:00+00:00",
            ),
            PriceChange(
                price=750000,
                human_price="€ 750.000 k.k.",
                source="funda",
                status="asking_price",
                date="1 maart 2024",
                timestamp="2024-03-01T00:00:00+00:00",
            ),
        )
    ),
    "90000003": PriceHistory(
        changes=(
            PriceChange(
                price=625000,
                human_price="€ 625.000 k.k.",
                source="funda",
                status="asking_price",
                date="10 februari 2024",
                timestamp="2024-02-10T00:00:00+00:00",
            ),
        )
    ),
}
