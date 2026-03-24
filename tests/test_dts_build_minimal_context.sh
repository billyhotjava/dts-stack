#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

TEST_REPO="${TMP_DIR}/repo"
FAKE_BIN="${TMP_DIR}/bin"
CONTEXT_LOG="${TMP_DIR}/contexts.log"
mkdir -p "${TEST_REPO}" "${FAKE_BIN}"

mkdir -p \
  "${TEST_REPO}/builds/dts-platform" \
  "${TEST_REPO}/source/dts-common/src/main/java/com/example" \
  "${TEST_REPO}/source/dts-platform/src/main/java/com/example" \
  "${TEST_REPO}/source/dts-admin/src/main/java/com/example" \
  "${TEST_REPO}/worklog" \
  "${TEST_REPO}/source/dts-platform/node_modules"

cp "${REPO_ROOT}/builds/dts-build.sh" "${TEST_REPO}/builds/dts-build.sh"
chmod +x "${TEST_REPO}/builds/dts-build.sh"

cat > "${TEST_REPO}/builds/dts-platform/Dockerfile" <<'EOF_FILE'
FROM scratch
COPY source/pom.xml /workspace/source/pom.xml
COPY builds/dts-platform/dts-platform.jar /workspace/builds/dts-platform/dts-platform.jar
EOF_FILE

cat > "${TEST_REPO}/builds/dts-platform/Dockerfile.offline" <<'EOF_FILE'
FROM scratch
COPY source/pom.xml /workspace/source/pom.xml
COPY builds/dts-platform/dts-platform.jar /workspace/builds/dts-platform/dts-platform.jar
EOF_FILE

cat > "${TEST_REPO}/imgversion.conf" <<'EOF_FILE'
IMAGE_DTS_PLATFORM=dts-platform:test
EOF_FILE

cat > "${TEST_REPO}/source/pom.xml" <<'EOF_FILE'
<project/>
EOF_FILE

cat > "${TEST_REPO}/source/dts-common/src/main/java/com/example/Common.java" <<'EOF_FILE'
package com.example;
class Common {}
EOF_FILE

cat > "${TEST_REPO}/source/dts-platform/src/main/java/com/example/Platform.java" <<'EOF_FILE'
package com.example;
class Platform {}
EOF_FILE

cat > "${TEST_REPO}/source/dts-admin/src/main/java/com/example/Admin.java" <<'EOF_FILE'
package com.example;
class Admin {}
EOF_FILE

cat > "${TEST_REPO}/builds/dts-platform/dts-platform.jar" <<'EOF_FILE'
fake-jar
EOF_FILE

truncate -s 1048576 "${TEST_REPO}/worklog/huge.bin"
truncate -s 1048576 "${TEST_REPO}/source/dts-platform/node_modules/huge.bin"

(
  cd "${TEST_REPO}"
  git init >/dev/null
  git config user.email test@example.com
  git config user.name test
  git add builds/dts-platform/Dockerfile builds/dts-platform/Dockerfile.offline imgversion.conf source/pom.xml \
    source/dts-common/src/main/java/com/example/Common.java \
    source/dts-platform/src/main/java/com/example/Platform.java \
    source/dts-admin/src/main/java/com/example/Admin.java
) >/dev/null

cat > "${FAKE_BIN}/docker" <<'EOF_DOCKER'
#!/usr/bin/env bash
set -euo pipefail

case "${1:-}" in
  build)
    if [[ "${2:-}" == "--help" ]]; then
      echo "--progress"
      exit 0
    fi
    context="${@: -1}"
    printf '%s\n' "$context" >> "__CONTEXT_LOG__"
    if [[ "$context" == "__EXPECTED_REPO__" ]]; then
      echo "expected reduced build context, got repository root" >&2
      exit 1
    fi
    [[ -f "$context/builds/dts-platform/Dockerfile" ]] || { echo "missing Dockerfile in reduced context" >&2; exit 1; }
    [[ -f "$context/builds/dts-platform/Dockerfile.offline" ]] || { echo "missing offline Dockerfile in reduced context" >&2; exit 1; }
    [[ -f "$context/builds/dts-platform/dts-platform.jar" ]] || { echo "missing prebuilt jar in reduced context" >&2; exit 1; }
    [[ -f "$context/source/pom.xml" ]] || { echo "missing source pom in reduced context" >&2; exit 1; }
    [[ -f "$context/source/dts-common/src/main/java/com/example/Common.java" ]] || { echo "missing dts-common source in reduced context" >&2; exit 1; }
    [[ -f "$context/source/dts-platform/src/main/java/com/example/Platform.java" ]] || { echo "missing dts-platform source in reduced context" >&2; exit 1; }
    [[ ! -e "$context/worklog" ]] || { echo "unexpected worklog directory copied into reduced context" >&2; exit 1; }
    [[ ! -e "$context/source/dts-platform/node_modules" ]] || { echo "unexpected node_modules copied into reduced context" >&2; exit 1; }
    exit 0
    ;;
  version)
    echo "1.41"
    exit 0
    ;;
  inspect)
    echo "arm64"
    exit 0
    ;;
  image|builder)
    exit 0
    ;;
  save)
    exit 0
    ;;
  *)
    exit 0
    ;;
esac
EOF_DOCKER
sed -i "s|__CONTEXT_LOG__|${CONTEXT_LOG}|g" "${FAKE_BIN}/docker"
sed -i "s|__EXPECTED_REPO__|${TEST_REPO}|g" "${FAKE_BIN}/docker"
chmod +x "${FAKE_BIN}/docker"

cat > "${FAKE_BIN}/free" <<'EOF_FREE'
#!/usr/bin/env bash
cat <<'EOF_MEM'
              total        used        free      shared  buff/cache   available
Mem:          65536       16384       32768           0       16384       32768
Swap:             0           0           0
EOF_MEM
EOF_FREE
chmod +x "${FAKE_BIN}/free"

cat > "${FAKE_BIN}/df" <<'EOF_DF'
#!/usr/bin/env bash
cat <<'EOF_DISK'
Filesystem     1G-blocks  Used Available Use% Mounted on
/dev/vda2            295   200        95  68% /
EOF_DISK
EOF_DF
chmod +x "${FAKE_BIN}/df"

PATH="${FAKE_BIN}:${PATH}" PREBUILD_JARS=0 ENABLE_MAVEN_BUILD=false "${TEST_REPO}/builds/dts-build.sh" --image dts-platform --no-save >/dev/null

if [[ "$(wc -l < "${CONTEXT_LOG}")" -lt 1 ]]; then
  echo "expected docker build to be invoked at least once" >&2
  exit 1
fi
