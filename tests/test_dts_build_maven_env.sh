#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

TEST_REPO="${TMP_DIR}/repo"
FAKE_BIN="${TMP_DIR}/bin"
FAKE_DOCKER_LOG="${TMP_DIR}/docker-run.args"
FAKE_DOCKER_COLLISION="${TMP_DIR}/docker-run.collision"
FAKE_DOCKER_ACTIVE="${TMP_DIR}/docker-run.active"
HOST_M2_DIR="${TMP_DIR}/host-m2"
mkdir -p "${TEST_REPO}/builds" "${TEST_REPO}/builds/dts-admin" "${TEST_REPO}/source/dts-admin/target" "${FAKE_BIN}" "${HOST_M2_DIR}"

cp "${REPO_ROOT}/builds/dts-build.sh" "${TEST_REPO}/builds/dts-build.sh"
chmod +x "${TEST_REPO}/builds/dts-build.sh"
sed -i "s|/root/\\.m2|${HOST_M2_DIR}|g" "${TEST_REPO}/builds/dts-build.sh"

cat > "${TEST_REPO}/source/pom.xml" <<'EOF_POM'
<project />
EOF_POM

cat > "${TEST_REPO}/builds/dts-admin/Dockerfile" <<'EOF_DOCKERFILE'
FROM scratch
EOF_DOCKERFILE

cat > "${TEST_REPO}/builds/dts-admin/Dockerfile.offline" <<'EOF_DOCKERFILE'
FROM scratch
EOF_DOCKERFILE

cat > "${FAKE_BIN}/uname" <<'EOF_UNAME'
#!/usr/bin/env bash
if [[ "${1:-}" == "-m" ]]; then
  echo "aarch64"
  exit 0
fi
/usr/bin/uname "$@"
EOF_UNAME
chmod +x "${FAKE_BIN}/uname"

cat > "${FAKE_BIN}/docker" <<'EOF_DOCKER'
#!/usr/bin/env bash
set -euo pipefail

case "${1:-}" in
  build)
    if [[ "${2:-}" == "--help" ]]; then
      echo "--progress"
    fi
    exit 0
    ;;
  version)
    echo "1.39"
    exit 0
    ;;
  inspect)
    echo "arm64"
    exit 0
    ;;
  image)
    exit 0
    ;;
  builder)
    exit 0
    ;;
  run)
    printf '%s\n' "$@" > "__FAKE_DOCKER_LOG__"
    if [[ "${FAKE_DOCKER_RUN_DELAY:-0}" != "0" ]]; then
      if ! mkdir "__FAKE_DOCKER_ACTIVE__" 2>/dev/null; then
        : > "__FAKE_DOCKER_COLLISION__"
        exit 91
      fi
      trap 'rmdir "__FAKE_DOCKER_ACTIVE__"' EXIT
      sleep "${FAKE_DOCKER_RUN_DELAY}"
    fi
    mkdir -p "__TEST_REPO__/source/dts-admin/target"
    : > "__TEST_REPO__/source/dts-admin/target/dts-admin-0.0.1-SNAPSHOT.jar"
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
    if [[ -n "${output}" ]]; then
      mkdir -p "$(dirname "${output}")"
      : > "${output}"
    fi
    exit 0
    ;;
  *)
    exit 0
    ;;
esac
EOF_DOCKER
sed -i "s|__FAKE_DOCKER_LOG__|${FAKE_DOCKER_LOG}|g" "${FAKE_BIN}/docker"
sed -i "s|__FAKE_DOCKER_COLLISION__|${FAKE_DOCKER_COLLISION}|g" "${FAKE_BIN}/docker"
sed -i "s|__FAKE_DOCKER_ACTIVE__|${FAKE_DOCKER_ACTIVE}|g" "${FAKE_BIN}/docker"
sed -i "s|__TEST_REPO__|${TEST_REPO}|g" "${FAKE_BIN}/docker"
chmod +x "${FAKE_BIN}/docker"

export PATH="${FAKE_BIN}:${PATH}"
export MAVEN_SETTINGS_FILE="${HOST_M2_DIR}/settings.xml"
cat > "${MAVEN_SETTINGS_FILE}" <<'EOF_SETTINGS'
<settings />
EOF_SETTINGS

"${TEST_REPO}/builds/dts-build.sh" --image dts-admin >/dev/null

if ! grep -qx -- "--entrypoint" "${FAKE_DOCKER_LOG}"; then
  echo "expected docker run to use explicit bash entrypoint on aarch64 + old Docker API" >&2
  exit 1
fi

if ! grep -qx -- "/bin/bash" "${FAKE_DOCKER_LOG}"; then
  echo "expected docker run to use /bin/bash entrypoint" >&2
  exit 1
fi

if ! grep -qx -- "--security-opt" "${FAKE_DOCKER_LOG}"; then
  echo "expected docker run to include security options on aarch64 + old Docker API" >&2
  exit 1
fi

if ! grep -qx -- "seccomp=unconfined" "${FAKE_DOCKER_LOG}"; then
  echo "expected docker run to include seccomp=unconfined" >&2
  exit 1
fi

if ! grep -qx -- "--ulimit" "${FAKE_DOCKER_LOG}"; then
  echo "expected docker run to include ulimit for old Docker aarch64 maven builds" >&2
  exit 1
fi

if ! grep -qx -- "nproc=65535:65535" "${FAKE_DOCKER_LOG}"; then
  echo "expected docker run to lift nproc limit for old Docker aarch64 maven builds" >&2
  exit 1
fi

if ! grep -q 'export JAVA_HOME=/opt/java/openjdk;' "${FAKE_DOCKER_LOG}"; then
  echo "expected fallback command to export JAVA_HOME explicitly" >&2
  exit 1
fi

if ! grep -q 'export PATH=/opt/java/openjdk/bin:' "${FAKE_DOCKER_LOG}"; then
  echo "expected fallback command to export PATH explicitly" >&2
  exit 1
fi

if ! grep -q 'exec /usr/bin/mvn ' "${FAKE_DOCKER_LOG}"; then
  echo "expected fallback command to execute /usr/bin/mvn directly" >&2
  exit 1
fi

FAKE_DOCKER_RUN_DELAY=1 "${TEST_REPO}/builds/dts-build.sh" --image dts-admin >/dev/null &
first_build_pid=$!
FAKE_DOCKER_RUN_DELAY=1 "${TEST_REPO}/builds/dts-build.sh" --image dts-admin >/dev/null &
second_build_pid=$!
wait "${first_build_pid}"
wait "${second_build_pid}"

if [[ -e "${FAKE_DOCKER_COLLISION}" ]]; then
  echo "expected concurrent builds to serialize Maven access to the shared target directory" >&2
  exit 1
fi
