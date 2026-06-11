from starlette.applications import Starlette
from starlette.responses import JSONResponse
from starlette.routing import Route
from starlette.testclient import TestClient

from correlation import CorrelationIdMiddleware, _correlation_id_var


def _read_endpoint(request):
    return JSONResponse({"correlation_id": _correlation_id_var.get("")})


_app = Starlette(routes=[Route("/", _read_endpoint)])
_app.add_middleware(CorrelationIdMiddleware)
_client = TestClient(_app)


def test_sets_correlation_id_from_header():
    resp = _client.get("/", headers={"X-Correlation-ID": "my-id"})
    assert resp.json()["correlation_id"] == "my-id"


def test_empty_string_when_header_absent():
    resp = _client.get("/")
    assert resp.json()["correlation_id"] == ""


def test_correlation_id_not_leaked_between_requests():
    # First request sets a correlation ID
    resp1 = _client.get("/", headers={"X-Correlation-ID": "first-id"})
    assert resp1.json()["correlation_id"] == "first-id"

    # Second request has no header — must not see the previous value
    resp2 = _client.get("/")
    assert resp2.json()["correlation_id"] == ""
