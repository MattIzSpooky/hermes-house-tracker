import os

from opentelemetry import metrics, trace
from opentelemetry.exporter.otlp.proto.http.metric_exporter import OTLPMetricExporter
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.instrumentation.logging import LoggingInstrumentor
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader
from opentelemetry.sdk.resources import Resource, SERVICE_NAME
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor

_configured = False


def configure_telemetry() -> None:
    global _configured
    if _configured:
        return
    _configured = True

    service_name = os.getenv("OTEL_SERVICE_NAME", "funda-proxy")
    resource = Resource({SERVICE_NAME: service_name})
    endpoint = os.getenv("OTEL_EXPORTER_OTLP_ENDPOINT")

    # Traces
    tracer_provider = TracerProvider(resource=resource)
    if endpoint:
        span_exporter = OTLPSpanExporter(endpoint=endpoint.rstrip("/") + "/v1/traces")
        tracer_provider.add_span_processor(BatchSpanProcessor(span_exporter))
    trace.set_tracer_provider(tracer_provider)

    # Metrics
    if endpoint:
        metric_exporter = OTLPMetricExporter(endpoint=endpoint.rstrip("/") + "/v1/metrics")
        meter_provider = MeterProvider(
            resource=resource,
            metric_readers=[PeriodicExportingMetricReader(metric_exporter, export_interval_millis=60_000)],
        )
        metrics.set_meter_provider(meter_provider)

    # Inject trace/span IDs into every log record
    LoggingInstrumentor().instrument(set_logging_format=True)
