#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-both}"

NORMAL_DIST="${REPO_ROOT}/builds/dist"
LEGACY_DIST="${REPO_ROOT}/builds/legacy-dist"
NODE_IMAGE="${NODE_IMAGE:-node:20.17.0-alpine3.20}"
PNPM_VERSION="${PNPM_VERSION:-10.28.0}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[buildAll] ERROR: '$1' not found in PATH" >&2
    exit 1
  fi
}

copy_dist() {
  local src="$1"
  local dest="$2"
  rm -rf "$dest"
  mkdir -p "$(dirname "$dest")"
  cp -a "$src" "$dest"
}

build_webapp() {
  local name="$1"
  local dir="$2"
  local build_cmd="$3"
  local out_dir="$4"

  echo "[buildAll] Building ${name} (${build_cmd})"
  docker run --rm -it \
    -v "${REPO_ROOT}:/workspace" \
    -w "/workspace/${dir#${REPO_ROOT}/}" \
    "${NODE_IMAGE}" \
    sh -lc "npm install -g pnpm@${PNPM_VERSION} >/dev/null 2>&1; pnpm install --frozen-lockfile; pnpm ${build_cmd}"
  copy_dist "${dir}/dist" "${out_dir}/${name}"
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
  mkdir -p "$NORMAL_DIST"
  build_webapp "dts-admin-webapp" "${REPO_ROOT}/source/dts-admin-webapp" "build:modern" "$NORMAL_DIST"
  build_webapp "dts-platform-webapp" "${REPO_ROOT}/source/dts-platform-webapp" "build:modern" "$NORMAL_DIST"
  build_webapp "dts-analytics-webapp-modern" "${REPO_ROOT}/source/dts-analytics-webapp/modern" "build" "$NORMAL_DIST"
fi

if [[ "$MODE" == "legacy" || "$MODE" == "both" ]]; then
  mkdir -p "$LEGACY_DIST"
  build_webapp "dts-admin-webapp" "${REPO_ROOT}/source/dts-admin-webapp" "build" "$LEGACY_DIST"
  build_webapp "dts-platform-webapp" "${REPO_ROOT}/source/dts-platform-webapp" "build" "$LEGACY_DIST"
fi

echo "[buildAll] Done. normal=${NORMAL_DIST} legacy=${LEGACY_DIST}"
