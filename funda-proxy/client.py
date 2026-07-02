import logging
import os
from contextlib import asynccontextmanager

from funda import Funda
from mock_client import MockFunda
from telemetry import setup_logging

logger = logging.getLogger(__name__)

_client: "Funda | MockFunda | None" = None


def get_client() -> "Funda | MockFunda":
    if _client is None:
        raise RuntimeError("Funda client not initialized")
    return _client


def _mock_mode_enabled() -> bool:
    return os.getenv("FUNDA_MOCK_MODE", "false").strip().lower() == "true"


@asynccontextmanager
async def lifespan(app):
    global _client
    # Must run here — uvicorn overwrites root logger handlers during startup,
    # so we re-apply our JSON + OTel bridge after it finishes.
    setup_logging()
    if _mock_mode_enabled():
        logger.warning(
            "FUNDA_MOCK_MODE is enabled - serving fixture listings, not calling Funda"
        )
        _client = MockFunda()
    else:
        _client = Funda()
    yield
    _client.close()
    _client = None
