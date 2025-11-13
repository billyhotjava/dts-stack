#!/usr/bin/env sh
set -eu

# Generate runtime-config.js if KOAL_PKI_ENDPOINTS is provided.
# This allows setting PKI endpoints in one place (compose/.env) for both webapps.
RUNTIME_JS="/usr/share/nginx/html/runtime-config.js"
if [ -n "${KOAL_PKI_ENDPOINTS:-}" ]; then
  json=$(printf '%s' "$KOAL_PKI_ENDPOINTS" | awk -F',' 'BEGIN{printf("[");first=1} {for(i=1;i<=NF;i++){gsub(/^ +| +$/, "", $i); if(length($i)){ if(!first) printf(","); printf("\"%s\"", $i); first=0}}} END{printf("]")}')
  cat > "$RUNTIME_JS" <<JS
(function(w){w.__RUNTIME_CONFIG__=w.__RUNTIME_CONFIG__||{};w.__RUNTIME_CONFIG__.koalPkiEndpoints=${json};})(window);
JS
  echo "[entrypoint] Wrote runtime-config.js with koalPkiEndpoints=${KOAL_PKI_ENDPOINTS}"
fi

exec nginx -g 'daemon off;'
