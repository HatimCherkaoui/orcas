#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}

def deps(path):
    root = ET.parse(path).getroot()
    return [x.text for x in root.findall("m:dependencies/m:dependency/m:artifactId", NS) if x.text]

root_pom = ET.parse(ROOT / "pom.xml").getroot()
parent = root_pom.find("m:parent", NS)
assert parent is not None
assert parent.findtext("m:groupId", namespaces=NS) == "org.springframework.boot"
assert parent.findtext("m:artifactId", namespaces=NS) == "spring-boot-starter-parent"
assert parent.findtext("m:version", namespaces=NS) == "4.1.1"

modules = [x.text for x in root_pom.findall("m:modules/m:module", NS)]
assert len(modules) == len(set(modules)), "duplicate Maven module declaration"

starter = deps(ROOT / "workflow-orchestrator-spring-boot-starter/pom.xml")
required = {
    "workflow-orchestrator-spring-boot-autoconfigure",
    "workflow-orchestrator-jdbc-starter",
    "workflow-orchestrator-kafka-starter",
    "workflow-orchestrator-rest-starter",
    "workflow-orchestrator-resilience-starter",
    "workflow-orchestrator-observability-starter",
    "workflow-orchestrator-service-starter",
    "workflow-orchestrator-dashboard-service-starter",
}
assert required.issubset(starter), f"unified starter missing: {sorted(required - set(starter))}"

example = set(deps(ROOT / "example-app/pom.xml"))
unified = "workflow-orchestrator-spring-boot-starter"
assert unified in example
for artifact in required - {"workflow-orchestrator-spring-boot-autoconfigure"}:
    assert artifact not in example, f"example directly declares {artifact}; unified starter should cover it"
for artifact in ("workflow-orchestrator-core", "workflow-orchestrator-rest", "workflow-orchestrator-resilience"):
    assert artifact not in example, f"example directly declares {artifact}; unified starter should cover it"

service_source = (ROOT / "workflow-orchestrator-service-autoconfigure/src/main/java/com/github/orcas/orchestrator/service/WorkflowServiceAutoConfiguration.java").read_text()
assert "WorkflowCoreAutoConfiguration.class" not in service_source
assert "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration" in service_source
assert "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration" in service_source
assert "WorkflowServiceController workflowServiceController" in service_source

dashboard_source = (ROOT / "workflow-orchestrator-dashboard-service-autoconfigure/src/main/java/com/github/orcas/orchestrator/dashboard/autoconfigure/WorkflowDashboardServiceAutoConfiguration.java").read_text()
assert "KafkaServiceController workflowKafkaServiceController" in dashboard_source
assert "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration" in dashboard_source

nginx = (ROOT / "workflow-orchestrator-dashboard/nginx.conf").read_text()
compose = (ROOT / "docker-compose.yml").read_text()
assert "proxy_pass http://workflow-management-service:8081;" in nginx
assert "dashboard:" in compose and "workflow-management-service:" in compose and "example-app:" in compose
assert "workflow-example-app" in compose and "workflow-management-service" in compose

print("Unified starter, auto-configuration, module graph and Docker dashboard contract checks passed.")
