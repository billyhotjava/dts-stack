#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
WEBAPP_DIR="${ROOT_DIR}/source/dts-analytics-webapp"

MB_VERSION="${MB_VERSION:-0.45.4.3}"
MB_URL="${MB_URL:-https://downloads.metabase.com/v${MB_VERSION}/metabase.jar}"
MB_JAR="${MB_JAR:-${WEBAPP_DIR}/metabase.jar}"

OUT_DIR="${WEBAPP_DIR}/legacy"
TMP_DIR="${WEBAPP_DIR}/.tmp-extract"
FALLBACK_EXTRACTED_DIR="${ROOT_DIR}/source/dts-analytics/target/metabase-ui/unpacked/frontend_client"

mkdir -p "${WEBAPP_DIR}"

if [[ ! -f "${MB_JAR}" ]]; then
  echo "[extract-metabase-ui] Downloading ${MB_URL} -> ${MB_JAR}"
  if ! curl -fL --retry 3 --retry-delay 1 -o "${MB_JAR}" "${MB_URL}"; then
    echo "[extract-metabase-ui] ERROR: failed to download ${MB_URL}" >&2
    if [[ -d "${FALLBACK_EXTRACTED_DIR}" ]]; then
      echo "[extract-metabase-ui] Falling back to existing extracted assets: ${FALLBACK_EXTRACTED_DIR}"
      rm -rf "${OUT_DIR}"
      mkdir -p "${OUT_DIR}"
      cp -R "${FALLBACK_EXTRACTED_DIR}" "${OUT_DIR}/frontend_client"
      echo "[extract-metabase-ui] Done: ${OUT_DIR}/frontend_client"
      exit 0
    fi
    echo "[extract-metabase-ui] Hint: download metabase.jar manually and place it at ${MB_JAR}" >&2
    exit 1
  fi
else
  echo "[extract-metabase-ui] Using existing jar: ${MB_JAR}"
fi

rm -rf "${TMP_DIR}"
mkdir -p "${TMP_DIR}"

echo "[extract-metabase-ui] Extracting frontend_client/** ..."
unzip -q "${MB_JAR}" "frontend_client/**" -d "${TMP_DIR}"

rm -rf "${OUT_DIR}"
mkdir -p "${OUT_DIR}"

mv "${TMP_DIR}/frontend_client" "${OUT_DIR}/frontend_client"
rm -rf "${TMP_DIR}"

echo "[extract-metabase-ui] Done: ${OUT_DIR}/frontend_client"
