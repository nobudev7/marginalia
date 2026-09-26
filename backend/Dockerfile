# Stage 1: Build stage with Maven and OpenJDK 25
FROM maven:3-eclipse-temurin-25 AS builder

WORKDIR /build

# Optimize layer caching: copy pom.xml first and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy application source code and compile production artifact
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal, hardened JRE 25 runtime stage
FROM eclipse-temurin:25-jre

WORKDIR /app

# Install curl for container health checks, create non-root user/group, and prepare app directory
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd -g 1001 marginalia && \
    useradd -u 1001 -g marginalia -s /bin/false -d /app marginalia && \
    chown -R marginalia:marginalia /app

# Copy the compiled jar from builder stage
COPY --from=builder --chown=marginalia:marginalia /build/target/marginalia-backend-*.jar /app/app.jar

# Run as non-root unprivileged user
USER marginalia:marginalia

# JVM ergonomics tailored for small VPS instances (Lightsail 1GB-2GB RAM)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/api/auth/status || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar \"$@\"", "--"]
