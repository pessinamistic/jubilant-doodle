# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — Build the React frontend
# ─────────────────────────────────────────────────────────────────────────────
FROM node:22-alpine AS frontend-builder

WORKDIR /app/frontend

# Install dependencies first (better layer caching)
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm ci

# Copy source and build
COPY frontend/ ./
RUN npm run build
# Output: /app/frontend/dist

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — Build the Spring Boot backend (with frontend baked in)
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS backend-builder

WORKDIR /app/backend

# Copy Gradle wrapper + build files first (layer caching)
COPY backend/gradlew backend/gradlew.bat ./
COPY backend/gradle/ ./gradle/
COPY backend/build.gradle.kts backend/settings.gradle.kts ./

# Pre-download dependencies (cache layer)
RUN ./gradlew dependencies --no-daemon -q 2>/dev/null || true

# Copy backend source
COPY backend/src/ ./src/

# Copy the built frontend into Spring Boot's static resources directory
# Spring Boot auto-serves anything under classpath:/static/
COPY --from=frontend-builder /app/frontend/dist ./src/main/resources/static/

# Build the fat JAR (skip tests — they need a running Docker daemon)
RUN ./gradlew bootJar --no-daemon -x test
# Output: /app/backend/build/libs/db-deployer-*.jar

# ─────────────────────────────────────────────────────────────────────────────
# Stage 3 — Minimal runtime image
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Create the data directory where DB Deployer stores managed DB volumes
# This will be bind-mounted by docker-compose, but creating it here
# ensures correct ownership when the container creates it on first run.
RUN mkdir -p /root/.db-deployer/data

# Copy the fat JAR from the builder stage
COPY --from=backend-builder /app/backend/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
