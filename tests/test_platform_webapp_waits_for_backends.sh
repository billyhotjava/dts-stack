#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

FAKE_BIN="${TMP_DIR}/bin"
NGINX_ROOT="${TMP_DIR}/nginx"
HTML_ROOT="${TMP_DIR}/html"
LOG_FILE="${TMP_DIR}/events.log"
PROBE_FILE="${TMP_DIR}/probe-count"
mkdir -p "${FAKE_BIN}" "${NGINX_ROOT}" "${HTML_ROOT}" "${NGINX_ROOT}/http.d" "${HTML_ROOT}/vendor"

cat > "${NGINX_ROOT}/http.d/default.conf.template" <<'EOF_TEMPLATE'
ACTIVE-CONFIG
proxy_pass ${UPSTREAM};
admin-upstream ${ADMIN_UPSTREAM};
admin ${ADMIN_UPSTREAM_API};
analytics ${ANALYTICS_API_UPSTREAM};
EOF_TEMPLATE

cat > "${FAKE_BIN}/envsubst" <<'EOF_ENVSUBST'
#!/usr/bin/env sh
cat
EOF_ENVSUBST
chmod +x "${FAKE_BIN}/envsubst"

cat > "${FAKE_BIN}/wget" <<'EOF_WGET'
#!/usr/bin/env sh
count=0
if [ -f "${FAKE_PROBE_COUNT_FILE}" ]; then
  count="$(cat "${FAKE_PROBE_COUNT_FILE}")"
fi
count=$((count + 1))
printf '%s' "${count}" > "${FAKE_PROBE_COUNT_FILE}"
if [ "${count}" -lt 4 ]; then
  exit 1
fi
exit 0
EOF_WGET
chmod +x "${FAKE_BIN}/wget"

cat > "${FAKE_BIN}/sleep" <<'EOF_SLEEP'
#!/usr/bin/env sh
exit 0
EOF_SLEEP
chmod +x "${FAKE_BIN}/sleep"

cat > "${FAKE_BIN}/nginx" <<'EOF_NGINX'
#!/usr/bin/env sh
if [ "${1:-}" = "-s" ] && [ "${2:-}" = "reload" ]; then
  printf 'reload\n' >> "${FAKE_NGINX_LOG_FILE}"
  cp "${FAKE_NGINX_CONFIG_PATH}" "${FAKE_NGINX_RELOAD_SNAPSHOT}"
  exit 0
fi
printf 'start\n' >> "${FAKE_NGINX_LOG_FILE}"
cp "${FAKE_NGINX_CONFIG_PATH}" "${FAKE_NGINX_START_SNAPSHOT}"
sleep 1
exit 0
EOF_NGINX
chmod +x "${FAKE_BIN}/nginx"

OUTPUT_FILE="${TMP_DIR}/entrypoint.log"
set +e
PATH="${FAKE_BIN}:${PATH}" \
  FAKE_PROBE_COUNT_FILE="${PROBE_FILE}" \
  FAKE_NGINX_LOG_FILE="${LOG_FILE}" \
  FAKE_NGINX_CONFIG_PATH="${NGINX_ROOT}/http.d/default.conf" \
  FAKE_NGINX_START_SNAPSHOT="${TMP_DIR}/nginx-start.conf" \
  FAKE_NGINX_RELOAD_SNAPSHOT="${TMP_DIR}/nginx-reload.conf" \
  NGINX_TEMPLATE_PATH="${NGINX_ROOT}/http.d/default.conf.template" \
  NGINX_CONFIG_PATH="${NGINX_ROOT}/http.d/default.conf" \
  WEBAPP_HTML_ROOT="${HTML_ROOT}" \
  RUNTIME_CONFIG_PATH="${HTML_ROOT}/runtime-config.js" \
  API_PROXY_TARGET="http://dts-platform:8081" \
  ADMIN_API_PROXY_TARGET="http://dts-admin:8081" \
  ANALYTICS_API_PROXY_TARGET="http://dts-analytics:3000" \
  WEBAPP_BACKEND_WAIT_INTERVAL_SECONDS=0 \
  sh "${REPO_ROOT}/builds/dts-platform-webapp/docker-entrypoint.sh" >"${OUTPUT_FILE}" 2>&1
STATUS=$?
set -e

if [ "${STATUS}" -ne 0 ]; then
  echo "expected platform webapp entrypoint to stay alive and succeed after backend probes recover" >&2
  cat "${OUTPUT_FILE}" >&2
  exit 1
fi

if ! grep -Fqx 'start' "${LOG_FILE}"; then
  echo "expected nginx to start with placeholder config first" >&2
  cat "${LOG_FILE}" >&2
  exit 1
fi

if ! grep -Fqx 'reload' "${LOG_FILE}"; then
  echo "expected nginx reload after backends became reachable" >&2
  cat "${LOG_FILE}" >&2
  exit 1
fi

if ! grep -Fq 'PLACEHOLDER-CONFIG' "${TMP_DIR}/nginx-start.conf"; then
  echo "expected initial nginx config snapshot to be placeholder mode" >&2
  cat "${TMP_DIR}/nginx-start.conf" >&2
  exit 1
fi

if ! grep -Fq 'ACTIVE-CONFIG' "${TMP_DIR}/nginx-reload.conf"; then
  echo "expected reloaded nginx config snapshot to switch to active mode" >&2
  cat "${TMP_DIR}/nginx-reload.conf" >&2
  exit 1
fi
