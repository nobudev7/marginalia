# Automated Database Backup & Disaster Recovery Guide (Step 5.5)

This document details the configuration, security architecture, execution procedures, and verification steps for automated database backups and disaster recovery in **Marginalia**.

---

## 1. Architecture & Design Highlights

The backup and restore toolset comprises two standalone executable scripts in the `deploy/` directory:

* **`deploy/backup.sh`**:
  * **Non-Blocking Hot Backup**: Uses `mysqldump --single-transaction --quick --no-tablespaces` to guarantee an ACID-consistent snapshot without locking InnoDB tables or degrading active application transactions.
  * **Streaming Compression**: Streams database output directly through `gzip -9` to minimize disk I/O, memory footprint, and peak storage consumption on small VPS instances.
  * **Format & Encoding Preservation**: Enforces `--default-character-set=utf8mb4`, `--routines`, `--triggers`, `--events`, and `--set-gtid-purged=OFF` to preserve full unicode content, emojis, and database metadata.
  * **Archive Integrity Verification**: Automatically runs `gzip -t` on the generated archive immediately upon completion to confirm checksum and format integrity.
  * **Strict Permission Hardening**: Applies `chmod 600` to backup archives so only the file owner can read or write the database dumps.
  * **Local Retention Rotation**: Automatically prunes backups older than the configured retention window (default 7 days) using `find -mtime` to prevent VPS disk exhaustion.
  * **Optional AWS S3 Off-Site Storage**: Automatically uploads archives to an Amazon S3 bucket with server-side encryption (`--sse AES256`) if `S3_BUCKET` is configured in `deploy/.env`.
  * **Environment Auto-Detection**: Reads configuration from `deploy/.env` with fallback defaults, automatically targeting the running MySQL container (`marginalia-mysql` in production or `marginalia-mysql-dev` in development).

* **`deploy/restore.sh`**:
  * **Disaster Recovery Restoration**: Restores database state from any compressed (`.sql.gz`) or plain text (`.sql`) dump.
  * **Pre-Flight Archive Verification**: Validates gzip archive integrity before touching the database.
  * **Safety Guardrails**: Requires explicit user confirmation (`Type 'yes' to proceed`) before executing, with a `--force` flag available for non-interactive headless recovery pipelines.
  * **Database Provisioning**: Ensures the target database exists with `utf8mb4` encoding before streaming data.
  * **Flyway Schema Validation**: Queries the `flyway_schema_history` table immediately following restoration to verify schema migration records and version compatibility.
  * **Record Count Audit**: Queries and prints restored row counts for `categories`, `feeds`, and `articles`.

---

## 2. Configuration Parameters

The scripts read the following variables from `deploy/.env` (with built-in defaults):

| Variable | Default Value | Description |
|---|---|---|
| `CONTAINER_NAME` | Auto-detected | Target container name (`marginalia-mysql` or `marginalia-mysql-dev`) |
| `DB_NAME` | `marginalia` | Target MySQL database name |
| `DB_USER` | `marginalia_user` | Application database username |
| `DB_PASSWORD` | `marginalia_pass` | Database user password |
| `MYSQL_ROOT_PASSWORD` | *(empty)* | Root password (used by restore script to create databases/grant permissions) |
| `BACKUP_DIR` | `deploy/backups` | Directory where local backup archives are stored |
| `BACKUP_RETENTION_DAYS` | `7` | Number of days to keep local backup archives |
| `S3_BUCKET` | *(disabled)* | Optional S3 destination (e.g. `s3://my-backup-bucket/marginalia`) |

---

## 3. Step-by-Step Verification

### Step 1: Execute Database Backup

Run the backup script from the repository root:

```bash
./deploy/backup.sh
```

**Expected Indicators:**
* Status banners show target container and database.
* Output confirms `Executing non-blocking mysqldump...`.
* Output confirms `Verifying gzip archive integrity...`.
* Success message reports filename and file size (e.g., `marginalia_marginalia_YYYYMMDD_HHMMSS.sql.gz (3.6K)`).
* Permissions on the created file in `deploy/backups/` are `-rw-------` (`600`).

---

### Step 2: Test Disaster Recovery into a Sandbox Database

To verify that the backup can be successfully restored without impacting live application data, restore the backup into a separate sandbox database (`marginalia_restore_test`):

```bash
# Locate your latest generated backup file
LATEST_BACKUP=$(ls -t deploy/backups/marginalia_*.sql.gz | head -n 1)

# Execute restore into sandbox database with --force
./deploy/restore.sh "${LATEST_BACKUP}" marginalia_restore_test --force
```

**Expected Indicators:**
* Archive integrity is validated (`Archive integrity confirmed.`).
* Target database `marginalia_restore_test` is created.
* Data streams into MySQL without syntax or privilege errors.
* Flyway schema table is verified, listing applied migrations (`V1__initial_schema`, `V2__spring_session`, `V3__whitelist`).
* Table row counts are displayed for `categories`, `feeds`, and `articles`.

---

### Step 3: Clean Up Sandbox Database

Remove the sandbox test database:

```bash
docker exec marginalia-mysql sh -c 'mysql -u root -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE IF EXISTS marginalia_restore_test;"'
```

---

## 4. Production Host Scheduling (Lightsail Setup)

On your production server (AWS Lightsail instance), configure a nightly cron job to execute the backup script automatically at 02:00 UTC:

```bash
# Open host crontab editor
crontab -e
```

Add the following cron entry (adjust path to your application directory):

```cron
0 2 * * * /opt/marginalia/deploy/backup.sh >> /var/log/marginalia-backup.log 2>&1
```

To enable off-site cloud replication, ensure the AWS CLI is configured on the host (`aws configure`) and define `S3_BUCKET=s3://your-bucket-name/marginalia` in `deploy/.env`.
