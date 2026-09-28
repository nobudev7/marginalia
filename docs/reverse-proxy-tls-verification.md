# Reverse Proxy & Automated TLS Verification Guide (Step 5.3)

This document provides instructions for validating and testing the production Caddy configuration in `deploy/Caddyfile` for **Marginalia**.

---

## 1. Architecture & Security Highlights

The `deploy/Caddyfile` acts as the unified reverse proxy, static file server, and TLS termination layer for Marginalia:

* **Automated Let's Encrypt TLS**:
  * Configurable domain binding via `{$DOMAIN_NAME:localhost}`.
  * ACME registration using `{$ACME_EMAIL:admin@example.com}` for automated certificate renewals and notifications.
  * In local environments (`localhost`), Caddy automatically configures local development HTTPS or HTTP without contacting public ACME servers.
* **Backend API & Authentication Routing**:
  * Selectively forwards `/api/*`, `/oauth2/*`, `/login/*`, and `/logout` to the backend service via `{$BACKEND_URL:backend:8080}`.
  * Automatically sets standard proxy headers (`X-Forwarded-For`, `X-Forwarded-Proto`, `X-Forwarded-Host`) to ensure Spring Boot correctly generates OAuth2 callback redirects.
* **Single Page Application (SPA) Routing**:
  * Evaluates client routes using `try_files {path} /index.html`, ensuring deep links (e.g., `/feeds/all`, `/saved`) resolve cleanly to the client bundle without 404 errors.
* **Security Headers**:
  * **HSTS**: `Strict-Transport-Security: max-age=63072000; includeSubDomains; preload` (enforcing HTTPS across all subdomains).
  * **MIME Sniffing**: `X-Content-Type-Options: nosniff`.
  * **Framing / Clickjacking**: `X-Frame-Options: DENY`.
  * **Referrer Policy**: `Referrer-Policy: strict-origin-when-cross-origin`.
  * **Content Security Policy**: `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com data:; img-src 'self' data: https: http:; connect-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self';` (secures origin resources while permitting Google Fonts and external article images/favicons).
* **Caching Strategy**:
  * 1-year immutable caching for static assets: `Cache-Control: public, max-age=31536000, immutable`.
  * Immediate revalidation for PWA assets (`/sw.js`, `/manifest.webmanifest`): `Cache-Control: public, max-age=0, must-revalidate`.
* **Dynamic Compression**:
  * `encode zstd gzip` enables on-the-fly zstd and gzip compression.
* **Operational Observability**:
  * Structured JSON access logging to `stdout` for container log aggregators.

---

## 2. Step-by-Step Verification

### Step 1: Validate Caddyfile Syntax

Validate the Caddyfile syntax without starting a server:

```bash
docker run --rm -v $(pwd)/deploy/Caddyfile:/etc/caddy/Caddyfile:ro caddy:2-alpine caddy validate --config /etc/caddy/Caddyfile
```

**Expected Output:**
```
Valid configuration
```

---

### Step 2: Test Reverse Proxy & Static Serving Locally

You can run an isolated test container with `deploy/Caddyfile` mounted, mapping port `3001` on your host:

```bash
docker run --rm -d --name caddy-proxy-test \
  -v $(pwd)/deploy/Caddyfile:/etc/caddy/Caddyfile:ro \
  -v $(pwd)/frontend/dist:/usr/share/caddy:ro \
  -p 3001:80 \
  -e DOMAIN_NAME=:80 \
  -e BACKEND_URL=host.docker.internal:8080 \
  caddy:2-alpine
```

---

### Step 3: Verify Proxy Behaviors

Execute the following verification requests against the test container:

#### A. Root Static Page & Security Headers
Verify root page delivery, HSTS, and CSP:

```bash
curl -i http://localhost:3001/
```

**Expected Indicators:**
* Status: `HTTP/1.1 200 OK`
* Header: `Strict-Transport-Security: max-age=63072000; includeSubDomains; preload`
* Header: `Content-Security-Policy: default-src 'self'; ...`
* Header: `X-Content-Type-Options: nosniff`
* Header: `X-Frame-Options: DENY`
* Header: `Referrer-Policy: strict-origin-when-cross-origin`

#### B. API Reverse Proxying
Send a request through Caddy to the backend:

```bash
curl -i http://localhost:3001/api/auth/status
```

**Expected Indicators:**
* Status: `HTTP/1.1 200 OK`
* Header: `Via: 1.1 Caddy`
* Body: `{"authenticated":false}`

#### C. Dev-Login Proxying & Cookie Forwarding
Test authenticated session creation through Caddy:

```bash
curl -i -X POST "http://localhost:3001/api/auth/dev-login?email=test@example.com"
```

**Expected Indicators:**
* Status: `HTTP/1.1 200 OK`
* Header: `Set-Cookie: MARGINALIA_SESSION=...; Path=/; HttpOnly; SameSite=Lax`

#### D. SPA Route Fallback
Request a deep client application route:

```bash
curl -s -I http://localhost:3001/feeds/all
```

**Expected Indicators:**
* Status: `HTTP/1.1 200 OK`
* Header: `Content-Type: text/html; charset=utf-8` (falls back to `/index.html`)

#### E. HTTP Compression
Verify zstd / gzip compression:

```bash
curl -s -I -H "Accept-Encoding: zstd" http://localhost:3001/
```

**Expected Indicators:**
* Header: `Content-Encoding: zstd`

#### F. Structured JSON Logging
Inspect stdout logs to confirm structured JSON output:

```bash
docker logs caddy-proxy-test | tail -n 5
```

**Expected Indicators:**
* Valid JSON log entries containing `logger: "http.log.access.log0"`, `uri`, `status`, `duration`, `size`.

---

## 3. Teardown

Stop and remove the test container:

```bash
docker stop caddy-proxy-test
```
