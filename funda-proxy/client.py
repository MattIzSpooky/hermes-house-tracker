from contextlib import asynccontextmanager
from funda import Funda

_client: Funda | None = None


def get_client() -> Funda:
    if _client is None:
        raise RuntimeError("Funda client not initialized")
    return _client


@asynccontextmanager
async def lifespan(app):
    global _client
    _client = Funda()
    yield
    _client.close()
    _client = None
