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
if [[ "${1:-}" == "compose" ]]; then
  compose_file=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      -f)
        compose_file="$2"
        shift 2
        ;;
      config)
        if [[ "${2:-}" == "--format" && "${3:-}" == "json" ]]; then
          cat "${compose_file}.json"
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

cat > "${SOURCE_ROOT}/docker-compose.yml" <<'EOF_SOURCE_COMPOSE'
services: {}
EOF_SOURCE_COMPOSE
cat > "${SOURCE_ROOT}/docker-compose.yml.json" <<'EOF_SOURCE_JSON'
{
  "services": {
    "existing": {
      "image": "new-image:2",
      "environment": {
        "KEEP": "new",
        "NEW_ONLY": "true"
      },
      "ports": ["9090:80"],
      "volumes": ["new-data:/app"],
      "extra_hosts": ["api:10.0.0.9"],
      "hostname": "new-host",
      "container_name": "existing-new",
      "labels": {
        "tier": "new"
      },
      "command": ["run"]
    },
    "new_service": {
      "image": "added:1"
    }
  },
  "volumes": {
    "new-data": {}
  },
  "networks": {
    "default": {
      "name": "new-net"
    }
  }
}
EOF_SOURCE_JSON

cat > "${TARGET_DIR}/docker-compose.yml" <<'EOF_TARGET_COMPOSE'
services: {}
EOF_TARGET_COMPOSE
cat > "${TARGET_DIR}/docker-compose.yml.json" <<'EOF_TARGET_JSON'
{
  "services": {
    "existing": {
      "image": "old-image:1",
      "environment": {
        "KEEP": "old",
        "SITE_IP": "10.0.0.8"
      },
      "ports": ["8080:80"],
      "volumes": ["site-data:/app"],
      "extra_hosts": ["db:10.0.0.2"],
      "hostname": "site-host",
      "container_name": "site-existing",
      "labels": {
        "site": "true"
      },
      "command": ["old"]
    },
    "legacy_service": {
      "image": "legacy:1"
    }
  },
  "volumes": {
    "site-data": {}
  },
  "networks": {
    "legacy-net": {
      "name": "legacy-net"
    }
  }
}
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

PATH="${FAKE_BIN}:${PATH}" \
  FAKE_DOCKER_STATE_FILE="${STATE_FILE}" \
  DTS_UPGRADE_SOURCE_ROOT="${SOURCE_ROOT}" \
  "${REPO_ROOT}/bin/dts-upgrade" \
  --target "${TARGET_DIR}" \
  --images-dir "${IMAGES_DIR}" \
  --extra-dir "${EXTRA_DIR}" >/dev/null

python3 - <<'PY' "${TARGET_DIR}/docker-compose.yml"
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fh:
    data = json.load(fh)

services = data["services"]
assert set(services) == {"existing", "new_service", "legacy_service"}, services.keys()
existing = services["existing"]
assert existing["image"] == "new-image:2"
assert existing["command"] == ["run"]
assert existing["environment"] == {"KEEP": "old", "SITE_IP": "10.0.0.8"}
assert existing["ports"] == ["8080:80"]
assert existing["volumes"] == ["site-data:/app"]
assert existing["extra_hosts"] == ["db:10.0.0.2"]
assert existing["hostname"] == "site-host"
assert existing["container_name"] == "site-existing"
assert existing["labels"] == {"site": "true"}
assert data["volumes"] == {"new-data": {}, "site-data": {}}
assert data["networks"] == {
    "default": {"name": "new-net"},
    "legacy-net": {"name": "legacy-net"},
}
PY
