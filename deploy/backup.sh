#!/usr/bin/env bash
# ==============================================================================
# Marginalia Database Backup Script
# Performs a non-blocking, compressed MySQL backup and manages retention.
# Designed for automated execution via host cron (e.g. daily at 02:00 UTC).
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source .env configuration if present
if [ -f "${SCRIPT_DIR}/.env" ]; then
  # Export variables from .env
  set -a
  # shellcheck disable=SC1091
  source "${SCRIPT_DIR}/.env"
  set +a
fi

# Configuration with fallback defaults
DB_NAME="${DB_NAME:-marginalia}"
DB_USER="${DB_USER:-marginalia_user}"
DB_PASSWORD="${DB_PASSWORD:-marginalia_pass}"
BACKUP_DIR="${BACKUP_DIR:-${SCRIPT_DIR}/backups}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-7}"
S3_BUCKET="${S3_BUCKET:-}"

# Auto-detect target MySQL container if not explicitly configured
if [ -z "${CONTAINER_NAME:-}" ]; then
  if docker ps --format '{{.Names}}' | grep -q '^marginalia-mysql$'; then
    CONTAINER_NAME="marginalia-mysql"
  elif docker ps --format '{{.Names}}' | grep -q '^marginalia-mysql-dev$'; then
    CONTAINER_NAME="marginalia-mysql-dev"
  else
    CONTAINER_NAME="marginalia-mysql"
  fi
fi

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILENAME="marginalia_${DB_NAME}_${TIMESTAMP}.sql.gz"
BACKUP_FILEPATH="${BACKUP_DIR}/${BACKUP_FILENAME}"

echo "=================================================================="
echo " Marginalia Database Backup: ${TIMESTAMP}"
echo "=================================================================="
echo "Target Container : ${CONTAINER_NAME}"
echo "Target Database  : ${DB_NAME}"
echo "Backup Directory : ${BACKUP_DIR}"

# Ensure Docker is available
if ! command -v docker >/dev/null 2>&1; then
  echo "[-] Error: docker command not found." >&2
  exit 1
fi

# Ensure MySQL container is running
if ! docker inspect -f '{{.State.Running}}' "${CONTAINER_NAME}" 2>/dev/null | grep -q "true"; then
  echo "[-] Error: Container '${CONTAINER_NAME}' is not running." >&2
  exit 1
fi

# Ensure destination directory exists
mkdir -p "${BACKUP_DIR}"

echo "[+] Executing non-blocking mysqldump..."

# Perform non-blocking dump using --single-transaction
# Pipe directly to gzip to minimize disk I/O and peak storage consumption
docker exec "${CONTAINER_NAME}" mysqldump \
  -u "${DB_USER}" \
  "-p${DB_PASSWORD}" \
  --single-transaction \
  --quick \
  --no-tablespaces \
  --routines \
  --triggers \
  --events \
  --set-gtid-purged=OFF \
  --default-character-set=utf8mb4 \
  "${DB_NAME}" | gzip -9 > "${BACKUP_FILEPATH}"

# Verify backup was created and has non-zero size
if [ ! -s "${BACKUP_FILEPATH}" ]; then
  echo "[-] Error: Backup file is empty or was not created." >&2
  rm -f "${BACKUP_FILEPATH}"
  exit 1
fi

# Verify gzip archive integrity
echo "[+] Verifying gzip archive integrity..."
if ! gzip -t "${BACKUP_FILEPATH}"; then
  echo "[-] Error: Gzip integrity check failed for ${BACKUP_FILEPATH}." >&2
  rm -f "${BACKUP_FILEPATH}"
  exit 1
fi

# Restrict permissions (read/write only by owner)
chmod 600 "${BACKUP_FILEPATH}"

BACKUP_SIZE=$(ls -lh "${BACKUP_FILEPATH}" | awk '{print $5}')
echo "[+] Backup successfully created: ${BACKUP_FILENAME} (${BACKUP_SIZE})"

# Optional AWS S3 upload
if [ -n "${S3_BUCKET}" ]; then
  if command -v aws >/dev/null 2>&1; then
    echo "[+] Uploading backup archive to S3 bucket: ${S3_BUCKET}..."
    aws s3 cp "${BACKUP_FILEPATH}" "${S3_BUCKET}/${BACKUP_FILENAME}" --sse AES256
    echo "[+] S3 upload complete."
  else
    echo "[!] Warning: S3_BUCKET configured (${S3_BUCKET}) but aws CLI not found. Skipping S3 upload."
  fi
fi

# Local retention management: prune backups older than retention window
echo "[+] Pruning local backups older than ${BACKUP_RETENTION_DAYS} days..."
PRUNED_COUNT=0
while IFS= read -r old_file; do
  if [ -n "${old_file}" ]; then
    echo "    Removing obsolete backup: $(basename "${old_file}")"
    rm -f "${old_file}"
    PRUNED_COUNT=$((PRUNED_COUNT + 1))
  fi
done < <(find "${BACKUP_DIR}" -name "marginalia_${DB_NAME}_*.sql.gz" -type f -mtime +"${BACKUP_RETENTION_DAYS}" 2>/dev/null || true)

echo "[+] Rotation complete. Pruned ${PRUNED_COUNT} old backup(s)."
echo "=================================================================="
echo " Backup finished successfully at $(date +"%Y-%m-%d %H:%M:%S")"
echo "=================================================================="
