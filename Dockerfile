# Stage 1: Build the Fat JAR
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY src src

RUN chmod +x gradlew
RUN ./gradlew shadowJar --no-daemon

# Stage 2: Run the application
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=build /app/build/libs/*.jar ./app.jar

EXPOSE 8080

# Use the PORT env var provided by Railway, default to 8080
ENTRYPOINT ["java", "-Xmx300m", "-Xms150m", "-server", "-Dktor.deployment.port=${PORT:-8080}", "-jar", "app.jar"]