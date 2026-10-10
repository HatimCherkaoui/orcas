package com.github.orcas.orchestrator.observability.autoconfigure;

import com.github.orcas.orchestrator.core.engine.WorkflowTelemetry;
import com.github.orcas.orchestrator.core.model.*;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.api.metrics.*;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.slf4j.MDC;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Correlation on spans/logs; metric labels deliberately exclude execution identifiers. */
public final class OpenTelemetryWorkflowTelemetry implements WorkflowTelemetry {
    private final OpenTelemetry otel;
    private final Tracer tracer;
    private final boolean mdc;
    private final DoubleHistogram latency;
    private final LongCounter operations;
    public OpenTelemetryWorkflowTelemetry(OpenTelemetry otel) { this(otel,true); }
    public OpenTelemetryWorkflowTelemetry(OpenTelemetry otel,boolean mdc) {
        this.otel = otel; this.mdc = mdc;
        tracer = otel.getTracer("io.github.hatimcherkaoui.orcas");
        var meter = otel.getMeter("io.github.hatimcherkaoui.orcas");
        latency = meter.histogramBuilder("orcas.operation.duration").setUnit("s")
                .setExplicitBucketBoundariesAdvice(List.of(.005,.01,.025,.05,.1,.25,.5,1.,2.5,5.,10.,30.,60.)).build();
        operations = meter.counterBuilder("orcas.operation.count").build();
    }
    @Override public boolean recordsCompletions() { return true; }
    @Override public Operation begin(String type, String name, Metadata metadata, Map<String,String> attributes) {
        var resolved = new LinkedHashMap<>(attributes);
        var execution = WorkflowContextHolder.execution();
        if (execution != null && execution.workflow() != null) resolved.putIfAbsent("workflow", execution.workflow());
        String workflowName = resolved.getOrDefault("workflow", io.opentelemetry.api.baggage.Baggage.current().getEntryValue("workflow"));
        if (workflowName != null) resolved.putIfAbsent("workflow", workflowName);
        CorrelationIdentifiers.ensure(metadata);
        Context parent = Context.current();
        var current = Span.fromContext(parent).getSpanContext();
        if (!current.isValid() || !current.getTraceId().equals(metadata.get("traceId"))) {
            var headers = new LinkedHashMap<>(CorrelationIdentifiers.headers(metadata));
            headers.putIfAbsent("traceparent", "00-" + metadata.get("traceId") + "-" + CorrelationIdentifiers.newId().replace("-", "").substring(0,16) + "-01");
            parent = otel.getPropagators().getTextMapPropagator().extract(Context.root(), headers, new TextMapGetter<Map<String,String>>() {
                public Iterable<String> keys(Map<String,String> carrier) { return carrier.keySet(); }
                public String get(Map<String,String> carrier, String key) { return carrier.get(key); }
            });
            // Works with an SDK using a no-op/default propagator too.
            if (!Span.fromContext(parent).getSpanContext().isValid()) {
                String tp = headers.get("traceparent");
                parent = Context.root().with(Span.wrap(SpanContext.createFromRemoteParent(tp.substring(3,35), tp.substring(36,52), TraceFlags.getSampled(), TraceState.getDefault())));
            }
        }
        var span = tracer.spanBuilder(name).setParent(parent).startSpan();
        span.setAttribute("eventType", type);
        metadata.identifiers().forEach(span::setAttribute);
        span.setAttribute(io.opentelemetry.api.common.AttributeKey.stringArrayKey("identifierKeys"), new java.util.ArrayList<>(metadata.identifiers().keySet()));
        resolved.forEach(span::setAttribute);
        span.setAttribute("operation", name);
        var baggage = io.opentelemetry.api.baggage.Baggage.builder();
        if (workflowName != null) baggage.put("workflow", workflowName);
        metadata.identifiers().forEach((key,value) -> { if (CorrelationIdentifiers.keys().contains(key)) baggage.put(key,value); });
        var scope = parent.with(baggage.build()).with(span).makeCurrent();
        Map<String,String> previous = MDC.getCopyOfContextMap();
        if (mdc) {
        metadata.identifiers().forEach(MDC::put);
        resolved.forEach(MDC::put);
        MDC.put("eventType", type);
        MDC.put("identifierKeys",String.join(",",metadata.identifiers().keySet()));
        if (span.getSpanContext().isValid()) { MDC.put("spanId",span.getSpanContext().getSpanId()); MDC.put("traceId",span.getSpanContext().getTraceId()); }
        }
        long started = System.nanoTime();
        return new Operation() {
            private boolean failed = "FAILED".equals(resolved.get("status"));
            private Double workflowDurationMs;
            private final AtomicBoolean ended = new AtomicBoolean();
            private boolean detached;
            public void error(Throwable error) { failed = true; span.recordException(error); span.setStatus(StatusCode.ERROR); }
            public void attribute(String key,String value) {
                span.setAttribute(key,value);
                if (key.equals("workflowDurationMs")) workflowDurationMs = Double.valueOf(value);
                if ((key.equals("status") && (value.equals("FAILED") || value.equals("SUSPENDED"))) || (key.equals("http.response.status_code") && Integer.parseInt(value) >= 400)) {
                    failed = true; span.setStatus(StatusCode.ERROR);
                }
            }
            public Map<String,String> propagation() {
                var result = new LinkedHashMap<>(CorrelationIdentifiers.headers(metadata));
                otel.getPropagators().getTextMapPropagator().inject(Context.current(), result, Map::put);
                io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator.getInstance().inject(Context.current(),result,Map::put);
                var sc = span.getSpanContext();
                if (sc.isValid()) result.put("traceparent", "00-"+sc.getTraceId()+"-"+sc.getSpanId()+"-"+sc.getTraceFlags().asHex());
                return Map.copyOf(result);
            }
            public void detach() {
                if (!detached) { scope.close(); if (mdc) { if (previous == null) MDC.clear(); else MDC.setContextMap(previous); } detached = true; }
            }
            public void close() {
                if (!ended.compareAndSet(false,true)) return;
                try {
                    var dimensionBuilder = Attributes.builder().put("eventType",type).put("operation",name)
                            .put("workflow",resolved.getOrDefault("workflow","none")).put("outcome",failed ? "failure" : "success");
                    if (resolved.containsKey("workflowStep")) dimensionBuilder.put("workflowStep", resolved.get("workflowStep"));
                    var dimensions = dimensionBuilder.build();
                    double durationMs = workflowDurationMs != null ? workflowDurationMs : (System.nanoTime()-started)/1_000_000.;
                    span.setAttribute("durationMs", durationMs);
                    span.setAttribute("outcome",failed ? "failure" : "success");
                    Context metricContext = Context.current().with(span);
                    latency.record(durationMs / 1000., dimensions, metricContext);
                    operations.add(1,dimensions,metricContext);
                    if (failed) span.setStatus(StatusCode.ERROR);
                    span.end();
                } finally { if (!detached) detach(); }
            }
        };
    }
}
