# Observability Starter

Adds workflow lifecycle observation and a curated OpenTelemetry runtime.

Application code only needs this starter when telemetry is enabled. OpenTelemetry SDK/exporter and Logback appender dependencies are owned by this starter; applications do not declare them individually.

The parent build pins the OpenTelemetry Java BOM to `1.62.0` and the compatible instrumentation BOM to `2.28.1-alpha`.

The example application keeps its existing OpenTelemetry configuration but has no direct OpenTelemetry dependencies.
