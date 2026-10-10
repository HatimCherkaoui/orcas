#!/usr/bin/env bash
set -euo pipefail
ES_URL="${ES_URL:-http://elasticsearch:9200}"
ELASTIC_PASSWORD="${ELASTIC_PASSWORD:-adminpassword}"
curl --fail -sS -u "elastic:$ELASTIC_PASSWORD" -X POST "$ES_URL/_security/user/kibana_system/_password" -H 'Content-Type: application/json' -d "{\"password\":\"$ELASTIC_PASSWORD\"}"
curl --fail -sS -u "elastic:$ELASTIC_PASSWORD" -X PUT "$ES_URL/_ingest/pipeline/orcas-correlation" -H 'Content-Type: application/json' --data-binary @/observability/elasticsearch-pipeline.json
curl --fail -sS -u "elastic:$ELASTIC_PASSWORD" -X PUT "$ES_URL/_index_template/orcas-observability" -H 'Content-Type: application/json' --data-binary @/observability/elasticsearch-template.json
