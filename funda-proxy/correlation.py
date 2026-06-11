from contextvars import ContextVar

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request

_correlation_id_var: ContextVar[str] = ContextVar("correlation_id", default="")

_HEADER = "X-Correlation-ID"


class CorrelationIdMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        correlation_id = request.headers.get(_HEADER, "")
        token = _correlation_id_var.set(correlation_id)
        try:
            return await call_next(request)
        finally:
            _correlation_id_var.reset(token)
