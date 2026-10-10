package com.github.orcas.orchestrator.kafka.autoconfigure;

import org.junit.jupiter.api.Test;
import org.apache.kafka.common.header.internals.RecordHeaders;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class KafkaCorrelationTest {
    @Test void preservesCodeMetadataAndCopiesOnlyHeaderIdentifiers() {
        var headers = new RecordHeaders();
        headers.add("x-correlation-id","corr-42".getBytes(StandardCharsets.UTF_8));
        headers.add("Authorization","secret".getBytes(StandardCharsets.UTF_8));
        var metadata = KafkaCorrelation.metadata(Map.of("orderId","42"),headers);
        assertThat(metadata.asMap()).containsEntry("orderId","42").containsEntry("correlationId","corr-42").doesNotContainKey("Authorization");
        var record = KafkaCorrelation.record("workflow.status","wf-42","body",com.github.orcas.orchestrator.core.model.CorrelationIdentifiers.headers(metadata));
        assertThat(new String(record.headers().lastHeader("x-correlation-id").value(),StandardCharsets.UTF_8)).isEqualTo("corr-42");
        assertThat(record.headers().lastHeader("x-request-id")).isNotNull();
        assertThat(record.headers().lastHeader("Authorization")).isNull();
    }
    @Test void payloadIdentityWinsOverAnUnrelatedHeader() {
        var headers = new RecordHeaders().add("x-correlation-id","other".getBytes(StandardCharsets.UTF_8));
        assertThat(KafkaCorrelation.metadata(Map.of("correlationId","original"),headers).get("correlationId")).isEqualTo("original");
    }
}
