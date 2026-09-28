# Production Container Orchestration Guide (Step 5.4)

This document details the configuration, security architecture, and verification procedures for the multi-container production deployment of **Marginalia** using Docker Compose.

---

## 1. Architecture & Security Highlights

The production orchestration defined in `deploy/compose.yaml` links three containerized services into an isolated network:

```
[ Internet ]
     │  (Ports 80, 443 TCP/UDP)
     ▼
┌────────────────────────────────────────────────────────┐
│ Caddy Reverse Proxy & Static Asset Server (caddy)      │
│ - TLS termination via Let's Encrypt                    │
│ - Single Page Application static delivery              │
│ - Dynamic zstd / gzip compression & HTTP/3 support     │
│ - Security headers & rate-limiting                     │
└───────────────────────────┬────────────────────────────┘
                            │ (Internal bridge network)
                            ▼
┌────────────────────────────────────────────────────────┐
│ Spring Boot 4 Backend (backend)                        │
│ - Java 25 LTS with Virtual Threads                     │
│ - Unprivileged runtime execution (UID 1001)            │
│ - Memory tuned for Lightsail (-XX:MaxRAMPercentage=75) │
│ - Flyway automatic schema migrations                   │
└───────────────────────────┬────────────────────────────┘
                            │ (Internal bridge network)
                            ▼
┌────────────────────────────────────────────────────────┐
│ MySQL 8.4 LTS Database (mysql)                         │
│ - Persistent named volume (mysql_data)                 │
│ - Memory tuned: InnoDB buffer pool 256M (mysql.cnf)    │
│ - Full UTF-8 support (utf8mb4 / utf8mb4_unicode_ci)    │
└────────────────────────────────────────────────────────┘
```

### Key Security & Operational Controls
1. **Network Isolation**: All services communicate across a private Docker bridge network (`marginalia_network`). Only Caddy exposes public ports (`80` and `443`). MySQL (`3306`) and Spring Boot (`8080`) are isolated from the host and cannot be accessed directly from the Internet.
2. **Cascading Healthcheck Dependencies**:
   * MySQL boots and runs periodic `mysqladmin ping` healthchecks.
   * Backend delays startup until MySQL is healthy (`depends_on: mysql: condition: service_healthy`).
   * Caddy delays traffic serving until the Backend is healthy (`depends_on: backend: condition: service_healthy`).
3. **Data Persistence**:
   * Database data is preserved across container lifecycles in the named volume `mysql_data`.
   * Caddy TLS certificates and cryptographic keys are stored in `caddy_data`.
4. **Failure Recovery**: All containers are configured with `restart: unless-stopped` to automatically recover from system reboots or transient process failures.
5. **Config & Secret Isolation**: Environment configurations and secrets are decoupled into `deploy/.env` (modeled after `deploy/.env.example`), avoiding hardcoded credentials.

---

## 2. Configuration Files Reference

* **`deploy/compose.yaml`**: Declares `caddy`, `backend`, and `mysql` services, volume mounts, resource healthchecks, and internal networking.
* **`deploy/mysql.cnf`**: Production tuning for MySQL 8.4 LTS, sizing the buffer pool for small VPS instances (1 GB–2 GB RAM).
* **`deploy/.env.example`**: Environment template documenting all required secrets, OAuth2 credentials, domain bindings, and crawler settings.

---

## 3. Step-by-Step Verification

### Step 1: Validate Compose File Syntax & Variable Interpolation

Verify that the compose specification is syntactically valid and that all variables interpolate cleanly:

```bash
docker compose --env-file deploy/.env.example -f deploy/compose.yaml config
```

**Expected Indicators:**
* Command exits with return code `0`.
* The fully resolved YAML definition prints without syntax errors or warnings about undefined variables.

---

### Step 2: Validate MySQL Configuration

Verify that `deploy/mysql.cnf` complies with MySQL 8.4 LTS syntax:

```bash
docker run --rm --entrypoint mysqld -v $(pwd)/deploy/mysql.cnf:/etc/mysql/conf.d/custom.cnf:ro mysql:8.4 --validate-config
```

**Expected Indicators:**
* Command exits with return code `0` and produces no fatal configuration errors.

---

### Step 3: Stack Deployment Runbook (Local Testing & Production VPS)

This runbook applies both to local full-stack testing and remote server deployment:

#### 1. Prepare Environment
```bash
cp deploy/.env.example deploy/.env
```

Edit `deploy/.env` for your target environment:

* **For Local Development / Testing**:
  * Set `DOMAIN_NAME=localhost` *(Caddy will automatically serve `http://localhost` without contacting Let's Encrypt)*.
  * Set `DEV_MODE=true` *(enables dev login for local testing without OAuth2 setup)*.
  * Set `FRONTEND_URL=http://localhost`.
  * Strong passwords are not strictly required for local testing; default template values will work.

* **For Production Server (AWS Lightsail / Cloud VPS)**:
  * Set `DOMAIN_NAME=reader.yourdomain.com` *(your registered public domain pointing to your server's static IP)*.
  * Set `ACME_EMAIL=your-actual-email@example.com` *(for Let's Encrypt automated certificate registration)*.
  * Set `DEV_MODE=false`.
  * Set `FRONTEND_URL=https://reader.yourdomain.com`.
  * Generate strong passwords for `DB_PASSWORD` and `MYSQL_ROOT_PASSWORD` using `openssl rand -base64 24`.
  * Configure `AUTH_WHITELIST_EMAILS` with your authorized email.
  * Provide OAuth2 credentials for Google and GitHub.

#### 2. Launch the Orchestrated Stack
```bash
docker compose -f deploy/compose.yaml up -d --build
```

#### 3. Monitor Service Initialization
Follow the startup sequence across all services:

```bash
docker compose -f deploy/compose.yaml logs -f
```

**Startup Sequence:**
1. `marginalia-mysql` starts and passes its healthcheck (`healthy`).
2. `marginalia-backend` begins execution, runs Flyway schema migrations, connects to MySQL, and reports `healthy`.
3. `marginalia-caddy` binds ports 80 and 443, provisions TLS certificates, and begins serving the application.

#### 4. Check Service Health Status
Verify that all services report healthy status:

```bash
docker compose -f deploy/compose.yaml ps
```

**Expected Output:**
```
NAME                 IMAGE                      COMMAND                  SERVICE   CREATED         STATUS                   PORTS
marginalia-caddy     deploy-caddy               "caddy run --config …"   caddy     ...             Up ... (healthy)         0.0.0.0:80->80/tcp, 0.0.0.0:443->443/tcp
marginalia-backend   deploy-backend             "sh -c 'exec java $J…"   backend   ...             Up ... (healthy)         
marginalia-mysql     mysql:8.4                  "docker-entrypoint.s…"   mysql     ...             Up ... (healthy)         
```

---

## 4. Maintenance Commands

* **Stop the Stack (preserving data)**:
  ```bash
  docker compose -f deploy/compose.yaml down
  ```

* **Update and Rebuild after code changes**:
  ```bash
  git pull && docker compose -f deploy/compose.yaml up -d --build
  ```

* **Inspect Resource Usage**:
  ```bash
  docker stats marginalia-caddy marginalia-backend marginalia-mysql
  ```
