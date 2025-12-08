#!/usr/bin/env sh
set -eu

# Generate/append runtime-config.js with optional runtime flags.
# Supports:
#   - KOAL_PKI_ENDPOINTS: comma-separated list for Koal local agent endpoints
#   - WEBAPP_PASSWORD_LOGIN_ENABLED: flag to enable password login UI
#   - VITE_HIDE_PASSWORD_LOGIN: flag to hide password login UI
#   - PKI_DEBUG: enable verbose PKI logs in browser (true/false)
RUNTIME_JS="/usr/share/nginx/html/runtime-config.js"
# Initialize file to ensure it's present (safe if empty)
printf '%s\n' '(function(w){w.__RUNTIME_CONFIG__=w.__RUNTIME_CONFIG__||{};})(window);' > "$RUNTIME_JS"
# Ensure runtime-config.js is world-readable (served by nginx worker)
chmod 0644 "$RUNTIME_JS" 2>/dev/null || true

if [ -n "${KOAL_PKI_ENDPOINTS:-}" ]; then
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

if [ -n "${PKI_DEBUG:-}" ]; then
  val=$(printf '%s' "$PKI_DEBUG" | tr '[:upper:]' '[:lower:]')
  printf '%s\n' "(function(w){w.__RUNTIME_CONFIG__=w.__RUNTIME_CONFIG__||{};w.__RUNTIME_CONFIG__.pkiDebug='${val}';})(window);" >> "$RUNTIME_JS"
  echo "[entrypoint] runtime-config.js: pkiDebug=${PKI_DEBUG}"
fi

# Fix permissions for vendor assets so nginx workers can read them (avoid 403 -> HTML)
if [ -d "/usr/share/nginx/html/vendor" ]; then
  chmod -R a+rX "/usr/share/nginx/html/vendor" 2>/dev/null || true
fi
# Ensure the full web root is world-readable (nginx worker is non-root)
chmod -R a+rX "/usr/share/nginx/html" 2>/dev/null || true

# Optional: explicit Koal vendor base for admin webapp (e.g., '/vendor/koal' or full https URL)
if [ -n "${KOAL_VENDOR_BASE:-}" ]; then
  printf '%s\n' "(function(w){w.__RUNTIME_CONFIG__=w.__RUNTIME_CONFIG__||{};w.__RUNTIME_CONFIG__.koalVendorBase='${KOAL_VENDOR_BASE}';})(window);" >> "$RUNTIME_JS"
  echo "[entrypoint] runtime-config.js: koalVendorBase=${KOAL_VENDOR_BASE}"
fi

exec nginx -g 'daemon off;'
