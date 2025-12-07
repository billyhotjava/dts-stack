#!/usr/bin/env bash
set -euo pipefail

# Multi-arch builder for Superset/Metabase with overlay application.
# Usage:
#   tools/bi-build.sh superset <tag> [--push] [--platform linux/amd64,linux/arm64]
#   tools/bi-build.sh metabase <tag> [--push] [--platform linux/amd64,linux/arm64]
#
# The script stages upstream source into a temp dir, applies overlay patches/files,
# chooses a Dockerfile (overlay/docker/Dockerfile if present, otherwise upstream),
# and builds via docker buildx. No changes are made to upstream working trees.

usage() {
  echo "Usage: $0 {superset|metabase} <image_tag> [--push] [--platform linux/amd64,linux/arm64] [--output <path>] [--version <v>]"
  echo "Default output (when not --push): builds/<service>-multi.oci"
}

SERVICE="${1:-}"
TAG="${2:-}"
shift $(( $# > 0 ? 1 : 0 ))
shift $(( $# > 0 ? 1 : 0 ))

PUSH=false
PLATFORMS="linux/amd64,linux/arm64"
OUTPUT=""
VERSION_OVERRIDE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --push) PUSH=true; shift ;;
    --platform) PLATFORMS="$2"; shift 2 ;;
    --output) OUTPUT="$2"; shift 2 ;;
    --version) VERSION_OVERRIDE="$2"; shift 2 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 1 ;;
  esac
done

if [[ -z "${SERVICE}" || -z "${TAG}" ]]; then
  usage; exit 1
fi
if [[ "${SERVICE}" != "superset" && "${SERVICE}" != "metabase" ]]; then
  echo "SERVICE must be superset or metabase" >&2; exit 1
fi

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
mkdir -p "${ROOT_DIR}/builds"
case "${SERVICE}" in
  superset)
    UPSTREAM="${ROOT_DIR}/source/dts-bi-dashboard"
    OVERLAY="${ROOT_DIR}/source/dts-bi-dashboard-overlay"
    DEFAULT_DOCKERFILE="Dockerfile"
    ;;
  metabase)
    UPSTREAM="${ROOT_DIR}/source/dts-bi-analytics"
    OVERLAY="${ROOT_DIR}/source/dts-bi-analytics-overlay"
    DEFAULT_DOCKERFILE="Dockerfile"
    ;;
esac

if [[ ! -d "${UPSTREAM}" ]]; then
  echo "Upstream directory missing: ${UPSTREAM}" >&2
  exit 1
fi

workdir="$(mktemp -d)"
cleanup() { rm -rf "${workdir}"; }
trap cleanup EXIT

echo "[bi-build] Staging ${SERVICE} sources into ${workdir} ..."
rsync -a --delete --exclude '.git' "${UPSTREAM}/" "${workdir}/"

apply_overlay() {
  local src="$1"
  local dest="$2"
  [[ -d "${src}" ]] || return 0

  # 1) patches
  if compgen -G "${src}/patches/*.patch" >/dev/null; then
    for p in "${src}"/patches/*.patch; do
      echo "[bi-build] Applying patch ${p##*/}"
      git -C "${dest}" apply "${p}"
    done
  fi

  # 2) plugins/config/docker overlays (rsync into tree)
  local subdir
  for subdir in plugins config docker; do
    if [[ -d "${src}/${subdir}" ]]; then
      echo "[bi-build] Overlaying ${subdir}/"
      rsync -a "${src}/${subdir}/" "${dest}/${subdir}/"
    fi
  done
}

apply_overlay "${OVERLAY}" "${workdir}"

# Choose Dockerfile: overlay/docker/Dockerfile > upstream default
DOCKERFILE=""
if [[ -f "${OVERLAY}/docker/Dockerfile" ]]; then
  DOCKERFILE="${workdir}/docker/Dockerfile"
elif [[ -f "${workdir}/${DEFAULT_DOCKERFILE}" ]]; then
  DOCKERFILE="${workdir}/${DEFAULT_DOCKERFILE}"
else
  echo "Dockerfile not found (overlay or upstream). Checked ${DEFAULT_DOCKERFILE}" >&2
  exit 1
fi

echo "[bi-build] Building ${TAG} with Dockerfile ${DOCKERFILE} (platforms: ${PLATFORMS})"
build_args=(
  docker buildx build
  --platform "${PLATFORMS}"
  -t "${TAG}"
  -f "${DOCKERFILE}"
  "${workdir}"
)
if [[ "${PUSH}" == "true" ]]; then
  build_args+=(--push)
else
  if [[ -z "${OUTPUT}" ]]; then
    OUTPUT="${ROOT_DIR}/builds/${SERVICE}-multi.oci"
  fi
  build_args+=(--output "${OUTPUT}")
fi

# Derive VERSION for builds that require it (Metabase needs :version)
VERSION="${VERSION_OVERRIDE:-}"
if [[ -z "${VERSION}" ]]; then
  if [[ "${TAG}" == *:* ]]; then
    VERSION="${TAG##*:}"
  else
    VERSION="${TAG}"
  fi
fi

# Only Metabase consumes VERSION; harmless to skip for Superset.
if [[ "${SERVICE}" == "metabase" ]]; then
  echo "[bi-build] Using Metabase version arg: ${VERSION}"
  build_args+=(--build-arg "VERSION=${VERSION}")
fi

"${build_args[@]}"

echo "[bi-build] Done."
