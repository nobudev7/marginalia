# Frontend Container Verification Guide (Step 5.2)

This document provides step-by-step instructions for building, running, and verifying the containerized frontend for **Marginalia** using the multi-stage `frontend/Dockerfile`.

---

## 1. Architecture & Security Highlights

The frontend container image provides a production-grade static web asset server designed for minimal resource consumption on small VPS instances:

* **Multi-Stage Build**:
  * **Stage 1 (`builder`)**: Uses `node:22-alpine`. Copies `package.json` and `package-lock.json` first to leverage Docker layer caching, followed by clean installation (`npm ci`) and compilation (`npm run build`).
  * **Stage 2 (Runtime)**: Minimal `caddy:2-alpine` static web server (~61.5 MB total image size).
* **Non-Root Execution**: Runs under a dedicated unprivileged system account `marginalia:marginalia` (UID `1001`).
* **Backend Reverse Proxy**: Seamlessly proxies `/api/*`, `/oauth2/*`, `/login/*`, and `/logout` to the backend service via configurable `BACKEND_URL` (defaults to `http://backend:8080`, configurable to `http://host.docker.internal:8080` for local testing).
* **SPA Routing Fallback**: Configured with `try_files {path} /index.html` to properly support client-side routing.
* **HTTP Compression**: High-efficiency on-the-fly `zstd` and `gzip` compression enabled.
* **Caching Strategy**:
  * Immutable 1-year caching for content-hashed assets (`/assets/*`): `Cache-Control: public, max-age=31536000, immutable`.
  * Immediate revalidation for PWA assets (`/sw.js` and `/manifest.webmanifest`): `Cache-Control: public, max-age=0, must-revalidate` to ensure client updates activate promptly.
* **Standard Security Headers**: Every response includes `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, and `Referrer-Policy: strict-origin-when-cross-origin`.
* **Automated Healthchecks**: Built-in Docker `HEALTHCHECK` periodically verifies server availability via `wget --spider http://localhost:80/`.

---

## 2. Step-by-Step Verification

### Step 1: Build the Container Image

From the repository root, build the Docker image tagged as `marginalia-frontend:test`:

```bash
docker build -t marginalia-frontend:test frontend/
```

> [!NOTE]
> The resulting image is approximately 61.5 MB, containing only the unprivileged Caddy binary, configuration, and compiled static assets.

---

### Step 2: Launch the Test Container

Run the container mapping container port `80` to host port `3000`. By supplying `-e BACKEND_URL=http://host.docker.internal:8080`, Caddy will forward API requests directly to the Spring Boot backend running on your host machine:

```bash
docker run --rm -d \
  --name marginalia-frontend-test \
  -p 3000:80 \
  -e BACKEND_URL=http://host.docker.internal:8080 \
  marginalia-frontend:test
```

> [!NOTE]
> Port `3000` is used for testing so it does not conflict with the local Vite development server (port `5173`) or the backend API (port `8080`).

---

### Step 3: Verify Container Health & Execution (CLI Checks)

Perform the following CLI verification checks:

#### A. Test Root HTML & Security Headers
Send a request to the root index URL:

```bash
curl -i http://localhost:3000/
```

**Expected Indicators:**
* Status: `HTTP/1.1 200 OK`
* Header: `Server: Caddy`
* Header: `X-Content-Type-Options: nosniff`
* Header: `X-Frame-Options: DENY`
* Header: `Referrer-Policy: strict-origin-when-cross-origin`
* HTML body includes `<link rel="manifest" href="/manifest.webmanifest">` and scripts.

#### B. Verify PWA Manifest & Service Worker Caching
Inspect the cache headers on PWA assets to confirm they require immediate revalidation:

```bash
curl -s -I http://localhost:3000/manifest.webmanifest
curl -s -I http://localhost:3000/sw.js
```

**Expected Indicators:**
* Status: `HTTP/1.1 200 OK`
* Header: `Cache-Control: public, max-age=0, must-revalidate`

#### C. Test SPA Route Fallback
Request a deep client-side application route that does not exist as a physical file on disk:

```bash
curl -s -I http://localhost:3000/feeds/all
```

**Expected Indicators:**
* Status: `HTTP/1.1 200 OK`
* Content-Type: `text/html; charset=utf-8` (falls back to `/index.html` for client routing).

#### D. Test Static Asset Long-Term Caching
Check the cache headers on a compiled asset:

```bash
curl -s -I http://localhost:3000/assets/index-DjcE52-N.js
```

**Expected Indicators:**
* Status: `HTTP/1.1 200 OK`
* Header: `Cache-Control: public, max-age=31536000, immutable`

#### E. Test Compression Support (zstd / gzip)
Request compressed responses:

```bash
curl -s -I -H "Accept-Encoding: zstd" http://localhost:3000/
curl -s -I -H "Accept-Encoding: gzip" http://localhost:3000/
```

**Expected Indicators:**
* Header: `Content-Encoding: zstd` (or `gzip`)

#### F. Inspect Docker Health Status
Confirm Docker's native health monitor registers the container as `healthy`:

```bash
docker inspect marginalia-frontend-test --format '{{.State.Health.Status}}'
```

**Expected Output:**
```
healthy
```

#### G. Verify Non-Root Process Security
Confirm that Caddy runs as the unprivileged user (UID `1001`):

```bash
docker top marginalia-frontend-test -o pid,user,comm
```

**Expected Output:**
```
PID    USER    COMMAND
...    1001    caddy
```

---

## 4. Browser Verification (Interactive Client & PWA Verification)

To test the actual client application running in the Docker container through your browser:

### 1. Open the Application
Open your web browser (e.g. Chrome, Firefox, Safari, or Edge) and navigate to:

```
http://localhost:3000
```

### 2. Verify Client Visuals & Session Authentication
1. **Login Card**: You should see the Marginalia login screen with its warm paper aesthetic (`#FAF8F5`), Newsreader serif typography, and login options.
2. **Dev Login**: Click the **"Dev Login (test@example.com)"** button.
   * Caddy forwards the `POST /api/auth/dev-login` request to the backend.
   * The backend authenticates the session and returns the session cookie (`MARGINALIA_SESSION`).
   * The UI immediately transitions into the authenticated app, displaying the sidebar, feeds, unread badges, and article stream.
3. **Interactive Navigation**:
   * Click category folders in the left sidebar to expand/collapse feeds.
   * Click an article card to slide open the distraction-free reading drawer.
   * Press `j` and `k` to cycle through articles, `m` to toggle read state, and `s` to bookmark.

### 3. Verify PWA Web Manifest in Browser DevTools
1. Open Developer Tools (`F12` or `Cmd+Option+I` on macOS).
2. Navigate to the **Application** tab (in Chrome / Edge) or **Storage** tab.
3. Under the **Application** sidebar, click **Manifest**:
   * **Name**: `Marginalia — Personal RSS Reader`
   * **Short Name**: `Marginalia`
   * **Start URL**: `/`
   * **Theme Color & Background Color**: `#FAF8F5`
   * **Display Mode**: `standalone`
   * **Icons**: Confirm the 192x192, 512x512, and maskable SVG/PNG icons are loaded.

### 4. Verify PWA Service Worker & Offline Caching
1. In the DevTools **Application** tab, select **Service Workers**:
   * Confirm `sw.js` is registered with status **activated and running**.
2. Switch to the DevTools **Network** tab:
   * In the network throttling dropdown (normally set to "No throttling"), select **Offline**.
3. Reload the page (`Cmd+R` or `F5`):
   * Notice that the application shell, cached CSS, JavaScript, and icons load from the service worker cache without showing the browser's "No Internet" error screen.
4. Set throttling back to **No throttling** to restore network connectivity.

---

## 5. Teardown

Once verification is complete, stop the container (it will automatically remove itself due to `--rm`):

```bash
docker stop marginalia-frontend-test
```
