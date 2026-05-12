# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
RUN mkdir -p /app/data

COPY --from=build /app/target/*.jar app.jar

# Render injects PORT at runtime — default 8080 as safe fallback
ENV PORT=8080
EXPOSE 8080

# Render requires the app to bind to 0.0.0.0:$PORT
# Pass PORT explicitly as both JVM system property and Spring property
# -Dserver.address=0.0.0.0 ensures binding on all interfaces (required by Render)
ENTRYPOINT ["sh", "-c", \
    "exec java \
     -Xms128m -Xmx400m \
     -XX:+UseSerialGC \
     -Djava.security.egd=file:/dev/./urandom \
     -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod} \
     -Dserver.port=${PORT} \
     -Dserver.address=0.0.0.0 \
     -jar app.jar"]
