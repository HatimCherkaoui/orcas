package com.github.orcas.orchestrator.observability.starter;

import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class WorkflowObservabilityTelemetryCompatibilityTest {

    @Test
    void loadsCompatibleTelemetryArtifacts() {
        assertThatCode(() -> OtlpHttpSpanExporter.builder()
                .setEndpoint("http://localhost:4318/v1/traces")
                .build())
                .doesNotThrowAnyException();

        assertThatCode(() -> OtlpHttpLogRecordExporter.builder()
                .setEndpoint("http://localhost:4318/v1/logs")
                .build())
                .doesNotThrowAnyException();

        assertThatCode(OpenTelemetryAppender.class::getName)
                .doesNotThrowAnyException();
    }
}
