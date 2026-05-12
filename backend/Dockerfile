# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Cache-bust argument — increment to force full rebuild on Render
# This ensures HealthController and all new classes are compiled fresh
ARG CACHE_BUST=3

# Copy dependency manifests first — cached layer speeds up rebuilds
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy ALL source — this layer always rebuilds when any source file changes
COPY src ./src

# Full clean build — ensures no stale .class files from previous builds
RUN mvn clean package -DskipTests -q

# Verify the JAR was built and list controllers for debugging
RUN echo "=== Built JAR ===" && ls -la target/*.jar && \
    echo "=== Controllers in JAR ===" && \
    jar tf target/*.jar | grep -i controller || true

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd -r appuser && useradd -r -g appuser appuser

WORKDIR /app
RUN mkdir -p /app/data && chown -R appuser:appuser /app

# Copy the JAR — wildcard handles any version suffix
COPY --from=build /app/target/*.jar app.jar
RUN chown appuser:appuser app.jar

USER appuser

ENV PORT=10000
EXPOSE 10000

HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD curl -f http://localhost:${PORT}/api/health || exit 1

ENTRYPOINT ["sh", "-c", \
    "java -Xms128m -Xmx400m -XX:+UseSerialGC \
     -Djava.security.egd=file:/dev/./urandom \
     -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod} \
     -Dserver.port=${PORT:-10000} \
     -jar app.jar"]
