#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

FAKE_BIN="${TMP_DIR}/bin"
SOURCE_ROOT="${TMP_DIR}/dts-stack"
TARGET_DIR="${TMP_DIR}/stack-old"
IMAGES_DIR="${TMP_DIR}/images"
EXTRA_DIR="${TMP_DIR}/extra"
DOCKER_LOG="${TMP_DIR}/docker.log"
COMPOSE_LOG="${TMP_DIR}/compose.log"
mkdir -p \
  "${FAKE_BIN}" \
  "${SOURCE_ROOT}/config" \
  "${SOURCE_ROOT}/bin" \
  "${TARGET_DIR}/config" \
  "${TARGET_DIR}/bin" \
  "${TARGET_DIR}/services/dts-pg/data/pgdata" \
  "${IMAGES_DIR}" \
  "${EXTRA_DIR}"

cat > "${FAKE_BIN}/docker-compose" <<'EOF_COMPOSE'
#!/usr/bin/env bash
echo "$*" >> "${FAKE_COMPOSE_LOG}"
if [[ "${1:-}" == "version" ]]; then
  echo "docker-compose version 1.29.2"
  exit 0
fi
while [[ $# -gt 0 ]]; do
  case "$1" in
    -f)
      shift 2
      ;;
    down)
      exit 0
      ;;
    up)
      if [[ "$*" != *"--force-recreate"* ]]; then
        echo "expected --force-recreate" >&2
        exit 2
      fi
      exit 0
      ;;
    ps)
      if [[ "$*" == *"--services"* ]]; then
        echo "dts-platform"
      fi
      exit 0
      ;;
    *)
      shift
      ;;
  esac
done
exit 0
EOF_COMPOSE
chmod +x "${FAKE_BIN}/docker-compose"

cat > "${FAKE_BIN}/docker" <<'EOF_DOCKER'
#!/usr/bin/env bash
echo "$*" >> "${FAKE_DOCKER_LOG}"
case "${1:-}" in
  load)
    [[ "${2:-}" == "-i" ]] || exit 2
    exit 0
    ;;
  version)
    if [[ "${2:-}" == "--format" ]]; then
      echo "18.09.0"
    else
      echo "Docker version 18.09.0"
    fi
    exit 0
    ;;
  *)
    exit 0
    ;;
esac
EOF_DOCKER
chmod +x "${FAKE_BIN}/docker"

cat > "${SOURCE_ROOT}/imgversion.conf" <<'EOF_IMG'
DTS_PRODUCT_VERSION=2.2.3
DTS_BUILD_TIMESTAMP=20260829210503
DTS_IMAGE_VERSION=2.2.3-20260829210503
IMAGE_POSTGRES=postgres:17.6
IMAGE_DTS_ADMIN=dts-admin:2.2.3-20260829210503
IMAGE_KEYCLOAK=keycloak:26
EOF_IMG

cat > "${SOURCE_ROOT}/.env" <<'EOF_SOURCE_ENV'
BASE_DOMAIN=bi.new.local
IMAGE_DTS_ADMIN=dts-admin:2.2.3-20260829210503
NEW_FEATURE_FLAG=true
EOF_SOURCE_ENV

cat > "${TARGET_DIR}/.env" <<'EOF_TARGET_ENV'
LEGACY_STACK=true
BASE_DOMAIN=bi.site.local
IMAGE_POSTGRES=postgres:17.5
IMAGE_DTS_ADMIN=dts-admin:1.0.0
SITE_ONLY=keep-me
EOF_TARGET_ENV

cat > "${SOURCE_ROOT}/docker-compose.legacy.yml" <<'EOF_SOURCE_COMPOSE'
version: "2.4"
services:
  dts-admin:
    image: ${IMAGE_DTS_ADMIN}
    extra_hosts:
      - mdm.local:10.0.0.9
EOF_SOURCE_COMPOSE

cat > "${TARGET_DIR}/docker-compose.legacy.yml" <<'EOF_TARGET_COMPOSE'
version: "2.4"
services:
  dts-admin:
    image: ${IMAGE_DTS_ADMIN}
    extra_hosts:
      - mdm.local:10.0.0.8
EOF_TARGET_COMPOSE

echo "17" > "${TARGET_DIR}/services/dts-pg/data/pgdata/PG_VERSION"

cat > "${SOURCE_ROOT}/config/app.properties" <<'EOF_SOURCE_CONFIG'
site.value=new
EOF_SOURCE_CONFIG
cat > "${TARGET_DIR}/config/app.properties" <<'EOF_TARGET_CONFIG'
site.value=old
EOF_TARGET_CONFIG
cat > "${SOURCE_ROOT}/config/new.properties" <<'EOF_NEW_CONFIG'
feature.enabled=true
EOF_NEW_CONFIG

cat > "${SOURCE_ROOT}/bin/existing-tool.sh" <<'EOF_SOURCE_TOOL'
#!/usr/bin/env bash
echo new
EOF_SOURCE_TOOL
cat > "${TARGET_DIR}/bin/existing-tool.sh" <<'EOF_TARGET_TOOL'
#!/usr/bin/env bash
echo old
EOF_TARGET_TOOL
cat > "${SOURCE_ROOT}/bin/new-tool.sh" <<'EOF_NEW_TOOL'
#!/usr/bin/env bash
echo added
EOF_NEW_TOOL
chmod +x "${SOURCE_ROOT}/bin/"*.sh "${TARGET_DIR}/bin/existing-tool.sh"

printf 'image-tar' > "${IMAGES_DIR}/dts-admin.tar"
(cd "${IMAGES_DIR}" && sha256sum dts-admin.tar) > "${EXTRA_DIR}/checksums.txt"
cat > "${EXTRA_DIR}/release-manifest.json" <<'EOF_MANIFEST'
{
  "version": "2.2.3-20260829210503",
  "productVersion": "2.2.3",
  "buildTimestamp": "20260829210503",
  "images": ["dts-admin.tar"]
}
EOF_MANIFEST

PATH="${FAKE_BIN}:${PATH}" \
  FAKE_DOCKER_LOG="${DOCKER_LOG}" \
  FAKE_COMPOSE_LOG="${COMPOSE_LOG}" \
  "${REPO_ROOT}/bin/dts-upgrade-lite" plan \
  --target "${TARGET_DIR}" \
  --source "${SOURCE_ROOT}" \
  --images-dir "${IMAGES_DIR}" \
  --extra-dir "${EXTRA_DIR}" >/dev/null

if ! grep -Fq "IMAGE_DTS_ADMIN=dts-admin:1.0.0" "${TARGET_DIR}/.env"; then
  echo "plan must not modify target .env" >&2
  exit 1
fi
if ! grep -Fq "mdm.local:10.0.0.8" "${TARGET_DIR}/docker-compose.legacy.yml"; then
  echo "plan must not modify target legacy compose" >&2
  exit 1
fi

PLAN_REPORT="$(find "${TARGET_DIR}/logs" -maxdepth 1 -type d -name 'upgrade-lite-*' | head -n 1)"
if [[ -z "${PLAN_REPORT}" || ! -f "${PLAN_REPORT}/report.html" ]]; then
  echo "expected plan report.html" >&2
  exit 1
fi
if ! grep -Fq "UPDATE_IMAGE" "${PLAN_REPORT}/env-plan.tsv"; then
  echo "expected env plan to include image update" >&2
  exit 1
fi
if ! grep -Fq "release version: 2.2.3-20260829210503" "${PLAN_REPORT}/summary.md"; then
  echo "expected plan summary to identify the release image version" >&2
  cat "${PLAN_REPORT}/summary.md" >&2
  exit 1
fi
if ! grep -Fq "KEEP_SITE_CONFLICT" "${PLAN_REPORT}/env-plan.tsv"; then
  echo "expected env plan to preserve conflicting site value" >&2
  exit 1
fi
if ! grep -Fq "默认不覆盖现场 compose" "${PLAN_REPORT}/risk-list.txt"; then
  echo "expected compose preservation risk note" >&2
  exit 1
fi

PATH="${FAKE_BIN}:${PATH}" \
  FAKE_DOCKER_LOG="${DOCKER_LOG}" \
  FAKE_COMPOSE_LOG="${COMPOSE_LOG}" \
  "${REPO_ROOT}/bin/dts-upgrade-lite" apply \
  --target "${TARGET_DIR}" \
  --source "${SOURCE_ROOT}" \
  --images-dir "${IMAGES_DIR}" \
  --extra-dir "${EXTRA_DIR}" \
  --yes >/dev/null

if ! grep -Fq "IMAGE_DTS_ADMIN=dts-admin:2.2.3-20260829210503" "${TARGET_DIR}/.env"; then
  echo "expected apply to update IMAGE_DTS_ADMIN" >&2
  exit 1
fi
if ! grep -Fq "IMAGE_POSTGRES=postgres:17.6" "${TARGET_DIR}/.env"; then
  echo "expected apply to update IMAGE_POSTGRES" >&2
  exit 1
fi
if ! grep -Fq "BASE_DOMAIN=bi.site.local" "${TARGET_DIR}/.env"; then
  echo "expected apply to preserve site BASE_DOMAIN" >&2
  exit 1
fi
if ! grep -Fq "NEW_FEATURE_FLAG=true" "${TARGET_DIR}/.env"; then
  echo "expected apply to append missing source env key" >&2
  exit 1
fi
if ! grep -Fq "IMAGE_KEYCLOAK=keycloak:26" "${TARGET_DIR}/.env"; then
  echo "expected apply to append missing image key from imgversion.conf" >&2
  exit 1
fi
if ! grep -Fq "mdm.local:10.0.0.8" "${TARGET_DIR}/docker-compose.legacy.yml"; then
  echo "expected apply to preserve target legacy compose" >&2
  exit 1
fi
if ! grep -Fq "site.value=old" "${TARGET_DIR}/config/app.properties"; then
  echo "expected apply to preserve existing config file" >&2
  exit 1
fi
if ! grep -Fq "feature.enabled=true" "${TARGET_DIR}/config/new.properties"; then
  echo "expected apply to add missing config file" >&2
  exit 1
fi
if ! grep -Fq "echo old" "${TARGET_DIR}/bin/existing-tool.sh"; then
  echo "expected apply to preserve existing runtime file" >&2
  exit 1
fi
if ! grep -Fq "echo added" "${TARGET_DIR}/bin/new-tool.sh"; then
  echo "expected apply to add missing runtime file" >&2
  exit 1
fi
if ! grep -Fq "load -i ${IMAGES_DIR}/dts-admin.tar" "${DOCKER_LOG}"; then
  echo "expected apply to docker load image tar" >&2
  exit 1
fi
if ! grep -Fq "up -d --force-recreate" "${COMPOSE_LOG}"; then
  echo "expected apply to force recreate containers" >&2
  exit 1
fi

APPLY_REPORT="$(find "${TARGET_DIR}/logs" -maxdepth 1 -type d -name 'upgrade-lite-*' | sort | tail -n 1)"
BACKUP_DIR="${APPLY_REPORT}/backup"
if [[ ! -f "${BACKUP_DIR}/.rollback-files.list" ]]; then
  echo "expected rollback file list" >&2
  exit 1
fi
if [[ ! -f "${APPLY_REPORT}/config-conflicts/config/app.properties.new" ]]; then
  echo "expected config conflict copy in report" >&2
  exit 1
fi
if [[ ! -f "${APPLY_REPORT}/runtime-conflicts/bin/existing-tool.sh.new" ]]; then
  echo "expected runtime conflict copy in report" >&2
  exit 1
fi

PATH="${FAKE_BIN}:${PATH}" \
  FAKE_DOCKER_LOG="${DOCKER_LOG}" \
  FAKE_COMPOSE_LOG="${COMPOSE_LOG}" \
  "${REPO_ROOT}/bin/dts-upgrade-lite" rollback \
  --target "${TARGET_DIR}" \
  --backup-dir "${BACKUP_DIR}" >/dev/null

if ! grep -Fq "IMAGE_DTS_ADMIN=dts-admin:1.0.0" "${TARGET_DIR}/.env"; then
  echo "expected rollback to restore old .env" >&2
  exit 1
fi
if [[ -f "${TARGET_DIR}/config/new.properties" ]]; then
  echo "expected rollback to remove added config file" >&2
  exit 1
fi
if [[ -f "${TARGET_DIR}/bin/new-tool.sh" ]]; then
  echo "expected rollback to remove added runtime file" >&2
  exit 1
fi
