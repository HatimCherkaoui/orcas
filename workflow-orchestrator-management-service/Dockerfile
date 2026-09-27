FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -pl workflow-orchestrator-management-service -am clean package -DskipTests

FROM eclipse-temurin:25-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/workflow-orchestrator-management-service/target/management-service.jar app.jar
HEALTHCHECK --interval=10s --timeout=5s --retries=30 --start-period=30s CMD curl -fsS http://127.0.0.1:8081/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "/app/app.jar"]
