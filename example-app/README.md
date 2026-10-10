# Order example

A complete example application showing the modular setup with Spring Boot, JDBC persistence, REST workflow steps, resilience, messaging and the operational dashboard APIs.

The application is also the integration-test fixture: PostgreSQL, Kafka and WireMock run through Testcontainers so workflow behavior is exercised against real infrastructure.

The integration scenarios configure WireMock by attempt to model an upstream payment provider: a 502 recovers on retry, a rate-limited request progresses through 429/503/504 before recovery, a connection reset recovers, and 400/500 responses fail terminally without creating a payment. A refund scenario returns two 502s to open its circuit breaker, suspends the workflow during cooldown, then recovers through a half-open probe. See [TESTCONTAINERS.md](TESTCONTAINERS.md) for the scenarios and run instructions.

Circuit-breaker window, threshold, probe count, and cooldown can be adjusted with the `DEMO_CIRCUIT_BREAKER_*` environment variables. Production defaults favor a longer cooldown; the integration suite overrides these values to keep the recovery scenario fast.

The application can be reduced to a smaller set of starters to demonstrate selective module consumption.
