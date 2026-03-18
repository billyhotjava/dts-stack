#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

# Enable BuildKit if Docker supports it (>= 18.09 with experimental, or >= 23.0 default).
# Older Docker on Kylin/EulerOS may not support it — detect and skip.
DOCKER_BUILDKIT_SUPPORTED=""
if docker build --help 2>&1 | grep -q -- '--progress'; then
  export DOCKER_BUILDKIT=1
  DOCKER_BUILDKIT_SUPPORTED="1"
else
  unset DOCKER_BUILDKIT 2>/dev/null || true
fi

MODE=""
IMAGE_ONLY=""
PACK_MODE=""
PACK_OUTPUT=""
PACK_INCLUDE_IMAGES="true"
SAVE_IMAGE_TARS="${SAVE_IMAGE_TARS:-true}"

usage() {
  cat <<USAGE
Usage:
  ${0##*/} -all
  ${0##*/} --image <name>
  ${0##*/} -all --no-save
  ${0##*/} --pack [--output <path>] [--no-images]
  ${0##*/} --bg -all              (run in background, safe for SSH)

Options:
  -all, --all           Build all images (same as legacy buildAll.sh behavior).
  --image <name>        Build a single image and save tarballs to both dist/ and legacy-dist/.
  --no-save             Build images but skip docker save tarball export (reduces disk pressure).
  --pack                Package dts-stack for deployment (excludes source, logs, git, etc.).
  --output <path>       Output path for the package tarball (default: ./dts-stack-<timestamp>.tar.gz).
  --no-images           Exclude image tarballs from package (smaller package, images loaded separately).
  --bg                  Run build in background via nohup. Safe for SSH sessions.
                        Log output goes to builds/dts-build.log. Use 'tail -f builds/dts-build.log' to follow.

Examples:
  ${0##*/} -all
  ${0##*/} -all --no-save
  ${0##*/} --bg -all
  ${0##*/} --bg --image dts-analytics
  ${0##*/} --image dts-admin
  ${0##*/} --image dts-dbt
  ${0##*/} --pack
  ${0##*/} --pack --output /tmp/dts-deploy.tar.gz
  ${0##*/} --pack --no-images
USAGE
}

# --bg: re-exec self under nohup so the build survives SSH disconnects.
if [[ "${1:-}" == "--bg" ]]; then
  shift
  BUILD_LOG="${SCRIPT_DIR}/dts-build.log"
  echo "[dts-build] Starting background build. Log: ${BUILD_LOG}"
  echo "[dts-build] Use 'tail -f ${BUILD_LOG}' to follow progress."
  nohup bash "${BASH_SOURCE[0]}" "$@" > "${BUILD_LOG}" 2>&1 &
  BG_PID=$!
  echo "[dts-build] Background PID: ${BG_PID}"
  # Detach from controlling terminal so SIGHUP won't propagate
  disown "${BG_PID}" 2>/dev/null || true
  exit 0
fi

if [[ $# -eq 0 ]]; then
  usage
  exit 1
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    -all|--all)
      MODE="all"
      shift
      ;;
    --image)
      IMAGE_ONLY="${2:-}"
      if [[ -z "${IMAGE_ONLY}" ]]; then
        echo "[dts-build] ERROR: --image requires a value" >&2
        exit 1
      fi
      shift 2
      ;;
    --pack)
      PACK_MODE="true"
      shift
      ;;
    --output)
      PACK_OUTPUT="${2:-}"
      if [[ -z "${PACK_OUTPUT}" ]]; then
        echo "[dts-build] ERROR: --output requires a value" >&2
        exit 1
      fi
      shift 2
      ;;
    --no-images)
      PACK_INCLUDE_IMAGES="false"
      shift
      ;;
    --no-save)
      SAVE_IMAGE_TARS="false"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[dts-build] ERROR: Unknown argument: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ -n "${MODE}" && -n "${IMAGE_ONLY}" ]]; then
  echo "[dts-build] ERROR: --all and --image cannot be used together" >&2
  exit 1
fi

if [[ -n "${PACK_MODE}" && ( -n "${MODE}" || -n "${IMAGE_ONLY}" ) ]]; then
  echo "[dts-build] ERROR: --pack cannot be used with --all or --image" >&2
  exit 1
fi

BUILD_TS="${BUILD_TS:-$(date +%Y%m%d-%H%M%S)}"

NORMAL_DIST="${REPO_ROOT}/builds/dist"
LEGACY_DIST="${REPO_ROOT}/builds/legacy-dist"
NODE_IMAGE="${NODE_IMAGE:-node:20.17.0-alpine3.20}"
PNPM_VERSION="${PNPM_VERSION:-10.28.0}"
IMGVERSION_FILE="${IMGVERSION_FILE:-${REPO_ROOT}/imgversion.conf}"
MAVEN_IMAGE="${MAVEN_IMAGE:-maven:3.9.9-eclipse-temurin-21}"
MAVEN_CONTAINER_JAVA_HOME="${MAVEN_CONTAINER_JAVA_HOME:-/opt/java/openjdk}"
MAVEN_SECURITY_OPT="${MAVEN_SECURITY_OPT:-}"
LEGACY_USE_HOST_MAVEN="${LEGACY_USE_HOST_MAVEN:-}"
MAVEN_DEBUG="${MAVEN_DEBUG:-}"
LEGACY_UNRESTRICTED="${LEGACY_UNRESTRICTED:-0}"
MAVEN_UNRESTRICTED="${MAVEN_UNRESTRICTED:-${LEGACY_UNRESTRICTED:-}}"
MAVEN_MEMORY_LIMIT="${MAVEN_MEMORY_LIMIT:-4g}"
MAVEN_MIRROR_URL="${MAVEN_MIRROR_URL:-https://maven.aliyun.com/repository/public}"
NPM_REGISTRY="${NPM_REGISTRY:-https://registry.npmmirror.com}"
MAVEN_SETTINGS_FILE="${MAVEN_SETTINGS_FILE:-/root/.m2/settings.xml}"
PREBUILD_JARS="${PREBUILD_JARS:-1}"
WEBAPP_BUILD_CMD="${WEBAPP_BUILD_CMD:-build}"

# Auto-detect host architecture for ARM64 (Kunpeng/Apple Silicon) compatibility.
HOST_ARCH="$(uname -m)"

# Detect if Docker supports --platform (requires API >= 1.40 / Docker >= 19.03).
DOCKER_PLATFORM_SUPPORTED=""
DOCKER_API_VERSION="$(docker version --format '{{.Server.APIVersion}}' 2>/dev/null || echo "0.0")"
DOCKER_API_MAJOR="${DOCKER_API_VERSION%%.*}"
DOCKER_API_MINOR="${DOCKER_API_VERSION#*.}"
if [[ "${DOCKER_API_MAJOR:-0}" -gt 1 ]] || { [[ "${DOCKER_API_MAJOR}" == "1" ]] && [[ "${DOCKER_API_MINOR:-0}" -ge 40 ]]; }; then
  DOCKER_PLATFORM_SUPPORTED="1"
fi

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[dts-build] ERROR: '$1' not found in PATH" >&2
    exit 1
  fi
}

preflight_check() {
  echo "[dts-build] === Pre-flight check ==="
  local arch
  arch="$(uname -m)"
  echo "[dts-build] Architecture: ${arch}"

  echo "[dts-build] Docker API: ${DOCKER_API_VERSION}, --platform supported: ${DOCKER_PLATFORM_SUPPORTED:-no}"

  # ARM64 architecture check: ensure Maven/Node images are native arm64, not x86 via QEMU
  if [[ "$arch" == "aarch64" || "$arch" == "arm64" ]]; then
    echo "[dts-build] INFO: Running on ARM64 (Kunpeng/Apple Silicon)."
    local existing_maven_arch
    existing_maven_arch="$(docker inspect "${MAVEN_IMAGE}" --format '{{.Architecture}}' 2>/dev/null || echo "none")"
    if [[ "$existing_maven_arch" == "amd64" ]]; then
      echo "[dts-build] WARN: Local Maven image '${MAVEN_IMAGE}' is amd64 (x86) — will cause JAVA_HOME errors on ARM64."
      echo "[dts-build] INFO: Removing wrong-arch image and re-pulling..."
      docker rmi "${MAVEN_IMAGE}" 2>/dev/null || true
      if [[ -n "${DOCKER_PLATFORM_SUPPORTED}" ]]; then
        docker pull --platform linux/arm64 "${MAVEN_IMAGE}"
      else
        docker pull "${MAVEN_IMAGE}"
      fi
      # Verify after re-pull
      existing_maven_arch="$(docker inspect "${MAVEN_IMAGE}" --format '{{.Architecture}}' 2>/dev/null || echo "none")"
      if [[ "$existing_maven_arch" == "amd64" ]]; then
        echo "[dts-build] ERROR: After re-pull, Maven image is still amd64. Your Docker registry may not have arm64 variant." >&2
        echo "[dts-build] TIP: Use LEGACY_USE_HOST_MAVEN=1 to build with host-installed Maven instead." >&2
        exit 1
      fi
    fi
    echo "[dts-build] INFO: Maven image arch: ${existing_maven_arch}"
  fi

  # Check available memory
  if command -v free >/dev/null 2>&1; then
    local avail_mb
    avail_mb="$(free -m | awk '/^Mem:/ {print $7}')"
    echo "[dts-build] Available memory: ${avail_mb} MB"
    if [[ "${avail_mb:-0}" -lt 2048 ]]; then
      echo "[dts-build] ERROR: Less than 2GB available memory. Build will likely OOM and crash the server." >&2
      echo "[dts-build] TIP: Free memory by stopping unused containers: docker compose down" >&2
      exit 1
    fi
    if [[ "${avail_mb:-0}" -lt 4096 ]]; then
      echo "[dts-build] WARN: Less than 4GB available. Consider using LEGACY_USE_HOST_MAVEN=1 to reduce memory pressure."
    fi
  fi

  # Check available disk space
  local avail_disk_gb
  local required_disk_gb
  avail_disk_gb="$(df -BG "${REPO_ROOT}" | awk 'NR==2 {gsub(/G/,"",$4); print $4}')"
  required_disk_gb="$(required_min_disk_gb)"
  echo "[dts-build] Available disk: ${avail_disk_gb} GB"
  echo "[dts-build] Estimated required disk: ${required_disk_gb} GB"
  if [[ "${avail_disk_gb:-0}" -lt "${required_disk_gb:-0}" ]]; then
    echo "[dts-build] ERROR: current mode requires at least ${required_disk_gb}GB free disk, but only ${avail_disk_gb}GB is available." >&2
    if [[ "${SAVE_IMAGE_TARS}" == "true" && ( "${MODE}" == "all" || -n "${IMAGE_ONLY}" ) ]]; then
      echo "[dts-build] TIP: Re-run with --no-save to skip docker save tarball export and reduce disk pressure." >&2
    fi
    exit 1
  fi

  # Prune dangling images to free resources
  echo "[dts-build] Pruning dangling Docker resources..."
  docker image prune -f >/dev/null 2>&1 || true
  # docker builder prune requires BuildKit (Docker >= 18.09 with experimental, or >= 23.0)
  if [[ -n "${DOCKER_BUILDKIT_SUPPORTED}" ]]; then
    docker builder prune -f --filter "until=24h" >/dev/null 2>&1 || true
  fi
  echo "[dts-build] === Pre-flight OK ==="
}

required_min_disk_gb() {
  if [[ -n "${PACK_MODE}" ]]; then
    if [[ "${PACK_INCLUDE_IMAGES}" == "true" ]]; then
      echo 12
    else
      echo 6
    fi
    return 0
  fi

  if [[ "${MODE}" == "all" ]]; then
    if [[ "${SAVE_IMAGE_TARS}" == "true" ]]; then
      echo 45
    else
      echo 30
    fi
    return 0
  fi

  if [[ -n "${IMAGE_ONLY}" ]]; then
    if [[ "${SAVE_IMAGE_TARS}" == "true" ]]; then
      echo 16
    else
      echo 12
    fi
    return 0
  fi

  echo 10
}

load_image_versions() {
  if [[ -f "$IMGVERSION_FILE" ]]; then
    set -a
    # shellcheck source=/dev/null
    . "$IMGVERSION_FILE"
    set +a
  else
    echo "[dts-build] WARN: ${IMGVERSION_FILE} not found; using default image tags" >&2
  fi
}

sanitize_tag() {
  echo "$1" | tr '/:' '__'
}

save_image() {
  local tag="$1"
  local output_dir="$2"
  if [[ "${SAVE_IMAGE_TARS}" != "true" ]]; then
    echo "[dts-build] Skipping tar export for ${tag} (--no-save)"
    return 0
  fi
  mkdir -p "$output_dir"
  local tar_path="${output_dir}/$(sanitize_tag "$tag")-${BUILD_TS}.tar"
  docker save "$tag" -o "$tar_path"
  echo "[dts-build] Saved ${tar_path}"
}

assert_distinct_backend_images() {
  local specs=(
    "dts-admin|${IMAGE_DTS_ADMIN}"
    "dts-platform|${IMAGE_DTS_PLATFORM}"
    "dts-ingestion|${IMAGE_DTS_INGESTION}"
    "dts-analytics|${IMAGE_DTS_ANALYTICS}"
  )
  declare -A seen=()
  local spec name tag
  for spec in "${specs[@]}"; do
    name="${spec%%|*}"
    tag="${spec#*|}"
    if [[ -n "${seen[$tag]:-}" ]]; then
      echo "[dts-build] ERROR: image tag collision detected: ${name} and ${seen[$tag]} both use '${tag}'." >&2
      echo "[dts-build]        Check IMAGE_DTS_* values in ${IMGVERSION_FILE} / .env." >&2
      exit 1
    fi
    seen["$tag"]="$name"
  done
}

list_jar_entries() {
  local jar_path="$1"
  if command -v unzip >/dev/null 2>&1; then
    unzip -Z1 "$jar_path"
    return 0
  fi
  if command -v jar >/dev/null 2>&1; then
    jar tf "$jar_path"
    return 0
  fi
  return 1
}

validate_module_jar_identity() {
  local module="$1"
  local jar_path="$2"
  local expected=""
  case "$module" in
    dts-analytics)
      expected="com/yuzhi/dts/analytics/DtsAnalyticsApp.class"
      ;;
    dts-ingestion)
      expected="com/yuzhi/dts/ingestion/DtsIngestionApp.class"
      ;;
    *)
      return 0
      ;;
  esac

  if ! list_jar_entries "$jar_path" >/tmp/.dts-build-jar-entries.$$ 2>/dev/null; then
    echo "[dts-build] WARN: cannot inspect ${jar_path} (missing 'unzip' or 'jar'); skip identity check for ${module}." >&2
    return 0
  fi

  if ! grep -Fq "$expected" "/tmp/.dts-build-jar-entries.$$"; then
    echo "[dts-build] ERROR: ${jar_path} does not look like ${module} artifact (missing ${expected})." >&2
    rm -f "/tmp/.dts-build-jar-entries.$$"
    exit 1
  fi
  rm -f "/tmp/.dts-build-jar-entries.$$"
}

verify_prebuilt_module_jar() {
  local module="$1"
  local jar_path="$2"
  if [[ ! -f "$jar_path" ]]; then
    echo "[dts-build] ERROR: required prebuilt jar missing: ${jar_path}" >&2
    exit 1
  fi
  validate_module_jar_identity "$module" "$jar_path"
}

build_image_ctx() {
  local name="$1"
  local tag="$2"
  local dockerfile="$3"
  local context_dir="$4"
  local output_dir="$5"
  shift 5
  local args=("$@")

  echo "[dts-build] Building ${name} -> ${tag}"
  docker build -t "$tag" -f "$dockerfile" "${args[@]}" "$context_dir"
  save_image "$tag" "$output_dir"
  # Free dangling layers after each image to prevent OOM on memory-constrained servers
  docker image prune -f >/dev/null 2>&1 || true
}

build_image() {
  local name="$1"
  local tag="$2"
  local dockerfile="$3"
  local output_dir="$4"
  shift 4
  local args=("$@")

  build_image_ctx "$name" "$tag" "$dockerfile" "$REPO_ROOT" "$output_dir" "${args[@]}"
}

build_maven_module() {
  local module="$1"
  local jar_glob="$2"
  local out_jar="$3"

  if [[ -f "$out_jar" ]]; then
    echo "[dts-build] Removing stale prebuilt jar: ${out_jar}"
    rm -f "$out_jar"
  fi
  if [[ "${LEGACY_USE_PREBUILT_JARS:-}" == "1" ]]; then
    echo "[dts-build] ERROR: ${out_jar} missing (LEGACY_USE_PREBUILT_JARS=1)" >&2
    exit 1
  fi

  if [[ -n "$LEGACY_USE_HOST_MAVEN" ]]; then
    require_cmd mvn
    echo "[dts-build] Building ${module} jar via host Maven"
    if [[ ! -f "$MAVEN_SETTINGS_FILE" ]]; then
      mkdir -p "$(dirname "$MAVEN_SETTINGS_FILE")"
      cat > "$MAVEN_SETTINGS_FILE" <<'MAVEN_SETTINGS_EOF'
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>*</mirrorOf>
      <url>__MAVEN_MIRROR_URL__</url>
    </mirror>
  </mirrors>
</settings>
MAVEN_SETTINGS_EOF
      sed -i "s|__MAVEN_MIRROR_URL__|${MAVEN_MIRROR_URL}|g" "$MAVEN_SETTINGS_FILE"
    fi
    mvn -B -e -DskipTests -s "$MAVEN_SETTINGS_FILE" -f "${REPO_ROOT}/source/pom.xml" -pl "$module" -am package
  else
    echo "[dts-build] Building ${module} jar via ${MAVEN_IMAGE}"
    local security_opts=()
    if [[ -n "$MAVEN_SECURITY_OPT" ]]; then
      security_opts+=(--security-opt "$MAVEN_SECURITY_OPT")
    fi
    if [[ "${MAVEN_UNRESTRICTED}" == "1" ]]; then
      security_opts+=(--security-opt "seccomp=unconfined" --ulimit "nproc=65535:65535")
    fi
    # Docker 18.09 on Kunpeng ARM64 has a restrictive seccomp profile that blocks
    # pthread_create (clone syscall), preventing JVM from starting GC threads.
    # Auto-enable seccomp=unconfined for old Docker on ARM64.
    if [[ -z "${DOCKER_PLATFORM_SUPPORTED}" && ("${HOST_ARCH}" == "aarch64" || "${HOST_ARCH}" == "arm64") ]]; then
      local has_seccomp=""
      for opt in "${security_opts[@]+"${security_opts[@]}"}"; do
        if [[ "$opt" == *seccomp* ]]; then has_seccomp="1"; break; fi
      done
      if [[ -z "$has_seccomp" ]]; then
        echo "[dts-build] INFO: Adding seccomp=unconfined for Docker ${DOCKER_API_VERSION} on ARM64 (JVM thread creation fix)"
        security_opts+=(--security-opt "seccomp=unconfined")
      fi
    fi
    # Generate settings.xml on the HOST side before docker run,
    # so we can run mvn directly without a shell wrapper.
    # Using sh -c inside the container corrupts JAVA_HOME on ARM64/Kunpeng.
    if [[ ! -f /root/.m2/settings.xml ]] && [[ -n "${MAVEN_MIRROR_URL}" ]]; then
      mkdir -p /root/.m2
      cat > /root/.m2/settings.xml <<SETTINGS_EOF
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>*</mirrorOf>
      <url>${MAVEN_MIRROR_URL}</url>
    </mirror>
  </mirrors>
</settings>
SETTINGS_EOF
      echo "[dts-build] Generated /root/.m2/settings.xml (mirror: ${MAVEN_MIRROR_URL})"
    fi

    local maven_args=(-B -e -DskipTests -f pom.xml -pl "$module" -am)
    if [[ -f "$MAVEN_SETTINGS_FILE" ]]; then
      maven_args+=(-s "$MAVEN_SETTINGS_FILE")
    elif [[ -f /root/.m2/settings.xml ]]; then
      maven_args+=(-s /root/.m2/settings.xml)
    fi

    if [[ -n "$MAVEN_DEBUG" ]]; then
      echo "[dts-build] DEBUG: mvn args: ${maven_args[*]} package"
      echo "[dts-build] DEBUG: security_opts: ${security_opts[*]+"${security_opts[*]}"}"
    fi
    local container_path="${MAVEN_CONTAINER_JAVA_HOME}/bin:/usr/share/maven/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
    local use_bash_maven_runner=""
    if [[ -z "${DOCKER_PLATFORM_SUPPORTED}" && ("${HOST_ARCH}" == "aarch64" || "${HOST_ARCH}" == "arm64") ]]; then
      use_bash_maven_runner="1"
    fi

    if [[ -n "${use_bash_maven_runner}" ]]; then
      local quoted_maven_args=()
      local arg
      for arg in "${maven_args[@]}" package; do
        quoted_maven_args+=("$(printf '%q' "${arg}")")
      done
      local bash_cmd="export JAVA_HOME=${MAVEN_CONTAINER_JAVA_HOME}; export PATH=${container_path}; exec /usr/bin/mvn ${quoted_maven_args[*]}"
      docker run --rm --memory="${MAVEN_MEMORY_LIMIT}" \
        ${security_opts[@]+"${security_opts[@]}"} \
        --entrypoint /bin/bash \
        -v "${REPO_ROOT}/source:/workspace" \
        -v "/root/.m2:/root/.m2" \
        -w /workspace \
        "$MAVEN_IMAGE" \
        -lc "${bash_cmd}"
    else
      # Run mvn directly — DO NOT wrap in sh -c or bash -lc.
      # Explicitly pass JAVA_HOME/PATH via -e for Docker 18.09 compatibility.
      docker run --rm --memory="${MAVEN_MEMORY_LIMIT}" \
        ${security_opts[@]+"${security_opts[@]}"} \
        -e "JAVA_HOME=${MAVEN_CONTAINER_JAVA_HOME}" \
        -e "PATH=${container_path}" \
        -v "${REPO_ROOT}/source:/workspace" \
        -v "/root/.m2:/root/.m2" \
        -w /workspace \
        "$MAVEN_IMAGE" \
        mvn "${maven_args[@]}" package
    fi
  fi

  local jar_path
  jar_path="$(ls -1t ${REPO_ROOT}/source/${module}/target/${jar_glob} 2>/dev/null | head -n 1 || true)"
  if [[ -z "$jar_path" ]]; then
    echo "[dts-build] ERROR: ${module} jar not found under source/${module}/target" >&2
    exit 1
  fi
  mkdir -p "$(dirname "$out_jar")"
  cp "$jar_path" "$out_jar"
  echo "[dts-build] Copied ${jar_path} -> ${out_jar}"
  validate_module_jar_identity "$module" "$out_jar"
}

init_images_normal() {
  load_image_versions
  IMAGE_DTS_ADMIN="${IMAGE_DTS_ADMIN:-dts-admin:local}"
  IMAGE_DTS_PLATFORM="${IMAGE_DTS_PLATFORM:-dts-platform:local}"
  IMAGE_DTS_INGESTION="${IMAGE_DTS_INGESTION:-dts-ingestion:local}"
  IMAGE_DTS_ANALYTICS="${IMAGE_DTS_ANALYTICS:-dts-analytics:local}"
  IMAGE_DTS_ADMIN_WEBAPP="${IMAGE_DTS_ADMIN_WEBAPP:-dts-admin-webapp:local}"
  IMAGE_DTS_PLATFORM_WEBAPP="${IMAGE_DTS_PLATFORM_WEBAPP:-dts-platform-webapp:local}"
  IMAGE_DTS_ANALYTICS_WEBAPP_MODERN="${IMAGE_DTS_ANALYTICS_WEBAPP_MODERN:-dts-analytics-webapp-modern:local}"
  IMAGE_DTS_AIRFLOW_OM="${IMAGE_DTS_AIRFLOW_OM:-${IMAGE_AIRFLOW:-dts-airflow-om:local}}"
  IMAGE_DTS_DBT="${IMAGE_DTS_DBT:-${IMAGE_DBT:-dts-dbt:1.10.0}}"
  IMAGE_DTS_ADDAX="${IMAGE_DTS_ADDAX:-${IMAGE_ADDAX:-dts-addax:6.0.8}}"
  assert_distinct_backend_images
}

init_images_legacy() {
  load_image_versions
  IMAGE_DTS_ADMIN="${IMAGE_DTS_ADMIN:-dts-admin:local}"
  IMAGE_DTS_PLATFORM="${IMAGE_DTS_PLATFORM:-dts-platform:local}"
  IMAGE_DTS_INGESTION="${IMAGE_DTS_INGESTION:-dts-ingestion:local}"
  IMAGE_DTS_ANALYTICS="${IMAGE_DTS_ANALYTICS:-dts-analytics:local}"
  IMAGE_DTS_ADMIN_WEBAPP="${IMAGE_DTS_ADMIN_WEBAPP:-dts-admin-webapp:local}"
  IMAGE_DTS_PLATFORM_WEBAPP="${IMAGE_DTS_PLATFORM_WEBAPP:-dts-platform-webapp:local}"
  IMAGE_DTS_ANALYTICS_WEBAPP_MODERN="${IMAGE_DTS_ANALYTICS_WEBAPP_MODERN:-dts-analytics-webapp-modern:local}"
  IMAGE_DTS_AIRFLOW_OM="${IMAGE_DTS_AIRFLOW_OM:-${IMAGE_AIRFLOW:-dts-airflow-om:local}}"
  IMAGE_DTS_DBT="${IMAGE_DTS_DBT:-${IMAGE_DBT:-dts-dbt:1.11.2}}"
  IMAGE_DTS_ADDAX="${IMAGE_DTS_ADDAX:-${IMAGE_ADDAX:-dts-addax:6.0.8}}"
  assert_distinct_backend_images
}

resolve_image() {
  local name="$1"
  local mode="$2"
  local tag=""
  local dockerfile=""
  case "$name" in
    dts-admin)
      tag="$IMAGE_DTS_ADMIN"
      dockerfile="${REPO_ROOT}/builds/dts-admin/${mode}"
      ;;
    dts-platform)
      tag="$IMAGE_DTS_PLATFORM"
      dockerfile="${REPO_ROOT}/builds/dts-platform/${mode}"
      ;;
    dts-ingestion)
      tag="$IMAGE_DTS_INGESTION"
      dockerfile="${REPO_ROOT}/builds/dts-ingestion/${mode}"
      ;;
    dts-analytics)
      tag="$IMAGE_DTS_ANALYTICS"
      dockerfile="${REPO_ROOT}/builds/dts-analytics/${mode}"
      ;;
    dts-admin-webapp)
      tag="$IMAGE_DTS_ADMIN_WEBAPP"
      dockerfile="${REPO_ROOT}/builds/dts-admin-webapp/Dockerfile"
      ;;
    dts-platform-webapp)
      tag="$IMAGE_DTS_PLATFORM_WEBAPP"
      dockerfile="${REPO_ROOT}/builds/dts-platform-webapp/Dockerfile"
      ;;
    dts-analytics-webapp-modern)
      tag="$IMAGE_DTS_ANALYTICS_WEBAPP_MODERN"
      dockerfile="${REPO_ROOT}/builds/dts-analytics-webapp/modern/Dockerfile"
      ;;
    dts-airflow-om)
      tag="$IMAGE_DTS_AIRFLOW_OM"
      dockerfile="${REPO_ROOT}/source/dts-airflow-om/Dockerfile"
      ;;
    dts-dbt)
      tag="$IMAGE_DTS_DBT"
      dockerfile="${REPO_ROOT}/builds/dts-dbt/Dockerfile"
      ;;
    dts-addax)
      tag="$IMAGE_DTS_ADDAX"
      dockerfile="${REPO_ROOT}/builds/dts-addax/Dockerfile"
      ;;
    *)
      return 1
      ;;
  esac

  echo "$tag|$dockerfile"
}

build_all_normal() {
  init_images_normal
  local enable_maven_build_arg="${ENABLE_MAVEN_BUILD:-true}"
  if [[ "${PREBUILD_JARS}" == "1" ]]; then
    build_maven_module "dts-admin" "dts-admin-*.jar" "${REPO_ROOT}/builds/dts-admin/dts-admin.jar"
    build_maven_module "dts-platform" "dts-platform-*.jar" "${REPO_ROOT}/builds/dts-platform/dts-platform.jar"
    build_maven_module "dts-ingestion" "dts-ingestion-*.jar" "${REPO_ROOT}/builds/dts-ingestion/dts-ingestion.jar"
    build_maven_module "dts-analytics" "dts-analytics-*.jar" "${REPO_ROOT}/builds/dts-analytics/dts-analytics.jar"
    enable_maven_build_arg="false"
  fi
  if [[ "${PREBUILD_JARS}" != "1" && "${enable_maven_build_arg}" != "true" ]]; then
    echo "[dts-build] INFO: ENABLE_MAVEN_BUILD=false and PREBUILD_JARS!=1, validating existing prebuilt backend jars."
    verify_prebuilt_module_jar "dts-admin" "${REPO_ROOT}/builds/dts-admin/dts-admin.jar"
    verify_prebuilt_module_jar "dts-platform" "${REPO_ROOT}/builds/dts-platform/dts-platform.jar"
    verify_prebuilt_module_jar "dts-ingestion" "${REPO_ROOT}/builds/dts-ingestion/dts-ingestion.jar"
    verify_prebuilt_module_jar "dts-analytics" "${REPO_ROOT}/builds/dts-analytics/dts-analytics.jar"
  fi

  build_image "dts-admin" "$IMAGE_DTS_ADMIN" "${REPO_ROOT}/builds/dts-admin/Dockerfile" "$NORMAL_DIST" \
    --build-arg ENABLE_MAVEN_BUILD="${enable_maven_build_arg}"
  build_image "dts-platform" "$IMAGE_DTS_PLATFORM" "${REPO_ROOT}/builds/dts-platform/Dockerfile" "$NORMAL_DIST" \
    --build-arg ENABLE_MAVEN_BUILD="${enable_maven_build_arg}"
  build_image "dts-ingestion" "$IMAGE_DTS_INGESTION" "${REPO_ROOT}/builds/dts-ingestion/Dockerfile" "$NORMAL_DIST" \
    --build-arg ENABLE_MAVEN_BUILD="${enable_maven_build_arg}"
  build_image "dts-analytics" "$IMAGE_DTS_ANALYTICS" "${REPO_ROOT}/builds/dts-analytics/Dockerfile" "$NORMAL_DIST" \
    --build-arg ENABLE_MAVEN_BUILD="${enable_maven_build_arg}"
  build_image "dts-admin-webapp" "$IMAGE_DTS_ADMIN_WEBAPP" "${REPO_ROOT}/builds/dts-admin-webapp/Dockerfile" "$NORMAL_DIST" \
    --build-arg PNPM_VERSION="${PNPM_VERSION}" \
    --build-arg WEBAPP_BUILD_CMD="${WEBAPP_BUILD_CMD}" \
    --build-arg NPM_REGISTRY="${NPM_REGISTRY:-}" \
    --build-arg NPM_HTTP_PROXY="${NPM_HTTP_PROXY:-${HTTP_PROXY:-}}" \
    --build-arg NPM_HTTPS_PROXY="${NPM_HTTPS_PROXY:-${HTTPS_PROXY:-}}"
  build_image "dts-platform-webapp" "$IMAGE_DTS_PLATFORM_WEBAPP" "${REPO_ROOT}/builds/dts-platform-webapp/Dockerfile" "$NORMAL_DIST" \
    --build-arg PNPM_VERSION="${PNPM_VERSION}" \
    --build-arg WEBAPP_BUILD_CMD="${WEBAPP_BUILD_CMD}" \
    --build-arg NPM_REGISTRY="${NPM_REGISTRY:-}" \
    --build-arg NPM_HTTP_PROXY="${NPM_HTTP_PROXY:-${HTTP_PROXY:-}}" \
    --build-arg NPM_HTTPS_PROXY="${NPM_HTTPS_PROXY:-${HTTPS_PROXY:-}}"
  build_image "dts-analytics-webapp-modern" "$IMAGE_DTS_ANALYTICS_WEBAPP_MODERN" "${REPO_ROOT}/builds/dts-analytics-webapp/modern/Dockerfile" "$NORMAL_DIST" \
    --build-arg PNPM_VERSION="${PNPM_VERSION}" \
    --build-arg WEBAPP_BUILD_CMD="${WEBAPP_BUILD_CMD}" \
    --build-arg NPM_REGISTRY="${NPM_REGISTRY:-}" \
    --build-arg NPM_HTTP_PROXY="${NPM_HTTP_PROXY:-${HTTP_PROXY:-}}" \
    --build-arg NPM_HTTPS_PROXY="${NPM_HTTPS_PROXY:-${HTTPS_PROXY:-}}"
  build_image "dts-airflow-om" "$IMAGE_DTS_AIRFLOW_OM" "${REPO_ROOT}/source/dts-airflow-om/Dockerfile" "$NORMAL_DIST" \
    --build-arg PIP_INDEX_URL="${PIP_INDEX_URL:-}" \
    --build-arg PIP_TRUSTED_HOST="${PIP_TRUSTED_HOST:-}" \
    --build-arg PIP_DEFAULT_TIMEOUT="${PIP_DEFAULT_TIMEOUT:-60}" \
    --build-arg PIP_RETRIES="${PIP_RETRIES:-10}" \
    --build-arg HTTP_PROXY="${HTTP_PROXY:-}" \
    --build-arg HTTPS_PROXY="${HTTPS_PROXY:-}" \
    --build-arg NO_PROXY="${NO_PROXY:-}"
  build_image_ctx "dts-dbt" "$IMAGE_DTS_DBT" "${REPO_ROOT}/builds/dts-dbt/Dockerfile" "${REPO_ROOT}/builds/dts-dbt" "$NORMAL_DIST"
  build_image_ctx "dts-addax" "$IMAGE_DTS_ADDAX" "${REPO_ROOT}/builds/dts-addax/Dockerfile" "${REPO_ROOT}/builds/dts-addax" "$NORMAL_DIST" \
    --build-arg MAVEN_MIRROR_URL="${MAVEN_MIRROR_URL}" \
    --build-arg HTTP_PROXY="${HTTP_PROXY:-}" \
    --build-arg HTTPS_PROXY="${HTTPS_PROXY:-}" \
    --build-arg NO_PROXY="${NO_PROXY:-}"
}

build_all_legacy() {
  init_images_legacy
  # Legacy/offline Dockerfiles consume prebuilt backend jars directly.
  # Validate identities before image build to avoid cross-module artifact mix-ups.
  verify_prebuilt_module_jar "dts-admin" "${REPO_ROOT}/builds/dts-admin/dts-admin.jar"
  verify_prebuilt_module_jar "dts-platform" "${REPO_ROOT}/builds/dts-platform/dts-platform.jar"
  verify_prebuilt_module_jar "dts-ingestion" "${REPO_ROOT}/builds/dts-ingestion/dts-ingestion.jar"
  verify_prebuilt_module_jar "dts-analytics" "${REPO_ROOT}/builds/dts-analytics/dts-analytics.jar"
  build_image "dts-admin" "$IMAGE_DTS_ADMIN" "${REPO_ROOT}/builds/dts-admin/Dockerfile.offline" "$LEGACY_DIST"
  build_image "dts-platform" "$IMAGE_DTS_PLATFORM" "${REPO_ROOT}/builds/dts-platform/Dockerfile.offline" "$LEGACY_DIST"
  build_image "dts-ingestion" "$IMAGE_DTS_INGESTION" "${REPO_ROOT}/builds/dts-ingestion/Dockerfile.offline" "$LEGACY_DIST"
  build_image "dts-analytics" "$IMAGE_DTS_ANALYTICS" "${REPO_ROOT}/builds/dts-analytics/Dockerfile.offline" "$LEGACY_DIST"
  build_image "dts-admin-webapp" "$IMAGE_DTS_ADMIN_WEBAPP" "${REPO_ROOT}/builds/dts-admin-webapp/Dockerfile" "$LEGACY_DIST" \
    --build-arg PNPM_VERSION="${PNPM_VERSION}" \
    --build-arg WEBAPP_BUILD_CMD="${WEBAPP_BUILD_CMD}" \
    --build-arg NPM_REGISTRY="${NPM_REGISTRY:-}" \
    --build-arg NPM_HTTP_PROXY="${NPM_HTTP_PROXY:-${HTTP_PROXY:-}}" \
    --build-arg NPM_HTTPS_PROXY="${NPM_HTTPS_PROXY:-${HTTPS_PROXY:-}}"
  build_image "dts-platform-webapp" "$IMAGE_DTS_PLATFORM_WEBAPP" "${REPO_ROOT}/builds/dts-platform-webapp/Dockerfile" "$LEGACY_DIST" \
    --build-arg PNPM_VERSION="${PNPM_VERSION}" \
    --build-arg WEBAPP_BUILD_CMD="${WEBAPP_BUILD_CMD}" \
    --build-arg NPM_REGISTRY="${NPM_REGISTRY:-}" \
    --build-arg NPM_HTTP_PROXY="${NPM_HTTP_PROXY:-${HTTP_PROXY:-}}" \
    --build-arg NPM_HTTPS_PROXY="${NPM_HTTPS_PROXY:-${HTTPS_PROXY:-}}"
  build_image "dts-analytics-webapp-modern" "$IMAGE_DTS_ANALYTICS_WEBAPP_MODERN" "${REPO_ROOT}/builds/dts-analytics-webapp/modern/Dockerfile" "$LEGACY_DIST" \
    --build-arg PNPM_VERSION="${PNPM_VERSION}" \
    --build-arg WEBAPP_BUILD_CMD="${WEBAPP_BUILD_CMD}" \
    --build-arg NPM_REGISTRY="${NPM_REGISTRY:-}" \
    --build-arg NPM_HTTP_PROXY="${NPM_HTTP_PROXY:-${HTTP_PROXY:-}}" \
    --build-arg NPM_HTTPS_PROXY="${NPM_HTTPS_PROXY:-${HTTPS_PROXY:-}}"
  build_image "dts-airflow-om" "$IMAGE_DTS_AIRFLOW_OM" "${REPO_ROOT}/source/dts-airflow-om/Dockerfile" "$LEGACY_DIST" \
    --build-arg PIP_INDEX_URL="${PIP_INDEX_URL:-}" \
    --build-arg PIP_TRUSTED_HOST="${PIP_TRUSTED_HOST:-}" \
    --build-arg PIP_DEFAULT_TIMEOUT="${PIP_DEFAULT_TIMEOUT:-60}" \
    --build-arg PIP_RETRIES="${PIP_RETRIES:-10}" \
    --build-arg HTTP_PROXY="${HTTP_PROXY:-}" \
    --build-arg HTTPS_PROXY="${HTTPS_PROXY:-}" \
    --build-arg NO_PROXY="${NO_PROXY:-}"
  build_image_ctx "dts-dbt" "$IMAGE_DTS_DBT" "${REPO_ROOT}/builds/dts-dbt/Dockerfile" "${REPO_ROOT}/builds/dts-dbt" "$LEGACY_DIST"
  build_image_ctx "dts-addax" "$IMAGE_DTS_ADDAX" "${REPO_ROOT}/builds/dts-addax/Dockerfile" "${REPO_ROOT}/builds/dts-addax" "$LEGACY_DIST" \
    --build-arg MAVEN_MIRROR_URL="${MAVEN_MIRROR_URL}" \
    --build-arg HTTP_PROXY="${HTTP_PROXY:-}" \
    --build-arg HTTPS_PROXY="${HTTPS_PROXY:-}" \
    --build-arg NO_PROXY="${NO_PROXY:-}"
}

build_single_image() {
  local name="$1"

  init_images_normal
  init_images_legacy

  local enable_maven_build_arg="${ENABLE_MAVEN_BUILD:-true}"
  if [[ "${PREBUILD_JARS}" == "1" ]]; then
    case "$name" in
      dts-admin)
        build_maven_module "dts-admin" "dts-admin-*.jar" "${REPO_ROOT}/builds/dts-admin/dts-admin.jar"
        enable_maven_build_arg="false"
        ;;
      dts-platform)
        build_maven_module "dts-platform" "dts-platform-*.jar" "${REPO_ROOT}/builds/dts-platform/dts-platform.jar"
        enable_maven_build_arg="false"
        ;;
      dts-ingestion)
        build_maven_module "dts-ingestion" "dts-ingestion-*.jar" "${REPO_ROOT}/builds/dts-ingestion/dts-ingestion.jar"
        enable_maven_build_arg="false"
        ;;
      dts-analytics)
        build_maven_module "dts-analytics" "dts-analytics-*.jar" "${REPO_ROOT}/builds/dts-analytics/dts-analytics.jar"
        enable_maven_build_arg="false"
        ;;
    esac
  fi
  if [[ "${PREBUILD_JARS}" != "1" && "${enable_maven_build_arg}" != "true" ]]; then
    case "$name" in
      dts-admin)
        verify_prebuilt_module_jar "dts-admin" "${REPO_ROOT}/builds/dts-admin/dts-admin.jar"
        ;;
      dts-platform)
        verify_prebuilt_module_jar "dts-platform" "${REPO_ROOT}/builds/dts-platform/dts-platform.jar"
        ;;
      dts-ingestion)
        verify_prebuilt_module_jar "dts-ingestion" "${REPO_ROOT}/builds/dts-ingestion/dts-ingestion.jar"
        ;;
      dts-analytics)
        verify_prebuilt_module_jar "dts-analytics" "${REPO_ROOT}/builds/dts-analytics/dts-analytics.jar"
        ;;
    esac
  fi

  local normal_res
  if ! normal_res=$(resolve_image "$name" "Dockerfile"); then
    echo "[dts-build] ERROR: unknown image name: ${name}" >&2
    exit 1
  fi
  local normal_tag="${normal_res%%|*}"
  local normal_df="${normal_res##*|}"
  local legacy_df=""

  case "$name" in
    dts-admin)
      legacy_df="${REPO_ROOT}/builds/dts-admin/Dockerfile.offline"
      ;;
    dts-platform)
      legacy_df="${REPO_ROOT}/builds/dts-platform/Dockerfile.offline"
      ;;
    dts-ingestion)
      legacy_df="${REPO_ROOT}/builds/dts-ingestion/Dockerfile.offline"
      ;;
    dts-analytics)
      legacy_df="${REPO_ROOT}/builds/dts-analytics/Dockerfile.offline"
      ;;
    *)
      legacy_df=""
      ;;
  esac

  local build_args=()
  case "$name" in
    dts-admin|dts-platform|dts-ingestion|dts-analytics)
      build_args+=(--build-arg ENABLE_MAVEN_BUILD="${enable_maven_build_arg}")
      ;;
    dts-admin-webapp|dts-platform-webapp)
      build_args+=(--build-arg PNPM_VERSION="${PNPM_VERSION}" --build-arg WEBAPP_BUILD_CMD="${WEBAPP_BUILD_CMD}"
        --build-arg NPM_REGISTRY="${NPM_REGISTRY:-}"
        --build-arg NPM_HTTP_PROXY="${NPM_HTTP_PROXY:-${HTTP_PROXY:-}}"
        --build-arg NPM_HTTPS_PROXY="${NPM_HTTPS_PROXY:-${HTTPS_PROXY:-}}")
      ;;
    dts-analytics-webapp-modern)
      build_args+=(--build-arg PNPM_VERSION="${PNPM_VERSION}"
        --build-arg WEBAPP_BUILD_CMD="${WEBAPP_BUILD_CMD}"
        --build-arg NPM_REGISTRY="${NPM_REGISTRY:-}"
        --build-arg NPM_HTTP_PROXY="${NPM_HTTP_PROXY:-${HTTP_PROXY:-}}"
        --build-arg NPM_HTTPS_PROXY="${NPM_HTTPS_PROXY:-${HTTPS_PROXY:-}}")
      ;;
    dts-airflow-om)
      build_args+=(
        --build-arg PIP_INDEX_URL="${PIP_INDEX_URL:-}"
        --build-arg PIP_TRUSTED_HOST="${PIP_TRUSTED_HOST:-}"
        --build-arg PIP_DEFAULT_TIMEOUT="${PIP_DEFAULT_TIMEOUT:-60}"
        --build-arg PIP_RETRIES="${PIP_RETRIES:-10}"
        --build-arg HTTP_PROXY="${HTTP_PROXY:-}"
        --build-arg HTTPS_PROXY="${HTTPS_PROXY:-}"
        --build-arg NO_PROXY="${NO_PROXY:-}"
      )
      ;;
    dts-addax)
      build_args+=(
        --build-arg MAVEN_MIRROR_URL="${MAVEN_MIRROR_URL}"
        --build-arg HTTP_PROXY="${HTTP_PROXY:-}"
        --build-arg HTTPS_PROXY="${HTTPS_PROXY:-}"
        --build-arg NO_PROXY="${NO_PROXY:-}"
      )
      ;;
  esac

  if [[ "$name" == "dts-dbt" || "$name" == "dts-addax" ]]; then
    build_image_ctx "$name" "$normal_tag" "$normal_df" "${REPO_ROOT}/builds/${name}" "$NORMAL_DIST" "${build_args[@]}"
  else
    build_image "$name" "$normal_tag" "$normal_df" "$NORMAL_DIST" "${build_args[@]}"
  fi

  if [[ -n "$legacy_df" && -f "$legacy_df" ]]; then
    if [[ "$name" == "dts-dbt" || "$name" == "dts-addax" ]]; then
      build_image_ctx "$name" "$normal_tag" "$legacy_df" "${REPO_ROOT}/builds/${name}" "$LEGACY_DIST" "${build_args[@]}"
    else
      build_image "$name" "$normal_tag" "$legacy_df" "$LEGACY_DIST" "${build_args[@]}"
    fi
  else
    save_image "$normal_tag" "$LEGACY_DIST"
  fi
}

pack_deployment() {
  local output_path="${PACK_OUTPUT:-}"
  local include_images="${PACK_INCLUDE_IMAGES:-true}"
  local timestamp
  timestamp="$(date +%Y%m%d-%H%M%S)"

  if [[ -z "$output_path" ]]; then
    output_path="${REPO_ROOT}/dts-stack-${timestamp}.tar.gz"
  fi

  echo "[dts-build] Packaging dts-stack for deployment..."
  echo "[dts-build] Output: ${output_path}"
  echo "[dts-build] Include images: ${include_images}"

  # Create temporary directory for packaging
  local tmp_dir
  tmp_dir="$(mktemp -d)"
  local pack_dir="${tmp_dir}/dts-stack"
  mkdir -p "${pack_dir}"

  # Define files/directories to include
  local include_files=(
    # Core scripts
    "init.sh"
    "start.sh"
    "stop.sh"
    "encry.sh"
    # Compose files
    "docker-compose.yml"
    "docker-compose.legacy.yml"
    "docker-compose-app.yml"
    # Config files
    "imgversion.conf"
    "imgversion.dts-source.conf"
    ".dockerignore"
  )

  local include_dirs=(
    # Service configurations
    "services/certs"
    "services/dts-admin"
    "services/dts-addax"
    "services/dts-airflow/config"
    "services/dts-airflow/plugins"
    "services/dts-airflow/extra"
    "services/dts-analytics"
    "services/dts-dbt/macros"
    "services/dts-dbt/profiles"
    "services/dts-elasticsearch"
    "services/dts-keycloak"
    "services/dts-minio-init"
    "services/dts-openmetadata"
    "services/dts-pg/init"
    "services/dts-platform"
    "services/dts-proxy"
    "services/dts-ranger"
    "services/dts-trino"
    "services/jdbc"
    # Tools
    "bin"
    "tools"
    # Config
    "config"
    # Builds (scripts only, not intermediate files)
    "builds/airflow"
  )

  # Copy individual files
  for file in "${include_files[@]}"; do
    if [[ -f "${REPO_ROOT}/${file}" ]]; then
      cp "${REPO_ROOT}/${file}" "${pack_dir}/"
      echo "[dts-build]   + ${file}"
    fi
  done

  # Copy directories
  for dir in "${include_dirs[@]}"; do
    if [[ -d "${REPO_ROOT}/${dir}" ]]; then
      mkdir -p "${pack_dir}/$(dirname "${dir}")"
      cp -r "${REPO_ROOT}/${dir}" "${pack_dir}/$(dirname "${dir}")/"
      echo "[dts-build]   + ${dir}/"
    fi
  done

  # Copy dbt project files (but not target/logs)
  mkdir -p "${pack_dir}/services/dts-dbt"
  cp "${REPO_ROOT}/services/dts-dbt/dbt_project.yml" "${pack_dir}/services/dts-dbt/" 2>/dev/null || true
  cp "${REPO_ROOT}/services/dts-dbt/run-tests.sh" "${pack_dir}/services/dts-dbt/" 2>/dev/null || true
  mkdir -p "${pack_dir}/services/dts-dbt/models"
  # Copy model structure but not content (will be regenerated)
  find "${REPO_ROOT}/services/dts-dbt/models" -type d -exec mkdir -p "${pack_dir}/services/dts-dbt/models/{}" \; 2>/dev/null || true

  # Copy build scripts
  mkdir -p "${pack_dir}/builds"
  cp "${REPO_ROOT}/builds/dts-build.sh" "${pack_dir}/builds/"
  cp "${REPO_ROOT}/builds/pull-images.sh" "${pack_dir}/builds/" 2>/dev/null || true
  echo "[dts-build]   + builds/dts-build.sh"

  # Copy image tarballs if requested
  if [[ "${include_images}" == "true" ]]; then
    if [[ -d "${REPO_ROOT}/builds/dist" ]] && [[ -n "$(ls -A "${REPO_ROOT}/builds/dist" 2>/dev/null)" ]]; then
      mkdir -p "${pack_dir}/builds/dist"
      cp "${REPO_ROOT}/builds/dist/"*.tar "${pack_dir}/builds/dist/" 2>/dev/null || true
      echo "[dts-build]   + builds/dist/*.tar"
    fi
    if [[ -d "${REPO_ROOT}/builds/legacy-dist" ]] && [[ -n "$(ls -A "${REPO_ROOT}/builds/legacy-dist" 2>/dev/null)" ]]; then
      mkdir -p "${pack_dir}/builds/legacy-dist"
      cp "${REPO_ROOT}/builds/legacy-dist/"*.tar "${pack_dir}/builds/legacy-dist/" 2>/dev/null || true
      echo "[dts-build]   + builds/legacy-dist/*.tar"
    fi
  fi

  # Create empty directories that init.sh expects
  mkdir -p "${pack_dir}/logs"
  mkdir -p "${pack_dir}/services/dts-pg/data"
  mkdir -p "${pack_dir}/services/dts-airflow/dags"
  mkdir -p "${pack_dir}/services/dts-airflow/logs"
  mkdir -p "${pack_dir}/services/dts-dbt/target"
  mkdir -p "${pack_dir}/services/dts-dbt/logs"

  # Create .env template (without secrets)
  cat > "${pack_dir}/.env.template" <<'ENV_TEMPLATE'
# DTS Stack Environment Configuration Template
# Copy this file to .env and modify as needed, or run init.sh to generate

# Base domain (required)
# BASE_DOMAIN=dts.local

# TLS port
# TLS_PORT=443

# Postgres mode: embedded or external
# PG_MODE=embedded
# PG_HOST=dts-pg

# For external Postgres, set these:
# PG_HOST=your-pg-host
# PG_PORT=5432
# PG_SUPER_USER=postgres
# PG_SUPER_PASSWORD=your-password

# Run init.sh to generate full configuration with secure passwords
ENV_TEMPLATE
  echo "[dts-build]   + .env.template"

  # Create README for deployment
  cat > "${pack_dir}/DEPLOY.md" <<'DEPLOY_README'
# DTS Stack Deployment Guide

## Quick Start

1. Extract the package:
   ```bash
   tar -xzf dts-stack-*.tar.gz
   cd dts-stack
   ```

2. Run initialization:
   ```bash
   ./init.sh single <your-password> <your-domain>
   ```
   Or for interactive mode:
   ```bash
   ./init.sh
   ```

3. Wait for services to start, then access:
   - Platform UI: https://bi.<your-domain>
   - Admin UI: https://biadmin.<your-domain>
   - SSO: https://sso.<your-domain>

## Loading Images (if --no-images was used)

If images were not included in the package, load them:
```bash
for tar in builds/dist/*.tar; do docker load -i "$tar"; done
```

## Directory Structure

- `services/` - Service configurations
- `builds/` - Build scripts and image tarballs
- `tools/` - Helper tools
- `config/` - Additional configurations
- `logs/` - Runtime logs (created on first run)

## Important Notes

- Run as root or with docker permissions
- Ensure ports 80, 443, and other required ports are available
- For production, use proper TLS certificates in services/certs/
DEPLOY_README
  echo "[dts-build]   + DEPLOY.md"

  # Create the tarball
  echo "[dts-build] Creating tarball..."
  tar -czf "${output_path}" -C "${tmp_dir}" "dts-stack"

  # Cleanup
  rm -rf "${tmp_dir}"

  # Show result
  local size
  size="$(du -h "${output_path}" | cut -f1)"
  echo "[dts-build] Package created: ${output_path} (${size})"
  echo "[dts-build] Done!"
}

if [[ -n "${PACK_MODE}" ]]; then
  pack_deployment
  exit 0
fi

require_cmd docker
preflight_check

if [[ "$MODE" == "all" ]]; then
  build_all_normal
  build_all_legacy
elif [[ -n "$IMAGE_ONLY" ]]; then
  build_single_image "$IMAGE_ONLY"
else
  usage
  exit 1
fi
