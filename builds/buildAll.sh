#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-both}"

NORMAL_DIST="${REPO_ROOT}/builds/dist"
LEGACY_DIST="${REPO_ROOT}/builds/legacy-dist"
NODE_IMAGE="${NODE_IMAGE:-node:20.17.0-alpine3.20}"
PNPM_VERSION="${PNPM_VERSION:-10.28.0}"
IMGVERSION_FILE="${IMGVERSION_FILE:-${REPO_ROOT}/imgversion.conf}"

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
  IMAGE_DTS_ANALYTICS="${IMAGE_DTS_ANALYTICS:-dts-analytics:local}"
  IMAGE_DTS_COMMON="${IMAGE_DTS_COMMON:-dts-common:local}"
  IMAGE_DTS_ADMIN_WEBAPP="${IMAGE_DTS_ADMIN_WEBAPP:-dts-admin-webapp:local}"
  IMAGE_DTS_PLATFORM_WEBAPP="${IMAGE_DTS_PLATFORM_WEBAPP:-dts-platform-webapp:local}"
  IMAGE_DTS_ANALYTICS_WEBAPP_MODERN="${IMAGE_DTS_ANALYTICS_WEBAPP_MODERN:-dts-analytics-webapp-modern:local}"

  build_image "dts-common" "$IMAGE_DTS_COMMON" "${REPO_ROOT}/builds/dts-common/Dockerfile" "$NORMAL_DIST"
  build_image "dts-admin" "$IMAGE_DTS_ADMIN" "${REPO_ROOT}/builds/dts-admin/Dockerfile" "$NORMAL_DIST" \
    --build-arg ENABLE_MAVEN_BUILD="${ENABLE_MAVEN_BUILD:-true}"
  build_image "dts-platform" "$IMAGE_DTS_PLATFORM" "${REPO_ROOT}/builds/dts-platform/Dockerfile" "$NORMAL_DIST" \
    --build-arg ENABLE_MAVEN_BUILD="${ENABLE_MAVEN_BUILD:-true}"
  build_image "dts-analytics" "$IMAGE_DTS_ANALYTICS" "${REPO_ROOT}/builds/dts-analytics/Dockerfile" "$NORMAL_DIST"
  build_image "dts-admin-webapp" "$IMAGE_DTS_ADMIN_WEBAPP" "${REPO_ROOT}/builds/dts-admin-webapp/Dockerfile" "$NORMAL_DIST" \
    --build-arg PNPM_VERSION="${PNPM_VERSION}" \
    --build-arg WEBAPP_BUILD_CMD="build:modern"
  build_image "dts-platform-webapp" "$IMAGE_DTS_PLATFORM_WEBAPP" "${REPO_ROOT}/builds/dts-platform-webapp/Dockerfile" "$NORMAL_DIST" \
    --build-arg PNPM_VERSION="${PNPM_VERSION}" \
    --build-arg WEBAPP_BUILD_CMD="build:modern"
  build_image "dts-analytics-webapp-modern" "$IMAGE_DTS_ANALYTICS_WEBAPP_MODERN" "${REPO_ROOT}/builds/dts-analytics-webapp/modern/Dockerfile" "$NORMAL_DIST" \
    --build-arg PNPM_VERSION="${PNPM_VERSION}"
fi

if [[ "$MODE" == "legacy" || "$MODE" == "both" ]]; then
  load_image_versions
  IMAGE_DTS_ADMIN="${IMAGE_DTS_ADMIN:-dts-admin:local}"
  IMAGE_DTS_PLATFORM="${IMAGE_DTS_PLATFORM:-dts-platform:local}"
  IMAGE_DTS_ANALYTICS="${IMAGE_DTS_ANALYTICS:-dts-analytics:local}"
  IMAGE_DTS_COMMON="${IMAGE_DTS_COMMON:-dts-common:local}"
  IMAGE_DTS_ADMIN_WEBAPP="${IMAGE_DTS_ADMIN_WEBAPP:-dts-admin-webapp:local}"
  IMAGE_DTS_PLATFORM_WEBAPP="${IMAGE_DTS_PLATFORM_WEBAPP:-dts-platform-webapp:local}"
  IMAGE_DTS_ANALYTICS_WEBAPP_LEGACY="${IMAGE_DTS_ANALYTICS_WEBAPP_LEGACY:-dts-analytics-webapp:local}"

  build_image "dts-common" "$IMAGE_DTS_COMMON" "${REPO_ROOT}/builds/dts-common/Dockerfile" "$LEGACY_DIST"
  build_image "dts-admin" "$IMAGE_DTS_ADMIN" "${REPO_ROOT}/builds/dts-admin/Dockerfile" "$LEGACY_DIST" \
    --build-arg ENABLE_MAVEN_BUILD="${ENABLE_MAVEN_BUILD:-true}"
  build_image "dts-platform" "$IMAGE_DTS_PLATFORM" "${REPO_ROOT}/builds/dts-platform/Dockerfile" "$LEGACY_DIST" \
    --build-arg ENABLE_MAVEN_BUILD="${ENABLE_MAVEN_BUILD:-true}"
  build_image "dts-analytics" "$IMAGE_DTS_ANALYTICS" "${REPO_ROOT}/builds/dts-analytics/Dockerfile" "$LEGACY_DIST"
  build_image "dts-admin-webapp" "$IMAGE_DTS_ADMIN_WEBAPP" "${REPO_ROOT}/builds/dts-admin-webapp/Dockerfile" "$LEGACY_DIST" \
    --build-arg PNPM_VERSION="${PNPM_VERSION}" \
    --build-arg WEBAPP_BUILD_CMD="build"
  build_image "dts-platform-webapp" "$IMAGE_DTS_PLATFORM_WEBAPP" "${REPO_ROOT}/builds/dts-platform-webapp/Dockerfile" "$LEGACY_DIST" \
    --build-arg PNPM_VERSION="${PNPM_VERSION}" \
    --build-arg WEBAPP_BUILD_CMD="build"
  build_image "dts-analytics-webapp" "$IMAGE_DTS_ANALYTICS_WEBAPP_LEGACY" "${REPO_ROOT}/builds/dts-analytics-webapp/Dockerfile" "$LEGACY_DIST"
fi

echo "[buildAll] Done. normal=${NORMAL_DIST} legacy=${LEGACY_DIST}"
