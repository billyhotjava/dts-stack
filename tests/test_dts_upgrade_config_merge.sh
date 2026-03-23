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
mkdir -p "${FAKE_BIN}" "${SOURCE_ROOT}/config" "${TARGET_DIR}/config" "${IMAGES_DIR}" "${EXTRA_DIR}"

cat > "${FAKE_BIN}/docker" <<'EOF_DOCKER'
#!/usr/bin/env bash
if [[ "${1:-}" == "compose" && "${2:-}" == "ps" ]]; then
  if [[ -f "${FAKE_DOCKER_STATE_FILE}" ]]; then
    printf 'dts-platform\n'
  fi
  exit 0
fi
if [[ "${1:-}" == "compose" && "${2:-}" == "up" && "${3:-}" == "-d" ]]; then
  : > "${FAKE_DOCKER_STATE_FILE}"
  exit 0
fi
if [[ "${1:-}" == "load" && "${2:-}" == "-i" ]]; then
  exit 0
fi
exit 0
EOF_DOCKER
chmod +x "${FAKE_BIN}/docker"

cat > "${TARGET_DIR}/docker-compose.yml" <<'EOF_COMPOSE'
services: {}
EOF_COMPOSE

cat > "${SOURCE_ROOT}/config/app.properties" <<'EOF_SOURCE_PROPERTIES'
site.mode=standard
new.flag=true
EOF_SOURCE_PROPERTIES
cat > "${TARGET_DIR}/config/app.properties" <<'EOF_TARGET_PROPERTIES'
site.mode=custom
site.ip=10.0.0.8
EOF_TARGET_PROPERTIES

cat > "${SOURCE_ROOT}/config/runtime.json" <<'EOF_SOURCE_JSON'
{
  "alpha": "new",
  "nested": {
    "b": 2,
    "c": 3
  },
  "newOnly": 1
}
EOF_SOURCE_JSON
cat > "${TARGET_DIR}/config/runtime.json" <<'EOF_TARGET_JSON'
{
  "alpha": "old",
  "nested": {
    "a": 1,
    "b": 9
  },
  "siteOnly": true
}
EOF_TARGET_JSON

cat > "${SOURCE_ROOT}/config/feature.yml" <<'EOF_SOURCE_YAML'
root:
  mode: new
  added: yes
EOF_SOURCE_YAML
cat > "${TARGET_DIR}/config/feature.yml" <<'EOF_TARGET_YAML'
root:
  mode: old
  site_ip: 10.0.0.8
EOF_TARGET_YAML

cat > "${SOURCE_ROOT}/config/custom.conf" <<'EOF_SOURCE_CONF'
new config payload
EOF_SOURCE_CONF
cat > "${TARGET_DIR}/config/custom.conf" <<'EOF_TARGET_CONF'
keep old config payload
EOF_TARGET_CONF

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

EXPECTED_PROPERTIES="${TMP_DIR}/expected.properties"
cat > "${EXPECTED_PROPERTIES}" <<'EOF_EXPECTED_PROPERTIES'
site.mode=custom
site.ip=10.0.0.8
new.flag=true
EOF_EXPECTED_PROPERTIES
if ! diff -u "${EXPECTED_PROPERTIES}" "${TARGET_DIR}/config/app.properties"; then
  echo "expected properties merge to keep old values and append new keys" >&2
  exit 1
fi

python3 - <<'PY' "${TARGET_DIR}/config/runtime.json"
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fh:
    data = json.load(fh)

assert data == {
    "alpha": "old",
    "nested": {
        "a": 1,
        "b": 9,
        "c": 3,
    },
    "newOnly": 1,
    "siteOnly": True,
}
PY

EXPECTED_YAML="${TMP_DIR}/expected.yml"
cat > "${EXPECTED_YAML}" <<'EOF_EXPECTED_YAML'
root:
  mode: old
  site_ip: 10.0.0.8
  added: yes
EOF_EXPECTED_YAML
if ! diff -u "${EXPECTED_YAML}" "${TARGET_DIR}/config/feature.yml"; then
  echo "expected yaml merge to keep old values and append new nested keys" >&2
  exit 1
fi

if ! grep -Fq 'keep old config payload' "${TARGET_DIR}/config/custom.conf"; then
  echo "expected unknown config file to keep old version" >&2
  exit 1
fi

BACKUP_FILE="$(find "${TARGET_DIR}/backups" -type f -path '*/config-conflicts/custom.conf.new' | head -n 1)"
if [[ -z "${BACKUP_FILE}" ]]; then
  echo "expected unknown source config to be backed up for manual review" >&2
  exit 1
fi

if ! grep -Fq 'new config payload' "${BACKUP_FILE}"; then
  echo "expected backup config file to contain new version content" >&2
  exit 1
fi
