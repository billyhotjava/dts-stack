#!/usr/bin/env sh
set -eu

# MinIO initialization script
# - Adds alias for MinIO
# - Creates the default bucket if missing
#
# Expected envs (passed from compose):
#   MINIO_ROOT_USER, MINIO_ROOT_PASSWORD, S3_BUCKET
# Optional envs:
#   MINIO_ENDPOINT (default: http://dts-minio:9000)

echo "[minio-init] Starting MinIO initialization"

MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://dts-minio:9000}"
BUCKET="${S3_BUCKET:-dts-lake}"

if [ -z "${MINIO_ROOT_USER:-}" ] || [ -z "${MINIO_ROOT_PASSWORD:-}" ]; then
  echo "[minio-init] ERROR: MINIO_ROOT_USER/MINIO_ROOT_PASSWORD not set" >&2
  exit 1
fi

# Ensure mc exists
if ! command -v mc >/dev/null 2>&1; then
  echo "[minio-init] ERROR: 'mc' client not found in image" >&2
  exit 1
fi

# Try to configure alias and wait for server to be ready
echo "[minio-init] Configuring alias for ${MINIO_ENDPOINT}"
tries=0
until mc alias set myminio "${MINIO_ENDPOINT}" "${MINIO_ROOT_USER}" "${MINIO_ROOT_PASSWORD}" >/dev/null 2>&1 \
  && mc ls myminio >/dev/null 2>&1; do
  tries=$((tries+1))
  if [ "$tries" -ge 60 ]; then
    echo "[minio-init] ERROR: Timeout waiting for MinIO at ${MINIO_ENDPOINT}" >&2
    exit 1
  fi
  sleep 2
done

echo "[minio-init] Connected. Ensuring bucket '${BUCKET}' exists"
mc mb --ignore-existing "myminio/${BUCKET}" >/dev/null 2>&1 || true

# Optionally enable versioning (best for lake-style buckets)
if mc version info "myminio/${BUCKET}" >/dev/null 2>&1; then
  mc version enable "myminio/${BUCKET}" >/dev/null 2>&1 || true
fi

echo "[minio-init] Done. Bucket '${BUCKET}' is ready"

