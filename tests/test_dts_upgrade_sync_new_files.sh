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
mkdir -p "${FAKE_BIN}" "${SOURCE_ROOT}/bin" "${SOURCE_ROOT}/services/dts-pg/data" \
  "${TARGET_DIR}/bin" "${TARGET_DIR}/services/dts-pg/data" "${IMAGES_DIR}" "${EXTRA_DIR}"

cat > "${FAKE_BIN}/docker" <<'EOF_DOCKER'
#!/usr/bin/env bash
compose_file=""

if [[ "${1:-}" == "load" && "${2:-}" == "-i" ]]; then
  exit 0
fi
if [[ "${1:-}" == "compose" ]]; then
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
      up)
        if [[ "${2:-}" == "-d" ]]; then
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
fi
exit 0
EOF_DOCKER
chmod +x "${FAKE_BIN}/docker"

cat > "${SOURCE_ROOT}/.env" <<'EOF_SOURCE_ENV'
IMAGE_POSTGRES=postgres:17.6
EOF_SOURCE_ENV

cat > "${TARGET_DIR}/.env" <<'EOF_TARGET_ENV'
IMAGE_POSTGRES=postgres:17.6
EOF_TARGET_ENV

cat > "${SOURCE_ROOT}/docker-compose-app.yml" <<'EOF_SOURCE_COMPOSE'
services: {}
EOF_SOURCE_COMPOSE
cat > "${SOURCE_ROOT}/docker-compose-app.yml.json" <<'EOF_SOURCE_JSON'
{"services":{}}
EOF_SOURCE_JSON
cat > "${TARGET_DIR}/docker-compose-app.yml" <<'EOF_TARGET_COMPOSE'
services: {}
EOF_TARGET_COMPOSE
cat > "${TARGET_DIR}/docker-compose-app.yml.json" <<'EOF_TARGET_JSON'
{"services":{}}
EOF_TARGET_JSON

cat > "${SOURCE_ROOT}/bin/existing-tool.sh" <<'EOF_SOURCE_TOOL'
#!/usr/bin/env bash
echo new-version
EOF_SOURCE_TOOL
chmod +x "${SOURCE_ROOT}/bin/existing-tool.sh"

cat > "${TARGET_DIR}/bin/existing-tool.sh" <<'EOF_TARGET_TOOL'
#!/usr/bin/env bash
echo old-version
EOF_TARGET_TOOL
chmod +x "${TARGET_DIR}/bin/existing-tool.sh"

cat > "${SOURCE_ROOT}/bin/new-tool.sh" <<'EOF_NEW_TOOL'
#!/usr/bin/env bash
echo brand-new
EOF_NEW_TOOL
chmod +x "${SOURCE_ROOT}/bin/new-tool.sh"

printf 'do-not-copy\n' > "${SOURCE_ROOT}/services/dts-pg/data/source.txt"
printf 'keep-runtime\n' > "${TARGET_DIR}/services/dts-pg/data/state.txt"

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

if ! grep -Fq 'new-version' "${TARGET_DIR}/bin/existing-tool.sh"; then
  echo "expected sync step to replace existing runtime file with new package version" >&2
  exit 1
fi

if ! grep -Fq 'brand-new' "${TARGET_DIR}/bin/new-tool.sh"; then
  echo "expected sync step to copy new runtime file into target" >&2
  exit 1
fi

if ! grep -Fq 'old-version' "$(find "${TARGET_DIR}/backups" -type f -path '*/bin/existing-tool.sh' | head -n 1)"; then
  echo "expected overwritten runtime file to be backed up before sync" >&2
  exit 1
fi

if ! grep -Fq 'keep-runtime' "${TARGET_DIR}/services/dts-pg/data/state.txt"; then
  echo "expected sync step not to touch postgres runtime data dir" >&2
  exit 1
fi

if [[ -e "${TARGET_DIR}/services/dts-pg/data/source.txt" ]]; then
  echo "expected sync step not to copy source postgres data skeleton into target" >&2
  exit 1
fi

python3 - <<'PY' "${EXTRA_DIR}/rollback-manifest.json"
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fh:
    data = json.load(fh)

assert "bin/existing-tool.sh" in data["backedUpFiles"], data
assert "bin/new-tool.sh" in data["addedFiles"], data
PY
