package com.github.orcas.orchestrator.observability.autoconfigure;

import com.github.orcas.orchestrator.core.model.*;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.api.trace.StatusCode;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class OpenTelemetryWorkflowTelemetryTest {
    @Test void linksAllEventTypesAndRestoresMdcWithoutHighCardinalityMetricLabels() {
        var exporter = InMemorySpanExporter.create();
        var reader = InMemoryMetricReader.create();
        try (var tracer = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).addSpanProcessor(new CorrelationSpanProcessor()).build();
             var meter = SdkMeterProvider.builder().registerMetricReader(reader).build()) {
            var otel = OpenTelemetrySdk.builder().setTracerProvider(tracer).setMeterProvider(meter).build();
            var telemetry = new OpenTelemetryWorkflowTelemetry(otel);
            var metadata = CorrelationIdentifiers.fromHeaders(Map.of("x-correlation-id","corr-42"));
            MDC.put("outside","retained");
            for (String type : new String[]{"Workflow","Step","Criteria","kafkaEvent","RestCall","Database","Dashboard"}) {
                try (var operation = telemetry.begin(type,"operation."+type,metadata,Map.of("workflowId","wf-42"))) {
                    assertThat(MDC.get("correlationId")).isEqualTo("corr-42");
                    assertThat(operation.propagation()).containsKeys("traceparent","baggage");
                }
                assertThat(MDC.get("outside")).isEqualTo("retained");
                assertThat(MDC.get("correlationId")).isNull();
            }
            assertThat(exporter.getFinishedSpanItems()).hasSize(7).allSatisfy(span -> {
                assertThat(span.getTraceId()).isEqualTo(metadata.get("traceId"));
                assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("workflowId"))).isEqualTo("wf-42");
            });
            assertThat(reader.collectAllMetrics()).isNotEmpty().allSatisfy(metric -> metric.getData().getPoints().forEach(point -> {
                assertThat(point.getAttributes().asMap().keySet()).noneMatch(key -> key.getKey().matches(".*(Id|id)$"));
            }));
        } finally { MDC.clear(); }
    }
    @Test void completionDurationDoesNotCollideWithWorkflowNameInElasticsearch() {
        var exporter = InMemorySpanExporter.create();
        try (var tracer = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build()) {
            var telemetry = new OpenTelemetryWorkflowTelemetry(OpenTelemetrySdk.builder().setTracerProvider(tracer).build());
            assertThat(telemetry.recordsCompletions()).isTrue();
            telemetry.completed(CorrelationIdentifiers.fromHeaders(Map.of()), Map.of("workflow", "order-pipeline"), java.time.Duration.ofSeconds(5));
            var span = exporter.getFinishedSpanItems().getFirst();
            assertThat(span.getName()).isEqualTo("workflow.completed");
            assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("workflow"))).isEqualTo("order-pipeline");
            assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.doubleKey("durationMs"))).isEqualTo(5000.);
            // The Elasticsearch exporter expands dotted keys into objects.
            assertThat(span.getAttributes().asMap().keySet()).noneMatch(key -> key.getKey().startsWith("workflow."));
        }
    }
    @Test void respectsDisabledMdcPropagation() {
        var telemetry = new OpenTelemetryWorkflowTelemetry(io.opentelemetry.api.OpenTelemetry.noop(),false);
        MDC.put("outside","keep");
        try (var operation = telemetry.begin("Step","step",CorrelationIdentifiers.fromHeaders(Map.of()),Map.of("workflowId","wf"))) {
            assertThat(MDC.get("requestId")).isNull();
            assertThat(MDC.get("workflowId")).isNull();
        } finally { assertThat(MDC.get("outside")).isEqualTo("keep"); MDC.clear(); }
    }
    @Test void detachedSpanCanFinishOnAnotherThreadWithoutClearingItsMdc() throws Exception {
        var exporter = InMemorySpanExporter.create();
        try (var tracer = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build()) {
            var telemetry = new OpenTelemetryWorkflowTelemetry(OpenTelemetrySdk.builder().setTracerProvider(tracer).build());
            var operation = telemetry.begin("RestCall","http.client GET",CorrelationIdentifiers.fromHeaders(Map.of()),Map.of());
            operation.detach();
            assertThat(MDC.get("requestId")).isNull();
            var worker = java.util.concurrent.CompletableFuture.runAsync(() -> {
                MDC.put("worker","preserved");
                operation.error(new IllegalStateException("failure")); operation.close(); operation.close();
                assertThat(MDC.get("worker")).isEqualTo("preserved"); MDC.clear();
            });
            worker.join();
            assertThat(exporter.getFinishedSpanItems()).hasSize(1);
            assertThat(exporter.getFinishedSpanItems().getFirst().getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        }
    }
}
