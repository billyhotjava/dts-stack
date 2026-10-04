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
mkdir -p "${FAKE_BIN}" "${SOURCE_ROOT}" "${TARGET_DIR}" "${IMAGES_DIR}" "${EXTRA_DIR}"

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

cat > "${TARGET_DIR}/docker-compose-app.yml" <<'EOF_COMPOSE'
services: {}
EOF_COMPOSE

cat > "${SOURCE_ROOT}/.env" <<'EOF_SOURCE_ENV'
BASE_DOMAIN=bi.new.local
SHARED_TIMEOUT=30
NEW_FEATURE_FLAG=true
EOF_SOURCE_ENV

cat > "${TARGET_DIR}/.env" <<'EOF_TARGET_ENV'
BASE_DOMAIN=bi.site.local
SHARED_TIMEOUT=60
SITE_IP=10.0.0.8
EOF_TARGET_ENV

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

EXPECTED_ENV_FILE="${TMP_DIR}/expected.env"
cat > "${EXPECTED_ENV_FILE}" <<'EOF_EXPECTED'
BASE_DOMAIN=bi.site.local
SHARED_TIMEOUT=60
SITE_IP=10.0.0.8
NEW_FEATURE_FLAG=true
EOF_EXPECTED

if ! diff -u "${EXPECTED_ENV_FILE}" "${TARGET_DIR}/.env"; then
  echo "expected .env merge to preserve old values and append new keys" >&2
  exit 1
fi

SUMMARY_FILE="$(find "${TARGET_DIR}/logs" -maxdepth 1 -type f -name 'upgrade-*.summary.md' | head -n 1)"
if [[ -z "${SUMMARY_FILE}" ]]; then
  echo "expected upgrade summary file to exist" >&2
  exit 1
fi

if ! grep -Fq 'env appended: NEW_FEATURE_FLAG' "${SUMMARY_FILE}"; then
  echo "expected summary to include appended env key" >&2
  exit 1
fi

if ! grep -Fq 'env preserved: BASE_DOMAIN, SHARED_TIMEOUT' "${SUMMARY_FILE}"; then
  echo "expected summary to include preserved env keys" >&2
  exit 1
fi
