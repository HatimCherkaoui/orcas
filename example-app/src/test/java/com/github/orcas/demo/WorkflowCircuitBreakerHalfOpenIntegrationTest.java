package com.github.orcas.demo;

import com.github.orcas.orchestrator.service.api.WorkflowQueryService;
import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkflowCircuitBreakerHalfOpenIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.6-alpine3.24")
                    .withDatabaseName("workflow")
                    .withUsername("workflow")
                    .withPassword("workflow");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"));

    @Container
    static final GenericContainer<?> WIREMOCK =
            new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.13.2"))
                    .withExposedPorts(8080)
                    .waitingFor(Wait.forHttp("/__admin/").forStatusCode(200));

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    WorkflowQueryService workflowQueryService;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        r.add("demo.customer.base-url", () -> "http://" + WIREMOCK.getHost() + ":" + WIREMOCK.getMappedPort(8080));
        r.add("demo.inventory.base-url", () -> "http://" + WIREMOCK.getHost() + ":" + WIREMOCK.getMappedPort(8080));
        r.add("demo.archive.base-url", () -> "http://" + WIREMOCK.getHost() + ":" + WIREMOCK.getMappedPort(8080));
        r.add("management.otlp.tracing.endpoint", () -> "http://" + WIREMOCK.getHost() + ":" + WIREMOCK.getMappedPort(8080) + "/v1/traces");
        r.add("management.otlp.logs.endpoint", () -> "http://" + WIREMOCK.getHost() + ":" + WIREMOCK.getMappedPort(8080) + "/v1/logs");
        r.add("workflow.orchestrator.circuit-breaker.minimum-number-of-calls", () -> "2");
        r.add("workflow.orchestrator.circuit-breaker.failure-rate-threshold", () -> "50");
        r.add("workflow.orchestrator.circuit-breaker.wait-duration-in-open-state", () -> "1s");
        r.add("workflow.orchestrator.circuit-breaker.permitted-number-of-calls-in-half-open-state", () -> "1");
        r.add("workflow.orchestrator.circuit-breaker.instances.archive-call.minimum-number-of-calls", () -> "2");
        r.add("workflow.orchestrator.circuit-breaker.instances.archive-call.failure-rate-threshold", () -> "50");
        r.add("workflow.orchestrator.circuit-breaker.instances.archive-call.wait-duration-in-open-state", () -> "1s");
        r.add("workflow.orchestrator.circuit-breaker.instances.archive-call.permitted-number-of-calls-in-half-open-state", () -> "1");
        r.add("workflow.orchestrator.retry.steps.archive-call.max-attempts", () -> "1");
    }

    @BeforeAll
    static void beforeAll() {
        RestAssured.filters(new RequestLoggingFilter(), new ResponseLoggingFilter());
    }

    @BeforeAll
    static void wiremockMappings() throws Exception {
        String base = "http://" + WIREMOCK.getHost() + ":" + WIREMOCK.getMappedPort(8080);
        // get stubs from directory wiremock/mappings/*.json
        String customer = Files.readString(Path.of("../wiremock/mappings/customer.json"));
        String inventory = Files.readString(Path.of("../wiremock/mappings/inventory.json"));
        String archive = Files.readString(Path.of("../wiremock/mappings/archive.json"));
        String archive1 = Files.readString(Path.of("../wiremock/mappings/archive-1.json"));
        String archive2 = Files.readString(Path.of("../wiremock/mappings/archive-2.json"));
        String archive3 = Files.readString(Path.of("../wiremock/mappings/archive-3.json"));
        String otlpTraces = """
                {"request":{"method":"POST","urlPath":"/v1/traces"},"response":{"status":200}}
                """;
        String otlpLogs = """
                {"request":{"method":"POST","urlPath":"/v1/logs"},"response":{"status":200}}
                """;
        HttpClient client = HttpClient.newHttpClient();
        for (String mapping : new String[]{customer, inventory, archive, archive1, archive2, archive3, otlpTraces, otlpLogs}) {
            client.send(HttpRequest.newBuilder(URI.create(base + "/__admin/mappings"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapping))
                    .build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void halfOpenReplayClosesBreakerAndResumesAllSuspendedWorkflowSteps() {
        var before = existingPipelineIds();

        for (int i = 0; i < 3; i++) {
            given().port(port).contentType("application/json")
                    .body("{\"orderId\":\"42\",\"amount\":125}")
                    .when().post("/workflows/order-pipeline")
                    .then().statusCode(202);
        }

        var createdPipelineIds = awaitNewPipelineIds(before, 3);
        String observedPipelineId = createdPipelineIds.iterator().next();

        await().atMost(Duration.ofSeconds(45)).pollInterval(Duration.ofMillis(500)).ignoreExceptions().untilAsserted(() ->
                given().port(port)
                        .when()
                        .get("/api/orchestrator/workflows/" + observedPipelineId + "/steps/archive-call")
                        .then()
                        .statusCode(200)
                        .body("stepConfig.circuitBreakerEnabled", equalTo(true))
                        .body("stepConfig.circuitBreakerName", equalTo("archive-call"))
                        .body("circuitBreakerState", notNullValue())
                        .body("scheduledRetry.type", equalTo("HALF_OPEN_REPLAY"))
                        .body("scheduledRetry.breakerName", equalTo("archive-call"))
                        .body("scheduledRetry.scheduledAt", notNullValue()));

        var workflowIds = awaitSuccessfulPipelineIds(before, 3);
        assertEquals(3, workflowIds.size());

        for (String workflowId : workflowIds) {
            await().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofMillis(500)).ignoreExceptions().untilAsserted(() -> {
                var step = workflowQueryService.step(workflowId, "archive-call");
                System.out.println("Step state for " + workflowId + ": " + step.state());
                assertEquals("SUCCESS", step.state());
            });

            assertEquals("SUCCESS", workflowQueryService.find(workflowId).orElseThrow().status());
        }

        await().atMost(Duration.ofSeconds(45)).pollInterval(Duration.ofMillis(500)).untilAsserted(() ->
                assertTrue(archiveRequestCount() >= 4, "expected the breaker to retry suspended workflows after half-open recovery"));
    }

    private java.util.Set<String> existingPipelineIds() {
        return new java.util.HashSet<>(jdbc.queryForList(
                "select pipeline_id from workflow where workflow='order-pipeline'", String.class));
    }

    private java.util.Set<String> awaitNewPipelineIds(java.util.Set<String> before, int expectedCount) {
        java.util.concurrent.atomic.AtomicReference<java.util.Set<String>> found = new java.util.concurrent.atomic.AtomicReference<>();
        await().atMost(Duration.ofSeconds(45)).pollInterval(Duration.ofMillis(250)).ignoreExceptions().untilAsserted(() -> {
            java.util.List<String> ids = jdbc.queryForList(
                    "select pipeline_id from workflow where workflow='order-pipeline' order by date_created desc",
                    String.class);
            java.util.Set<String> next = ids.stream().filter(id -> !before.contains(id))
                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
            assertEquals(expectedCount, next.size(), "expected the requested number of created pipelines");
            found.set(next);
        });
        return found.get();
    }

    private java.util.Set<String> awaitSuccessfulPipelineIds(java.util.Set<String> before, int expectedCount) {
        java.util.concurrent.atomic.AtomicReference<java.util.Set<String>> found = new java.util.concurrent.atomic.AtomicReference<>();
        await().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofMillis(500)).ignoreExceptions().untilAsserted(() -> {
            java.util.List<String> ids = jdbc.queryForList(
                    "select pipeline_id from workflow where workflow='order-pipeline' and status='SUCCESS' order by date_created desc",
                    String.class);
            java.util.Set<String> next = ids.stream().filter(id -> !before.contains(id))
                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
            System.out.println("Waiting for " + expectedCount + " successful pipelines, found: " + next.size());
            assertEquals(expectedCount, next.size(), "expected the requested number of successful pipelines");
            found.set(next);
        });
        return found.get();
    }

    private int archiveRequestCount() throws Exception {
        String base = "http://" + WIREMOCK.getHost() + ":" + WIREMOCK.getMappedPort(8080);
        String body = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base + "/__admin/requests/count"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"method\": \"GET\", \"urlPathPattern\": \"/archive/.*\"}"))
                        .build(), HttpResponse.BodyHandlers.ofString())
                .body();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"count\"\\s*:\\s*(\\d+)").matcher(body);
        if (!matcher.find()) throw new IllegalStateException("Unexpected WireMock response: " + body);
        return Integer.parseInt(matcher.group(1));
    }
}




