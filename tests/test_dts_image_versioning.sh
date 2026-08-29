#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

if ! grep -Fqx 'DTS_PRODUCT_VERSION=2.2.3' "${REPO_ROOT}/imgversion.conf"; then
  echo "expected imgversion.conf to own DTS_PRODUCT_VERSION=2.2.3" >&2
  exit 1
fi

if grep -Eq '^IMAGE_DTS_[A-Z_]+=dts-[^:]+:1\.0\.0$' "${REPO_ROOT}/imgversion.conf"; then
  echo "DTS-owned image defaults must not remain on the mutable 1.0.0 tag" >&2
  exit 1
fi

TEST_REPO="${TMP_DIR}/repo"
FAKE_BIN="${TMP_DIR}/bin"
DOCKER_LOG="${TMP_DIR}/docker.log"
mkdir -p \
  "${TEST_REPO}/builds/dts-admin" \
  "${TEST_REPO}/source" \
  "${FAKE_BIN}"

cp "${REPO_ROOT}/builds/dts-build.sh" "${TEST_REPO}/builds/dts-build.sh"
chmod +x "${TEST_REPO}/builds/dts-build.sh"

cat > "${TEST_REPO}/imgversion.conf" <<'EOF_CONF'
DTS_PRODUCT_VERSION=2.2.3
IMAGE_DTS_ADMIN=dts-admin:2.2.3-local
EOF_CONF

cat > "${TEST_REPO}/source/pom.xml" <<'EOF_POM'
<project />
EOF_POM

cat > "${TEST_REPO}/builds/dts-admin/Dockerfile.offline" <<'EOF_DOCKERFILE'
FROM scratch
EOF_DOCKERFILE

: > "${TEST_REPO}/builds/dts-admin/dts-admin.jar"

cat > "${FAKE_BIN}/docker" <<'EOF_DOCKER'
#!/usr/bin/env bash
set -euo pipefail
case "${1:-}" in
  build)
    if [[ "${2:-}" == "--help" ]]; then
      echo "--progress"
      exit 0
    fi
    printf '%s\n' "$*" >> "${FAKE_DOCKER_LOG}"
    exit 0
    ;;
  save)
    shift
    output=""
    while [[ $# -gt 0 ]]; do
      case "$1" in
        -o)
          output="${2:-}"
          shift 2
          ;;
        *)
          shift
          ;;
      esac
    done
    mkdir -p "$(dirname "${output}")"
    : > "${output}"
    exit 0
    ;;
  version)
    echo "1.41"
    exit 0
    ;;
  image|builder)
    exit 0
    ;;
  *)
    exit 0
    ;;
esac
EOF_DOCKER
chmod +x "${FAKE_BIN}/docker"

cat > "${FAKE_BIN}/git" <<'EOF_GIT'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$*" == *"rev-parse HEAD"* ]]; then
  echo "388400785fa78e2c8e393bb0f724e3361e4a4122"
fi
exit 0
EOF_GIT
chmod +x "${FAKE_BIN}/git"

cat > "${FAKE_BIN}/free" <<'EOF_FREE'
#!/usr/bin/env bash
echo 'Mem: 65536 16384 32768 0 16384 32768'
EOF_FREE
chmod +x "${FAKE_BIN}/free"

cat > "${FAKE_BIN}/df" <<'EOF_DF'
#!/usr/bin/env bash
cat <<'EOF_DISK'
Filesystem 1G-blocks Used Available Use% Mounted on
/dev/vda2 295 120 175 69% /
EOF_DISK
EOF_DF
chmod +x "${FAKE_BIN}/df"

cat > "${FAKE_BIN}/unzip" <<'EOF_UNZIP'
#!/usr/bin/env bash
echo 'META-INF/MANIFEST.MF'
EOF_UNZIP
chmod +x "${FAKE_BIN}/unzip"

PATH="${FAKE_BIN}:${PATH}" \
  FAKE_DOCKER_LOG="${DOCKER_LOG}" \
  DTS_BUILD_TIMESTAMP=20260829210503 \
  PREBUILD_JARS=0 \
  "${TEST_REPO}/builds/dts-build.sh" --image dts-admin --legacy >/dev/null

EXPECTED_VERSION='2.2.3-20260829210503'
if ! grep -Fq -- "-t dts-admin:${EXPECTED_VERSION}" "${DOCKER_LOG}"; then
  echo "expected Docker build to use the product-version plus timestamp tag" >&2
  cat "${DOCKER_LOG}" >&2
  exit 1
fi

for expected_label in \
  "org.opencontainers.image.version=${EXPECTED_VERSION}" \
  'org.opencontainers.image.revision=388400785fa78e2c8e393bb0f724e3361e4a4122' \
  'com.yuzhi.dts.product.version=2.2.3' \
  'com.yuzhi.dts.build.timestamp=20260829210503'
do
  if ! grep -Fq -- "--label ${expected_label}" "${DOCKER_LOG}"; then
    echo "expected Docker build label: ${expected_label}" >&2
    cat "${DOCKER_LOG}" >&2
    exit 1
  fi
done

if [[ ! -f "${TEST_REPO}/builds/legacy-dist/dts-admin_${EXPECTED_VERSION}.tar" ]]; then
  echo "expected exported image tar to use the immutable image version" >&2
  find "${TEST_REPO}/builds" -maxdepth 3 -type f -print >&2
  exit 1
fi

INVALID_LOG="${TMP_DIR}/invalid.log"
set +e
PATH="${FAKE_BIN}:${PATH}" \
  FAKE_DOCKER_LOG="${DOCKER_LOG}" \
  DTS_BUILD_TIMESTAMP=20260829 \
  PREBUILD_JARS=0 \
  "${TEST_REPO}/builds/dts-build.sh" --image dts-admin --legacy --no-save >"${INVALID_LOG}" 2>&1
INVALID_STATUS=$?
set -e

if [[ ${INVALID_STATUS} -eq 0 ]] || ! grep -Fq 'DTS_BUILD_TIMESTAMP must use YYYYMMDDHHmmss' "${INVALID_LOG}"; then
  echo "expected invalid DTS_BUILD_TIMESTAMP to be rejected" >&2
  cat "${INVALID_LOG}" >&2
  exit 1
fi

echo "DTS image versioning test passed."
