import logging
import os
import sys

from opentelemetry import metrics, trace
from opentelemetry._logs import set_logger_provider
from opentelemetry.exporter.otlp.proto.http._log_exporter import OTLPLogExporter
from opentelemetry.exporter.otlp.proto.http.metric_exporter import OTLPMetricExporter
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.instrumentation.logging import LoggingInstrumentor
from opentelemetry.sdk._logs import LoggerProvider
from opentelemetry.sdk._logs.export import BatchLogRecordProcessor
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader
from opentelemetry.sdk.resources import Resource, SERVICE_NAME
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from pythonjsonlogger.json import JsonFormatter

from correlation import _correlation_id_var

_configured = False
_logging_configured = False


class _OtelJsonFormatter(JsonFormatter):
    """JSON formatter that reads trace/span IDs directly from the active OTel span."""

    def add_fields(self, log_record, record, message_dict):
        super().add_fields(log_record, record, message_dict)
        span = trace.get_current_span()
        ctx = span.get_span_context()
        if ctx.is_valid:
            log_record["trace_id"] = format(ctx.trace_id, "032x")
            log_record["span_id"] = format(ctx.span_id, "016x")
        else:
            log_record["trace_id"] = ""
            log_record["span_id"] = ""
        log_record["correlation_id"] = _correlation_id_var.get("")


def setup_logging() -> None:
    """Must be called AFTER uvicorn has configured its own logging (i.e. in lifespan startup).
    Uvicorn calls logging.config.dictConfig() which strips all handlers, so anything set
    before that point is lost."""
    global _logging_configured
    if _logging_configured:
        return
    _logging_configured = True

    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(_OtelJsonFormatter("%(levelname)s %(name)s %(message)s"))
    root = logging.getLogger()
    root.setLevel(logging.INFO)
    root.handlers = [handler]

    # Bridge Python logging records to the OTel LoggerProvider for OTLP export to Loki.
    # set_logging_format=False so we keep our JSON formatter.
    LoggingInstrumentor().instrument(set_logging_format=False)


def configure_telemetry() -> None:
    """Sets up OTel SDK (tracer, meter, logger providers). Call at module load time.
    Does NOT configure Python logging — call setup_logging() separately from lifespan."""
    global _configured
    if _configured:
        return
    _configured = True

    service_name = os.getenv("OTEL_SERVICE_NAME", "funda-proxy")
    resource = Resource({SERVICE_NAME: service_name})
    endpoint = os.getenv("OTEL_EXPORTER_OTLP_ENDPOINT")
    base = endpoint.rstrip("/") if endpoint else None

    tracer_provider = TracerProvider(resource=resource)
    if base:
        tracer_provider.add_span_processor(
            BatchSpanProcessor(OTLPSpanExporter(endpoint=f"{base}/v1/traces"))
        )
    trace.set_tracer_provider(tracer_provider)

    if base:
        meter_provider = MeterProvider(
            resource=resource,
            metric_readers=[
                PeriodicExportingMetricReader(
                    OTLPMetricExporter(endpoint=f"{base}/v1/metrics"),
                    export_interval_millis=60_000,
                )
            ],
        )
        metrics.set_meter_provider(meter_provider)

    logger_provider = LoggerProvider(resource=resource)
    if base:
        logger_provider.add_log_record_processor(
            BatchLogRecordProcessor(OTLPLogExporter(endpoint=f"{base}/v1/logs"))
        )
    set_logger_provider(logger_provider)
