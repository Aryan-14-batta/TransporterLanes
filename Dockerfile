# syntax=docker/dockerfile:1

# ---- Build stage: compile and package the executable Spring Boot JAR -------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Resolve dependencies in their own layer so code-only changes rebuild quickly.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src/ src/
# Tests run locally/CI (they need no database); skipping them here keeps image builds fast.
RUN mvn -B -q -DskipTests package

# ---- Runtime stage: JRE only, non-root user ----------------------------------------------------------------------
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --no-create-home app
WORKDIR /app

COPY --from=build /workspace/target/*.jar app.jar

USER app

# Documentation only: the app listens on $PORT (Render's default is 10000), or 8080 when PORT is unset.
EXPOSE 10000

# Size the heap relative to the container's memory limit (e.g. 512 MB on small instances).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

# Database settings (DB_URL, DB_USERNAME, DB_PASSWORD) are supplied as environment variables at runtime.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
