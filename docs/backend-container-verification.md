# Backend Container Verification Guide (Step 5.1)

This document provides step-by-step instructions for building, running, and verifying the containerized Spring Boot backend for **Marginalia** using the multi-stage `backend/Dockerfile`.

---

## 1. Architecture & Security Highlights

The backend container image is designed for efficient, secure execution on small VPS instances (such as AWS Lightsail 1 GB–2 GB plans):

* **Multi-Stage Build**:
  * **Stage 1 (`builder`)**: Uses `maven:3-eclipse-temurin-25`. Copies `pom.xml` first and executes `mvn dependency:go-offline -B` to cache dependencies in Docker layers, followed by compilation (`mvn clean package -DskipTests -B`).
  * **Stage 2 (Runtime)**: Minimal `eclipse-temurin:25-jre` slim runtime (~430 MB total image size).
* **Non-Root Execution**: Runs under a dedicated unprivileged system account `marginalia:marginalia` (UID/GID `1001`).
* **Memory Ergonomics**: Configured with `-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError` to dynamically adjust heap size to container memory limits and prevent out-of-memory kernel kills.
* **Graceful Termination**: Uses `exec java $JAVA_OPTS -jar /app/app.jar "$@"` so `SIGTERM` signals directly reach the JVM for orderly Spring Boot shutdown.
* **Automated Healthchecks**: Built-in Docker `HEALTHCHECK` periodically verifies `/api/auth/status`.

---

## 2. Prerequisites

Ensure Docker is running and the local MySQL development container is active:

```bash
docker compose -f compose.dev.yaml up -d
```

Verify that `marginalia-mysql-dev` is running:

```bash
docker ps --filter "name=marginalia-mysql-dev"
```

---

## 3. Step-by-Step Verification

### Step 1: Build the Container Image

From the repository root, build the Docker image tagged as `marginalia-backend:test`:

```bash
docker build -t marginalia-backend:test backend/
```

> [!NOTE]
> Subsequent builds will reuse cached dependency layers and compile in just a few seconds.

---

### Step 2: Launch the Test Container

Run the container attached to the `marginalia_default` Docker network so it can communicate directly with the MySQL container. 

By default, map container port `8080` directly to host port `8080`:

```bash
docker run --rm -d \
  --name marginalia-backend-test \
  --network marginalia_default \
  -p 8080:8080 \
  -e DB_HOST=mysql \
  -e DB_USE_SSL=false \
  -e DB_ALLOW_PUBLIC_KEY=true \
  -e SPRING_PROFILES_ACTIVE=dev \
  marginalia-backend:test
```

> [!NOTE]
> **Workaround if Local Spring Boot Is Running on Port 8080**  
> If you already have a local Spring Boot instance running on port `8080` (e.g. via `./mvnw spring-boot:run`) and need to keep it running, map the test container to host port `8081` instead to avoid a port collision (`bind: address already in use`):
> ```bash
> docker run --rm -d \
>   --name marginalia-backend-test \
>   --network marginalia_default \
>   -p 8081:8080 \
>   -e DB_HOST=mysql \
>   -e DB_USE_SSL=false \
>   -e DB_ALLOW_PUBLIC_KEY=true \
>   -e SPRING_PROFILES_ACTIVE=dev \
>   marginalia-backend:test
> ```
> *(If using this workaround, adjust the test curl request in Step 3B to use port `8081`).*

---

### Step 3: Verify Container Health & Execution

Perform the following 4 verification checks:

#### A. Inspect Startup Logs
Check the initialization logs to ensure Spring Boot, Flyway, and Hibernate start cleanly:

```bash
docker logs marginalia-backend-test
```

**Expected Indicators:**
* `Started MarginaliaApplication in ~3 seconds`
* `Flyway: Successfully validated X migrations`
* `Tomcat started on port 8080 (http)`
* `Starting scheduled crawl of feeds`

#### B. Test the HTTP Endpoint
Send a request to the public authentication status check endpoint:

```bash
curl -i http://localhost:8080/api/auth/status
```
*(Or `http://localhost:8081/api/auth/status` if using the port 8081 workaround).*

**Expected Response:**
```http
HTTP/1.1 200 OK
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
Content-Type: application/json

{"authenticated":false}
```

#### C. Inspect Docker Health Status
Confirm Docker's native health monitor registers the container as `healthy`:

```bash
docker inspect marginalia-backend-test --format '{{.State.Health.Status}}'
```

**Expected Output:**
```
healthy
```

#### D. Verify Non-Root Process Security
Confirm that the running Java process is owned by the unprivileged user (UID `1001`):

```bash
docker top marginalia-backend-test -o pid,user,comm
```

**Expected Output:**
```
PID    USER    COMMAND
...    1001    java
```

---

## 4. Teardown

Once verification is complete, stop the container (it will automatically remove itself due to `--rm`):

```bash
docker stop marginalia-backend-test
```
