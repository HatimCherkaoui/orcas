#!/usr/bin/env bash
set -euo pipefail

curl -s -X POST http://localhost:8080/orders -H 'Content-Type: application/json' -d '{"customerId":1,"items":[{"sku":"ORCA-BOOK","quantity":2,"unitPrice":25.00},{"sku":"ORCA-MUG","quantity":1,"unitPrice":12.50}]}'
echo

echo 'After starting, inspect the workflow dashboard or GET /orders/1.'

echo 'Success callback:'
curl -s -X POST http://localhost:8080/payments/callback/success -H 'Content-Type: application/json' -d '{"orderId":1,"paymentId":"pay-demo-001"}'
echo
