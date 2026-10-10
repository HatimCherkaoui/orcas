package com.github.orcas.orchestrator.observability.autoconfigure;

import com.github.orcas.orchestrator.core.model.*;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

/** Opt-in real OTLP -> Collector -> Elasticsearch -> Kibana contract validation. */
@Tag("observability-stack")
class ObservabilityStackIntegrationTest {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    private static String request(String method,String url,String body,String contentType) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                .header("Content-Type",contentType).header("kbn-xsrf","true")
                .method(method,HttpRequest.BodyPublishers.ofString(body)).build();
        var response = HTTP.send(request,HttpResponse.BodyHandlers.ofString());
        long deadline = System.nanoTime()+Duration.ofSeconds(60).toNanos();
        while (response.statusCode()==503 && url.contains("_search") && System.nanoTime()<deadline) {
            Thread.sleep(500);
            response = HTTP.send(request,HttpResponse.BodyHandlers.ofString());
        }
        assertThat(response.statusCode()).withFailMessage(response.body()).isBetween(200,299);
        return response.body();
    }
    @Test void exportsAllCategoriesAndImportsQualityOfServiceDashboard() throws Exception {
        try (var network = Network.newNetwork();
             var es = new GenericContainer<>("docker.elastic.co/elasticsearch/elasticsearch:8.15.0")
                     .withNetwork(network).withNetworkAliases("elasticsearch").withExposedPorts(9200)
                     .withEnv("discovery.type","single-node").withEnv("xpack.security.enabled","false")
                     .withEnv("ES_JAVA_OPTS","-Xms512m -Xmx512m").waitingFor(Wait.forHttp("/_cluster/health").forPort(9200)).withStartupTimeout(Duration.ofMinutes(3));
             var collector = new GenericContainer<>("otel/opentelemetry-collector-contrib:0.123.0")
                     .withNetwork(network).withExposedPorts(4318,13133).withEnv("ELASTIC_PASSWORD","unused")
                     .withCopyFileToContainer(MountableFile.forHostPath(ROOT.resolve("otel-collector-config.yaml")),"/etc/otelcol-contrib/config.yaml")
                     .waitingFor(Wait.forHttp("/").forPort(13133)).withStartupTimeout(Duration.ofMinutes(2));
             var kibana = new GenericContainer<>("docker.elastic.co/kibana/kibana:8.15.0")
                     .withNetwork(network).withExposedPorts(5601).withEnv("ELASTICSEARCH_HOSTS","http://elasticsearch:9200")
                     .withEnv("NODE_OPTIONS","--max-old-space-size=512").withEnv("XPACK_SECURITY_ENABLED","false")
                     .waitingFor(Wait.forHttp("/api/status").forPort(5601)).withStartupTimeout(Duration.ofMinutes(4))) {
            es.start();
            String elastic = "http://"+es.getHost()+":"+es.getMappedPort(9200);
            request("PUT",elastic+"/_ingest/pipeline/orcas-correlation",Files.readString(ROOT.resolve("observability/elasticsearch-pipeline.json")),"application/json");
            request("PUT",elastic+"/_index_template/orcas-observability",Files.readString(ROOT.resolve("observability/elasticsearch-template.json")),"application/json");
            collector.start();
            String endpoint = "http://"+collector.getHost()+":"+collector.getMappedPort(4318);
            var resource = Resource.create(Attributes.of(AttributeKey.stringKey("service.name"),"observability-contract"));
            try (var tracer = SdkTracerProvider.builder().setResource(resource).addSpanProcessor(SimpleSpanProcessor.create(OtlpHttpSpanExporter.builder().setEndpoint(endpoint+"/v1/traces").build())).build();
                 var meter = SdkMeterProvider.builder().setResource(resource).registerMetricReader(PeriodicMetricReader.builder(OtlpHttpMetricExporter.builder().setEndpoint(endpoint+"/v1/metrics").setAggregationTemporalitySelector(AggregationTemporalitySelector.deltaPreferred()).build()).setInterval(Duration.ofHours(1)).build()).build();
                 var logger = SdkLoggerProvider.builder().setResource(resource).addLogRecordProcessor(SimpleLogRecordProcessor.create(OtlpHttpLogRecordExporter.builder().setEndpoint(endpoint+"/v1/logs").build())).build()) {
                var telemetry = new OpenTelemetryWorkflowTelemetry(OpenTelemetrySdk.builder().setTracerProvider(tracer).setMeterProvider(meter).setLoggerProvider(logger).build());
                CorrelationIdentifiers.register("businessReference","x-business-reference",() -> "contract-reference");
                var metadata = CorrelationIdentifiers.fromHeaders(Map.of("x-correlation-id","contract-correlation"));
                for (String type : new String[]{"kafkaEvent","RestCall","Database","Workflow","Step","Criteria","Dashboard"}) {
                    try (var operation = telemetry.begin(type,"contract."+type,metadata,Map.of("workflowId","contract-workflow"))) {
                        if (type.equals("Step")) operation.error(new IllegalStateException("expected test failure"));
                        if (type.equals("Workflow")) {
                            logger.get("contract").logRecordBuilder().setBody("correlated execution log")
                                    .setAllAttributes(Attributes.builder().put("correlationId","contract-correlation").put("workflowId","contract-workflow").build()).emit();
                        }
                    }
                }
                tracer.forceFlush().join(30,TimeUnit.SECONDS); logger.forceFlush().join(30,TimeUnit.SECONDS); meter.forceFlush().join(30,TimeUnit.SECONDS);
            }
            String result = "";
            long deadline = System.nanoTime()+Duration.ofSeconds(60).toNanos();
            while (System.nanoTime()<deadline) {
                request("POST",elastic+"/_refresh","","application/json");
                result = request("POST",elastic+"/traces-orcas-*,logs-orcas-*/_search", "{\"size\":100,\"query\":{\"term\":{\"correlationId\":\"contract-correlation\"}}}","application/json");
                if (result.contains("correlated execution log") && result.contains("contract.Dashboard")) break;
                Thread.sleep(500);
            }
            for (String category : new String[]{"kafkaEvent","RestCall","Database","Workflow","Step","Criteria","Dashboard","Log"}) assertThat(result).contains("\"eventType\":\""+category+"\"");
            assertThat(result).contains("\"businessReference\":\"contract-reference\"","\"durationMs\"","\"serviceName\":\"observability-contract\"","\"outcome\":\"failure\"");
            String quality = request("POST",elastic+"/traces-orcas-*/_search",
                    "{\"size\":0,\"aggs\":{\"latency\":{\"percentiles\":{\"field\":\"durationMs\",\"percents\":[50,95,99]}},\"failures\":{\"filter\":{\"term\":{\"outcome\":\"failure\"}}}}}","application/json");
            assertThat(quality).contains("\"50.0\"","\"95.0\"","\"99.0\"","\"failures\":{\"doc_count\":1}");
            String metricResult = request("POST",elastic+"/metrics-orcas-*/_search","{\"size\":100}","application/json");
            assertThat(metricResult).contains("orcas","duration","count");
            kibana.start();
            String saved = Files.readString(ROOT.resolve("observability/kibana/orcas.ndjson"));
            String boundary = "orcas-boundary";
            String multipart = "--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"orcas.ndjson\"\r\nContent-Type: application/ndjson\r\n\r\n"+saved+"\r\n--"+boundary+"--\r\n";
            String imported = request("POST","http://"+kibana.getHost()+":"+kibana.getMappedPort(5601)+"/api/saved_objects/_import?overwrite=true",multipart,"multipart/form-data; boundary="+boundary);
            assertThat(imported).contains("\"success\":true","orcas-quality-of-service");
        }
    }
}
