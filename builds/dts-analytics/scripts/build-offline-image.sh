#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../../.." && pwd)"

IMAGE_TAG="${IMAGE_TAG:-dts-analytics:1.0.0}"

if ! command -v docker >/dev/null 2>&1; then
  echo "[dts-analytics] ERROR: docker not found" >&2
  exit 1
fi

if [[ ! -f "${REPO_ROOT}/builds/dts-analytics/dts-analytics.jar" ]]; then
  echo "[dts-analytics] ERROR: builds/dts-analytics/dts-analytics.jar not found; run scripts/build-offline-jar.sh first" >&2
  exit 1
fi

if command -v jar >/dev/null 2>&1; then
  if ! jar tf "${REPO_ROOT}/builds/dts-analytics/dts-analytics.jar" | grep -Fq "com/yuzhi/dts/analytics/DtsAnalyticsApp.class"; then
    echo "[dts-analytics] ERROR: dts-analytics.jar missing analytics main class" >&2
    exit 1
  fi
  if jar tf "${REPO_ROOT}/builds/dts-analytics/dts-analytics.jar" | grep -Fq "com/yuzhi/dts/ingestion/DtsIngestionApp.class"; then
    echo "[dts-analytics] ERROR: dts-analytics.jar contains ingestion main class (artifact mismatch)" >&2
    exit 1
  fi
elif command -v unzip >/dev/null 2>&1; then
  if ! unzip -Z1 "${REPO_ROOT}/builds/dts-analytics/dts-analytics.jar" | grep -Fq "com/yuzhi/dts/analytics/DtsAnalyticsApp.class"; then
    echo "[dts-analytics] ERROR: dts-analytics.jar missing analytics main class" >&2
    exit 1
  fi
  if unzip -Z1 "${REPO_ROOT}/builds/dts-analytics/dts-analytics.jar" | grep -Fq "com/yuzhi/dts/ingestion/DtsIngestionApp.class"; then
    echo "[dts-analytics] ERROR: dts-analytics.jar contains ingestion main class (artifact mismatch)" >&2
    exit 1
  fi
else
  if ! grep -aFq "com/yuzhi/dts/analytics/DtsAnalyticsApp.class" "${REPO_ROOT}/builds/dts-analytics/dts-analytics.jar"; then
    echo "[dts-analytics] ERROR: dts-analytics.jar missing analytics main class (grep fallback)" >&2
    exit 1
  fi
  if grep -aFq "com/yuzhi/dts/ingestion/DtsIngestionApp.class" "${REPO_ROOT}/builds/dts-analytics/dts-analytics.jar"; then
    echo "[dts-analytics] ERROR: dts-analytics.jar contains ingestion main class (artifact mismatch; grep fallback)" >&2
    exit 1
  fi
fi

docker build --no-cache -t "${IMAGE_TAG}" -f "${REPO_ROOT}/builds/dts-analytics/Dockerfile.offline" "${REPO_ROOT}"
echo "[dts-analytics] built ${IMAGE_TAG}"
