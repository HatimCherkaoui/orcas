package com.github.orcas.orchestrator.core.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Allowlisted transport identifiers. Register application identifiers before accepting traffic. */
public final class CorrelationIdentifiers {
    public static final String REQUEST_ID = "requestId";
    public static final String CORRELATION_ID = "correlationId";
    public static final String TRANSACTION_ID = "transactionId";
    public static final String TRACE_ID = "traceId";
    private record Definition(String header, Supplier<String> generator, Set<String> aliases) { }
    private static final Map<String, Definition> DEFINITIONS = new ConcurrentHashMap<>();
    private static final Pattern VALUE = Pattern.compile("[A-Za-z0-9._:/-]{1,256}");
    private static final Pattern TRACEPARENT = Pattern.compile("00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})");
    static {
        register(REQUEST_ID, "x-request-id", CorrelationIdentifiers::newId, "request-id");
        register(CORRELATION_ID, "x-correlation-id", CorrelationIdentifiers::newId, "correlation-id");
        register(TRANSACTION_ID, "x-transaction-id", CorrelationIdentifiers::newId, "transaction-id");
        register(TRACE_ID, "x-trace-id", () -> newId().replace("-", ""), "trace-id");
    }
    private CorrelationIdentifiers() { }
    public static String newId() { return UUID.randomUUID().toString(); }
    /** Extends the allowlist; values are validated and missing values generated. */
    public static synchronized void register(String key, String header, Supplier<String> generator, String... aliases) {
        if (key == null || !key.matches("[a-zA-Z][a-zA-Z0-9]{0,63}") || header == null
                || !header.matches("[a-zA-Z][a-zA-Z0-9-]{0,63}")) throw new IllegalArgumentException("Invalid identifier name");
        Set<String> names = new HashSet<>();
        names.add(key.toLowerCase(Locale.ROOT)); names.add(header.toLowerCase(Locale.ROOT));
        for (String alias : aliases) names.add(alias.toLowerCase(Locale.ROOT));
        for (var entry : DEFINITIONS.entrySet()) {
            if (!entry.getKey().equals(key) && entry.getValue().aliases().stream().anyMatch(names::contains))
                throw new IllegalArgumentException("Identifier aliases overlap: " + key);
        }
        DEFINITIONS.put(key, new Definition(header.toLowerCase(Locale.ROOT), Objects.requireNonNull(generator), Set.copyOf(names)));
    }
    public static Set<String> keys() { return Set.copyOf(DEFINITIONS.keySet()); }
    public static boolean isIdentifier(String key) { return key != null && (DEFINITIONS.containsKey(key) || key.equals("workflowId") || key.equals("traceparent") || key.equals("tracestate")); }
    public static String header(String key) { var d = DEFINITIONS.get(key); return d == null ? ("workflowId".equals(key) ? "x-workflow-id" : key) : d.header(); }
    public static String canonical(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase(Locale.ROOT);
        return DEFINITIONS.entrySet().stream().filter(e -> e.getValue().aliases().contains(lower)).map(Map.Entry::getKey).findFirst().orElse(null);
    }
    public static boolean valid(String key, String value) {
        if (value == null || !VALUE.matcher(value).matches()) return false;
        return !TRACE_ID.equals(key) || (value.matches("[0-9a-f]{32}") && !value.equals("0".repeat(32)));
    }
    public static boolean validTraceparent(String value) {
        if (value == null) return false;
        var m = TRACEPARENT.matcher(value);
        return m.matches() && !m.group(1).equals("0".repeat(32)) && !m.group(2).equals("0".repeat(16));
    }
    public static boolean validTracestate(String state) {
        if (state == null || state.length() > 512) return false;
        var members = state.split(",",-1);
        if (members.length > 32) return false;
        Set<String> keys = new HashSet<>();
        for (String member : members) {
            var pair = member.trim().split("=",-1);
            if (pair.length != 2 || !keys.add(pair[0])
                    || !pair[0].matches("(?:[a-z][a-z0-9_*/-]{0,255}|[a-z0-9][a-z0-9_*/-]{0,240}@[a-z][a-z0-9_*/-]{0,13})")
                    || pair[1].isEmpty() || pair[1].length() > 256 || pair[1].endsWith(" ")
                    || !pair[1].chars().allMatch(c -> c >= 32 && c <= 126 && c != ',' && c != '=')) return false;
        }
        return true;
    }
    /** Captures only registered identifiers and W3C trace context, never authorization/cookies/baggage. */
    public static Metadata fromHeaders(Map<String, String> headers) {
        Metadata metadata = new Metadata();
        if (headers != null) headers.forEach((name,value) -> {
            if (!name.equalsIgnoreCase("baggage") || value == null || value.length()>8192) return;
            for (String member : value.split(",")) {
                var pair = member.split(";",2)[0].trim().split("=",2);
                if (pair.length != 2) continue;
                String key = canonical(pair[0].trim());
                try {
                    String text = java.net.URLDecoder.decode(pair[1].trim(),java.nio.charset.StandardCharsets.UTF_8);
                    if (key != null && valid(key,text)) metadata.put(key,text);
                } catch (IllegalArgumentException ignored) { /* Malformed transport values are not persisted. */ }
            }
        });
        if (headers != null) headers.forEach((name, value) -> {
            String key = canonical(name);
            if (key != null && valid(key, value)) metadata.put(key, value);
            if (name.equalsIgnoreCase("traceparent") && validTraceparent(value)) {
                metadata.put("traceparent", value); metadata.put(TRACE_ID, value.substring(3, 35));
            }
            if (name.equalsIgnoreCase("tracestate") && validTracestate(value)) metadata.put("tracestate", value);
        });
        // A valid W3C parent takes precedence over a separate trace-id header, regardless of map order.
        if (metadata.get("traceparent") != null) metadata.put(TRACE_ID, metadata.get("traceparent").substring(3,35));
        return ensure(metadata);
    }
    public static Metadata ensure(Metadata metadata) {
        String parent = metadata.get("traceparent");
        if (parent != null) {
            if (validTraceparent(parent)) metadata.put(TRACE_ID,parent.substring(3,35));
            else { metadata.remove("traceparent"); metadata.remove("tracestate"); }
        }
        if (!validTraceparent(metadata.get("traceparent")) || !validTracestate(metadata.get("tracestate"))) metadata.remove("tracestate");
        for (var e : DEFINITIONS.entrySet()) {
            if (!valid(e.getKey(), metadata.get(e.getKey()))) {
                String value = e.getValue().generator().get();
                if (!valid(e.getKey(), value)) throw new IllegalArgumentException("Invalid generated identifier: " + e.getKey());
                metadata.put(e.getKey(), value);
            }
        }
        return metadata;
    }
    public static Map<String, String> headers(Metadata metadata) {
        Map<String, String> headers = new LinkedHashMap<>();
        metadata.identifiers().forEach((k,v) -> headers.put(header(k),v));
        return Map.copyOf(headers);
    }
}
