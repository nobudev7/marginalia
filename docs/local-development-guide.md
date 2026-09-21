# Local Development & Dev Profile Guide

This guide explains how to activate and use the `dev` profile in **Marginalia**, how it interacts with the fail-closed production default, and how to test authenticated features locally without third-party OAuth2 credentials.

---

## 1. Overview & Security Posture

Marginalia follows a **Secure by Default / Fail-Closed** architecture:

* **Production Baseline (`backend/src/main/resources/application.yml`)**:
  * `dev-mode: false`: The local bypass endpoint (`/api/auth/dev-login`) is completely disabled and returns HTTP `404 Not Found`.
  * `whitelist-emails: ""`: No default test email is granted administrator or access privileges.
  * `secure-cookie: true`: Session cookies mandate HTTPS (`Secure` flag).
  * `useSSL: true` / `allowPublicKeyRetrieval: false`: Database connections mandate TLS encryption and strict server identity verification.
  * If the application is launched in production without environment variables, it will **rather fail to connect or deny access than expose security vulnerabilities**.
* **Development Profile (`backend/src/main/resources/application-dev.yml`)**:
  * `dev-mode: true`: Enables the `/api/auth/dev-login` endpoint (restricted to `POST` to mitigate Cross-Site Login Attacks).
  * `whitelist-emails: test@example.com`: Authorizes a default local testing account.
  * `secure-cookie: false`: Allows plain HTTP cookies on `http://localhost:8080`.
  * `useSSL: false` / `allowPublicKeyRetrieval: true`: Connects to unencrypted local Docker MySQL.

---

## 2. How to Activate the `dev` Profile

> [!IMPORTANT]
> **Prerequisite: Start Local Database**  
> Before running the backend, start the local MySQL container from the project root:
> ```bash
> docker compose -f compose.dev.yaml up -d
> ```

You can activate the `dev` profile using any of the following methods:

### Option A: Maven Wrapper CLI (Recommended for Terminal)
Pass the `-Dspring-boot.run.profiles=dev` property to the Spring Boot Maven plugin:

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Option B: Environment Variable (Inline or Shell Export)
Set `SPRING_PROFILES_ACTIVE=dev` in your shell session:

```bash
# Inline for a single run
cd backend
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run

# Or export in your current terminal session
export SPRING_PROFILES_ACTIVE=dev
cd backend
./mvnw spring-boot:run
```

### Option C: Standalone JAR / JVM Argument
If running a compiled JAR artifact:

```bash
java -Dspring.profiles.active=dev -jar target/marginalia-backend-0.0.1-SNAPSHOT.jar
```

### Option D: Docker Container
If running the backend container in a local development environment:

```bash
docker run -d \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  --name marginalia-backend-dev \
  marginalia-backend:test
```

### Option E: IDE Configuration
* **IntelliJ IDEA**:
  1. Open **Run/Debug Configurations** > **Spring Boot** > `MarginaliaApplication`.
  2. Locate the **Active profiles** field.
  3. Enter: `dev`
  4. Click **Apply** and start the application.
* **Visual Studio Code (`launch.json`)**:
  Add `"env": { "SPRING_PROFILES_ACTIVE": "dev" }` to your launch configuration.

---

## 3. Using Local Dev Authentication (`/api/auth/dev-login`)

When the `dev` profile is active, you can authenticate without setting up Google Cloud Console or GitHub OAuth2 applications.

### In Frontend or Terminal
1. Start the backend with the `dev` profile and frontend dev server (`npm run dev`).
2. On the frontend login screen (`http://localhost:5173`), click **"Sign in as Dev User"** (which dispatches a `POST` request to `/api/auth/dev-login`).
3. The server sets a session cookie (`MARGINALIA_SESSION`).
4. You are instantly authenticated into Marginalia.

### In Terminal (`curl`)
Save the session cookie to a local jar:
```bash
curl -c cookies.txt -X POST "http://localhost:8080/api/auth/dev-login?email=test@example.com"
```

Use the session cookie for subsequent API calls:
```bash
curl -b cookies.txt "http://localhost:8080/api/articles?size=5"
```

---

## 4. Verifying Production Fail-Closed Behavior

To confirm that dev-mode is disabled when running in production mode:

1. Launch without the `dev` profile:
   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```
2. Attempt to call dev-login:
   ```bash
   curl -i -X POST "http://localhost:8080/api/auth/dev-login?email=test@example.com"
   ```
3. Verify the server returns:
   ```http
   HTTP/1.1 404 Not Found
   ```
