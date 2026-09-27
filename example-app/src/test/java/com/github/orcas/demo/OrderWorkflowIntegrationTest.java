package com.github.orcas.demo;

import com.github.orcas.demo.domain.Customer;
import com.github.orcas.demo.domain.Inventory;
import com.github.orcas.demo.domain.Order;
import com.github.orcas.demo.domain.OrderStatus;
import com.github.orcas.demo.domain.Payment;
import com.github.orcas.demo.domain.PaymentStatus;
import com.github.orcas.demo.repository.CustomerRepository;
import com.github.orcas.demo.repository.InventoryRepository;
import com.github.orcas.demo.repository.OrderRepository;
import com.github.orcas.demo.repository.PaymentRepository;
import com.github.orcas.orchestrator.core.builder.StepCatalog;
import com.github.orcas.orchestrator.core.event.WorkflowEventPublisher;
import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.kafka.core.KafkaTemplate;
import com.github.orcas.orchestrator.core.engine.WorkflowEngine;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;

import java.util.Map;
import org.springframework.test.context.DynamicPropertySource;
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
import java.util.UUID;
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
                    .withExposedPorts(8080)
                    .waitingFor(Wait.forHttp("/__admin/").forStatusCode(200));


    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.consumer.properties.group.protocol", () -> "classic");
        registry.add("spring.kafka.producer.key-serializer", () -> "org.apache.kafka.common.serialization.StringSerializer");
        registry.add("spring.kafka.producer.value-serializer", () -> "org.apache.kafka.common.serialization.StringSerializer");
        registry.add("management.otlp.metrics.export.enabled", () -> "false");
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

    @BeforeAll
    static void beforeAll() throws IOException, InterruptedException {
        RestAssured.filters(new RequestLoggingFilter(), new ResponseLoggingFilter());
        configureWireMock();
    }

    @BeforeEach
    void resetState() {
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

        awaitOrder(o -> o.getId().equals(order.getId()) && o.getStatus() == OrderStatus.REFUNDED);
        assertThat(payments.findByOrderId(order.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        awaitWireMockRequest("/payments/pay-it-success/refund");
        assertThat(wireMockCountAsInt("/payments/pay-it-success/refund")).isEqualTo(1);
    }

    private Customer customer() {
        return customers.save(new Customer(
                "Integration Test Customer",
                "it-" + UUID.randomUUID() + "@example.test"));
    }

    private Inventory stock(String sku, int quantity) {
        return inventory.save(new Inventory(sku, sku, quantity));
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
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting", e);
            }
        }
        assertThat(condition.getAsBoolean())
                .as("condition within 20 seconds")
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

        String refundSuccess = """
                {
                  "request": {
                    "method": "POST",
                    "urlPathPattern": "/payments/.*/refund"
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "paymentId": "pay-it-success",
                      "status": "REFUNDED"
                    },
                    "headers": {
                      "Content-Type": "application/json"
                    }
                  }
                }
                """;

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
                payment,
                refundSuccess,
                otlpTraces,
                otlpLogs}) {
            client.send(HttpRequest.newBuilder(URI.create(wiremockUrl() + "/__admin/mappings"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapping))
                    .build(), HttpResponse.BodyHandlers.ofString());
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
