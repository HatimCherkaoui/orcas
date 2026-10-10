package com.github.orcas.orchestrator.observability.autoconfigure;

import com.github.orcas.orchestrator.core.model.*;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.*;
import org.slf4j.MDC;

/** Enriches framework/instrumentation spans created inside an execution scope. */
public final class CorrelationSpanProcessor implements SpanProcessor {
    @Override public void onStart(Context parentContext,ReadWriteSpan span) {
        var current = WorkflowContextHolder.execution();
        if (current != null) {
            current.context().metadata().identifiers().forEach(span::setAttribute);
            if (current.workflowId() != null) span.setAttribute("workflowId",current.workflowId());
            if (current.workflow() != null) span.setAttribute("workflow",current.workflow());
            if (current.step() != null) span.setAttribute("workflowStep",current.step().stepName());
        }
        String workflow = Baggage.fromContext(parentContext).getEntryValue("workflow");
        if (workflow != null && span.getAttribute(AttributeKey.stringKey("workflow")) == null)
            span.setAttribute("workflow", workflow);
        span.setAttribute("operation", span.getName());
        Baggage.fromContext(parentContext).forEach((key,entry) -> {
            String canonical = CorrelationIdentifiers.canonical(key);
            if (canonical != null && CorrelationIdentifiers.valid(canonical,entry.getValue())) span.setAttribute(canonical,entry.getValue());
        });
        for (String key : CorrelationIdentifiers.keys()) {
            String value = MDC.get(key);
            if (CorrelationIdentifiers.valid(key,value)) span.setAttribute(key,value);
        }
        span.setAttribute("traceId",span.getSpanContext().getTraceId());
        if (span.getAttribute(AttributeKey.stringKey("eventType")) == null) {
            String type = switch (span.getKind()) {
                case PRODUCER, CONSUMER -> "kafkaEvent";
                case SERVER -> "RestCall";
                case CLIENT -> span.getAttribute(AttributeKey.stringKey("db.system")) != null
                        || span.getAttribute(AttributeKey.stringKey("db.system.name")) != null ? "Database" : "RestCall";
                case INTERNAL -> "Workflow";
            };
            span.setAttribute("eventType",type);
        }
    }
    @Override public boolean isStartRequired() { return true; }
    @Override public void onEnd(ReadableSpan span) { }
    @Override public boolean isEndRequired() { return false; }
}
