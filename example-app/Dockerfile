FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /workspace
COPY . .

RUN mvn -B -pl example-app -am clean package -DskipTests

FROM eclipse-temurin:25-jre

WORKDIR /app
COPY --from=build /workspace/example-app/target/*.jar app.jar

ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "/app/app.jar"]
