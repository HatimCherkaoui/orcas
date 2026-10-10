package com.github.orcas.orchestrator.core.model;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class CorrelationIdentifiersTest {
    @Test void capturesOnlyIdentifiersAndGeneratesMissingValues() {
        var context = WorkflowContext.of("order",Map.of("Authorization","secret","Cookie","secret","baggage","secret","X-Correlation-ID","corr-42"));
        assertThat(context.metadata().asMap()).containsEntry("correlationId","corr-42").containsKeys("requestId","transactionId","traceId");
        assertThat(context.metadata().asMap()).doesNotContainKeys("Authorization","Cookie","baggage");
        assertThat(CorrelationIdentifiers.headers(context.metadata())).containsEntry("x-correlation-id","corr-42");
    }
    @Test void w3cParentWinsOverTraceHeader() {
        String trace = "0123456789abcdef0123456789abcdef";
        var metadata = CorrelationIdentifiers.fromHeaders(Map.of("traceparent","00-"+trace+"-0123456789abcdef-01","x-trace-id","fedcba9876543210fedcba9876543210"));
        assertThat(metadata.identifier("traceId")).isEqualTo(trace);
    }
    @Test void rejectsZeroParentsAndHeaderInjection() {
        var metadata = CorrelationIdentifiers.fromHeaders(Map.of("traceparent","00-"+"0".repeat(32)+"-0123456789abcdef-01","x-request-id","bad\r\nheader","x-transaction-id","x".repeat(257)));
        assertThat(metadata.get("traceparent")).isNull();
        assertThat(metadata.identifier("requestId")).doesNotContain("\r","\n");
        assertThat(metadata.identifier("transactionId")).hasSize(36);
    }
    @Test void keepsIdentityWhenRecomputed() {
        var metadata = CorrelationIdentifiers.fromHeaders(Map.of());
        var before = metadata.asMap();
        assertThat(CorrelationIdentifiers.ensure(metadata).asMap()).isEqualTo(before);
    }
    @Test void extractsOnlyAllowlistedBaggageEntries() {
        var metadata = CorrelationIdentifiers.fromHeaders(Map.of("baggage","requestId=remote-request,correlationId=remote-correlation,secret=must-not-persist", "x-request-id","explicit-request"));
        assertThat(metadata.asMap()).containsEntry("requestId","explicit-request").containsEntry("correlationId","remote-correlation").doesNotContainKeys("baggage","secret");
        assertThat(CorrelationIdentifiers.validTracestate("vendor=value,other=value")).isTrue();
        assertThat(CorrelationIdentifiers.validTracestate("vendor=value,vendor=duplicate")).isFalse();
    }
    @Test void rejectsOverlappingIdentifierAliases() {
        assertThatThrownBy(() -> CorrelationIdentifiers.register("badAlias","x-request-id",() -> "id")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void acceptsCodeDefinedIdentifierAndAlias() {
        CorrelationIdentifiers.register("businessTransactionId","x-business-transaction-id",() -> "business-default");
        var metadata = CorrelationIdentifiers.fromHeaders(Map.of("x-business-transaction-id","business-42"));
        assertThat(metadata.identifier("businessTransactionId")).isEqualTo("business-42");
        metadata.withIdentifier("x-business-transaction-id","business-43");
        assertThat(metadata.identifiers()).containsEntry("businessTransactionId","business-43");
        assertThatThrownBy(() -> metadata.withIdentifier("Authorization","secret")).isInstanceOf(IllegalArgumentException.class);
    }
}
