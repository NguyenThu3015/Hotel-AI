FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 hotel
COPY --from=build /workspace/target/reservation-service-*.jar app.jar
USER hotel
EXPOSE 8104
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
