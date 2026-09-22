# Orcas Order Workflow Example

A compact end-to-end example using the Orcas Workflow Orchestrator starter, Spring Data JPA, Lombok and PostgreSQL.

## Flow

```text
POST /orders
  -> validate-and-reserve
  -> load-order
  -> initiate-payment (REST, 202)
  -> workflow ends waiting for provider callback

POST /payments/callback/success
  -> confirm-payment
  -> async notify-payment-success

POST /payments/callback/failed
  -> fail-payment
  -> release inventory
  -> async notify-payment-failed

POST /payments/callback/refund
  -> refund-record
  -> refund-payment (REST)
  -> refund-completed
```

The first transaction is atomic for local order/inventory/payment-pending changes. Once the payment provider is external and asynchronous, a database transaction cannot roll back the provider; compensation is therefore explicit through the refund workflow.

## Start dependencies

```bash
docker compose up -d
```

The application expects the Orcas starter artifacts at `${project.version}` in Maven.

```bash
mvn spring-boot:run
```

## Create an order

```bash
curl -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -d '{
    "customerId": 1,
    "items": [
      {"sku":"ORCA-BOOK","quantity":2,"unitPrice":25.00},
      {"sku":"ORCA-MUG","quantity":1,"unitPrice":12.50}
    ]
  }'
```

The payment mock returns `pay-demo-001` with status `PENDING`.

## Simulate successful payment callback

Use the order id created by the first workflow:

```bash
curl -X POST http://localhost:8080/payments/callback/success \
  -H 'Content-Type: application/json' \
  -d '{"orderId":1,"paymentId":"pay-demo-001"}'
```

This confirms the payment/order and asynchronously sends the success notification.

## Simulate failed payment callback

```bash
curl -X POST http://localhost:8080/payments/callback/failed \
  -H 'Content-Type: application/json' \
  -d '{"orderId":1,"paymentId":"pay-demo-001"}'
```

The failure path marks the payment failed, releases reserved inventory and sends an asynchronous notification.

## Simulate compensation/refund

```bash
curl -X POST http://localhost:8080/payments/callback/refund \
  -H 'Content-Type: application/json' \
  -d '{"orderId":1,"paymentId":"pay-demo-001"}'
```

## Important transactional boundary

`@Transactional` protects PostgreSQL changes. It cannot roll back a real external payment provider. The example therefore demonstrates the Saga/compensation boundary: provider success -> confirm order; downstream failure -> call refund API -> mark refunded.

## Dashboard

The existing Orcas service starter exposes the workflow query/admin APIs under `/api/orchestrator` so this example can be inspected in the Orcas dashboard.
