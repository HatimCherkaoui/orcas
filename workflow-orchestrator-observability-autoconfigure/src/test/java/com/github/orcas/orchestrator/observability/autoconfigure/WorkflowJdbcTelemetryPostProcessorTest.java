package com.github.orcas.orchestrator.observability.autoconfigure;

import com.github.orcas.orchestrator.core.engine.WorkflowTelemetry;
import com.github.orcas.orchestrator.core.model.*;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.h2.jdbcx.JdbcDataSource;
import javax.sql.DataSource;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class WorkflowJdbcTelemetryPostProcessorTest {
    @Test void instrumentsAcquisitionQueriesAndTransactionsWithoutExtraConnections() throws Exception {
        var exporter = InMemorySpanExporter.create();
        try (var tracer = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build()) {
            var factory = new DefaultListableBeanFactory();
            factory.registerSingleton("telemetry",new OpenTelemetryWorkflowTelemetry(OpenTelemetrySdk.builder().setTracerProvider(tracer).build()));
            var source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:telemetry");
            var wrapped = (DataSource) new WorkflowJdbcTelemetryPostProcessor(factory.getBeanProvider(WorkflowTelemetry.class)).postProcessAfterInitialization(source,"dataSource");
            var context = WorkflowContext.of(null,Map.of("x-correlation-id","sql-correlation"));
            WorkflowContextHolder.set("wf-sql","sql-workflow",context);
            try (var connection = wrapped.getConnection(); var statement = connection.createStatement()) {
                connection.setAutoCommit(false);
                try (var result = statement.executeQuery("select 42")) { assertThat(result.next()).isTrue(); assertThat(result.getInt(1)).isEqualTo(42); }
                connection.commit();
                assertThatThrownBy(() -> statement.execute("invalid SQL")).isInstanceOf(java.sql.SQLException.class);
                connection.rollback();
            } finally { WorkflowContextHolder.clear(); }
            assertThat(exporter.getFinishedSpanItems()).hasSize(5).allSatisfy(span -> {
                assertThat(span.getTraceId()).isEqualTo(context.metadata().get("traceId"));
                assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("eventType"))).isEqualTo("Database");
                assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("workflowId"))).isEqualTo("wf-sql");
            });
            assertThat(exporter.getFinishedSpanItems()).anySatisfy(span -> assertThat(span.getStatus().getStatusCode()).isEqualTo(io.opentelemetry.api.trace.StatusCode.ERROR));
        }
    }
}
