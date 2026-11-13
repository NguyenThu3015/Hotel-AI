FROM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 appuser
WORKDIR /app
COPY --from=build /workspace/target/reporting-audit-service-0.1.0-SNAPSHOT.jar app.jar
USER appuser
EXPOSE 8112
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
