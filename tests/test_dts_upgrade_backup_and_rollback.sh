#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

FAKE_BIN="${TMP_DIR}/bin"
SOURCE_ROOT="${TMP_DIR}/source"
TARGET_DIR="${TMP_DIR}/old-dts"
IMAGES_DIR="${TMP_DIR}/images"
EXTRA_DIR="${TMP_DIR}/extra"
STATE_FILE="${TMP_DIR}/services-running"
mkdir -p "${FAKE_BIN}" "${SOURCE_ROOT}" "${TARGET_DIR}/services/dts-pg/data" "${IMAGES_DIR}" "${EXTRA_DIR}"

cat > "${FAKE_BIN}/docker" <<'EOF_DOCKER'
#!/usr/bin/env bash
if [[ "${1:-}" == "load" && "${2:-}" == "-i" ]]; then
  exit 0
fi
if [[ "${1:-}" == "compose" ]]; then
  shift
  while [[ $# -gt 0 ]]; do
    case "$1" in
      -f)
        shift 2
        ;;
      ps)
        if [[ -f "${FAKE_DOCKER_STATE_FILE}" ]]; then
          printf 'dts-platform\n'
        fi
        exit 0
        ;;
      up)
        if [[ "${2:-}" == "-d" ]]; then
          : > "${FAKE_DOCKER_STATE_FILE}"
          exit 0
        fi
        shift
        ;;
      *)
        shift
        ;;
    esac
  done
fi
exit 0
EOF_DOCKER
chmod +x "${FAKE_BIN}/docker"

cat > "${TARGET_DIR}/docker-compose.yml" <<'EOF_COMPOSE'
services: {}
EOF_COMPOSE

cat > "${SOURCE_ROOT}/.env" <<'EOF_SOURCE_ENV'
BASE_DOMAIN=bi.new.local
NEW_FLAG=true
EOF_SOURCE_ENV

cat > "${TARGET_DIR}/.env" <<'EOF_TARGET_ENV'
BASE_DOMAIN=bi.site.local
EOF_TARGET_ENV

printf 'keep-runtime-data' > "${TARGET_DIR}/services/dts-pg/data/state.txt"

printf 'placeholder' > "${IMAGES_DIR}/placeholder.tar"
cat > "${EXTRA_DIR}/release-manifest.json" <<'EOF_MANIFEST'
{
  "images": [
    "placeholder.tar"
  ]
}
EOF_MANIFEST
(cd "${IMAGES_DIR}" && sha256sum placeholder.tar) > "${EXTRA_DIR}/checksums.txt"

PATH="${FAKE_BIN}:${PATH}" \
  FAKE_DOCKER_STATE_FILE="${STATE_FILE}" \
  DTS_UPGRADE_SOURCE_ROOT="${SOURCE_ROOT}" \
  "${REPO_ROOT}/bin/dts-upgrade" \
  --target "${TARGET_DIR}" \
  --images-dir "${IMAGES_DIR}" \
  --extra-dir "${EXTRA_DIR}" >/dev/null

BACKUP_DIR="$(find "${TARGET_DIR}/backups" -maxdepth 1 -mindepth 1 -type d -name 'upgrade-*' | head -n 1)"
if [[ -z "${BACKUP_DIR}" ]]; then
  echo "expected upgrade backup directory to be created" >&2
  exit 1
fi

if [[ ! -f "${BACKUP_DIR}/.env" ]]; then
  echo "expected target .env to be backed up before merge" >&2
  exit 1
fi

if ! grep -Fq 'BASE_DOMAIN=bi.site.local' "${BACKUP_DIR}/.env"; then
  echo "expected .env backup to contain original target content" >&2
  exit 1
fi

if ! grep -Fq 'keep-runtime-data' "${TARGET_DIR}/services/dts-pg/data/state.txt"; then
  echo "expected runtime data directory to remain untouched" >&2
  exit 1
fi

if [[ ! -f "${BACKUP_DIR}/services/dts-pg/data/state.txt" ]]; then
  echo "expected postgres data directory to be cold-backed up into backup dir" >&2
  exit 1
fi

if ! grep -Fq 'keep-runtime-data' "${BACKUP_DIR}/services/dts-pg/data/state.txt"; then
  echo "expected postgres cold backup to preserve original runtime data" >&2
  exit 1
fi

python3 - <<'PY' "${EXTRA_DIR}/rollback-manifest.json"
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fh:
    data = json.load(fh)

assert ".env" in data["backedUpFiles"], data
assert "services/dts-pg/data" in data["protectedDataDirs"], data
assert "services/dts-pg/data" in data["backedUpDataDirs"], data
PY
