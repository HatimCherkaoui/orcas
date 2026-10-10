# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-25 AS backend-build
WORKDIR /workspace
ENV MAVEN_OPTS="-Xms64m -Xmx512m -XX:+ExitOnOutOfMemoryError"
# Dockerfile-specific allowlist includes only Maven descriptors and main sources.
COPY . .
ARG MAVEN_THREADS=2
ARG BUILD_TIMEOUT_SECONDS=300
RUN --mount=type=cache,id=orcas-maven-repository,target=/root/.m2,sharing=locked \
    timeout --signal=TERM --kill-after=15s "${BUILD_TIMEOUT_SECONDS}s" \
    mvn --batch-mode --no-transfer-progress --fail-fast -T "${MAVEN_THREADS}" \
    -pl example-app,workflow-orchestrator-management-service -am \
    -Dmaven.test.skip=true -Dmaven.wagon.http.retryHandler.count=2 \
    -Dmaven.wagon.rto=30000 package

FROM eclipse-temurin:25-jre AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app

FROM runtime AS management
COPY --from=backend-build /workspace/workflow-orchestrator-management-service/target/management-service.jar app.jar
HEALTHCHECK --interval=10s --timeout=5s --retries=30 --start-period=30s CMD curl -fsS http://127.0.0.1:8081/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "/app/app.jar"]

FROM runtime AS example
COPY --from=backend-build /workspace/example-app/target/app.jar app.jar
HEALTHCHECK --interval=10s --timeout=5s --retries=30 --start-period=30s CMD curl -fsS http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "/app/app.jar"]
