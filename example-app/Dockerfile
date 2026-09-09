# syntax=docker/dockerfile:1
FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY workflow-orchestrator-core/pom.xml workflow-orchestrator-core/pom.xml
COPY workflow-orchestrator-spring-boot-starter/pom.xml workflow-orchestrator-spring-boot-starter/pom.xml
COPY workflow-orchestrator-service-starter/pom.xml workflow-orchestrator-service-starter/pom.xml
COPY example-app/pom.xml example-app/pom.xml
RUN mvn -B -pl example-app -am dependency:go-offline
COPY workflow-orchestrator-core/src workflow-orchestrator-core/src
COPY workflow-orchestrator-spring-boot-starter/src workflow-orchestrator-spring-boot-starter/src
COPY workflow-orchestrator-service-starter/src workflow-orchestrator-service-starter/src
COPY example-app/src example-app/src
RUN mvn -B -pl example-app -am clean package -DskipTests
RUN test -f example-app/target/app.jar && jar tf example-app/target/app.jar | grep -q 'BOOT-INF/'

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/example-app/target/app.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
