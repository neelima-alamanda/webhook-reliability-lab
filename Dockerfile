# ==============================================================================
# Multi-stage Dockerfile for Webhook Reliability Lab (Spring Boot 3 / Java 17)
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build stage using Maven and Eclipse Temurin JDK 17
# ------------------------------------------------------------------------------
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# Copy pom.xml first to cache Maven dependency downloads
COPY pom.xml .

# Download dependencies offline for caching benefits
RUN mvn dependency:go-offline -B || true

# Copy source code and package executable fat JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# ------------------------------------------------------------------------------
# Stage 2: Minimal, secure runtime stage using Eclipse Temurin JRE 17
# ------------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Run as a dedicated non-root user for security hardening
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy compiled JAR from the builder stage
COPY --from=builder /build/target/webhook-reliability-lab-*.jar app.jar

# Set file permissions
RUN chown -R appuser:appgroup /app

# Switch to non-privileged user
USER appuser:appgroup

# Expose Spring Boot web port
EXPOSE 8080

# Configure JVM flags optimized for container environments
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Healthcheck testing the OpenAPI documentation endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/v3/api-docs || exit 1

# Execute Spring Boot application
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
