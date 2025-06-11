# Stage 1: Build the Fat JAR
FROM openjdk:17-jdk-slim AS build
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY src src

# Make gradlew executable
RUN chmod +x gradlew

# Build the fat JAR
RUN ./gradlew shadowJar --no-daemon

# Stage 2: Run the application
FROM openjdk:17-jre-slim-bookworm
WORKDIR /app

# Copy the fat JAR from the build stage
COPY --from=build /app/build/libs/*.jar ./app.jar

# Expose the port your Ktor application listens on (e.g., 8080)
EXPOSE 8080

# Command to run your Ktor application
ENTRYPOINT ["java", "-server", "-Dktor.deployment.port=8080", "-jar", "app.jar"]