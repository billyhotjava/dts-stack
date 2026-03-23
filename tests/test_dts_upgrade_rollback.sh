#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

FAKE_BIN="${TMP_DIR}/bin"
mkdir -p "${FAKE_BIN}"

cat > "${FAKE_BIN}/docker" <<'EOF_DOCKER'
#!/usr/bin/env bash
compose_files=()

record_compose_event() {
  local action="$1"
  shift
  local joined=""
  local file
  for file in "$@"; do
    if [[ -z "${joined}" ]]; then
      joined="${file}"
    else
      joined="${joined},${file}"
    fi
  done
  printf '%s:%s\n' "${action}" "${joined:-cwd}" >> "${FAKE_DOCKER_EVENTS_FILE}"
}

if [[ "${1:-}" == "compose" ]]; then
  shift
  while [[ $# -gt 0 ]]; do
    case "$1" in
      -f)
        compose_files+=("$2")
        shift 2
        ;;
      ps)
        record_compose_event "ps" "${compose_files[@]}"
        if [[ -f "${FAKE_DOCKER_STATE_FILE}" ]]; then
          printf 'dts-platform\n'
        fi
        exit 0
        ;;
      down)
        record_compose_event "down" "${compose_files[@]}"
        rm -f "${FAKE_DOCKER_STATE_FILE}"
        exit 0
        ;;
      up)
        if [[ "${2:-}" == "-d" ]]; then
          record_compose_event "up" "${compose_files[@]}"
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

run_rollback() {
  local target_dir="$1"
  local backup_dir="$2"
  shift 2
  PATH="${FAKE_BIN}:${PATH}" \
    FAKE_DOCKER_STATE_FILE="${TMP_DIR}/services-running" \
    FAKE_DOCKER_EVENTS_FILE="${TMP_DIR}/docker-events.log" \
    "${REPO_ROOT}/bin/dts-upgrade-rollback" \
    --target "${target_dir}" \
    --backup-dir "${backup_dir}" \
    "$@"
}

prepare_manifest() {
  local backup_dir="$1"
  cat > "${backup_dir}/rollback-manifest.json" <<'EOF_MANIFEST'
{
  "backedUpDataDirs": [],
  "backedUpFiles": [
    ".env",
    "config/app.properties",
    "docker-compose.yml"
  ],
  "protectedDataDirs": [
    "services/dts-pg/data"
  ]
}
EOF_MANIFEST
}

NORMAL_TARGET="${TMP_DIR}/normal-target"
NORMAL_BACKUP="${TMP_DIR}/normal-backup"
mkdir -p "${NORMAL_TARGET}/config" "${NORMAL_TARGET}/services/dts-pg/data" "${NORMAL_BACKUP}/config"

cat > "${NORMAL_TARGET}/.env" <<'EOF_TARGET_ENV'
LEGACY_STACK=false
DEPLOY_MODE=single
BASE_DOMAIN=broken.local
EOF_TARGET_ENV
cat > "${NORMAL_BACKUP}/.env" <<'EOF_BACKUP_ENV'
LEGACY_STACK=false
DEPLOY_MODE=single
BASE_DOMAIN=stable.local
EOF_BACKUP_ENV

cat > "${NORMAL_TARGET}/docker-compose.yml" <<'EOF_TARGET_COMPOSE'
services: {}
EOF_TARGET_COMPOSE
cat > "${NORMAL_BACKUP}/docker-compose.yml" <<'EOF_BACKUP_COMPOSE'
services: {}
EOF_BACKUP_COMPOSE

cat > "${NORMAL_TARGET}/config/app.properties" <<'EOF_TARGET_PROPS'
site.mode=broken
EOF_TARGET_PROPS
cat > "${NORMAL_BACKUP}/config/app.properties" <<'EOF_BACKUP_PROPS'
site.mode=stable
EOF_BACKUP_PROPS

printf 'current-data' > "${NORMAL_TARGET}/services/dts-pg/data/state.txt"
prepare_manifest "${NORMAL_BACKUP}"

: > "${TMP_DIR}/docker-events.log"
run_rollback "${NORMAL_TARGET}" "${NORMAL_BACKUP}" >/dev/null

if ! grep -Fq 'BASE_DOMAIN=stable.local' "${NORMAL_TARGET}/.env"; then
  echo "expected rollback to restore target .env from backup" >&2
  exit 1
fi

if ! grep -Fq 'site.mode=stable' "${NORMAL_TARGET}/config/app.properties"; then
  echo "expected rollback to restore config file from backup" >&2
  exit 1
fi

if ! grep -Fq 'current-data' "${NORMAL_TARGET}/services/dts-pg/data/state.txt"; then
  echo "expected rollback without --restore-db to keep current postgres data directory" >&2
  exit 1
fi

if ! grep -Fqx 'down:docker-compose.yml' "${TMP_DIR}/docker-events.log"; then
  echo "expected rollback to stop current stack before restore" >&2
  cat "${TMP_DIR}/docker-events.log" >&2
  exit 1
fi

if ! grep -Fqx 'up:docker-compose.yml' "${TMP_DIR}/docker-events.log"; then
  echo "expected rollback to start restored stack after restore" >&2
  cat "${TMP_DIR}/docker-events.log" >&2
  exit 1
fi

LEGACY_TARGET="${TMP_DIR}/legacy-target"
LEGACY_BACKUP="${TMP_DIR}/legacy-backup"
mkdir -p "${LEGACY_TARGET}/services/dts-pg/data" "${LEGACY_BACKUP}/services/dts-pg/data"

cat > "${LEGACY_TARGET}/.env" <<'EOF_LEGACY_TARGET_ENV'
LEGACY_STACK=true
BASE_DOMAIN=broken-legacy.local
EOF_LEGACY_TARGET_ENV
cat > "${LEGACY_BACKUP}/.env" <<'EOF_LEGACY_BACKUP_ENV'
LEGACY_STACK=true
BASE_DOMAIN=stable-legacy.local
EOF_LEGACY_BACKUP_ENV

cat > "${LEGACY_TARGET}/docker-compose.legacy.yml" <<'EOF_LEGACY_TARGET_COMPOSE'
services: {}
EOF_LEGACY_TARGET_COMPOSE
cat > "${LEGACY_BACKUP}/docker-compose.legacy.yml" <<'EOF_LEGACY_BACKUP_COMPOSE'
services: {}
EOF_LEGACY_BACKUP_COMPOSE

printf 'current-legacy-data' > "${LEGACY_TARGET}/services/dts-pg/data/state.txt"
printf 'backup-legacy-data' > "${LEGACY_BACKUP}/services/dts-pg/data/state.txt"

cat > "${LEGACY_BACKUP}/rollback-manifest.json" <<'EOF_LEGACY_MANIFEST'
{
  "backedUpDataDirs": [
    "services/dts-pg/data"
  ],
  "backedUpFiles": [
    ".env",
    "docker-compose.legacy.yml"
  ],
  "protectedDataDirs": [
    "services/dts-pg/data"
  ]
}
EOF_LEGACY_MANIFEST

: > "${TMP_DIR}/docker-events.log"
run_rollback "${LEGACY_TARGET}" "${LEGACY_BACKUP}" --restore-db >/dev/null

if ! grep -Fq 'backup-legacy-data' "${LEGACY_TARGET}/services/dts-pg/data/state.txt"; then
  echo "expected rollback with --restore-db to restore postgres cold backup" >&2
  exit 1
fi

if ! grep -Fqx 'down:docker-compose.legacy.yml' "${TMP_DIR}/docker-events.log"; then
  echo "expected legacy rollback to stop stack with docker-compose.legacy.yml" >&2
  cat "${TMP_DIR}/docker-events.log" >&2
  exit 1
fi

if ! grep -Fqx 'up:docker-compose.legacy.yml' "${TMP_DIR}/docker-events.log"; then
  echo "expected legacy rollback to restart stack with docker-compose.legacy.yml" >&2
  cat "${TMP_DIR}/docker-events.log" >&2
  exit 1
fi
