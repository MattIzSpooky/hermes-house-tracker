from contextlib import asynccontextmanager
from funda import Funda
from telemetry import setup_logging

_client: Funda | None = None


def get_client() -> Funda:
    if _client is None:
        raise RuntimeError("Funda client not initialized")
    return _client


@asynccontextmanager
async def lifespan(app):
    global _client
    # Must run here — uvicorn overwrites root logger handlers during startup,
    # so we re-apply our JSON + OTel bridge after it finishes.
    setup_logging()
    _client = Funda()
    yield
    _client.close()
    _client = None
