#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

IMAGE_TAG="${IMAGE_TAG:-dts-airflow-om:2.9.3-om}"
PIP_INDEX_URL="${PIP_INDEX_URL:-}"
PIP_TRUSTED_HOST="${PIP_TRUSTED_HOST:-}"
PIP_DEFAULT_TIMEOUT="${PIP_DEFAULT_TIMEOUT:-60}"
PIP_RETRIES="${PIP_RETRIES:-10}"
HTTP_PROXY="${HTTP_PROXY:-}"
HTTPS_PROXY="${HTTPS_PROXY:-}"
NO_PROXY="${NO_PROXY:-}"

echo "[airflow-build] Building ${IMAGE_TAG} ..."

build_args=()
docker_args=()
proxy_host_rewrite() {
  local proxy="$1"
  if [[ -z "${proxy}" ]]; then
    echo ""
    return
  fi
  if [[ "${proxy}" == *"127.0.0.1"* || "${proxy}" == *"localhost"* ]]; then
    echo "${proxy//127.0.0.1/host.docker.internal}"
  else
    echo "${proxy}"
  fi
}
if [[ -n "${PIP_INDEX_URL}" ]]; then
  build_args+=(--build-arg "PIP_INDEX_URL=${PIP_INDEX_URL}")
fi
if [[ -n "${PIP_TRUSTED_HOST}" ]]; then
  build_args+=(--build-arg "PIP_TRUSTED_HOST=${PIP_TRUSTED_HOST}")
fi
if [[ -n "${PIP_DEFAULT_TIMEOUT}" ]]; then
  build_args+=(--build-arg "PIP_DEFAULT_TIMEOUT=${PIP_DEFAULT_TIMEOUT}")
fi
if [[ -n "${PIP_RETRIES}" ]]; then
  build_args+=(--build-arg "PIP_RETRIES=${PIP_RETRIES}")
fi
if [[ -n "${HTTP_PROXY}" ]]; then
  HTTP_PROXY="$(proxy_host_rewrite "${HTTP_PROXY}")"
  build_args+=(--build-arg "HTTP_PROXY=${HTTP_PROXY}")
fi
if [[ -n "${HTTPS_PROXY}" ]]; then
  HTTPS_PROXY="$(proxy_host_rewrite "${HTTPS_PROXY}")"
  build_args+=(--build-arg "HTTPS_PROXY=${HTTPS_PROXY}")
fi
if [[ -n "${NO_PROXY}" ]]; then
  build_args+=(--build-arg "NO_PROXY=${NO_PROXY}")
fi
if [[ "${HTTP_PROXY}" == *"host.docker.internal"* || "${HTTPS_PROXY}" == *"host.docker.internal"* ]]; then
  docker_args+=(--add-host=host.docker.internal:host-gateway)
fi

docker build \
  "${docker_args[@]}" \
  -t "${IMAGE_TAG}" \
  -f "${ROOT_DIR}/source/dts-airflow-om/Dockerfile" \
  "${ROOT_DIR}" \
  "${build_args[@]}"

echo "[airflow-build] Done."
