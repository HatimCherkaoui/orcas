#!/usr/bin/env bash
set -euo pipefail
java -version
mvn -version
python3 scripts/check-module-boundaries.py
python3 scripts/verify-unified-starter.py
mvn --batch-mode --no-transfer-progress clean verify
( cd workflow-orchestrator-dashboard && npm ci && npm test && npm run build )
