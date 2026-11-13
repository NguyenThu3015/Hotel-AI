FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
ENV MAVEN_OPTS="-XX:ActiveProcessorCount=2 -XX:MaxRAMPercentage=50 -XX:+UseSerialGC"
COPY pom.xml .
COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests package \
    && jar tf target/notification-service-*.jar > /dev/null

FROM build AS test
RUN --mount=type=cache,target=/root/.m2 mvn -B test

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 hotelai
USER 10001
WORKDIR /app
COPY --from=build /workspace/target/notification-service-*.jar app.jar
EXPOSE 8111
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
