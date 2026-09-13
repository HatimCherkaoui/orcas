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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkflowIntegrationTest {
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
    }

    @BeforeAll
    static void beforeAll() {
        RestAssured.filters(new RequestLoggingFilter(), new ResponseLoggingFilter());
    }

    @BeforeAll
    static void wiremockMappings() throws Exception {
        String base = "http://" + WIREMOCK.getHost() + ":" + WIREMOCK.getMappedPort(8080);
        String customer = """
                {"request":{"method":"GET","urlPathPattern":"/customers/.*"},"response":{"status":200,"fixedDelayMilliseconds":1000,"jsonBody":{"source":"customer","status":"OK"}}}
                """;
        String inventory = """
                {"request":{"method":"GET","urlPathPattern":"/inventory/.*"},"response":{"status":200,"fixedDelayMilliseconds":1000,"jsonBody":{"source":"inventory","status":"OK"}}}
                """;
        HttpClient client = HttpClient.newHttpClient();
        for (String mapping : new String[]{customer, inventory}) {
            client.send(HttpRequest.newBuilder(URI.create(base + "/__admin/mappings"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapping))
                    .build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void wiremockParallelRestCallsPersistIndependentStepContextsAndAsyncJoin() {
        long started = System.nanoTime();
        var before = existingPipelineIds();
        given().port(port).contentType("application/json")
                .body("{\"orderId\":\"42\",\"amount\":125}")
                .when().post("/workflows/order-pipeline").then().statusCode(202);

        String pipelineId = awaitNewSuccessfulPipeline(before);

        // The workflow instance may flip to SUCCESS a moment before the parallel
        // step-context rows for its branches are fully persisted, so poll (rather
        // than assert once) until both branch contexts are visible.
        var contexts = new java.util.concurrent.atomic.AtomicReference<com.github.orcas.orchestrator.core.model.StepContext[]>();
        await().atMost(Duration.ofSeconds(10)).ignoreExceptions().untilAsserted(() -> {
            var c = workflowQueryService.stepContext(pipelineId, "customer-call").orElseThrow();
            var i = workflowQueryService.stepContext(pipelineId, "inventory-call").orElseThrow();
            contexts.set(new com.github.orcas.orchestrator.core.model.StepContext[]{c, i});
        });
        var customer = contexts.get()[0];
        var inventory = contexts.get()[1];
        assertEquals("extract-order", customer.parentStepName());
        assertEquals("extract-order", inventory.parentStepName());
        assertTrue(customer.output() != null);
        assertTrue(inventory.output() != null);
        assertTrue(customer.attributes().containsKey("http.status"));
        assertTrue(inventory.attributes().containsKey("http.status"));

        long customerUpdated = customer.updatedAt().toEpochMilli();
        long inventoryUpdated = inventory.updatedAt().toEpochMilli();
        assertTrue(Math.abs(customerUpdated - inventoryUpdated) < 800,
                "parallel calls should complete close together");

        assertTrue((System.nanoTime() - started) < Duration.ofSeconds(8).toNanos(),
                "two 1s calls should not behave like a long sequential chain");

        given().port(port).when().get("/api/orchestrator/workflows/" + pipelineId + "/steps/customer-call/context")
                .then().statusCode(200).body("parentStepName", equalTo("extract-order"));
    }

    @Test
    void restStartsWorkflowAndPersistsNormalizedStateAndLogs() {
        var before = existingPipelineIds();
        given()
                .port(port)
                .contentType("application/json")
                .body("{\"orderId\":\"42\",\"amount\":125}")
                .when()
                .post("/workflows/order-pipeline")
                .then()
                .statusCode(202);

        String pipelineId = awaitNewSuccessfulPipeline(before);

        // The overall workflow status can flip to SUCCESS a moment before every
        // persisted row (context/metadata/steps/logs) for the async 'notify' step
        // is visible, so poll here instead of asserting once.
        await().atMost(Duration.ofSeconds(10)).ignoreExceptions().untilAsserted(() -> {
            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from workflow_context where pipeline_id=?",
                    Integer.class, pipelineId));

            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from workflow_metadata where pipeline_id=?",
                    Integer.class, pipelineId));

            assertTrue(jdbc.queryForObject(
                    "select count(*) from workflow_step where pipeline_id=?",
                    Integer.class, pipelineId) >= 4);

            assertTrue(jdbc.queryForObject(
                    "select count(*) from workflow_log where pipeline_id=?",
                    Integer.class, pipelineId) > 0);

            assertTrue(jdbc.queryForObject(
                    "select count(*) from workflow_step_log where pipeline_id=?",
                    Integer.class, pipelineId) > 0);
        });

        given()
                .port(port)
                .when()
                .get("/api/orchestrator/workflows/" + pipelineId)
                .then()
                .statusCode(200)
                .body("workflowId", equalTo(pipelineId))
                .body("status", equalTo("SUCCESS"));

        given()
                .port(port)
                .when()
                .get("/api/orchestrator/workflows?page=0&size=10&status=SUCCESS")
                .then()
                .statusCode(200)
                .body("content.size()", greaterThan(0));

        given()
                .port(port)
                .when()
                .get("/api/orchestrator/workflows/" + pipelineId + "/steps")
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0));

        // The async 'notify' step can still be finishing its own terminal-state
        // transition even after the main chain already flipped the workflow to
        // SUCCESS; wait for it to settle so it can't race with (and overwrite)
        // the manual PATCH override performed below.
        await().atMost(Duration.ofSeconds(30)).ignoreExceptions().untilAsserted(() -> {
            var notify = workflowQueryService.step(pipelineId, "notify");
            assertTrue(notify.state().equals("SUCCESS") || notify.state().equals("FAILED"));
        });

        given()
                .port(port)
                .contentType("application/json")
                .body("{\"status\":\"FAILED\"}")
                .when()
                .patch("/api/orchestrator/workflows/" + pipelineId)
                .then()
                .statusCode(204);

        given()
                .port(port)
                .when()
                .get("/api/orchestrator/workflows/" + pipelineId)
                .then()
                .statusCode(200)
                .body("status", equalTo("FAILED"));

        given()
                .port(port)
                .when()
                .get("/api/orchestrator/kafka/topics")
                .then()
                .statusCode(200);
    }


    @Test
    void restStarts() {
        var before = existingPipelineIds();
        given()
                .port(port)
                .contentType("application/json")
                .body("{\"orderId\":\"42\",\"amount\":125}")
                .when()
                .post("/workflows/order-pipeline")
                .then()
                .statusCode(202);

        String pipelineId = awaitNewSuccessfulPipeline(before);

        await().atMost(Duration.ofSeconds(60)).pollDelay(Duration.ofMillis(10)).ignoreExceptions().untilAsserted(() -> {
            var steps = workflowQueryService.steps(pipelineId);
            System.out.println(steps);
            assertTrue(steps.size() >= 4);
        });
        await().atMost(Duration.ofSeconds(60)).pollDelay(Duration.ofMillis(10)).ignoreExceptions().untilAsserted(() -> {
            var step = workflowQueryService.step(pipelineId, "tofail");
            System.out.println("state: " + step);
            assertTrue(step.state().equals("SUCCESS"));
        });
    }

    /** Snapshot of every {@code order-pipeline} instance id already present before a test starts a new one. */
    private java.util.Set<String> existingPipelineIds() {
        return new java.util.HashSet<>(jdbc.queryForList(
                "select pipeline_id from workflow where workflow='order-pipeline'", String.class));
    }

    /**
     * Waits for a new {@code order-pipeline} instance (not present in {@code before}) to reach
     * {@code SUCCESS}, and returns its pipeline id. Scoping the wait to a specific new instance
     * (rather than any successful row) keeps this test isolated from other test methods and any
     * previously completed workflows sharing the same workflow name.
     */
    private String awaitNewSuccessfulPipeline(java.util.Set<String> before) {
        java.util.concurrent.atomic.AtomicReference<String> found = new java.util.concurrent.atomic.AtomicReference<>();
        await().atMost(Duration.ofSeconds(30)).ignoreExceptions().untilAsserted(() -> {
            java.util.List<String> ids = jdbc.queryForList(
                    "select pipeline_id from workflow where workflow='order-pipeline' and status='SUCCESS' order by date_created desc",
                    String.class);
            String next = ids.stream().filter(id -> !before.contains(id)).findFirst().orElse(null);
            assertTrue(next != null, "expected a new successful order-pipeline instance");
            found.set(next);
        });
        return found.get();
    }
}
