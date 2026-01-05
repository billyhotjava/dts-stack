#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../../.." && pwd)"

IMAGE_TAG="${IMAGE_TAG:-dts-analytics:1.0.0}"

if ! command -v docker >/dev/null 2>&1; then
  echo "[dts-analytics] ERROR: docker not found" >&2
  exit 1
fi

if [[ ! -f "${REPO_ROOT}/source/builds/dts-analytics.jar" ]]; then
  echo "[dts-analytics] ERROR: source/builds/dts-analytics.jar not found; run scripts/build-offline-jar.sh first" >&2
  exit 1
fi

docker build --no-cache -t "${IMAGE_TAG}" -f "${REPO_ROOT}/source/dts-analytics/Dockerfile.offline" "${REPO_ROOT}/source"
echo "[dts-analytics] built ${IMAGE_TAG}"

