FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 hotelai
USER 10001
WORKDIR /app
COPY --from=build /workspace/target/customer-service-*.jar app.jar
EXPOSE 8102
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
