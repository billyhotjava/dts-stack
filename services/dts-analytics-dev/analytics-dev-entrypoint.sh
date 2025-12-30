#!/usr/bin/env bash
set -euo pipefail

rewrite_localhost_proxy="${DTS_DEV_PROXY_REWRITE_LOCALHOST:-true}"
if [[ "${rewrite_localhost_proxy}" == "true" ]]; then
  for k in http_proxy https_proxy HTTP_PROXY HTTPS_PROXY; do
    v="${!k:-}"
    if [[ -n "${v}" && "${v}" == *"127.0.0.1"* ]]; then
      export "${k}=${v//127.0.0.1/host.docker.internal}"
    fi
  done

  for k in no_proxy NO_PROXY; do
    v="${!k:-}"
    if [[ -n "${v}" && "${v}" != *"host.docker.internal"* ]]; then
      export "${k}=${v},host.docker.internal"
    fi
  done
fi

hp="${http_proxy:-${HTTP_PROXY:-}}"
sp="${https_proxy:-${HTTPS_PROXY:-}}"
np="${no_proxy:-${NO_PROXY:-}}"
echo "[analytics-dev] http_proxy=${hp} https_proxy=${sp} no_proxy=${np}"
echo "[analytics-dev] yarn=$(yarn --version) node=$(node --version) java=$(java -version 2>&1 | head -n 1)"

exec yarn dev

