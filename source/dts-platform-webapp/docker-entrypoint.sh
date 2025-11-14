#!/usr/bin/env sh
set -eu

# Compute upstream from env with sensible default for local dev
# Examples:
#   -e API_PROXY_TARGET=http://host.docker.internal:8081  (host-run dev backend)
#   -e API_PROXY_TARGET=http://dts-platform-gateway:8080   (compose service)
UPSTREAM="${API_PROXY_TARGET:-http://host.docker.internal:8081}"
export UPSTREAM

TEMPLATE="/etc/nginx/http.d/default.conf.template"
TARGET="/etc/nginx/http.d/default.conf"

if [ -f "$TEMPLATE" ]; then
  echo "[entrypoint] Rendering Nginx config with UPSTREAM=$UPSTREAM"
  # shellcheck disable=SC2016
  envsubst '${UPSTREAM}' < "$TEMPLATE" > "$TARGET"
fi

# Render runtime-config.js for front-end to read runtime flags
# Supports:
#   - KOAL_PKI_ENDPOINTS: comma-separated local agent endpoints
#   - WEBAPP_PASSWORD_LOGIN_ENABLED: enable password login UI
#   - VITE_HIDE_PASSWORD_LOGIN: hide password login UI
RUNTIME_JS="/usr/share/nginx/html/runtime-config.js"
# Initialize stub to ensure file exists
printf '%s\n' '(function(w){w.__RUNTIME_CONFIG__=w.__RUNTIME_CONFIG__||{};})(window);' > "$RUNTIME_JS"

if [ -n "${KOAL_PKI_ENDPOINTS:-}" ]; then
  # Transform comma-separated list to JSON array (POSIX/BusyBox compatible)
  json=$(printf '%s' "$KOAL_PKI_ENDPOINTS" | awk -F',' 'BEGIN{printf("[");first=1} {for(i=1;i<=NF;i++){gsub(/^ +| +$/, "", $i); if(length($i)){ if(!first) printf(","); printf("\"%s\"", $i); first=0}}} END{printf("]")}')
  printf '%s\n' "(function(w){w.__RUNTIME_CONFIG__=w.__RUNTIME_CONFIG__||{};w.__RUNTIME_CONFIG__.koalPkiEndpoints=${json};})(window);" >> "$RUNTIME_JS"
  echo "[entrypoint] runtime-config.js: koalPkiEndpoints=${KOAL_PKI_ENDPOINTS}"
fi

if [ -n "${WEBAPP_PASSWORD_LOGIN_ENABLED:-}" ]; then
  val=$(printf '%s' "$WEBAPP_PASSWORD_LOGIN_ENABLED" | tr '[:upper:]' '[:lower:]')
  printf '%s\n' "(function(w){w.__RUNTIME_CONFIG__=w.__RUNTIME_CONFIG__||{};w.__RUNTIME_CONFIG__.enablePasswordLogin='${val}';})(window);" >> "$RUNTIME_JS"
  echo "[entrypoint] runtime-config.js: enablePasswordLogin=${WEBAPP_PASSWORD_LOGIN_ENABLED}"
fi

if [ -n "${VITE_HIDE_PASSWORD_LOGIN:-}" ]; then
  val=$(printf '%s' "$VITE_HIDE_PASSWORD_LOGIN" | tr '[:upper:]' '[:lower:]')
  printf '%s\n' "(function(w){w.__RUNTIME_CONFIG__=w.__RUNTIME_CONFIG__||{};w.__RUNTIME_CONFIG__.hidePasswordLogin='${val}';})(window);" >> "$RUNTIME_JS"
  echo "[entrypoint] runtime-config.js: hidePasswordLogin=${VITE_HIDE_PASSWORD_LOGIN}"
fi

exec nginx -g 'daemon off;'
