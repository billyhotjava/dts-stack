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

# Render runtime-config.js for front-end to read unified PKI endpoints at runtime
# Accept KOAL_PKI_ENDPOINTS env (comma-separated). Leave empty to rely on built-in defaults.
RUNTIME_JS="/usr/share/nginx/html/runtime-config.js"
if [ -n "${KOAL_PKI_ENDPOINTS:-}" ]; then
  # Transform comma-separated list to JSON array (POSIX/BusyBox compatible)
  json=$(printf '%s' "$KOAL_PKI_ENDPOINTS" | awk -F',' 'BEGIN{printf("[");first=1} {for(i=1;i<=NF;i++){gsub(/^ +| +$/, "", $i); if(length($i)){ if(!first) printf(","); printf("\"%s\"", $i); first=0}}} END{printf("]")}')
  cat > "$RUNTIME_JS" <<JS
(function(w){w.__RUNTIME_CONFIG__=w.__RUNTIME_CONFIG__||{};w.__RUNTIME_CONFIG__.koalPkiEndpoints=${json};})(window);
JS
  echo "[entrypoint] Wrote runtime-config.js with koalPkiEndpoints=${KOAL_PKI_ENDPOINTS}"
fi

exec nginx -g 'daemon off;'
