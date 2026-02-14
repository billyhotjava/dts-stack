#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLATFORM_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
V221_DIR="$(cd "${PLATFORM_DIR}/.." && pwd)"
REPO_DIR="$(cd "${PLATFORM_DIR}/../../.." && pwd)"
OUT_DIR="${PLATFORM_DIR}"
TAG=""

usage() {
  cat <<USAGE
Usage: $(basename "$0") [options]

Options:
  --tag <name>      Optional label in package name
  --out-dir <path>  Output directory (default: platform)
  -h, --help        Show help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --tag)
      TAG="${2:-}"
      shift 2
      ;;
    --out-dir)
      OUT_DIR="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

mkdir -p "${OUT_DIR}"

RUN_AT="$(date -u +"%Y%m%dT%H%M%SZ")"
SUFFIX="${RUN_AT}"
if [[ -n "${TAG}" ]]; then
  SUFFIX="${SUFFIX}-${TAG}"
fi

PKG_NAME="deliverable-${SUFFIX}.tar.gz"
PKG_PATH="${OUT_DIR}/${PKG_NAME}"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

STAGE_DIR="${TMP_DIR}/platform"
mkdir -p "${STAGE_DIR}"
mkdir -p "${STAGE_DIR}/raw"
mkdir -p "${STAGE_DIR}/tasks"
mkdir -p "${STAGE_DIR}/scripts"
mkdir -p "${STAGE_DIR}/v221"

copy_if_exists() {
  local src="$1"
  local dst="$2"
  if [[ -e "${src}" ]]; then
    cp -a "${src}" "${dst}"
  fi
}

copy_if_exists "${PLATFORM_DIR}/README.md" "${STAGE_DIR}/"
copy_if_exists "${PLATFORM_DIR}/first-run-report.md" "${STAGE_DIR}/"
copy_if_exists "${PLATFORM_DIR}/stability-24h.md" "${STAGE_DIR}/"
copy_if_exists "${PLATFORM_DIR}/addax-airbyte-semantic-compare.md" "${STAGE_DIR}/"
copy_if_exists "${PLATFORM_DIR}/isolation-lineage-regression.md" "${STAGE_DIR}/"
copy_if_exists "${PLATFORM_DIR}/k8s-airbyte-readiness.md" "${STAGE_DIR}/"
copy_if_exists "${PLATFORM_DIR}/arm-kylin-hardening.md" "${STAGE_DIR}/"

copy_if_exists "${V221_DIR}/README.md" "${STAGE_DIR}/v221/"
copy_if_exists "${V221_DIR}/platform-analytics-v2.2.1-task-list.md" "${STAGE_DIR}/v221/"
copy_if_exists "${V221_DIR}/p0-regression-checklist.md" "${STAGE_DIR}/v221/"
copy_if_exists "${V221_DIR}/p0-regression-matrix.md" "${STAGE_DIR}/v221/"

if [[ -d "${PLATFORM_DIR}/raw" ]]; then
  cp -a "${PLATFORM_DIR}/raw/." "${STAGE_DIR}/raw/"
fi
if [[ -d "${PLATFORM_DIR}/tasks" ]]; then
  cp -a "${PLATFORM_DIR}/tasks/." "${STAGE_DIR}/tasks/"
fi
if [[ -d "${PLATFORM_DIR}/scripts" ]]; then
  cp -a "${PLATFORM_DIR}/scripts/." "${STAGE_DIR}/scripts/"
fi

BRANCH="$(git -C "${REPO_DIR}" branch --show-current 2>/dev/null || true)"
COMMIT="$(git -C "${REPO_DIR}" rev-parse --short HEAD 2>/dev/null || true)"
STATUS_SUMMARY="$(git -C "${REPO_DIR}" status --short 2>/dev/null | wc -l | tr -d ' ')"

cat > "${STAGE_DIR}/DELIVERABLE_MANIFEST.txt" <<MANIFEST
name=${PKG_NAME}
generated_utc=${RUN_AT}
branch=${BRANCH}
commit=${COMMIT}
working_tree_changes=${STATUS_SUMMARY}
source_dir=${PLATFORM_DIR}
MANIFEST

(
  cd "${TMP_DIR}"
  tar -czf "${PKG_PATH}" platform
)

echo "package generated"
echo "- path: ${PKG_PATH}"
echo "- manifest: platform/DELIVERABLE_MANIFEST.txt"
