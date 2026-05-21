#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

TEST_OPMANAGER="${TMP_DIR}/opmanager"
FAKE_BIN="${TMP_DIR}/bin"
DOCKER_LOG="${TMP_DIR}/docker.log"

mkdir -p \
  "${TEST_OPMANAGER}/src/main/webapp" \
  "${TEST_OPMANAGER}/src/main/resources" \
  "${FAKE_BIN}"

cp "${REPO_ROOT}/opmanager/build-image.sh" "${TEST_OPMANAGER}/build-image.sh"
cp "${REPO_ROOT}/opmanager/Dockerfile" "${TEST_OPMANAGER}/Dockerfile"
cp "${REPO_ROOT}/opmanager/.dockerignore" "${TEST_OPMANAGER}/.dockerignore"
chmod +x "${TEST_OPMANAGER}/build-image.sh"

cat > "${TEST_OPMANAGER}/pom.xml" <<'EOF_POM'
<project />
EOF_POM

cat > "${TEST_OPMANAGER}/src/main/webapp/package.json" <<'EOF_PACKAGE'
{"scripts":{"build":"true"}}
EOF_PACKAGE

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
  version)
    echo "1.39"
    exit 0
    ;;
  run)
    printf 'run:%s\n' "$*" >> "__DOCKER_LOG__"
    mkdir -p "__TEST_OPMANAGER__/src/main/resources/static" "__TEST_OPMANAGER__/target"
    : > "__TEST_OPMANAGER__/src/main/resources/static/index.html"
    : > "__TEST_OPMANAGER__/target/dts-opmanager-2.2.3-SNAPSHOT.jar"
    exit 0
    ;;
  build)
    printf 'build:%s\n' "$*" >> "__DOCKER_LOG__"
    exit 0
    ;;
  image)
    if [[ "${2:-}" == "inspect" ]]; then
      echo "[opmanager-build] Image architecture: arm64"
    fi
    exit 0
    ;;
  *)
    exit 0
    ;;
esac
EOF_DOCKER
sed -i "s|__DOCKER_LOG__|${DOCKER_LOG}|g" "${FAKE_BIN}/docker"
sed -i "s|__TEST_OPMANAGER__|${TEST_OPMANAGER}|g" "${FAKE_BIN}/docker"
chmod +x "${FAKE_BIN}/docker"

PATH="${FAKE_BIN}:${PATH}" HOME="${TMP_DIR}/home" "${TEST_OPMANAGER}/build-image.sh" dts-opmanager:test >/dev/null

if ! grep -Fq -- "--security-opt seccomp=unconfined" "${DOCKER_LOG}"; then
  echo "expected opmanager Maven container to relax seccomp on ARM64" >&2
  cat "${DOCKER_LOG}" >&2
  exit 1
fi

if ! grep -Fq -- "--ulimit nproc=65535:65535" "${DOCKER_LOG}"; then
  echo "expected opmanager Maven container to raise nproc on ARM64" >&2
  cat "${DOCKER_LOG}" >&2
  exit 1
fi

if ! grep -Fq -- "--entrypoint /bin/bash" "${DOCKER_LOG}"; then
  echo "expected opmanager Maven container to use bash runner on old ARM64 Docker" >&2
  cat "${DOCKER_LOG}" >&2
  exit 1
fi

if ! grep -Fq "build:build -t dts-opmanager:test -f ${TEST_OPMANAGER}/Dockerfile ${TEST_OPMANAGER}" "${DOCKER_LOG}"; then
  echo "expected opmanager runtime image build to use the prebuilt-jar Dockerfile" >&2
  cat "${DOCKER_LOG}" >&2
  exit 1
fi

if grep -Fq "FROM maven" "${TEST_OPMANAGER}/Dockerfile" || grep -Fq "RUN mvn" "${TEST_OPMANAGER}/Dockerfile"; then
  echo "expected opmanager Dockerfile not to run Maven inside Docker build" >&2
  exit 1
fi
