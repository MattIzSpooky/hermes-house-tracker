import dateparser
from datetime import date as _Date, datetime as _DateTime, timezone
from pydantic import BaseModel


class ListingResponse(BaseModel):
    global_id: int | None = None
    tiny_id: str | None = None
    url: str | None = None
    street: str | None = None
    house_number: str | None = None
    house_number_suffix: str | None = None
    zip_code: str | None = None
    city: str | None = None
    province: str | None = None
    asking_price: int | None = None
    living_area_m2: int | None = None
    rooms: int | None = None
    bedrooms: int | None = None
    energy_label: str | None = None
    publication_date: str | None = None
    status: str | None = None
    offering_type: str | None = None

    @classmethod
    def from_listing(cls, listing) -> "ListingResponse":
        addr = listing.address
        # search parser sets street_name; detail parser sets title
        street = getattr(addr, "street_name", None) or getattr(addr, "title", None)
        return cls(
            global_id=listing.global_id,
            tiny_id=listing.tiny_id,
            url=getattr(listing.urls, "full", None),
            street=street,
            house_number=getattr(addr, "house_number", None),
            house_number_suffix=getattr(addr, "house_number_suffix", None),
            zip_code=getattr(addr, "postcode", None),
            city=getattr(addr, "city", None),
            province=getattr(addr, "province", None),
            asking_price=getattr(listing.price, "amount", None),
            living_area_m2=getattr(listing.areas, "living", None),
            rooms=getattr(listing.rooms, "total", None),
            bedrooms=getattr(listing.rooms, "bedrooms", None),
            energy_label=getattr(listing.property_details, "energy_label", None),
            publication_date=listing.publication_date,
            status=getattr(listing.property_details, "status", None),
            offering_type=listing.offering_type,
        )


class PriceChangeResponse(BaseModel):
    price: int | None = None
    human_price: str | None = None
    status: str | None = None
    source: str | None = None
    date: _Date | None = None
    timestamp: _DateTime | None = None

    @classmethod
    def from_change(cls, change) -> "PriceChangeResponse":
        return cls(
            price=change.price,
            human_price=change.human_price,
            status=change.status,
            source=change.source,
            date=_parse_date(change.date),
            timestamp=_parse_timestamp(change.timestamp),
        )


def _parse_date(raw: str | None) -> _Date | None:
    if not raw:
        return None
    try:
        parsed = dateparser.parse(raw, languages=["nl"])
        return parsed.date() if parsed else None
    except Exception:
        return None


def _parse_timestamp(raw: str | None) -> _DateTime | None:
    if not raw:
        return None
    try:
        dt = _DateTime.fromisoformat(raw)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return dt
    except Exception:
        return None
