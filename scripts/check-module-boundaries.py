#!/usr/bin/env python3
"""Static checks for the published module boundaries."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}

EXPECTED = {
    "workflow-orchestrator-core": set(),
    "workflow-orchestrator-spring-boot-autoconfigure": {"workflow-orchestrator-core"},
    "workflow-orchestrator-rest": {"workflow-orchestrator-core"},
    "workflow-orchestrator-rest-autoconfigure": {"workflow-orchestrator-core", "workflow-orchestrator-rest", "workflow-orchestrator-spring-boot-autoconfigure"},
    "workflow-orchestrator-resilience": {"workflow-orchestrator-core"},
    "workflow-orchestrator-resilience-autoconfigure": {"workflow-orchestrator-core", "workflow-orchestrator-resilience"},
    "workflow-orchestrator-jdbc-autoconfigure": {"workflow-orchestrator-core"},
    "workflow-orchestrator-kafka-autoconfigure": {"workflow-orchestrator-core", "workflow-orchestrator-service", "workflow-orchestrator-spring-boot-autoconfigure"},
    "workflow-orchestrator-observability-autoconfigure": {"workflow-orchestrator-core"},
    "workflow-orchestrator-service": {"workflow-orchestrator-core"},
    "workflow-orchestrator-service-autoconfigure": {"workflow-orchestrator-core", "workflow-orchestrator-jdbc-autoconfigure", "workflow-orchestrator-service"},
    "workflow-orchestrator-dashboard-service": set(),
    "workflow-orchestrator-dashboard-service-autoconfigure": {"workflow-orchestrator-dashboard-service"},
}


PURE_API_MODULES = {
    "workflow-orchestrator-rest",
    "workflow-orchestrator-resilience",
    "workflow-orchestrator-service",
    "workflow-orchestrator-dashboard-service",
}

def local_dependencies(module: str) -> set[str]:
    root = ET.parse(ROOT / module / "pom.xml").getroot()
    return {
        node.text
        for node in root.findall("m:dependencies/m:dependency/m:artifactId", NS)
        if node.text and node.text.startswith("workflow-orchestrator-")
    }

def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)

for module, expected in EXPECTED.items():
    actual = local_dependencies(module)
    if actual != expected:
        fail(f"{module}: expected local dependencies {sorted(expected)}, found {sorted(actual)}")

for module in ("workflow-orchestrator-core", *PURE_API_MODULES):
    for source in (ROOT / module / "src/main/java").rglob("*.java"):
        text = source.read_text(encoding="utf-8")
        for forbidden in ("org.springframework", "org.slf4j", "reactor", "io.github.resilience4j", "jakarta.persistence", "org.apache.kafka"):
            if f"import {forbidden}" in text:
                fail(f"framework/transport-free module must not import {forbidden}: {source}")

print("Module boundary checks passed.")
