#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

FAKE_BIN="${TMP_DIR}/bin"
SOURCE_ROOT="${TMP_DIR}/source"
STATE_FILE="${TMP_DIR}/services-running"
EVENTS_FILE="${TMP_DIR}/docker-events.log"
mkdir -p "${FAKE_BIN}" "${SOURCE_ROOT}"

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

if [[ "${1:-}" == "load" && "${2:-}" == "-i" ]]; then
  exit 0
fi

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
      up)
        if [[ "${2:-}" == "-d" ]]; then
          record_compose_event "up" "${compose_files[@]}"
          : > "${FAKE_DOCKER_STATE_FILE}"
          exit 0
        fi
        shift
        ;;
      config)
        if [[ "${2:-}" == "--format" && "${3:-}" == "json" ]]; then
          compose_file="${compose_files[${#compose_files[@]}-1]}"
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
fi
exit 0
EOF_DOCKER
chmod +x "${FAKE_BIN}/docker"

run_upgrade() {
  local target_dir="$1"
  local images_dir="$2"
  local extra_dir="$3"
  PATH="${FAKE_BIN}:${PATH}" \
    FAKE_DOCKER_STATE_FILE="${STATE_FILE}" \
    FAKE_DOCKER_EVENTS_FILE="${EVENTS_FILE}" \
    DTS_UPGRADE_SOURCE_ROOT="${SOURCE_ROOT}" \
    "${REPO_ROOT}/bin/dts-upgrade" \
    --target "${target_dir}" \
    --images-dir "${images_dir}" \
    --extra-dir "${extra_dir}"
}

prepare_package() {
  local images_dir="$1"
  local extra_dir="$2"
  mkdir -p "${images_dir}" "${extra_dir}"
  printf 'placeholder' > "${images_dir}/placeholder.tar"
  cat > "${extra_dir}/release-manifest.json" <<'EOF_MANIFEST'
{
  "images": [
    "placeholder.tar"
  ]
}
EOF_MANIFEST
  (
    cd "${images_dir}"
    sha256sum placeholder.tar
  ) > "${extra_dir}/checksums.txt"
}

prepare_empty_compose_json() {
  local compose_file="$1"
  cat > "${compose_file}" <<'EOF_COMPOSE'
services: {}
EOF_COMPOSE
  cat > "${compose_file}.json" <<'EOF_JSON'
{
  "services": {}
}
EOF_JSON
}

expect_failure() {
  local expected="$1"
  shift
  local output_file="${TMP_DIR}/failure.log"
  set +e
  run_upgrade "$@" >"${output_file}" 2>&1
  local status=$?
  set -e
  if [[ ${status} -eq 0 ]]; then
    echo "expected dts-upgrade to fail" >&2
    cat "${output_file}" >&2
    exit 1
  fi
  if ! grep -Fq "${expected}" "${output_file}"; then
    echo "expected failure output to contain: ${expected}" >&2
    cat "${output_file}" >&2
    exit 1
  fi
}

expect_success() {
  local output_file="${TMP_DIR}/success.log"
  if ! run_upgrade "$@" >"${output_file}" 2>&1; then
    cat "${output_file}" >&2
    exit 1
  fi
}

LEGACY_TARGET="${TMP_DIR}/legacy-target"
LEGACY_IMAGES="${TMP_DIR}/legacy-images"
LEGACY_EXTRA="${TMP_DIR}/legacy-extra"
mkdir -p "${LEGACY_TARGET}"
prepare_package "${LEGACY_IMAGES}" "${LEGACY_EXTRA}"

cat > "${SOURCE_ROOT}/.env" <<'EOF_SOURCE_ENV'
LEGACY_STACK=false
IMAGE_POSTGRES=postgres:17.6
EOF_SOURCE_ENV

cat > "${LEGACY_TARGET}/.env" <<'EOF_TARGET_ENV'
LEGACY_STACK=true
IMAGE_POSTGRES=postgres:17.6
EOF_TARGET_ENV

prepare_empty_compose_json "${SOURCE_ROOT}/docker-compose.legacy.yml"
prepare_empty_compose_json "${SOURCE_ROOT}/docker-compose.yml"
prepare_empty_compose_json "${SOURCE_ROOT}/docker-compose-app.yml"
prepare_empty_compose_json "${LEGACY_TARGET}/docker-compose.legacy.yml"

: > "${EVENTS_FILE}"
expect_success "${LEGACY_TARGET}" "${LEGACY_IMAGES}" "${LEGACY_EXTRA}"

if ! grep -Fqx "up:docker-compose.legacy.yml" "${EVENTS_FILE}"; then
  echo "expected legacy upgrade to start with docker-compose.legacy.yml" >&2
  cat "${EVENTS_FILE}" >&2
  exit 1
fi

if [[ -e "${LEGACY_TARGET}/docker-compose.yml" || -e "${LEGACY_TARGET}/docker-compose-app.yml" ]]; then
  echo "expected legacy upgrade not to copy normal compose files into target" >&2
  find "${LEGACY_TARGET}" -maxdepth 1 -type f -name 'docker-compose*.yml' | sort >&2
  exit 1
fi

rm -f "${STATE_FILE}"

NORMAL_TARGET="${TMP_DIR}/normal-target"
NORMAL_IMAGES="${TMP_DIR}/normal-images"
NORMAL_EXTRA="${TMP_DIR}/normal-extra"
mkdir -p "${NORMAL_TARGET}"
prepare_package "${NORMAL_IMAGES}" "${NORMAL_EXTRA}"

cat > "${NORMAL_TARGET}/.env" <<'EOF_NORMAL_TARGET_ENV'
LEGACY_STACK=false
DEPLOY_MODE=single
IMAGE_POSTGRES=postgres:17.6
EOF_NORMAL_TARGET_ENV

prepare_empty_compose_json "${SOURCE_ROOT}/docker-compose.yml"
prepare_empty_compose_json "${SOURCE_ROOT}/docker-compose-app.yml"
prepare_empty_compose_json "${NORMAL_TARGET}/docker-compose.yml"

: > "${EVENTS_FILE}"
expect_success "${NORMAL_TARGET}" "${NORMAL_IMAGES}" "${NORMAL_EXTRA}"

if ! grep -Fqx "up:docker-compose.yml,docker-compose-app.yml" "${EVENTS_FILE}"; then
  echo "expected normal single upgrade to start with docker-compose.yml and docker-compose-app.yml" >&2
  cat "${EVENTS_FILE}" >&2
  exit 1
fi

rm -f "${STATE_FILE}"

PG_TARGET="${TMP_DIR}/pg-target"
PG_IMAGES="${TMP_DIR}/pg-images"
PG_EXTRA="${TMP_DIR}/pg-extra"
mkdir -p "${PG_TARGET}/services/dts-pg/data/pgdata"
prepare_package "${PG_IMAGES}" "${PG_EXTRA}"

cat > "${PG_TARGET}/.env" <<'EOF_PG_TARGET_ENV'
LEGACY_STACK=false
IMAGE_POSTGRES=postgres:17.6
EOF_PG_TARGET_ENV

prepare_empty_compose_json "${PG_TARGET}/docker-compose.yml"
prepare_empty_compose_json "${SOURCE_ROOT}/docker-compose.yml"

printf '16\n' > "${PG_TARGET}/services/dts-pg/data/pgdata/PG_VERSION"

expect_failure "postgres major version mismatch" "${PG_TARGET}" "${PG_IMAGES}" "${PG_EXTRA}"
