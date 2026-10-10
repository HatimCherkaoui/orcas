package com.github.orcas.orchestrator.observability.autoconfigure;

import com.github.orcas.orchestrator.core.engine.WorkflowTelemetry;
import com.github.orcas.orchestrator.core.model.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.core.Ordered;
import java.io.IOException;
import java.util.*;

/** Captures identifiers once at ingress and returns them for callers and dashboard actions. */
public final class WorkflowCorrelationFilter extends OncePerRequestFilter implements Ordered {
    private final WorkflowTelemetry telemetry;
    public WorkflowCorrelationFilter(WorkflowTelemetry telemetry) { this.telemetry = telemetry; }
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 10; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        var headers = new LinkedHashMap<String,String>();
        Collections.list(request.getHeaderNames()).forEach(name -> headers.put(name,request.getHeader(name)));
        var context = WorkflowContext.of(null,headers);
        var incoming = io.opentelemetry.api.trace.Span.current();
        var spanContext = incoming.getSpanContext();
        if (spanContext.isValid()) {
            context.metadata().put("traceId",spanContext.getTraceId());
            context.metadata().put("traceparent","00-"+spanContext.getTraceId()+"-"+spanContext.getSpanId()+"-"+spanContext.getTraceFlags().asHex());
            context.metadata().identifiers().forEach(incoming::setAttribute);
        }
        var previous = WorkflowContextHolder.execution();
        String type = request.getRequestURI().startsWith("/api/orchestrator") ? "Dashboard" : "RestCall";
        incoming.setAttribute("eventType",type);
        var attributes = new LinkedHashMap<String,String>();
        attributes.put("http.request.method",request.getMethod());
        var path = request.getRequestURI().split("/");
        for (int i=0; i<path.length-1; i++) {
            if (path[i].equals("workflows") && !path[i+1].equals("definitions") && !path[i+1].equals("replay")) attributes.put("workflowId",path[i+1]);
            if (path[i].equals("steps")) attributes.put("workflowStep",path[i+1]);
        }
        try (var operation = telemetry.begin(type,"http.server " + request.getMethod(),context.metadata(),attributes)) {
            var propagation = operation.propagation();
            if (propagation.isEmpty()) propagation = CorrelationIdentifiers.headers(context.metadata());
            // Only allowlisted identifiers and trace context become persisted metadata.
            CorrelationIdentifiers.fromHeaders(propagation).identifiers().forEach(context.metadata()::put);
            CorrelationIdentifiers.headers(context.metadata()).forEach(response::setHeader);
            response.setHeader("Access-Control-Expose-Headers",String.join(",",CorrelationIdentifiers.headers(context.metadata()).keySet())+",x-workflow-id");
            WorkflowContextHolder.set(context);
            try {
                chain.doFilter(request,response);
                if (context.metadata().get("workflowId") != null) {
                    operation.attribute("workflowId",context.metadata().get("workflowId"));
                    incoming.setAttribute("workflowId",context.metadata().get("workflowId"));
                    response.setHeader("x-workflow-id",context.metadata().get("workflowId"));
                }
                operation.attribute("http.response.status_code",Integer.toString(response.getStatus()));
            }
            catch (IOException | ServletException | RuntimeException error) { operation.error(error); throw error; }
            finally { WorkflowContextHolder.restore(previous); }
        }
    }
}
