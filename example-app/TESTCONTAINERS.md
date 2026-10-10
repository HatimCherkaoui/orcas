# Testcontainers integration tests

`OrderWorkflowIntegrationTest` starts three containers:

- PostgreSQL 18
- Kafka 4.3.1
- WireMock 3.13.2

The Spring Boot application itself runs on a random port. Dynamic properties point JPA, Kafka and the three REST clients at the containers.

## Scenarios covered

1. Successful order -> inventory reservation -> payment initiation -> success callback -> confirmation -> asynchronous notification.
2. Order creation -> payment failure callback -> inventory release -> asynchronous failure notification.
3. Insufficient inventory -> workflow failure with no payment and unchanged stock.
4. HTTP 502 -> automatic replay -> success, with payment persistence only after the successful response.
5. Connection reset and HTTP 429 -> 503 -> 504 attempt sequences -> automatic replay until recovery; the rate-limit response supplies `Retry-After`.
6. HTTP 400 and 500 -> terminal step/workflow failure with no automatic replay or payment side effect.
7. Repeated HTTP 503 exhausts the configured retry limit and fails the step.
8. Refund endpoint returns two 502 responses, opening the refund circuit breaker; the workflow is suspended through cooldown, a half-open probe succeeds, and the breaker closes.
9. Confirmed payment -> explicit refund callback -> refunded order/payment.

WireMock mappings use scenario state to return responses by attempt. The suite resets scenario state and its request journal before each test, and uses short retry/circuit-breaker cooldowns so recovery phases are observable without production-length waits.

## Run

```bash
mvn test
```

Docker must be running. The test does not require the local PostgreSQL/Kafka/WireMock services from `docker-compose.yml`.
