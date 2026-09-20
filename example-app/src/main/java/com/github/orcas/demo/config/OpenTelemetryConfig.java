package com.github.orcas.demo.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenTelemetryConfig {

    @Bean
    public OtlpHttpSpanExporter otlpHttpSpanExporter(
            @Value("${management.otlp.tracing.endpoint:http://otel-collector:4318/v1/traces}") String endpoint) {
        return OtlpHttpSpanExporter.builder()
                .setEndpoint(endpoint)
                .build();
    }

    @Bean
    public OtlpHttpLogRecordExporter otlpHttpLogRecordExporter(
            @Value("${management.otlp.logs.endpoint:http://otel-collector:4318/v1/logs}") String endpoint) {
        return OtlpHttpLogRecordExporter.builder()
                .setEndpoint(endpoint)
                .build();
    }

    @Bean
    public OpenTelemetry openTelemetry(
            OtlpHttpLogRecordExporter logExporter,
            @Value("${spring.application.name:workflow-example-app}") String serviceName) {

        Resource resource = Resource.getDefault().toBuilder()
                .put(AttributeKey.stringKey("service.name"), serviceName)
                .build();

        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .setResource(resource)
                .addLogRecordProcessor(BatchLogRecordProcessor.builder(logExporter).build())
                .build();

        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setLoggerProvider(loggerProvider)
                .build();

        try {
            GlobalOpenTelemetry.set(openTelemetry);
        } catch (IllegalStateException ignored) {
            // Tests can bootstrap multiple application contexts in the same JVM.
        }

        OpenTelemetryAppender.install(openTelemetry);

        return openTelemetry;
    }
}

