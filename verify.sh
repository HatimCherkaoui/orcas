#!/usr/bin/env bash
set -euo pipefail
java -version
mvn -version
mvn -U clean verify
