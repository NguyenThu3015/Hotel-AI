FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
RUN mkdir -p /app/keys && chown app:app /app/keys
COPY --from=build /workspace/target/*.jar app.jar
USER app
EXPOSE 8101
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
