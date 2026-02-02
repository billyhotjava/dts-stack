#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-both}"

# Force BuildKit – the legacy builder's seccomp profile blocks JVM thread
# creation (pthread_create EPERM) which breaks Maven builds inside docker build.
export DOCKER_BUILDKIT=1

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
MAVEN_SETTINGS_FILE="${MAVEN_SETTINGS_FILE:-/root/.m2/settings.xml}"
PREBUILD_JARS="${PREBUILD_JARS:-1}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[buildAll] ERROR: '$1' not found in PATH" >&2
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
    echo "[buildAll] WARN: ${IMGVERSION_FILE} not found; using default image tags" >&2
  fi
}

sanitize_tag() {
  echo "$1" | tr '/:' '__'
}

build_image() {
  local name="$1"
  local tag="$2"
  local dockerfile="$3"
  local output_dir="$4"
  shift 4
  local args=("$@")

  echo "[buildAll] Building ${name} -> ${tag}"
  docker build -t "$tag" -f "$dockerfile" "${args[@]}" "$REPO_ROOT"
  mkdir -p "$output_dir"
  local tar_path="${output_dir}/$(sanitize_tag "$tag").tar"
  docker save "$tag" -o "$tar_path"
  echo "[buildAll] Saved ${tar_path}"
}

build_maven_module() {
  local module="$1"
  local jar_glob="$2"
  local out_jar="$3"

  if [[ -f "$out_jar" ]]; then
    echo "[buildAll] Using prebuilt jar for ${module}: ${out_jar}"
    return
  fi
  if [[ "${LEGACY_USE_PREBUILT_JARS:-}" == "1" ]]; then
    echo "[buildAll] ERROR: ${out_jar} missing (LEGACY_USE_PREBUILT_JARS=1)" >&2
    exit 1
  fi

  if [[ -n "$LEGACY_USE_HOST_MAVEN" ]]; then
    require_cmd mvn
    echo "[buildAll] Building ${module} jar via host Maven"
    if [[ ! -f "$MAVEN_SETTINGS_FILE" ]]; then
      mkdir -p "$(dirname "$MAVEN_SETTINGS_FILE")"
      cat > "$MAVEN_SETTINGS_FILE" <<EOF
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
EOF
    fi
    mvn -B -e -DskipTests -s "$MAVEN_SETTINGS_FILE" -f "${REPO_ROOT}/source/pom.xml" -pl "$module" -am package
  else
    echo "[buildAll] Building ${module} jar via ${MAVEN_IMAGE}"
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
  fi

  local jar_path
  jar_path="$(ls -1t ${REPO_ROOT}/source/${module}/target/${jar_glob} 2>/dev/null | head -n 1 || true)"
  if [[ -z "$jar_path" ]]; then
    echo "[buildAll] ERROR: ${module} jar not found under source/${module}/target" >&2
    exit 1
  fi
  mkdir -p "$(dirname "$out_jar")"
  cp "$jar_path" "$out_jar"
  echo "[buildAll] Copied ${jar_path} -> ${out_jar}"
}

require_cmd docker

case "$MODE" in
  normal|legacy|both) ;;
  *)
    echo "Usage: $0 [normal|legacy|both]" >&2
    exit 1
    ;;
esac

if [[ "$MODE" == "normal" || "$MODE" == "both" ]]; then
  load_image_versions
  IMAGE_DTS_ADMIN="${IMAGE_DTS_ADMIN:-dts-admin:local}"
  IMAGE_DTS_PLATFORM="${IMAGE_DTS_PLATFORM:-dts-platform:local}"
  IMAGE_DTS_INGESTION="${IMAGE_DTS_INGESTION:-dts-ingestion:local}"
  IMAGE_DTS_ANALYTICS="${IMAGE_DTS_ANALYTICS:-dts-analytics:local}"
  IMAGE_DTS_ADMIN_WEBAPP="${IMAGE_DTS_ADMIN_WEBAPP:-dts-admin-webapp:local}"
  IMAGE_DTS_PLATFORM_WEBAPP="${IMAGE_DTS_PLATFORM_WEBAPP:-dts-platform-webapp:local}"
  IMAGE_DTS_ANALYTICS_WEBAPP_MODERN="${IMAGE_DTS_ANALYTICS_WEBAPP_MODERN:-dts-analytics-webapp-modern:local}"
  IMAGE_DTS_AIRFLOW_OM="${IMAGE_DTS_AIRFLOW_OM:-${IMAGE_AIRFLOW:-dts-airflow-om:local}}"

  ENABLE_MAVEN_BUILD_ARG="${ENABLE_MAVEN_BUILD:-true}"
  if [[ "${PREBUILD_JARS}" == "1" ]]; then
    build_maven_module "dts-admin" "dts-admin-*.jar" "${REPO_ROOT}/builds/dts-admin/dts-admin.jar"
    build_maven_module "dts-platform" "dts-platform-*.jar" "${REPO_ROOT}/builds/dts-platform/dts-platform.jar"
    build_maven_module "dts-ingestion" "dts-ingestion-*.jar" "${REPO_ROOT}/builds/dts-ingestion/dts-ingestion.jar"
    ENABLE_MAVEN_BUILD_ARG="false"
  fi

  build_image "dts-admin" "$IMAGE_DTS_ADMIN" "${REPO_ROOT}/builds/dts-admin/Dockerfile" "$NORMAL_DIST" \
    --build-arg ENABLE_MAVEN_BUILD="${ENABLE_MAVEN_BUILD_ARG}"
  build_image "dts-platform" "$IMAGE_DTS_PLATFORM" "${REPO_ROOT}/builds/dts-platform/Dockerfile" "$NORMAL_DIST" \
    --build-arg ENABLE_MAVEN_BUILD="${ENABLE_MAVEN_BUILD_ARG}"
  build_image "dts-ingestion" "$IMAGE_DTS_INGESTION" "${REPO_ROOT}/builds/dts-ingestion/Dockerfile" "$NORMAL_DIST" \
    --build-arg ENABLE_MAVEN_BUILD="${ENABLE_MAVEN_BUILD_ARG}"
  build_image "dts-analytics" "$IMAGE_DTS_ANALYTICS" "${REPO_ROOT}/builds/dts-analytics/Dockerfile" "$NORMAL_DIST"
  build_image "dts-admin-webapp" "$IMAGE_DTS_ADMIN_WEBAPP" "${REPO_ROOT}/builds/dts-admin-webapp/Dockerfile" "$NORMAL_DIST" \
    --build-arg PNPM_VERSION="${PNPM_VERSION}" \
    --build-arg WEBAPP_BUILD_CMD="build:modern"
  build_image "dts-platform-webapp" "$IMAGE_DTS_PLATFORM_WEBAPP" "${REPO_ROOT}/builds/dts-platform-webapp/Dockerfile" "$NORMAL_DIST" \
    --build-arg PNPM_VERSION="${PNPM_VERSION}" \
    --build-arg WEBAPP_BUILD_CMD="build:modern"
  build_image "dts-analytics-webapp-modern" "$IMAGE_DTS_ANALYTICS_WEBAPP_MODERN" "${REPO_ROOT}/builds/dts-analytics-webapp/modern/Dockerfile" "$NORMAL_DIST" \
    --build-arg PNPM_VERSION="${PNPM_VERSION}"
  build_image "dts-airflow-om" "$IMAGE_DTS_AIRFLOW_OM" "${REPO_ROOT}/source/dts-airflow-om/Dockerfile" "$NORMAL_DIST" \
    --build-arg PIP_INDEX_URL="${PIP_INDEX_URL:-}" \
    --build-arg PIP_TRUSTED_HOST="${PIP_TRUSTED_HOST:-}" \
    --build-arg PIP_DEFAULT_TIMEOUT="${PIP_DEFAULT_TIMEOUT:-60}" \
    --build-arg PIP_RETRIES="${PIP_RETRIES:-10}" \
    --build-arg HTTP_PROXY="${HTTP_PROXY:-}" \
    --build-arg HTTPS_PROXY="${HTTPS_PROXY:-}" \
    --build-arg NO_PROXY="${NO_PROXY:-}"
fi

if [[ "$MODE" == "legacy" || "$MODE" == "both" ]]; then
  load_image_versions
  IMAGE_DTS_ADMIN="${IMAGE_DTS_ADMIN:-dts-admin:local}"
  IMAGE_DTS_PLATFORM="${IMAGE_DTS_PLATFORM:-dts-platform:local}"
  IMAGE_DTS_INGESTION="${IMAGE_DTS_INGESTION:-dts-ingestion:local}"
  IMAGE_DTS_ANALYTICS="${IMAGE_DTS_ANALYTICS:-dts-analytics:local}"
  IMAGE_DTS_ADMIN_WEBAPP="${IMAGE_DTS_ADMIN_WEBAPP:-dts-admin-webapp:local}"
  IMAGE_DTS_PLATFORM_WEBAPP="${IMAGE_DTS_PLATFORM_WEBAPP:-dts-platform-webapp:local}"
  IMAGE_DTS_ANALYTICS_WEBAPP_LEGACY="${IMAGE_DTS_ANALYTICS_WEBAPP_LEGACY:-dts-analytics-webapp:local}"
  IMAGE_DTS_AIRFLOW_OM="${IMAGE_DTS_AIRFLOW_OM:-${IMAGE_AIRFLOW:-dts-airflow-om:local}}"

  build_maven_module "dts-admin" "dts-admin-*.jar" "${REPO_ROOT}/builds/dts-admin/dts-admin.jar"
  build_maven_module "dts-platform" "dts-platform-*.jar" "${REPO_ROOT}/builds/dts-platform/dts-platform.jar"
  build_maven_module "dts-analytics" "dts-analytics-*.jar" "${REPO_ROOT}/builds/dts-analytics/dts-analytics.jar"
  build_maven_module "dts-ingestion" "dts-ingestion-*.jar" "${REPO_ROOT}/builds/dts-ingestion/dts-ingestion.jar"

  build_image "dts-admin" "$IMAGE_DTS_ADMIN" "${REPO_ROOT}/builds/dts-admin/Dockerfile.offline" "$LEGACY_DIST"
  build_image "dts-platform" "$IMAGE_DTS_PLATFORM" "${REPO_ROOT}/builds/dts-platform/Dockerfile.offline" "$LEGACY_DIST"
  build_image "dts-ingestion" "$IMAGE_DTS_INGESTION" "${REPO_ROOT}/builds/dts-ingestion/Dockerfile.offline" "$LEGACY_DIST"
  build_image "dts-analytics" "$IMAGE_DTS_ANALYTICS" "${REPO_ROOT}/builds/dts-analytics/Dockerfile.offline" "$LEGACY_DIST"
  build_image "dts-admin-webapp" "$IMAGE_DTS_ADMIN_WEBAPP" "${REPO_ROOT}/builds/dts-admin-webapp/Dockerfile" "$LEGACY_DIST" \
    --build-arg PNPM_VERSION="${PNPM_VERSION}" \
    --build-arg WEBAPP_BUILD_CMD="build"
  build_image "dts-platform-webapp" "$IMAGE_DTS_PLATFORM_WEBAPP" "${REPO_ROOT}/builds/dts-platform-webapp/Dockerfile" "$LEGACY_DIST" \
    --build-arg PNPM_VERSION="${PNPM_VERSION}" \
    --build-arg WEBAPP_BUILD_CMD="build"
  build_image "dts-analytics-webapp" "$IMAGE_DTS_ANALYTICS_WEBAPP_LEGACY" "${REPO_ROOT}/builds/dts-analytics-webapp/Dockerfile" "$LEGACY_DIST"
  build_image "dts-airflow-om" "$IMAGE_DTS_AIRFLOW_OM" "${REPO_ROOT}/source/dts-airflow-om/Dockerfile" "$LEGACY_DIST" \
    --build-arg PIP_INDEX_URL="${PIP_INDEX_URL:-}" \
    --build-arg PIP_TRUSTED_HOST="${PIP_TRUSTED_HOST:-}" \
    --build-arg PIP_DEFAULT_TIMEOUT="${PIP_DEFAULT_TIMEOUT:-60}" \
    --build-arg PIP_RETRIES="${PIP_RETRIES:-10}" \
    --build-arg HTTP_PROXY="${HTTP_PROXY:-}" \
    --build-arg HTTPS_PROXY="${HTTPS_PROXY:-}" \
    --build-arg NO_PROXY="${NO_PROXY:-}"
fi

echo "[buildAll] Done. normal=${NORMAL_DIST} legacy=${LEGACY_DIST}"
