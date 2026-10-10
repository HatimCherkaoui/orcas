#!/usr/bin/env bash
set -euo pipefail
KIBANA_URL="${KIBANA_URL:-http://kibana:5601}"
ELASTIC_PASSWORD="${ELASTIC_PASSWORD:-adminpassword}"
response=$(curl --fail-with-body -sS -u "elastic:$ELASTIC_PASSWORD" -X POST "$KIBANA_URL/api/saved_objects/_import?overwrite=true" -H 'kbn-xsrf: true' -F file=@/observability/kibana/orcas.ndjson)
printf '%s\n' "$response"
[[ "$response" == *'"success":true'* ]]
