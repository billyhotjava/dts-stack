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
    "${target_repo}/builds/dts-ingestion" \
    "${target_repo}/builds/dts-analytics" \
    "${target_repo}/builds/dts-admin-webapp" \
    "${target_repo}/builds/dts-platform-webapp" \
    "${target_repo}/builds/dts-analytics-webapp/modern" \
    "${target_repo}/builds/dts-dbt" \
    "${target_repo}/builds/dts-addax" \
    "${target_repo}/source/dts-airflow-om" \
    "${target_repo}/source"

  cp "${REPO_ROOT}/builds/dts-build.sh" "${target_repo}/builds/dts-build.sh"
  chmod +x "${target_repo}/builds/dts-build.sh"

  cat > "${target_repo}/source/pom.xml" <<'EOF_POM'
<project />
EOF_POM

  local dockerfile_paths=(
    "builds/dts-admin/Dockerfile"
    "builds/dts-admin/Dockerfile.offline"
    "builds/dts-platform/Dockerfile"
    "builds/dts-platform/Dockerfile.offline"
    "builds/dts-ingestion/Dockerfile"
    "builds/dts-ingestion/Dockerfile.offline"
    "builds/dts-analytics/Dockerfile"
    "builds/dts-analytics/Dockerfile.offline"
    "builds/dts-admin-webapp/Dockerfile"
    "builds/dts-platform-webapp/Dockerfile"
    "builds/dts-analytics-webapp/modern/Dockerfile"
    "builds/dts-dbt/Dockerfile"
    "builds/dts-addax/Dockerfile"
    "source/dts-airflow-om/Dockerfile"
  )

  local path
  for path in "${dockerfile_paths[@]}"; do
    cat > "${target_repo}/${path}" <<'EOF_DOCKERFILE'
FROM scratch
EOF_DOCKERFILE
  done

  : > "${target_repo}/builds/dts-admin/dts-admin.jar"
  : > "${target_repo}/builds/dts-platform/dts-platform.jar"
  : > "${target_repo}/builds/dts-ingestion/dts-ingestion.jar"
  : > "${target_repo}/builds/dts-analytics/dts-analytics.jar"
}

setup_fake_bin() {
  local fake_bin="$1"
  local test_repo="$2"
  local docker_log="$3"
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
  inspect)
    echo "arm64"
    exit 0
    ;;
  image|builder|pull|rmi)
    exit 0
    ;;
  save)
    shift
    local_output=""
    while [[ $# -gt 0 ]]; do
      case "$1" in
        -o)
          local_output="${2:-}"
          shift 2
          ;;
        *)
          shift
          ;;
      esac
    done
    if [[ -n "${local_output}" ]]; then
      mkdir -p "$(dirname "${local_output}")"
      : > "${local_output}"
      printf 'save:%s\n' "${local_output}" >> "__DOCKER_LOG__"
    fi
    exit 0
    ;;
  *)
    exit 0
    ;;
esac
EOF_DOCKER
  sed -i "s|__DOCKER_LOG__|${docker_log}|g" "${fake_bin}/docker"
  chmod +x "${fake_bin}/docker"

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
jar_path="${2:-}"
case "${jar_path}" in
  *dts-analytics.jar)
    echo "com/yuzhi/dts/analytics/DtsAnalyticsApp.class"
    ;;
  *dts-ingestion.jar)
    echo "com/yuzhi/dts/ingestion/DtsIngestionApp.class"
    ;;
  *)
    echo "META-INF/MANIFEST.MF"
    ;;
esac
EOF_UNZIP
  chmod +x "${fake_bin}/unzip"
}

SCENARIO1_REPO="${TMP_DIR}/scenario1-repo"
SCENARIO1_BIN="${TMP_DIR}/scenario1-bin"
SCENARIO1_DOCKER_LOG="${TMP_DIR}/scenario1-docker.log"
setup_repo "${SCENARIO1_REPO}"
setup_fake_bin "${SCENARIO1_BIN}" "${SCENARIO1_REPO}" "${SCENARIO1_DOCKER_LOG}"

PATH="${SCENARIO1_BIN}:${PATH}" PREBUILD_JARS=0 "${SCENARIO1_REPO}/builds/dts-build.sh" --image dts-admin --legacy >/dev/null

if ! ls "${SCENARIO1_REPO}"/builds/legacy-dist/dts-admin_local-*.tar >/dev/null 2>&1; then
  echo "expected --image dts-admin --legacy to export tar only to builds/legacy-dist" >&2
  exit 1
fi

if ls "${SCENARIO1_REPO}"/builds/dist/dts-admin_local-*.tar >/dev/null 2>&1; then
  echo "expected --image dts-admin --legacy not to write builds/dist tarball" >&2
  exit 1
fi

if ! grep -Fq "${SCENARIO1_REPO}/builds/dts-admin/Dockerfile.offline" "${SCENARIO1_DOCKER_LOG}"; then
  echo "expected --image dts-admin --legacy to build with offline Dockerfile" >&2
  cat "${SCENARIO1_DOCKER_LOG}" >&2
  exit 1
fi

if grep -Fq "${SCENARIO1_REPO}/builds/dts-admin/Dockerfile " "${SCENARIO1_DOCKER_LOG}"; then
  echo "expected --image dts-admin --legacy not to build normal Dockerfile" >&2
  cat "${SCENARIO1_DOCKER_LOG}" >&2
  exit 1
fi

SCENARIO2_REPO="${TMP_DIR}/scenario2-repo"
SCENARIO2_BIN="${TMP_DIR}/scenario2-bin"
SCENARIO2_DOCKER_LOG="${TMP_DIR}/scenario2-docker.log"
setup_repo "${SCENARIO2_REPO}"
setup_fake_bin "${SCENARIO2_BIN}" "${SCENARIO2_REPO}" "${SCENARIO2_DOCKER_LOG}"

PATH="${SCENARIO2_BIN}:${PATH}" PREBUILD_JARS=0 "${SCENARIO2_REPO}/builds/dts-build.sh" -all --legacy --no-save >/dev/null

build_count="$(grep -c '^build:' "${SCENARIO2_DOCKER_LOG}")"
if [[ "${build_count}" != "10" ]]; then
  echo "expected -all --legacy to build exactly 10 legacy-chain images, got ${build_count}" >&2
  cat "${SCENARIO2_DOCKER_LOG}" >&2
  exit 1
fi

if grep -Fq "${SCENARIO2_REPO}/builds/dts-platform/Dockerfile " "${SCENARIO2_DOCKER_LOG}"; then
  echo "expected -all --legacy not to invoke normal backend Dockerfiles" >&2
  cat "${SCENARIO2_DOCKER_LOG}" >&2
  exit 1
fi

if ! grep -Fq "${SCENARIO2_REPO}/builds/dts-platform/Dockerfile.offline" "${SCENARIO2_DOCKER_LOG}"; then
  echo "expected -all --legacy to invoke offline backend Dockerfiles" >&2
  cat "${SCENARIO2_DOCKER_LOG}" >&2
  exit 1
fi
