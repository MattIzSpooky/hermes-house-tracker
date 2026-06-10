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
