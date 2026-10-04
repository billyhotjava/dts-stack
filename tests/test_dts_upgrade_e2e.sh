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
EVENTS_FILE="${TMP_DIR}/docker-events.log"
STALE_FILE="${TMP_DIR}/stale-containers"
mkdir -p "${FAKE_BIN}" "${SOURCE_ROOT}/config" "${TARGET_DIR}/config" "${IMAGES_DIR}" "${EXTRA_DIR}"

cat > "${FAKE_BIN}/docker" <<'EOF_DOCKER'
#!/usr/bin/env bash
if [[ "${1:-}" == "load" && "${2:-}" == "-i" ]]; then
  printf 'load:%s\n' "${3##*/}" >> "${FAKE_DOCKER_EVENTS_FILE}"
  exit 0
fi
if [[ "${1:-}" == "compose" ]]; then
  compose_file=""
  shift
  while [[ $# -gt 0 ]]; do
    case "$1" in
      -f)
        compose_file="$2"
        shift 2
        ;;
      ps)
        if [[ -f "${FAKE_DOCKER_STATE_FILE}" ]]; then
          printf 'dts-platform\n'
        fi
        exit 0
        ;;
      config)
        if [[ "${2:-}" == "--format" && "${3:-}" == "json" ]]; then
          cat "${compose_file}.json"
          exit 0
        fi
        ;;
      down)
        rm -f "${FAKE_STALE_CONTAINERS_FILE}" "${FAKE_DOCKER_STATE_FILE}"
        printf 'down:%s\n' "${compose_file:-cwd}" >> "${FAKE_DOCKER_EVENTS_FILE}"
        exit 0
        ;;
      up)
        if [[ "${2:-}" == "-d" ]]; then
          if [[ -f "${FAKE_STALE_CONTAINERS_FILE}" ]]; then
            echo "Cannot start service: network deadbeef not found" >&2
            exit 1
          fi
          : > "${FAKE_DOCKER_STATE_FILE}"
          printf 'up:%s\n' "${compose_file:-cwd}" >> "${FAKE_DOCKER_EVENTS_FILE}"
          exit 0
        fi
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

cat > "${SOURCE_ROOT}/.env" <<'EOF_SOURCE_ENV'
BASE_DOMAIN=bi.new.local
NEW_FLAG=true
EOF_SOURCE_ENV

cat > "${TARGET_DIR}/.env" <<'EOF_TARGET_ENV'
BASE_DOMAIN=bi.site.local
EOF_TARGET_ENV

cat > "${SOURCE_ROOT}/docker-compose-app.yml" <<'EOF_SOURCE_COMPOSE'
services: {}
EOF_SOURCE_COMPOSE
cat > "${SOURCE_ROOT}/docker-compose-app.yml.json" <<'EOF_SOURCE_JSON'
{
  "services": {
    "platform": {
      "image": "dts-platform:new"
    }
  }
}
EOF_SOURCE_JSON

cat > "${TARGET_DIR}/docker-compose-app.yml" <<'EOF_TARGET_COMPOSE'
services: {}
EOF_TARGET_COMPOSE
cat > "${TARGET_DIR}/docker-compose-app.yml.json" <<'EOF_TARGET_JSON'
{
  "services": {
    "platform": {
      "image": "dts-platform:old"
    }
  }
}
EOF_TARGET_JSON

cat > "${SOURCE_ROOT}/config/app.properties" <<'EOF_SOURCE_PROPERTIES'
new.flag=true
EOF_SOURCE_PROPERTIES
cat > "${TARGET_DIR}/config/app.properties" <<'EOF_TARGET_PROPERTIES'
site.mode=custom
EOF_TARGET_PROPERTIES

printf 'placeholder' > "${IMAGES_DIR}/placeholder.tar"
cat > "${EXTRA_DIR}/release-manifest.json" <<'EOF_MANIFEST'
{
  "images": [
    "placeholder.tar"
  ]
}
EOF_MANIFEST
(cd "${IMAGES_DIR}" && sha256sum placeholder.tar) > "${EXTRA_DIR}/checksums.txt"
touch "${STALE_FILE}"

PATH="${FAKE_BIN}:${PATH}" \
  FAKE_DOCKER_STATE_FILE="${STATE_FILE}" \
  FAKE_DOCKER_EVENTS_FILE="${EVENTS_FILE}" \
  FAKE_STALE_CONTAINERS_FILE="${STALE_FILE}" \
  DTS_UPGRADE_SOURCE_ROOT="${SOURCE_ROOT}" \
  "${REPO_ROOT}/bin/dts-upgrade" \
  --target "${TARGET_DIR}" \
  --images-dir "${IMAGES_DIR}" \
  --extra-dir "${EXTRA_DIR}" >/dev/null

if ! grep -Fq 'down:docker-compose-app.yml' "${EVENTS_FILE}"; then
  echo "expected upgrade flow to clean stale containers before start" >&2
  cat "${EVENTS_FILE}" >&2
  exit 1
fi

if ! grep -Fq 'up:docker-compose-app.yml' "${EVENTS_FILE}"; then
  echo "expected upgrade flow to start target stack" >&2
  cat "${EVENTS_FILE}" >&2
  exit 1
fi

SUMMARY_FILE="$(find "${TARGET_DIR}/logs" -maxdepth 1 -type f -name 'upgrade-*.summary.md' | head -n 1)"
if [[ -z "${SUMMARY_FILE}" ]]; then
  echo "expected summary file to exist" >&2
  exit 1
fi

for expected_line in \
  '- state PRECHECK' \
  '- state LOAD_IMAGES' \
  '- state MERGE_ENV' \
  '- state MERGE_COMPOSE' \
  '- state MERGE_CONFIG' \
  '- state START_CONTAINERS' \
  '- state POSTCHECK' \
  '- final status: success'
do
  if ! grep -Fq -- "${expected_line}" "${SUMMARY_FILE}"; then
    echo "expected summary to contain ${expected_line}" >&2
    cat "${SUMMARY_FILE}" >&2
    exit 1
  fi
done

if ! grep -Fq 'NEW_FLAG=true' "${TARGET_DIR}/.env"; then
  echo "expected env merge to happen during e2e flow" >&2
  exit 1
fi

if ! grep -Fq 'new.flag=true' "${TARGET_DIR}/config/app.properties"; then
  echo "expected config merge to happen during e2e flow" >&2
  exit 1
fi
