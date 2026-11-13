FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x mvnw && ./mvnw -DskipTests package

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10003 hotelai
WORKDIR /app
COPY --from=build /workspace/build/backend-*.jar app.jar
USER 10003
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
