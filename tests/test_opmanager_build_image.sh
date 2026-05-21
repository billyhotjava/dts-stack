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
  "${TEST_OPMANAGER}/deploy" \
  "${FAKE_BIN}"

cp "${REPO_ROOT}/opmanager/build-image.sh" "${TEST_OPMANAGER}/build-image.sh"
cp "${REPO_ROOT}/opmanager/Dockerfile" "${TEST_OPMANAGER}/Dockerfile"
cp "${REPO_ROOT}/opmanager/.dockerignore" "${TEST_OPMANAGER}/.dockerignore"
cp "${REPO_ROOT}/opmanager/deploy/docker-compose.yml" "${TEST_OPMANAGER}/deploy/docker-compose.yml"
cp "${REPO_ROOT}/opmanager/deploy/env.example" "${TEST_OPMANAGER}/deploy/env.example"
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

cat > "${FAKE_BIN}/mvn" <<'EOF_MVN'
#!/usr/bin/env bash
echo "host mvn must not be used unless --host-maven or OPMANAGER_USE_HOST_MAVEN=1 is set" >&2
exit 99
EOF_MVN
chmod +x "${FAKE_BIN}/mvn"

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
  save)
    printf 'save:%s\n' "$*" >> "__DOCKER_LOG__"
    output=""
    shift
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
  image)
    if [[ "${2:-}" == "inspect" ]]; then
      if [[ "${*: -1}" == "[opmanager-build] Image architecture: {{.Architecture}}" ]]; then
        echo "[opmanager-build] Image architecture: arm64"
      else
        echo "arm64"
      fi
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

RUNTIME_PACKAGE="${TMP_DIR}/dts-opmanager-runtime-test-linux-arm64.tar.gz"
PATH="${FAKE_BIN}:${PATH}" HOME="${TMP_DIR}/home" LEGACY_USE_HOST_MAVEN=1 "${TEST_OPMANAGER}/build-image.sh" --tag dts-opmanager:test --output "${RUNTIME_PACKAGE}" >/dev/null

rm -f "${FAKE_BIN}/mvn"
FALLBACK_PACKAGE="${TMP_DIR}/dts-opmanager-runtime-fallback-linux-arm64.tar.gz"
FALLBACK_STDERR="${TMP_DIR}/fallback.stderr"
PATH="${FAKE_BIN}:/usr/bin:/bin" HOME="${TMP_DIR}/home" OPMANAGER_USE_HOST_MAVEN=1 "${TEST_OPMANAGER}/build-image.sh" --tag dts-opmanager:test --output "${FALLBACK_PACKAGE}" >/dev/null 2>"${FALLBACK_STDERR}"

if ! grep -Fq "falling back to Maven container" "${FALLBACK_STDERR}"; then
  echo "expected opmanager build script to fall back when host Maven is requested but mvn is missing" >&2
  cat "${FALLBACK_STDERR}" >&2
  exit 1
fi

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

if ! grep -Eq "save:save dts-opmanager:test -o .*/deploy/dts-opmanager-test-linux-arm64.tar" "${DOCKER_LOG}"; then
  echo "expected opmanager build script to export a docker save tar inside the runtime package" >&2
  cat "${DOCKER_LOG}" >&2
  exit 1
fi

if [[ ! -f "${RUNTIME_PACKAGE}" ]]; then
  echo "expected opmanager build script to create the requested runtime package" >&2
  exit 1
fi

PACKAGE_CONTENTS="$(tar -tzf "${RUNTIME_PACKAGE}")"
for expected in \
  deploy/docker-compose.yml \
  deploy/env.example \
  deploy/start.sh \
  deploy/dts-opmanager-test-linux-arm64.tar \
  data/ \
  packages/ \
  README.md
do
  if ! grep -qx "${expected}" <<<"${PACKAGE_CONTENTS}"; then
    echo "expected runtime package to contain ${expected}" >&2
    tar -tzf "${RUNTIME_PACKAGE}" >&2
    exit 1
  fi
done

if ! tar -xOf "${RUNTIME_PACKAGE}" deploy/env.example | grep -Fxq "OPMANAGER_IMAGE=dts-opmanager:test"; then
  echo "expected packaged env.example to use the requested image tag" >&2
  tar -xOf "${RUNTIME_PACKAGE}" deploy/env.example >&2
  exit 1
fi

if grep -Fq "FROM maven" "${TEST_OPMANAGER}/Dockerfile" || grep -Fq "RUN mvn" "${TEST_OPMANAGER}/Dockerfile"; then
  echo "expected opmanager Dockerfile not to run Maven inside Docker build" >&2
  exit 1
fi

if ! grep -Fq 'ENTRYPOINT ["/opt/java/openjdk/bin/java", "-jar", "/app/dts-opmanager/dts-opmanager.jar"]' "${TEST_OPMANAGER}/Dockerfile"; then
  echo "expected opmanager runtime image to use an absolute Java entrypoint" >&2
  exit 1
fi

if ! grep -Fq "ENV JAVA_HOME=/opt/java/openjdk" "${TEST_OPMANAGER}/Dockerfile"; then
  echo "expected opmanager runtime image to set JAVA_HOME explicitly" >&2
  exit 1
fi

if ! tar -xOf "${RUNTIME_PACKAGE}" deploy/docker-compose.yml | grep -Fq "seccomp=unconfined"; then
  echo "expected packaged compose to relax seccomp for Kunpeng/Kylin Java runtime" >&2
  tar -xOf "${RUNTIME_PACKAGE}" deploy/docker-compose.yml >&2
  exit 1
fi

if ! tar -xOf "${RUNTIME_PACKAGE}" deploy/docker-compose.yml | grep -Fq "nproc: 65535"; then
  echo "expected packaged compose to raise nproc for Kunpeng/Kylin Java runtime" >&2
  tar -xOf "${RUNTIME_PACKAGE}" deploy/docker-compose.yml >&2
  exit 1
fi

if ! tar -xOf "${RUNTIME_PACKAGE}" deploy/docker-compose.yml | grep -Fq "working_dir: /app/dts-opmanager"; then
  echo "expected packaged compose to set an explicit runtime working directory" >&2
  tar -xOf "${RUNTIME_PACKAGE}" deploy/docker-compose.yml >&2
  exit 1
fi

if ! tar -xOf "${RUNTIME_PACKAGE}" deploy/docker-compose.yml | grep -Fq "/opt/java/openjdk/bin/java"; then
  echo "expected packaged compose to override the Java entrypoint with an absolute path" >&2
  tar -xOf "${RUNTIME_PACKAGE}" deploy/docker-compose.yml >&2
  exit 1
fi

if ! tar -xOf "${RUNTIME_PACKAGE}" deploy/start.sh | grep -Fq "docker version --format"; then
  echo "expected packaged start.sh to print Docker version diagnostics" >&2
  tar -xOf "${RUNTIME_PACKAGE}" deploy/start.sh >&2
  exit 1
fi

if ! tar -xOf "${RUNTIME_PACKAGE}" deploy/start.sh | grep -Fq "expected exactly one dts-opmanager image tar"; then
  echo "expected packaged start.sh to reject ambiguous image tar files" >&2
  tar -xOf "${RUNTIME_PACKAGE}" deploy/start.sh >&2
  exit 1
fi

if ! tar -xOf "${RUNTIME_PACKAGE}" deploy/start.sh | grep -Fq "Loaded image:"; then
  echo "expected packaged start.sh to inspect loaded image metadata" >&2
  tar -xOf "${RUNTIME_PACKAGE}" deploy/start.sh >&2
  exit 1
fi

if tar -xOf "${RUNTIME_PACKAGE}" deploy/start.sh | grep -Fq -- "--entrypoint /bin/sh"; then
  echo "expected packaged start.sh not to use shell-based docker run checks on Docker 18/Kylin" >&2
  tar -xOf "${RUNTIME_PACKAGE}" deploy/start.sh >&2
  exit 1
fi

if ! tar -xOf "${RUNTIME_PACKAGE}" deploy/start.sh | grep -Fq "loaded image entrypoint is not compatible"; then
  echo "expected packaged start.sh to validate image compatibility using inspect metadata" >&2
  tar -xOf "${RUNTIME_PACKAGE}" deploy/start.sh >&2
  exit 1
fi
