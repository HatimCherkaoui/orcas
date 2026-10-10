package com.github.orcas.orchestrator.observability.autoconfigure;

import com.github.orcas.orchestrator.core.engine.WorkflowObserver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Installs the optional core lifecycle observer. */
@AutoConfiguration(beforeName = "com.github.orcas.orchestrator.autoconfigure.core.WorkflowCoreAutoConfiguration")
@ConditionalOnProperty(prefix = "workflow.orchestrator.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WorkflowObservabilityProperties.class)
public final class WorkflowObservabilityAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(com.github.orcas.orchestrator.core.engine.WorkflowTelemetry.class)
    com.github.orcas.orchestrator.core.engine.WorkflowTelemetry workflowTelemetry(org.springframework.beans.factory.ObjectProvider<io.opentelemetry.api.OpenTelemetry> provider,WorkflowObservabilityProperties properties) {
        return new OpenTelemetryWorkflowTelemetry(provider.getIfAvailable(io.opentelemetry.api.OpenTelemetry::noop),properties.isMdc());
    }
    @Bean
    @ConditionalOnMissingBean(WorkflowObserver.class)
    WorkflowObserver workflowObserver(WorkflowObservabilityProperties properties, com.github.orcas.orchestrator.core.engine.WorkflowTelemetry telemetry) {
        return new WorkflowMdcObserver(properties, telemetry);
    }
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnClass(javax.sql.DataSource.class)
    @ConditionalOnProperty(prefix = "workflow.orchestrator.observability",name = "jdbc",havingValue = "true",matchIfMissing = true)
    static WorkflowJdbcTelemetryPostProcessor workflowJdbcTelemetry(org.springframework.beans.factory.ObjectProvider<com.github.orcas.orchestrator.core.engine.WorkflowTelemetry> telemetry) {
        return new WorkflowJdbcTelemetryPostProcessor(telemetry);
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnClass(name = "jakarta.servlet.Filter")
    static class HttpCorrelationConfiguration {
        @Bean WorkflowCorrelationFilter workflowCorrelationFilter(com.github.orcas.orchestrator.core.engine.WorkflowTelemetry telemetry) { return new WorkflowCorrelationFilter(telemetry); }
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnClass(name = "io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender")
    @ConditionalOnMissingBean(name = "workflowOpenTelemetryLogs")
    org.springframework.beans.factory.InitializingBean workflowOpenTelemetryLogs(org.springframework.beans.factory.ObjectProvider<io.opentelemetry.api.OpenTelemetry> otel) {
        return () -> {
            io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender.install(otel.getIfAvailable(io.opentelemetry.api.OpenTelemetry::noop));
            if (org.slf4j.LoggerFactory.getILoggerFactory() instanceof ch.qos.logback.classic.LoggerContext context) {
                var root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
                var appenders = root.iteratorForAppenders();
                while (appenders.hasNext()) if (appenders.next() instanceof io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender) return;
                var appender = new io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender();
                appender.setName("ORCAS_OTEL"); appender.setContext(context); appender.setCaptureMdcAttributes("*"); appender.start(); root.addAppender(appender);
            }
        };
    }
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnClass(name = "io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter")
    @ConditionalOnProperty(prefix = "workflow.orchestrator.observability",name = "metrics-export",havingValue = "true")
    static class MetricsConfiguration {
        @Bean(destroyMethod = "close")
        @ConditionalOnMissingBean(io.opentelemetry.sdk.metrics.SdkMeterProvider.class)
        io.opentelemetry.sdk.metrics.SdkMeterProvider workflowMeterProvider(org.springframework.core.env.Environment environment,
                org.springframework.beans.factory.ObjectProvider<io.opentelemetry.sdk.resources.Resource> resource) {
            var exporter = io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter.builder()
                    .setEndpoint(environment.getProperty("workflow.orchestrator.observability.metrics-endpoint","http://localhost:4318/v1/metrics"))
                    .setAggregationTemporalitySelector(io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector.deltaPreferred()).build();
            var reader = io.opentelemetry.sdk.metrics.export.PeriodicMetricReader.builder(exporter)
                    .setInterval(java.time.Duration.ofSeconds(environment.getProperty("workflow.orchestrator.observability.metrics-interval-seconds",Long.class,30L))).build();
            return io.opentelemetry.sdk.metrics.SdkMeterProvider.builder().setResource(resource.getIfAvailable(io.opentelemetry.sdk.resources.Resource::getDefault))
                    .registerMetricReader(reader).build();
        }
    }
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnClass(name = "io.opentelemetry.sdk.trace.SpanProcessor")
    static class SpanEnrichmentConfiguration {
        @Bean CorrelationSpanProcessor workflowCorrelationSpanProcessor() { return new CorrelationSpanProcessor(); }
    }
}
