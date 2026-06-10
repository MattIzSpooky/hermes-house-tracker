import logging
import os

from opentelemetry import trace
from opentelemetry.instrumentation.logging import LoggingInstrumentor
from opentelemetry.sdk.resources import Resource, SERVICE_NAME
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter

_configured = False


def configure_telemetry() -> None:
    global _configured
    if _configured:
        return
    _configured = True

    service_name = os.getenv("OTEL_SERVICE_NAME", "funda-proxy")
    resource = Resource({SERVICE_NAME: service_name})
    provider = TracerProvider(resource=resource)

    endpoint = os.getenv("OTEL_EXPORTER_OTLP_ENDPOINT")
    if endpoint:
        exporter = OTLPSpanExporter(endpoint=endpoint.rstrip("/") + "/v1/traces")
        provider.add_span_processor(BatchSpanProcessor(exporter))

    trace.set_tracer_provider(provider)
    # Injects otelTraceID / otelSpanID into every log record
    LoggingInstrumentor().instrument(set_logging_format=True)
