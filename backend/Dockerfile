# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy dependency manifests first — cached layer speeds up rebuilds
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build the fat JAR, skipping tests
COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy

# Install curl for health checks
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser

WORKDIR /app

# Persistent data directory for H2 database
RUN mkdir -p /app/data && chown -R appuser:appuser /app

# Copy the built JAR
COPY --from=build /app/target/*.jar app.jar
RUN chown appuser:appuser app.jar

USER appuser

# Render injects PORT at runtime — default to 10000 to match Render's free tier
ENV PORT=10000
EXPOSE 10000

# Health check — Render uses this to verify the service is up
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD curl -f http://localhost:${PORT}/health || exit 1

# JVM flags tuned for Render free tier (512MB RAM)
# -Xmx400m keeps heap under the 512MB container limit
# -XX:+UseSerialGC is lighter than G1GC for small containers
ENTRYPOINT ["sh", "-c", \
    "java -Xms128m -Xmx400m -XX:+UseSerialGC \
     -Djava.security.egd=file:/dev/./urandom \
     -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod} \
     -Dserver.port=${PORT:-10000} \
     -jar app.jar"]
