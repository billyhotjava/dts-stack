#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

export DOCKER_BUILDKIT=1

MODE=""
IMAGE_ONLY=""
PACK_MODE=""
PACK_OUTPUT=""
PACK_INCLUDE_IMAGES="true"

usage() {
  cat <<USAGE
Usage:
  ${0##*/} -all
  ${0##*/} --image <name>
  ${0##*/} --pack [--output <path>] [--no-images]

Options:
  -all, --all           Build all images (same as legacy buildAll.sh behavior).
  --image <name>        Build a single image and save tarballs to both dist/ and legacy-dist/.
  --pack                Package dts-stack for deployment (excludes source, logs, git, etc.).
  --output <path>       Output path for the package tarball (default: ./dts-stack-<timestamp>.tar.gz).
  --no-images           Exclude image tarballs from package (smaller package, images loaded separately).

Examples:
  ${0##*/} -all
  ${0##*/} --image dts-admin
  ${0##*/} --image dts-dbt
  ${0##*/} --pack
  ${0##*/} --pack --output /tmp/dts-deploy.tar.gz
  ${0##*/} --pack --no-images
USAGE
}

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
MAVEN_SECURITY_OPT="${MAVEN_SECURITY_OPT:-}"
LEGACY_USE_HOST_MAVEN="${LEGACY_USE_HOST_MAVEN:-}"
MAVEN_DEBUG="${MAVEN_DEBUG:-}"
LEGACY_UNRESTRICTED="${LEGACY_UNRESTRICTED:-1}"
MAVEN_UNRESTRICTED="${MAVEN_UNRESTRICTED:-${LEGACY_UNRESTRICTED:-}}"
MAVEN_MIRROR_URL="${MAVEN_MIRROR_URL:-https://maven.aliyun.com/repository/public}"
NPM_REGISTRY="${NPM_REGISTRY:-https://registry.npmmirror.com}"
MAVEN_SETTINGS_FILE="${MAVEN_SETTINGS_FILE:-/root/.m2/settings.xml}"
PREBUILD_JARS="${PREBUILD_JARS:-1}"
WEBAPP_BUILD_CMD="${WEBAPP_BUILD_CMD:-build}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[dts-build] ERROR: '$1' not found in PATH" >&2
    exit 1
  fi
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
  mkdir -p "$output_dir"
  local tar_path="${output_dir}/$(sanitize_tag "$tag")-${BUILD_TS}.tar"
  docker save "$tag" -o "$tar_path"
  echo "[dts-build] Saved ${tar_path}"
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
      security_opts+=(--security-opt "seccomp=unconfined" --pids-limit=-1 --ulimit "nproc=65535:65535")
    fi
    local maven_args=(-B -e -DskipTests -f pom.xml -pl "$module" -am)
    if [[ -f "$MAVEN_SETTINGS_FILE" ]]; then
      maven_args=(-B -e -DskipTests -s "$MAVEN_SETTINGS_FILE" -f pom.xml -pl "$module" -am)
    fi

    if [[ -n "$MAVEN_DEBUG" ]]; then
      docker run --rm "${security_opts[@]}" \
        -e "MAVEN_MIRROR_URL=${MAVEN_MIRROR_URL}" \
        -v "${REPO_ROOT}/source:/workspace" \
        -v "/root/.m2:/root/.m2" \
        -w /workspace \
        "$MAVEN_IMAGE" \
        sh -lc 'set -eux; JAVA_BIN=$(command -v java || true); if [ -z "$JAVA_BIN" ]; then echo >&2 "java not found"; exit 1; fi; JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$JAVA_BIN")")")"; export JAVA_HOME; env | grep -E "JAVA_HOME|PATH"; java -version; ls -la "$JAVA_HOME/bin/java"; \
          if [ ! -f /root/.m2/settings.xml ]; then \
            printf "%s\n" \
              "<settings xmlns=\"http://maven.apache.org/SETTINGS/1.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd\">" \
              "  <mirrors>" \
              "    <mirror>" \
              "      <id>aliyun</id>" \
              "      <mirrorOf>*</mirrorOf>" \
              "      <url>${MAVEN_MIRROR_URL}</url>" \
              "    </mirror>" \
              "  </mirrors>" \
              "</settings>" \
              > /root/.m2/settings.xml; \
          fi; \
          mvn -v; mvn "$@" package' \
        -- "${maven_args[@]}"
    else
      docker run --rm "${security_opts[@]}" \
        -e "MAVEN_MIRROR_URL=${MAVEN_MIRROR_URL}" \
        -v "${REPO_ROOT}/source:/workspace" \
        -v "/root/.m2:/root/.m2" \
        -w /workspace \
        "$MAVEN_IMAGE" \
        sh -lc 'JAVA_BIN=$(command -v java || true); if [ -z "$JAVA_BIN" ]; then echo >&2 "java not found"; exit 1; fi; JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$JAVA_BIN")")")"; export JAVA_HOME; \
        if [ ! -f /root/.m2/settings.xml ]; then \
          printf "%s\n" \
            "<settings xmlns=\"http://maven.apache.org/SETTINGS/1.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd\">" \
            "  <mirrors>" \
            "    <mirror>" \
            "      <id>aliyun</id>" \
            "      <mirrorOf>*</mirrorOf>" \
            "      <url>${MAVEN_MIRROR_URL}</url>" \
            "    </mirror>" \
            "  </mirrors>" \
            "</settings>" \
            > /root/.m2/settings.xml; \
        fi; \
        mvn "$@" package' \
        -- "${maven_args[@]}"
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
  IMAGE_DTS_DBT="${IMAGE_DTS_DBT:-${IMAGE_DBT:-dts-dbt:1.11.2}}"
  IMAGE_DTS_ADDAX="${IMAGE_DTS_ADDAX:-${IMAGE_ADDAX:-dts-addax:6.0.8}}"
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

if [[ "$MODE" == "all" ]]; then
  build_all_normal
  build_all_legacy
elif [[ -n "$IMAGE_ONLY" ]]; then
  build_single_image "$IMAGE_ONLY"
else
  usage
  exit 1
fi
