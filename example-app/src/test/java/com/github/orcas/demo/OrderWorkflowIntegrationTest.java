package com.github.orcas.demo;

import com.github.orcas.demo.domain.*;
import com.github.orcas.demo.repository.CustomerRepository;
import com.github.orcas.demo.repository.InventoryRepository;
import com.github.orcas.demo.repository.OrderRepository;
import com.github.orcas.demo.repository.PaymentRepository;
import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
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
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"));

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
        registry.add("workflow.orchestrator.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("demo.payment.base-url", OrderWorkflowIntegrationTest::wiremockUrl);
        registry.add("demo.notification.base-url", OrderWorkflowIntegrationTest::wiremockUrl);
        registry.add("demo.inventory.base-url", OrderWorkflowIntegrationTest::wiremockUrl);
        registry.add("management.otlp.tracing.endpoint", () -> "http://" + WIREMOCK.getHost() + ":" + WIREMOCK.getMappedPort(8080) + "/v1/traces");
        registry.add("management.otlp.logs.endpoint", () -> "http://" + WIREMOCK.getHost() + ":" + WIREMOCK.getMappedPort(8080) + "/v1/logs");

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
        //resetWireMock();
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

    private static void resetWireMock() {
        given().baseUri(wiremockUrl())
                .when().post("/__admin/mappings/reset")
                .then().statusCode(200);
        //given().baseUri(wiremockUrl())
        //        .when().post("/__admin/requests/reset")
        //        .then().statusCode(200);
    }

    private static void configureWireMock() throws IOException, InterruptedException {
        String notification_failed = Files.readString(Path.of("../wiremock/mappings/notification-failed.json"));
        String notification_success = Files.readString(Path.of("../wiremock/mappings/notification-success.json"));
        String payment = Files.readString(Path.of("../wiremock/mappings/payment.json"));
        String payment_refund = Files.readString(Path.of("../wiremock/mappings/refund.json"));
        String payment_refund2 = Files.readString(Path.of("../wiremock/mappings/refund-2.json"));
        String payment_refund3 = Files.readString(Path.of("../wiremock/mappings/refund-3.json"));

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
                payment_refund,
                payment_refund2,
                payment_refund3,
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
