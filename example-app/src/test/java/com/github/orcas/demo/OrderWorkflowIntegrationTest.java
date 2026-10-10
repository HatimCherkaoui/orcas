package com.github.orcas.demo;

import com.github.orcas.demo.domain.*;
import com.github.orcas.demo.repository.CustomerRepository;
import com.github.orcas.demo.repository.InventoryRepository;
import com.github.orcas.demo.repository.OrderRepository;
import com.github.orcas.demo.repository.PaymentRepository;
import com.github.orcas.orchestrator.core.builder.StepCatalog;
import com.github.orcas.orchestrator.core.engine.WorkflowEngine;
import com.github.orcas.orchestrator.core.event.WorkflowEventPublisher;
import io.restassured.http.ContentType;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderWorkflowIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18-alpine")
                    .withDatabaseName("workflow")
                    .withUsername("workflow")
                    .withPassword("workflow");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"))
                    // Kafka 4.3.x enables share-group infrastructure. Keep its
                    // single-broker internal topic viable for integration tests.
                    .withEnv("KAFKA_SHARE_COORDINATOR_STATE_TOPIC_REPLICATION_FACTOR", "1")
                    .withEnv("KAFKA_SHARE_COORDINATOR_STATE_TOPIC_MIN_ISR", "1")
                    .withEnv("KAFKA_SHARE_COORDINATOR_STATE_TOPIC_NUM_PARTITIONS", "1")
                    .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0");

    @Container
    static final GenericContainer<?> WIREMOCK =
            new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.13.2"))
                    .withCommand("--global-response-templating")
                    .withExposedPorts(8080)
                    .waitingFor(Wait.forHttp("/__admin/").forStatusCode(200));


    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Keep the integration workload within a small Colima/CI resource budget.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "4");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "1");
        registry.add("spring.datasource.hikari.connection-timeout", () -> "5000");
        registry.add("workflow.orchestrator.async.concurrency", () -> "4");
        registry.add("workflow.orchestrator.kafka.concurrency", () -> "2");
        registry.add("workflow.orchestrator.retry.delay", () -> "100ms");
        registry.add("workflow.orchestrator.retry.steps.initiate-payment.delay", () -> "100ms");
        registry.add("workflow.orchestrator.retry.steps.initiate-payment.max-attempts", () -> "3");
        registry.add("workflow.orchestrator.retry.steps.refund-payment.delay", () -> "100ms");
        registry.add("workflow.orchestrator.retry.steps.refund-payment.max-attempts", () -> "5");
        registry.add("workflow.orchestrator.circuit-breaker.sliding-window-size", () -> "2");
        registry.add("workflow.orchestrator.circuit-breaker.minimum-number-of-calls", () -> "2");
        registry.add("workflow.orchestrator.circuit-breaker.failure-rate-threshold", () -> "50");
        registry.add("workflow.orchestrator.circuit-breaker.permitted-number-of-calls-in-half-open-state", () -> "1");
        registry.add("workflow.orchestrator.circuit-breaker.wait-duration-in-open-state", () -> "500ms");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.consumer.properties.group.protocol", () -> "classic");
        registry.add("spring.kafka.producer.key-serializer", () -> "org.apache.kafka.common.serialization.StringSerializer");
        registry.add("spring.kafka.producer.value-serializer", () -> "org.apache.kafka.common.serialization.StringSerializer");
        registry.add("management.otlp.metrics.export.enabled", () -> "false");
        registry.add("workflow.orchestrator.observability.metrics-export", () -> "false");
        registry.add("demo.payment.base-url", OrderWorkflowIntegrationTest::wiremockUrl);
        registry.add("demo.notification.base-url", OrderWorkflowIntegrationTest::wiremockUrl);
        registry.add("demo.inventory.base-url", OrderWorkflowIntegrationTest::wiremockUrl);
        registry.add("management.opentelemetry.tracing.export.otlp.endpoint",
                () -> wireMockUrl("/v1/traces"));
        registry.add("management.opentelemetry.logging.export.otlp.endpoint",
                () -> wireMockUrl("/v1/logs"));

    }

    @LocalServerPort
    int port;

    @Autowired
    CustomerRepository customers;

    @Autowired
    InventoryRepository inventory;

    @Autowired
    OrderRepository orders;

    @Autowired
    PaymentRepository payments;

    @Autowired
    StepCatalog stepCatalog;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    WorkflowEngine workflowEngine;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    WorkflowEventPublisher workflowEventPublisher;

    @Autowired
    io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry circuitBreakers;

    @BeforeAll
    static void beforeAll() throws IOException, InterruptedException {
        configureWireMock();
    }

    @BeforeEach
    void resetState() {
        resetWireMock();
        circuitBreakers.getAllCircuitBreakers().forEach(io.github.resilience4j.circuitbreaker.CircuitBreaker::reset);
        payments.deleteAll();
        orders.deleteAll();
        inventory.deleteAll();
        customers.deleteAll();
    }

    @Test
    void contextProvidesApplicationTransactionManager() {
        assertThat(transactionManager).isNotNull();
    }

    @Test
    void contextProvidesKafkaBrokerConnectivity() throws Exception {
        System.out.println("Kafka Testcontainer bootstrap servers: " + KAFKA.getBootstrapServers());
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            assertThat(admin.describeCluster().nodes().get().stream()).isNotEmpty();
        }
    }

    @Test
    void launchWorkflowAspectIsAppliedToOrderController() {
        assertThat(AopUtils.isAopProxy(applicationContext.getBean(com.github.orcas.demo.controller.OrderController.class)))
                .as("@LaunchWorkflow controller must be proxied")
                .isTrue();
        assertThat(workflowEngine).isNotNull();
    }

    @Test
    void workflowLaunchPersistsInitialState() {
        Customer customer = customer();
        postApp("/orders", """
                {
                  "customerId": %d,
                  "items": []
                }
                """.formatted(customer.getId())).statusCode(202);
        await(() -> jdbcTemplate.queryForObject("select count(*) from workflow", Integer.class) > 0);
        // The controller responds before Kafka finishes the workflow; do not let
        // the next test delete customers while this execution creates its order.
        await(() -> jdbcTemplate.queryForObject(
                "select count(*) from workflow where status not in ('SUCCESS','FAILED')", Integer.class) == 0);
    }

    @Test
    void testcontainersWorkflowBurstPerformance() {
        final int workflows = 50;
        Customer customer = customer();
        stock("PERF-ITEM", workflows);
        String body = """
                {"customerId":%d,"items":[{"sku":"PERF-ITEM","quantity":1,"unitPrice":12345.01}]}
                """.formatted(customer.getId());

        java.time.OffsetDateTime launchedAfter = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
        long startedAt = System.nanoTime();
        try (var launchers = Executors.newVirtualThreadPerTaskExecutor()) {
            var requests = java.util.stream.IntStream.range(0, workflows)
                    .mapToObj(i -> CompletableFuture.runAsync(
                            () -> postApp("/orders", body).statusCode(202), launchers))
                    .toList();
            CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).join();
        }
        long launchFinishedAt = System.nanoTime();

        await(() -> jdbcTemplate.queryForObject("""
                select count(*) from workflow
                 where workflow='order-pipeline' and date_created >= ?
                """, Integer.class, launchedAfter) == workflows
                && jdbcTemplate.queryForObject("""
                select count(*) from workflow
                 where workflow='order-pipeline' and date_created >= ? and status='SUCCESS'
                """, Integer.class, launchedAfter) == workflows);
        System.out.printf("Burst state after workflow SUCCESS: orders=%d, payments=%d, steps=%s%n",
                orders.count(), payments.count(), jdbcTemplate.queryForList("""
                        select ws.step_name, ws.state, count(*) as count
                          from workflow_step ws join workflow w using (pipeline_id)
                         where w.workflow='order-pipeline' and w.date_created >= ?
                         group by ws.step_name, ws.state order by ws.step_name, ws.state
                        """, launchedAfter));
        await(() -> orders.count() == workflows && payments.count() == workflows);
        long completedAt = System.nanoTime();

        double launchRate = workflows / ((launchFinishedAt - startedAt) / 1_000_000_000d);
        double workflowRate = workflows / ((completedAt - startedAt) / 1_000_000_000d);
        System.out.printf("Testcontainers concurrent burst: %d workflows, %.1f launch req/s, %.1f workflows/s end-to-end%n",
                workflows, launchRate, workflowRate);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from workflow
                 where workflow='order-pipeline' and date_created >= ? and status='SUCCESS'
                """, Integer.class, launchedAfter)).isEqualTo(workflows);
        assertThat(orders.count()).isEqualTo(workflows);
        assertThat(payments.count()).isEqualTo(workflows);
        // Verify the same completed rows through the API used by the dashboard.
        given().port(port)
                .queryParam("workflow", "order-pipeline")
                .queryParam("status", "SUCCESS")
                .queryParam("createdFrom", launchedAfter.toInstant().toString())
                .queryParam("size", 100)
                .when().get("/api/orchestrator/workflows")
                .then().statusCode(200)
                .body("totalElements", org.hamcrest.Matchers.equalTo(workflows));
    }

    @Test
    void contextProvidesKafkaInfrastructure() {
        assertThat(kafkaTemplate).isNotNull();
        assertThat(workflowEventPublisher)
                .isInstanceOf(com.github.orcas.orchestrator.kafka.autoconfigure.KafkaWorkflowEventPublisher.class);
        assertThat(applicationBean(com.github.orcas.orchestrator.kafka.autoconfigure.WorkflowEventConsumer.class)).isNotNull();
        assertThat(kafkaListenerEndpointRegistry.getListenerContainers())
                .as("ORCAS Kafka listeners")
                .hasSize(2);
    }

    private <T> T applicationBean(Class<T> type) {
        return applicationContext.getBean(type);
    }

    @Test
    void discoversDeclarativeRestWorkflowSteps() {
        assertThat(stepCatalog.names())
                .contains(
                        "check-external-inventory",
                        "initiate-payment",
                        "refund-payment",
                        "notify-payment-success",
                        "notify-payment-failed");
    }

    @Test
    void shouldReserveInventoryStartPaymentAndConfirmOrderAfterSuccessCallback() {
        Customer customer = customer();
        stock("ORCA-BOOK", 10);

        String body = """
                {
                  "customerId": %d,
                  "items": [
                    {"sku":"ORCA-BOOK","quantity":2,"unitPrice":25.00}
                  ]
                }
                """.formatted(customer.getId());

        postApp("/orders", body).statusCode(202);

        Order order = awaitOrder(o -> o.getStatus() == OrderStatus.PENDING_PAYMENT);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("50.00");
        assertThat(inventory.findBySku("ORCA-BOOK").orElseThrow().getQuantity()).isEqualTo(8);

        Payment payment = awaitPayment(order.getId(), p -> p.getStatus() == PaymentStatus.PENDING);
        assertThat(payment.getProviderPaymentId()).isEqualTo("pay-it-success");

        postApp("/payments/callback/success", """
                {"orderId":%d,"paymentId":"%s"}
                """.formatted(order.getId(), payment.getProviderPaymentId())).statusCode(202);

        Order confirmed = awaitOrder(o ->
                o.getId().equals(order.getId()) && o.getStatus() == OrderStatus.CONFIRMED);
        assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(payments.findByOrderId(order.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.CONFIRMED);

        awaitWireMockRequest("/notifications/payment-success");
    }

    @Test
    void retriesGatewayErrorsByAttemptAndPersistsPaymentOnlyAfterSuccess() {
        Customer customer = customer();
        stock("RETRY-502", 1);
        java.time.OffsetDateTime started = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);

        postApp("/orders", orderBody(customer, "RETRY-502", "42.01")).statusCode(202);

        awaitWorkflow(started, "SUCCESS");
        await(() -> payments.count() == 1);
        assertThat(wireMockCountAsInt("/payments")).isEqualTo(2);
        await(() -> jdbcTemplate.queryForObject("""
                select retry_count from workflow_step ws join workflow w using (pipeline_id)
                 where w.workflow='order-pipeline' and w.date_created >= ? and ws.step_name='initiate-payment'
                """, Integer.class, started) == 1);
    }

    @Test
    void terminalHttp500FailsWithoutCreatingPaymentOrRetrying() {
        Customer customer = customer();
        stock("TERMINAL-500", 1);
        stock("TERMINAL-400", 1);
        java.time.OffsetDateTime started = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);

        postApp("/orders", orderBody(customer, "TERMINAL-500", "500.00")).statusCode(202);
        postApp("/orders", orderBody(customer, "TERMINAL-400", "400.00")).statusCode(202);

        await(() -> jdbcTemplate.queryForObject("""
                select count(*) from workflow where workflow='order-pipeline'
                  and date_created >= ? and status='FAILED'
                """, Integer.class, started) == 2);
        assertThat(payments.count()).isZero();
        assertThat(wireMockCountAsInt("/payments")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList("""
                select state from workflow_step where step_name='initiate-payment'
                  and pipeline_id in (select pipeline_id from workflow where workflow='order-pipeline' and date_created >= ?)
                """, String.class, started)).containsOnly("FAILED");
        assertThat(jdbcTemplate.queryForList("""
                select failure_code from workflow_step where step_name='initiate-payment'
                  and pipeline_id in (select pipeline_id from workflow where date_created >= ?)
                """, String.class, started)).containsExactlyInAnyOrder("HTTP 400", "HTTP 500");
        assertThat(jdbcTemplate.queryForList("""
                select failure_category from workflow_step where step_name='initiate-payment'
                  and pipeline_id in (select pipeline_id from workflow where date_created >= ?)
                """, String.class, started)).contains("HTTP_CLIENT_ERROR", "HTTP_INTERNAL_SERVER_ERROR");
    }

    @Test
    void retriesConnectionResetAndRateLimitResponsesUntilUpstreamRecovers() {
        Customer customer = customer();
        stock("RESET-RETRY", 1);
        stock("RATE-RETRY", 1);
        stock("EXHAUSTED-RETRY", 1);
        java.time.OffsetDateTime started = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);

        postApp("/orders", orderBody(customer, "RESET-RETRY", "7.07")).statusCode(202);
        postApp("/orders", orderBody(customer, "RATE-RETRY", "429.00")).statusCode(202);
        postApp("/orders", orderBody(customer, "EXHAUSTED-RETRY", "503.99")).statusCode(202);

        await(() -> jdbcTemplate.queryForObject("""
                select count(*) from workflow where workflow='order-pipeline'
                  and date_created >= ? and status='SUCCESS'
                """, Integer.class, started) == 2
                && jdbcTemplate.queryForObject("""
                select count(*) from workflow where workflow='order-pipeline'
                  and date_created >= ? and status='FAILED'
                """, Integer.class, started) == 1,
                () -> "workflowStates=" + jdbcTemplate.queryForList("""
                        select w.status, ws.step_name, ws.state, ws.retry_count, ws.failure_code, ws.failure_message
                          from workflow w join workflow_step ws using (pipeline_id)
                         where w.workflow='order-pipeline' and w.date_created >= ? order by w.date_created
                        """, started) + ", paymentRequests=" + wireMockCountAsInt("/payments"));

        assertThat(payments.count()).isEqualTo(2);
        assertThat(wireMockCountAsInt("/payments")).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject("""
                select retry_count from workflow_step ws join workflow w using (pipeline_id)
                 where w.workflow='order-pipeline' and w.date_created >= ? and ws.step_name='initiate-payment'
                   and w.status='FAILED'
                """, Integer.class, started)).isEqualTo(3);
    }

    @Test
    void circuitBreakerSuspendsDuringCooldownThenRunsHalfOpenRecoveryProbe() {
        Customer customer = customer();
        Order order = orders.save(new Order(customer, OrderStatus.CONFIRMED, new java.math.BigDecimal("25.00")));
        Payment payment = payments.save(new Payment(order, order.getTotalAmount(), PaymentStatus.CONFIRMED, "cb-refund-" + order.getId()));

        postApp("/payments/callback/refund", """
                {"orderId":%d,"paymentId":"%s"}
                """.formatted(order.getId(), payment.getProviderPaymentId())).statusCode(202);

        await(() -> jdbcTemplate.queryForObject("""
                select count(*) from workflow_step ws join workflow w using (pipeline_id)
                 where w.workflow='payment-refund' and ws.step_name='refund-payment' and ws.state='SUSPENDED'
                """, Integer.class) > 0);
        await(() -> orders.findById(order.getId())
                .filter(value -> value.getStatus() == OrderStatus.REFUNDED).isPresent());

        assertThat(wireMockCountAsInt("/payments/" + payment.getProviderPaymentId() + "/refund")).isEqualTo(3);
        assertThat(circuitBreakers.circuitBreaker("refund-payment").getState())
                .isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from workflow_step_log
                 where step_name='refund-payment' and snapshot_json like '%circuit breaker half-open probe%'
                """, Integer.class)).isGreaterThan(0);
    }

    @Test
    void shouldReleaseInventoryAndNotifyWhenPaymentFails() {
        Customer customer = customer();
        stock("ORCA-MUG", 5);

        postApp("/orders", """
                {
                  "customerId": %d,
                  "items": [
                    {"sku":"ORCA-MUG","quantity":2,"unitPrice":12.50}
                  ]
                }
                """.formatted(customer.getId())).statusCode(202);

        Order order = awaitOrder(o -> o.getStatus() == OrderStatus.PENDING_PAYMENT);
        Payment payment = awaitPayment(order.getId(), p -> p.getStatus() == PaymentStatus.PENDING);

        postApp("/payments/callback/failed", """
                {"orderId":%d,"paymentId":"%s"}
                """.formatted(order.getId(), payment.getProviderPaymentId())).statusCode(202);

        awaitOrder(o -> o.getId().equals(order.getId()) && o.getStatus() == OrderStatus.CANCELLED);
        assertThat(payments.findByOrderId(order.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.FAILED);
        assertThat(inventory.findBySku("ORCA-MUG").orElseThrow().getQuantity()).isEqualTo(5);

        awaitWireMockRequest("/notifications/payment-failed");
    }

    @Test
    void shouldRollbackOrderAndInventoryWhenStockIsInsufficient() {
        Customer customer = customer();
        stock("ORCA-LOW", 1);

        postApp("/orders", """
                {
                  "customerId": %d,
                  "items": [
                    {"sku":"ORCA-LOW","quantity":2,"unitPrice":10.00}
                  ]
                }
                """.formatted(customer.getId())).statusCode(202);

        await(() -> orders.count() == 0);
        assertThat(inventory.findBySku("ORCA-LOW").orElseThrow().getQuantity()).isEqualTo(1);
        assertThat(payments.count()).isZero();
    }

    @Test
    void dashboardCanRetryFailedWorkflowOrRetryItsFailedStepDirectly() {
        Customer customer = customer();
        Inventory directRetryStock = stock("RETRY-STEP", 0);
        String directRetryBody = retryOrderBody(customer, "RETRY-STEP");
        java.time.OffsetDateTime directStarted = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
        postApp("/orders", directRetryBody).statusCode(202);
        String directWorkflowId = awaitFailedWorkflow(directStarted);

        directRetryStock.setQuantity(1);
        inventory.save(directRetryStock);
        given().port(port).contentType(ContentType.JSON).when()
                .post("/api/orchestrator/workflows/{id}/steps/{step}/replay", directWorkflowId, "validate-and-reserve")
                .then().statusCode(202);
        await(() -> jdbcTemplate.queryForObject("select status from workflow where pipeline_id=?", String.class,
                directWorkflowId).equals("SUCCESS") && orders.count() == 1 && payments.count() == 1);

        Inventory batchRetryStock = stock("RETRY-WORKFLOW", 0);
        String batchRetryBody = retryOrderBody(customer, "RETRY-WORKFLOW");
        java.time.OffsetDateTime batchStarted = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
        postApp("/orders", batchRetryBody).statusCode(202);
        String batchWorkflowId = awaitFailedWorkflow(batchStarted);

        batchRetryStock.setQuantity(1);
        inventory.save(batchRetryStock);
        given().port(port).contentType(ContentType.JSON)
                .body(Map.of("workflowId", batchWorkflowId, "page", 0, "size", 25))
                .when().post("/api/orchestrator/workflows/replay")
                .then().statusCode(200)
                .body("matched", org.hamcrest.Matchers.equalTo(1))
                .body("replayed", org.hamcrest.Matchers.equalTo(1));
        await(() -> jdbcTemplate.queryForObject("select status from workflow where pipeline_id=?", String.class,
                batchWorkflowId).equals("SUCCESS") && orders.count() == 2 && payments.count() == 2);
    }

    @Test
    void shouldRefundAfterExplicitRefundCallback() {
        Customer customer = customer();
        stock("ORCA-REFUND", 4);

        postApp("/orders", """
                {
                  "customerId": %d,
                  "items": [
                    {"sku":"ORCA-REFUND","quantity":1,"unitPrice":80.00}
                  ]
                }
                """.formatted(customer.getId())).statusCode(202);

        Order order = awaitOrder(o -> o.getStatus() == OrderStatus.PENDING_PAYMENT);
        Payment payment = awaitPayment(order.getId(), p -> p.getStatus() == PaymentStatus.PENDING);

        postApp("/payments/callback/success", """
                {"orderId":%d,"paymentId":"%s"}
                """.formatted(order.getId(), payment.getProviderPaymentId())).statusCode(202);

        awaitOrder(o -> o.getId().equals(order.getId()) && o.getStatus() == OrderStatus.CONFIRMED);

        postApp("/payments/callback/refund", """
                {"orderId":%d,"paymentId":"%s"}
                """.formatted(order.getId(), payment.getProviderPaymentId())).statusCode(202);

        await(() -> orders.findById(order.getId())
                        .filter(o -> o.getStatus() == OrderStatus.REFUNDED).isPresent(),
                () -> "refund did not complete: order=" + orders.findById(order.getId()).map(Order::getStatus)
                        + ", payment=" + payments.findByOrderId(order.getId()).map(Payment::getStatus)
                        + ", refundRequests=" + wireMockCountAsInt("/payments/pay-it-success/refund")
                        + ", workflows=" + jdbcTemplate.queryForList(
                                "select workflow, status from workflow order by date_created desc limit 5")
                        + ", stepErrors=" + jdbcTemplate.queryForList("""
                                select ws.step_name, ws.state, ws.failure_category, ws.failure_code, ws.failure_message
                                  from workflow_step ws join workflow w using (pipeline_id)
                                 where w.workflow='payment-refund' order by w.date_created desc limit 3
                                """));
        assertThat(payments.findByOrderId(order.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        awaitWireMockRequest("/payments/pay-it-success/refund");
        assertThat(wireMockCountAsInt("/payments/pay-it-success/refund")).isEqualTo(3);
    }

    private Customer customer() {
        return customers.save(new Customer(
                "Integration Test Customer",
                "it-" + UUID.randomUUID() + "@example.test"));
    }

    private Inventory stock(String sku, int quantity) {
        return inventory.save(new Inventory(sku, sku, quantity));
    }

    private String retryOrderBody(Customer customer, String sku) {
        return """
                {"customerId":%d,"items":[{"sku":"%s","quantity":1,"unitPrice":12345.01}]}
                """.formatted(customer.getId(), sku);
    }

    @Test
    void carriesIdentifiersAcrossHttpKafkaStepsAndOutboundRest() {
        Customer customer = customer(); stock("CORRELATION-SKU",10);
        String correlation = "integration-correlation-42";
        String trace = "0123456789abcdef0123456789abcdef";
        given().port(port).contentType(ContentType.JSON)
                .header("X-Request-ID","integration-request-42")
                .header("X-Correlation-ID",correlation)
                .header("X-Transaction-ID","integration-transaction-42")
                .header("traceparent","00-"+trace+"-0123456789abcdef-01")
                .header("Authorization","Bearer not-to-be-persisted")
                .body(orderBody(customer,"CORRELATION-SKU","25.00"))
                .post("/orders").then().statusCode(202)
                .header("x-request-id","integration-request-42");
        awaitOrder(order -> order.getStatus() == OrderStatus.PENDING_PAYMENT);
        await(() -> jdbcTemplate.queryForObject("select count(*) from workflow_metadata where metadata_json::jsonb->>'correlationId'=?",Integer.class,correlation)>0);
        String id = jdbcTemplate.queryForObject("select pipeline_id from workflow_metadata where metadata_json::jsonb->>'correlationId'=?",String.class,correlation);
        await(() -> "SUCCESS".equals(jdbcTemplate.queryForObject(
                "select status from workflow where pipeline_id=?", String.class, id)));
        String metadata = jdbcTemplate.queryForObject("select metadata_json from workflow_metadata where pipeline_id=?",String.class,id);
        assertThat(metadata).contains("integration-request-42","integration-transaction-42",trace).doesNotContain("Authorization","not-to-be-persisted");
        var view = given().port(port).get("/api/orchestrator/workflows/"+id+"/metadata").then().statusCode(200).extract().jsonPath();
        assertThat(view.getString("identifiers.correlationId")).isEqualTo(correlation);
        String requests = given().get(wireMockUrl("/__admin/requests")).then().statusCode(200).extract().asString();
        assertThat(requests).contains("integration-request-42","integration-transaction-42",correlation,trace);
        var snapshots = jdbcTemplate.queryForList("select snapshot_json from workflow_metadata_log where pipeline_id=?",String.class,id);
        assertThat(snapshots).isNotEmpty().allSatisfy(snapshot -> assertThat(snapshot).contains(correlation,"integration-transaction-42",trace));
    }

    private String orderBody(Customer customer, String sku, String unitPrice) {
        return """
                {"customerId":%d,"items":[{"sku":"%s","quantity":1,"unitPrice":%s}]}
                """.formatted(customer.getId(), sku, unitPrice);
    }

    private String awaitWorkflow(java.time.OffsetDateTime createdAfter, String status) {
        await(() -> jdbcTemplate.queryForObject("""
                select count(*) from workflow where workflow='order-pipeline'
                  and date_created >= ? and status=?
                """, Integer.class, createdAfter, status) > 0);
        return jdbcTemplate.queryForObject("""
                select pipeline_id from workflow where workflow='order-pipeline'
                  and date_created >= ? and status=? order by date_created desc limit 1
                """, String.class, createdAfter, status);
    }

    private String awaitFailedWorkflow(java.time.OffsetDateTime createdAfter) {
        await(() -> jdbcTemplate.queryForObject("""
                select count(*) from workflow
                 where workflow='order-pipeline' and date_created >= ? and status='FAILED'
                """, Integer.class, createdAfter) == 1);
        return jdbcTemplate.queryForObject("""
                select pipeline_id from workflow
                 where workflow='order-pipeline' and date_created >= ? and status='FAILED'
                 order by date_created desc limit 1
                """, String.class, createdAfter);
    }

    private io.restassured.response.ValidatableResponse postApp(String path, String json) {
        return given()
                .port(port)
                .contentType(ContentType.JSON)
                .body(json)
                .when()
                .post(path)
                .then();
    }

    private Order awaitOrder(Predicate<Order> condition) {
        final Order[] found = new Order[1];
        await(() -> {
            found[0] = orders.findAll().stream().filter(condition).findFirst().orElse(null);
            return found[0] != null;
        });
        return found[0];
    }

    private Payment awaitPayment(Long orderId, Predicate<Payment> condition) {
        final Payment[] found = new Payment[1];
        await(() -> {
            found[0] = payments.findByOrderId(orderId).filter(condition).orElse(null);
            return found[0] != null;
        });
        return found[0];
    }

    private void awaitWireMockRequest(String urlPath) {
        await(() -> wireMockCountAsInt(urlPath) > 0);
    }

    private static void await(BooleanSupplier condition) {
        await(condition, () -> "condition within 20 seconds");
    }

    private static void await(BooleanSupplier condition, java.util.function.Supplier<String> failureDescription) {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting", e);
            }
        }
        assertThat(condition.getAsBoolean())
                .as(failureDescription.get())
                .isTrue();
    }

    private static String wiremockUrl() {
        return "http://" + WIREMOCK.getHost() + ":" + WIREMOCK.getMappedPort(8080);
    }

    private static String wireMockUrl(String path) {
        return wiremockUrl() + path;
    }

    private static void configureWireMock() throws IOException, InterruptedException {
        String notification_failed = Files.readString(Path.of("../wiremock/mappings/notification-failed.json"));
        String notification_success = Files.readString(Path.of("../wiremock/mappings/notification-success.json"));
        String payment = Files.readString(Path.of("../wiremock/mappings/payment.json"));
        String paymentPerf = Files.readString(Path.of("../wiremock/mappings/payment-perf.json"));

        String refund = Files.readString(Path.of("../wiremock/mappings/refund.json"));
        String refund2 = Files.readString(Path.of("../wiremock/mappings/refund-2.json"));
        String refund3 = Files.readString(Path.of("../wiremock/mappings/refund-3.json"));
        String payment502 = Files.readString(Path.of("../wiremock/mappings/payment-502-recovery.json"));
        String payment500 = Files.readString(Path.of("../wiremock/mappings/payment-500-terminal.json"));
        String payment503Exhausted = Files.readString(Path.of("../wiremock/mappings/payment-503-exhausted.json"));
        String payment400 = Files.readString(Path.of("../wiremock/mappings/payment-400-terminal.json"));
        String paymentReset = Files.readString(Path.of("../wiremock/mappings/payment-connection-reset.json"));
        String paymentResetRecovered = Files.readString(Path.of("../wiremock/mappings/payment-connection-reset-recovered.json"));
        String payment429 = Files.readString(Path.of("../wiremock/mappings/payment-rate-limit-recovery.json"));
        String payment503 = Files.readString(Path.of("../wiremock/mappings/payment-rate-limit-503.json"));
        String payment504 = Files.readString(Path.of("../wiremock/mappings/payment-rate-limit-504.json"));
        String paymentRateRecovered = Files.readString(Path.of("../wiremock/mappings/payment-rate-limit-recovered.json"));

        String otlpTraces = """
                {"request":{"method":"POST","urlPath":"/v1/traces"},"response":{"status":200}}
                """;
        String otlpLogs = """
                {"request":{"method":"POST","urlPath":"/v1/logs"},"response":{"status":200}}
                """;
        HttpClient client = HttpClient.newHttpClient();
        for (String mapping : new String[]{
                notification_failed,
                notification_success,
                paymentPerf,
                payment502,
                payment500,
                payment503Exhausted,
                payment400,
                paymentReset,
                paymentResetRecovered,
                payment429,
                payment503,
                payment504,
                paymentRateRecovered,
                payment,
                refund,
                refund2,
                refund3,
                otlpTraces,
                otlpLogs}) {
            client.send(HttpRequest.newBuilder(URI.create(wiremockUrl() + "/__admin/mappings"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapping))
                    .build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private static void resetWireMock() {
        HttpClient client = HttpClient.newHttpClient();
        resetWireMockEndpoint(client, "/__admin/scenarios/reset", true);
        resetWireMockEndpoint(client, "/__admin/requests", false);
    }

    private static void resetWireMockEndpoint(HttpClient client, String endpoint, boolean post) {
        try {
            var request = HttpRequest.newBuilder(URI.create(wiremockUrl() + endpoint));
            if (post) request.POST(HttpRequest.BodyPublishers.noBody()); else request.DELETE();
            HttpResponse<Void> response = client.send(request.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("WireMock reset failed for " + endpoint + ": " + response.statusCode());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to reset WireMock endpoint " + endpoint, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted resetting WireMock", e);
        }
    }

    private static void mapping(String method, String urlPathPattern, int status, String body) {
        given()
                .baseUri(wiremockUrl())
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "request": {
                            "method": "%s",
                            "urlPathPattern": "%s"
                          },
                          "response": {
                            "status": %d,
                            "headers": {"Content-Type":"application/json"},
                            "body": %s
                          }
                        }
                        """.formatted(method, urlPathPattern, status, quoteJson(body)))
                .when()
                .post("/__admin/mappings")
                .then()
                .statusCode(201);
    }

    private static String wireMockCount(String urlPath) {
        return given()
                .baseUri(wiremockUrl())
                .contentType(ContentType.JSON)
                .body("""
                        {"method":"POST","urlPath":"%s"}
                        """.formatted(urlPath))
                .when()
                .post("/__admin/requests/count")
                .then()
                .statusCode(200)
                .extract()
                .path("count")
                .toString();
    }

    private static int wireMockCountAsInt(String urlPath) {
        return Integer.parseInt(wireMockCount(urlPath));
    }

    private static String quoteJson(String value) {
        return '"' + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                + '"';
    }
}
