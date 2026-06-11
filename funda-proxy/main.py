import logging

from fastapi import FastAPI, HTTPException, Query
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor

from funda.exceptions import FundaError, ListingNotFound
from client import lifespan, get_client
from models import ListingResponse
from telemetry import configure_telemetry
from correlation import CorrelationIdMiddleware

configure_telemetry()

logger = logging.getLogger(__name__)

app = FastAPI(title="funda-proxy", lifespan=lifespan)
FastAPIInstrumentor.instrument_app(app)
app.add_middleware(CorrelationIdMiddleware)


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
    logger.info("search location=%s page=%d", location, page)
    try:
        listings = get_client().search(
            location,
            min_price=min_price,
            max_price=max_price,
            min_area=min_area,
            max_area=max_area,
            page=page,
        )
        logger.info("search location=%s returned %d listings", location, len(listings))
        return [ListingResponse.from_listing(l) for l in listings]
    except FundaError as e:
        logger.warning("search location=%s failed: %s", location, e)
        raise HTTPException(status_code=502, detail=str(e))


@app.get("/listings/{listing_id}", response_model=ListingResponse)
def get_listing(listing_id: str):
    logger.info("get_listing id=%s", listing_id)
    try:
        listing = get_client().listing(listing_id)
    except ListingNotFound as e:
        logger.warning("get_listing id=%s not found: %s", listing_id, e)
        raise HTTPException(status_code=404, detail=str(e))
    except FundaError as e:
        logger.warning("get_listing id=%s failed: %s", listing_id, e)
        raise HTTPException(status_code=502, detail=str(e))
    if listing is None:
        raise HTTPException(status_code=404, detail="Listing not found")
    logger.info("get_listing id=%s success", listing_id)
    return ListingResponse.from_listing(listing)
