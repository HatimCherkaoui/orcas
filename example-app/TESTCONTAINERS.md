# Testcontainers integration tests

`OrderWorkflowIntegrationTest` starts three containers:

- PostgreSQL 18
- Kafka 4.3.1
- WireMock 3.13.1

The Spring Boot application itself runs on a random port. Dynamic properties point JPA, Kafka and the three REST clients at the containers.

## Scenarios covered

1. Order creation -> inventory reservation -> payment initiation -> success callback -> order confirmation -> asynchronous success notification.
2. Order creation -> payment failure callback -> inventory release -> asynchronous failure notification.
3. Insufficient inventory -> workflow failure with no persisted order/payment and unchanged stock.
4. Confirmed payment -> explicit refund callback -> refund API -> refunded order/payment.

## Run

```bash
mvn test
```

Docker must be running. The test does not require the local PostgreSQL/Kafka/WireMock services from `docker-compose.yml`.
