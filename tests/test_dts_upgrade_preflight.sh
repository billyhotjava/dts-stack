#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

FAKE_BIN="${TMP_DIR}/bin"
OLD_DIR="${TMP_DIR}/old-dts"
IMAGES_DIR="${TMP_DIR}/images"
EXTRA_DIR="${TMP_DIR}/extra"
SOURCE_ROOT="${TMP_DIR}/source"
STATE_FILE="${TMP_DIR}/services-running"
mkdir -p "${FAKE_BIN}" "${IMAGES_DIR}" "${EXTRA_DIR}" "${SOURCE_ROOT}"

cat > "${FAKE_BIN}/docker" <<'EOF_DOCKER'
#!/usr/bin/env bash
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
        else
          printf '%s' "${FAKE_DOCKER_PS_OUTPUT:-}"
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

run_upgrade() {
  PATH="${FAKE_BIN}:${PATH}" \
    FAKE_DOCKER_STATE_FILE="${STATE_FILE}" \
    DTS_UPGRADE_SOURCE_ROOT="${SOURCE_ROOT}" \
    "${REPO_ROOT}/bin/dts-upgrade" \
    --target "${OLD_DIR}" \
    --images-dir "${IMAGES_DIR}" \
    --extra-dir "${EXTRA_DIR}"
}

expect_failure() {
  local expected="$1"
  local output_file="${TMP_DIR}/failure.log"
  set +e
  run_upgrade >"${output_file}" 2>&1
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
  PATH="${FAKE_BIN}:${PATH}" \
    FAKE_DOCKER_STATE_FILE="${STATE_FILE}" \
    DTS_UPGRADE_SOURCE_ROOT="${SOURCE_ROOT}" \
    "${REPO_ROOT}/bin/dts-upgrade" \
    --target "${OLD_DIR}" \
    --images-dir "${IMAGES_DIR}" \
    --extra-dir "${EXTRA_DIR}" >"${output_file}" 2>&1
}

expect_failure "target directory not found"

mkdir -p "${OLD_DIR}"
cat > "${OLD_DIR}/docker-compose-app.yml" <<'EOF_COMPOSE'
services: {}
EOF_COMPOSE
printf 'placeholder' > "${IMAGES_DIR}/placeholder.tar"
cat > "${EXTRA_DIR}/release-manifest.json" <<'EOF_MANIFEST'
{
  "images": [
    "placeholder.tar"
  ]
}
EOF_MANIFEST
(cd "${IMAGES_DIR}" && sha256sum placeholder.tar) > "${EXTRA_DIR}/checksums.txt"

FAKE_DOCKER_PS_OUTPUT=$'dts-platform\n' expect_failure "target deployment still has running containers"

FAKE_DOCKER_PS_OUTPUT="" touch "${OLD_DIR}/.upgrade-lock"
expect_failure "upgrade lock already exists"
rm -f "${OLD_DIR}/.upgrade-lock"

FAKE_DOCKER_PS_OUTPUT="" expect_success

if [[ ! -f "${OLD_DIR}/.upgrade-lock" ]]; then
  echo "expected upgrade lock to be created" >&2
  exit 1
fi

if ! find "${OLD_DIR}/logs" -maxdepth 1 -type f -name 'upgrade-*.log' | grep -q .; then
  echo "expected upgrade log file to be created" >&2
  exit 1
fi

if ! find "${OLD_DIR}/logs" -maxdepth 1 -type f -name 'upgrade-*.summary.md' | grep -q .; then
  echo "expected upgrade summary file to be created" >&2
  exit 1
fi
