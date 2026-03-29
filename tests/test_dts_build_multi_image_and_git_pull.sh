#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

setup_repo() {
  local target_repo="$1"

  mkdir -p \
    "${target_repo}/builds" \
    "${target_repo}/builds/dts-admin" \
    "${target_repo}/builds/dts-platform" \
    "${target_repo}/source"

  cp "${REPO_ROOT}/builds/dts-build.sh" "${target_repo}/builds/dts-build.sh"
  chmod +x "${target_repo}/builds/dts-build.sh"

  cat > "${target_repo}/source/pom.xml" <<'EOF_POM'
<project />
EOF_POM

  cat > "${target_repo}/builds/dts-admin/Dockerfile.offline" <<'EOF_DOCKERFILE'
FROM scratch
EOF_DOCKERFILE
  cat > "${target_repo}/builds/dts-platform/Dockerfile.offline" <<'EOF_DOCKERFILE'
FROM scratch
EOF_DOCKERFILE

  : > "${target_repo}/builds/dts-admin/dts-admin.jar"
  : > "${target_repo}/builds/dts-platform/dts-platform.jar"
  mkdir -p "${target_repo}/.git"
}

setup_fake_bin() {
  local fake_bin="$1"
  local docker_log="$2"
  local git_log="$3"
  mkdir -p "${fake_bin}"

  cat > "${fake_bin}/docker" <<'EOF_DOCKER'
#!/usr/bin/env bash
set -euo pipefail

case "${1:-}" in
  build)
    if [[ "${2:-}" == "--help" ]]; then
      echo "--progress"
      exit 0
    fi
    printf 'build:%s\n' "$*" >> "__DOCKER_LOG__"
    exit 0
    ;;
  version)
    echo "1.41"
    exit 0
    ;;
  image|builder|pull|rmi)
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
  sed -i "s|__DOCKER_LOG__|${docker_log}|g" "${fake_bin}/docker"
  chmod +x "${fake_bin}/docker"

  cat > "${fake_bin}/git" <<'EOF_GIT'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "__GIT_LOG__"
if [[ "${*: -2}" == "pull --ff-only" || "${*: -1}" == "pull" ]]; then
  exit 1
fi
exit 0
EOF_GIT
  sed -i "s|__GIT_LOG__|${git_log}|g" "${fake_bin}/git"
  chmod +x "${fake_bin}/git"

  cat > "${fake_bin}/free" <<'EOF_FREE'
#!/usr/bin/env bash
cat <<'EOF_MEM'
              total        used        free      shared  buff/cache   available
Mem:          65536       16384       32768           0       16384       32768
Swap:             0           0           0
EOF_MEM
EOF_FREE
  chmod +x "${fake_bin}/free"

  cat > "${fake_bin}/df" <<'EOF_DF'
#!/usr/bin/env bash
cat <<'EOF_DISK'
Filesystem     1G-blocks  Used Available Use% Mounted on
/dev/vda2            295   120       175  69% /
EOF_DISK
EOF_DF
  chmod +x "${fake_bin}/df"

  cat > "${fake_bin}/unzip" <<'EOF_UNZIP'
#!/usr/bin/env bash
echo "META-INF/MANIFEST.MF"
EOF_UNZIP
  chmod +x "${fake_bin}/unzip"
}

SCENARIO_REPO="${TMP_DIR}/repo"
SCENARIO_BIN="${TMP_DIR}/bin"
DOCKER_LOG="${TMP_DIR}/docker.log"
GIT_LOG="${TMP_DIR}/git.log"
OUTPUT_LOG="${TMP_DIR}/build.log"

setup_repo "${SCENARIO_REPO}"
setup_fake_bin "${SCENARIO_BIN}" "${DOCKER_LOG}" "${GIT_LOG}"

set +e
PATH="${SCENARIO_BIN}:${PATH}" PREBUILD_JARS=0 \
  "${SCENARIO_REPO}/builds/dts-build.sh" --image dts-admin dts-platform --legacy --no-save >"${OUTPUT_LOG}" 2>&1
STATUS=$?
set -e

if [[ ${STATUS} -ne 0 ]]; then
  echo "expected multi-image legacy build to succeed" >&2
  cat "${OUTPUT_LOG}" >&2
  exit 1
fi

if ! grep -Fq "${SCENARIO_REPO}/builds/dts-admin/Dockerfile.offline" "${DOCKER_LOG}"; then
  echo "expected dts-admin offline image build to run" >&2
  cat "${DOCKER_LOG}" >&2
  exit 1
fi

if ! grep -Fq "${SCENARIO_REPO}/builds/dts-platform/Dockerfile.offline" "${DOCKER_LOG}"; then
  echo "expected dts-platform offline image build to run" >&2
  cat "${DOCKER_LOG}" >&2
  exit 1
fi

if ! grep -Fq "pull --ff-only" "${GIT_LOG}"; then
  echo "expected build script to attempt git pull --ff-only before build" >&2
  cat "${GIT_LOG}" >&2
  exit 1
fi
