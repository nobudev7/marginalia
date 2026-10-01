#!/usr/bin/env bash
# ==============================================================================
# Marginalia Database Disaster Recovery & Restoration Script
# Restores database state from a compressed (.sql.gz) or plain (.sql) dump file.
# Verifies archive integrity, prompts for confirmation, and checks Flyway schema.
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

usage() {
  cat << EOF
Usage: $(basename "$0") <path-to-backup-file> [target-database-name] [--force]

Arguments:
  <path-to-backup-file>   Path to .sql.gz or .sql backup dump file (required)
  [target-database-name]  Target database name (optional, defaults to DB_NAME from .env or 'marginalia')
  --force                 Bypass interactive confirmation prompt

Examples:
  $(basename "$0") deploy/backups/marginalia_marginalia_20260928_020000.sql.gz
  $(basename "$0") deploy/backups/marginalia_marginalia_20260928_020000.sql.gz marginalia_restore_test --force
EOF
  exit 1
}

# Parse command line flags and arguments
FORCE=false
BACKUP_FILE=""
TARGET_DB=""

for arg in "$@"; do
  case "${arg}" in
    --force|-f)
      FORCE=true
      ;;
    -h|--help)
      usage
      ;;
    *)
      if [ -z "${BACKUP_FILE}" ]; then
        BACKUP_FILE="${arg}"
      elif [ -z "${TARGET_DB}" ]; then
        TARGET_DB="${arg}"
      else
        echo "[-] Error: Unknown argument '${arg}'" >&2
        usage
      fi
      ;;
  esac
done

if [ -z "${BACKUP_FILE}" ]; then
  echo "[-] Error: Backup file path is required." >&2
  usage
fi

if [ ! -f "${BACKUP_FILE}" ]; then
  echo "[-] Error: Backup file not found: ${BACKUP_FILE}" >&2
  exit 1
fi

TARGET_DB="${TARGET_DB:-${DB_NAME:-marginalia}}"
DB_USER="${DB_USER:-marginalia_user}"
DB_PASSWORD="${DB_PASSWORD:-marginalia_pass}"

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

echo "=================================================================="
echo " Marginalia Database Disaster Recovery & Restore"
echo "=================================================================="
echo "Backup File      : ${BACKUP_FILE}"
echo "Target Container : ${CONTAINER_NAME}"
echo "Target Database  : ${TARGET_DB}"
echo "Database User    : ${DB_USER}"

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

# Verify backup integrity if compressed
if [[ "${BACKUP_FILE}" =~ \.gz$ ]]; then
  echo "[+] Validating gzip archive integrity..."
  if ! gzip -t "${BACKUP_FILE}"; then
    echo "[-] Error: Gzip archive integrity check failed. File may be corrupted." >&2
    exit 1
  fi
  echo "[+] Archive integrity confirmed."
fi

# Interactive confirmation prompt unless forced
if [ "${FORCE}" != "true" ] && [ "${NON_INTERACTIVE:-false}" != "true" ]; then
  echo ""
  echo "WARNING: Restoring will overwrite existing data in database '${TARGET_DB}'"
  echo "inside container '${CONTAINER_NAME}'."
  read -r -p "Type 'yes' to proceed with database restoration: " CONFIRMATION
  if [ "${CONFIRMATION}" != "yes" ]; then
    echo "[!] Restoration cancelled by user."
    exit 0
  fi
fi

# Check if target database exists; if not, create it
echo "[+] Ensuring target database '${TARGET_DB}' exists..."
if [ -n "${MYSQL_ROOT_PASSWORD:-}" ]; then
  docker exec "${CONTAINER_NAME}" mysql -u root "-p${MYSQL_ROOT_PASSWORD}" \
    -e "CREATE DATABASE IF NOT EXISTS \`${TARGET_DB}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; GRANT ALL PRIVILEGES ON \`${TARGET_DB}\`.* TO '${DB_USER}'@'%'; FLUSH PRIVILEGES;"
else
  docker exec "${CONTAINER_NAME}" mysql -u "${DB_USER}" "-p${DB_PASSWORD}" \
    -e "CREATE DATABASE IF NOT EXISTS \`${TARGET_DB}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>/dev/null || true
fi

# Execute restore
echo "[+] Restoring data into '${TARGET_DB}'..."
if [[ "${BACKUP_FILE}" =~ \.gz$ ]]; then
  gzip -dc "${BACKUP_FILE}" | docker exec -i "${CONTAINER_NAME}" mysql -u "${DB_USER}" "-p${DB_PASSWORD}" "${TARGET_DB}"
else
  docker exec -i "${CONTAINER_NAME}" mysql -u "${DB_USER}" "-p${DB_PASSWORD}" "${TARGET_DB}" < "${BACKUP_FILE}"
fi

echo "[+] Database restore complete."

# Verify Flyway schema migration history
echo "[+] Validating Flyway schema compatibility..."
if docker exec "${CONTAINER_NAME}" mysql -u "${DB_USER}" "-p${DB_PASSWORD}" "${TARGET_DB}" \
  -e "SELECT version, description, success, installed_on FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;" 2>/dev/null; then
  echo "[+] Flyway schema migration table validated."
else
  echo "[!] Warning: flyway_schema_history table not found in restored database."
fi

# Verify record counts in restored database
echo "[+] Verifying restored record counts..."
docker exec "${CONTAINER_NAME}" mysql -u "${DB_USER}" "-p${DB_PASSWORD}" "${TARGET_DB}" \
  -e "SELECT 'categories' AS table_name, count(*) AS count FROM categories
      UNION ALL
      SELECT 'feeds', count(*) FROM feeds
      UNION ALL
      SELECT 'articles', count(*) FROM articles;" 2>/dev/null || true

echo "=================================================================="
echo " Restoration successfully completed at $(date +"%Y-%m-%d %H:%M:%S")"
echo "=================================================================="
