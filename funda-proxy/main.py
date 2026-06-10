from fastapi import FastAPI, HTTPException, Query
from funda.exceptions import FundaError, ListingNotFound
from client import lifespan, get_client
from models import ListingResponse

app = FastAPI(title="funda-proxy", lifespan=lifespan)


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/search", response_model=list[ListingResponse])
def search(
    location: str = Query(...),
    min_price: int | None = Query(None),
    max_price: int | None = Query(None),
    min_area: int | None = Query(None),
    max_area: int | None = Query(None),
    page: int = Query(0, ge=0),
):
    try:
        listings = get_client().search(
            location,
            min_price=min_price,
            max_price=max_price,
            min_area=min_area,
            max_area=max_area,
            page=page,
        )
        return [ListingResponse.from_listing(l) for l in listings]
    except FundaError as e:
        raise HTTPException(status_code=502, detail=str(e))


@app.get("/listings/{listing_id}", response_model=ListingResponse)
def get_listing(listing_id: str):
    try:
        listing = get_client().listing(listing_id)
    except ListingNotFound as e:
        raise HTTPException(status_code=404, detail=str(e))
    except FundaError as e:
        raise HTTPException(status_code=502, detail=str(e))
    if listing is None:
        raise HTTPException(status_code=404, detail="Listing not found")
    return ListingResponse.from_listing(listing)
