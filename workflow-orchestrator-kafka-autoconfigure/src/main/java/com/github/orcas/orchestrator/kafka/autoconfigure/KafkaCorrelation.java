package com.github.orcas.orchestrator.kafka.autoconfigure;

import com.github.orcas.orchestrator.core.model.*;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Identifier propagation shared by status, replay and dead-letter records. */
final class KafkaCorrelation {
    private KafkaCorrelation() { }
    static Metadata metadata(Map<String,String> payload, Headers headers) {
        var values = new LinkedHashMap<String,String>();
        if (headers != null) headers.forEach(h -> { if (h.value() != null) values.put(h.key(),new String(h.value(),StandardCharsets.UTF_8)); });
        if (payload != null) values.putAll(payload);
        var metadata = new Metadata(payload);
        CorrelationIdentifiers.fromHeaders(values).identifiers().forEach(metadata::put);
        return metadata;
    }
    static ProducerRecord<String,String> record(String topic,String key,String body,Map<String,String> headers) {
        var record = new ProducerRecord<String,String>(topic,key,body);
        headers.forEach((name,value) -> record.headers().add(name,value.getBytes(StandardCharsets.UTF_8)));
        return record;
    }
}
