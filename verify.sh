#!/usr/bin/env bash
set -euo pipefail
java -version
mvn -version
python3 scripts/check-module-boundaries.py
python3 scripts/verify-unified-starter.py
python3 scripts/verify-build.py
