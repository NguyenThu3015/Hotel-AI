FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S hotel && adduser -S hotel -G hotel
COPY --from=build /workspace/target/room-inventory-service-*.jar app.jar
USER hotel
EXPOSE 8103
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
