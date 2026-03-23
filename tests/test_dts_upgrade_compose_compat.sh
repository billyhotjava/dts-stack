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
EVENTS_FILE="${TMP_DIR}/compose-events.log"
mkdir -p "${FAKE_BIN}" "${SOURCE_ROOT}" "${TARGET_DIR}" "${IMAGES_DIR}" "${EXTRA_DIR}"

cat > "${FAKE_BIN}/docker" <<'EOF_DOCKER'
#!/usr/bin/env bash
if [[ "${1:-}" == "load" && "${2:-}" == "-i" ]]; then
  exit 0
fi
if [[ "${1:-}" == "compose" ]]; then
  echo "docker: 'compose' is not a docker command." >&2
  exit 1
fi
exit 0
EOF_DOCKER
chmod +x "${FAKE_BIN}/docker"

cat > "${FAKE_BIN}/docker-compose" <<'EOF_DOCKER_COMPOSE'
#!/usr/bin/env bash
compose_file=""
if [[ "${1:-}" == "version" ]]; then
  echo "docker-compose version 1.29.2, build test"
  exit 0
fi
while [[ $# -gt 0 ]]; do
  case "$1" in
    -f)
      compose_file="$2"
      shift 2
      ;;
    ps)
      printf 'ps:%s\n' "${compose_file:-cwd}" >> "${FAKE_DOCKER_COMPOSE_EVENTS_FILE}"
      if [[ -f "${FAKE_DOCKER_STATE_FILE}" ]]; then
        printf 'dts-platform\n'
      fi
      exit 0
      ;;
    up)
      if [[ "${2:-}" == "-d" ]]; then
        printf 'up:%s\n' "${compose_file:-cwd}" >> "${FAKE_DOCKER_COMPOSE_EVENTS_FILE}"
        : > "${FAKE_DOCKER_STATE_FILE}"
        exit 0
      fi
      shift
      ;;
    config)
      if [[ "${2:-}" == "--format" && "${3:-}" == "json" ]]; then
        cat "${compose_file}.json"
        exit 0
      fi
      shift
      ;;
    *)
      shift
      ;;
  esac
done
exit 0
EOF_DOCKER_COMPOSE
chmod +x "${FAKE_BIN}/docker-compose"

cat > "${SOURCE_ROOT}/.env" <<'EOF_SOURCE_ENV'
IMAGE_POSTGRES=postgres:17.6
EOF_SOURCE_ENV

cat > "${TARGET_DIR}/.env" <<'EOF_TARGET_ENV'
IMAGE_POSTGRES=postgres:17.6
EOF_TARGET_ENV

cat > "${SOURCE_ROOT}/docker-compose.yml" <<'EOF_SOURCE_COMPOSE'
services: {}
EOF_SOURCE_COMPOSE
cat > "${SOURCE_ROOT}/docker-compose.yml.json" <<'EOF_SOURCE_JSON'
{"services":{}}
EOF_SOURCE_JSON
cat > "${TARGET_DIR}/docker-compose.yml" <<'EOF_TARGET_COMPOSE'
services: {}
EOF_TARGET_COMPOSE
cat > "${TARGET_DIR}/docker-compose.yml.json" <<'EOF_TARGET_JSON'
{"services":{}}
EOF_TARGET_JSON

printf 'placeholder' > "${IMAGES_DIR}/placeholder.tar"
cat > "${EXTRA_DIR}/release-manifest.json" <<'EOF_MANIFEST'
{
  "images": [
    "placeholder.tar"
  ]
}
EOF_MANIFEST
(cd "${IMAGES_DIR}" && sha256sum placeholder.tar) > "${EXTRA_DIR}/checksums.txt"

OUTPUT_FILE="${TMP_DIR}/upgrade.log"
set +e
PATH="${FAKE_BIN}:${PATH}" \
  FAKE_DOCKER_STATE_FILE="${STATE_FILE}" \
  FAKE_DOCKER_COMPOSE_EVENTS_FILE="${EVENTS_FILE}" \
  DTS_UPGRADE_SOURCE_ROOT="${SOURCE_ROOT}" \
  "${REPO_ROOT}/bin/dts-upgrade" \
  --target "${TARGET_DIR}" \
  --images-dir "${IMAGES_DIR}" \
  --extra-dir "${EXTRA_DIR}" >"${OUTPUT_FILE}" 2>&1
STATUS=$?
set -e

if [[ ${STATUS} -ne 0 ]]; then
  echo "expected dts-upgrade to fall back to docker-compose when docker compose is unavailable" >&2
  cat "${OUTPUT_FILE}" >&2
  exit 1
fi

if ! grep -Fqx 'up:docker-compose.yml' "${EVENTS_FILE}"; then
  echo "expected docker-compose fallback to start target stack" >&2
  cat "${EVENTS_FILE}" >&2
  exit 1
fi
