#!/usr/bin/env sh
set -eu

# Compute upstream from env with sensible default for local dev
# Examples:
#   -e API_PROXY_TARGET=http://host.docker.internal:8081  (host-run dev backend)
#   -e API_PROXY_TARGET=http://dts-platform-gateway:8080   (compose service)
UPSTREAM="${API_PROXY_TARGET:-http://host.docker.internal:8081}"
export UPSTREAM

# Admin API proxy (mounted under /admin/api on this webapp, maps to /api on dts-admin)
# Examples:
#   -e ADMIN_API_PROXY_TARGET=http://host.docker.internal:8081
#   -e ADMIN_API_PROXY_TARGET=http://dts-admin:8081
ADMIN_BASE="${ADMIN_API_PROXY_TARGET:-http://dts-admin:8081}"
# Normalize trailing slashes and append "/api/" for path rewriting.
ADMIN_UPSTREAM_API="$(printf '%s' "$ADMIN_BASE" | sed 's:/*$::')/api/"
export ADMIN_UPSTREAM_API

ANALYTICS_API_UPSTREAM="${ANALYTICS_API_PROXY_TARGET:-http://dts-analytics:3000}"
export ANALYTICS_API_UPSTREAM

NGINX_TEMPLATE_PATH="${NGINX_TEMPLATE_PATH:-/etc/nginx/http.d/default.conf.template}"
NGINX_CONFIG_PATH="${NGINX_CONFIG_PATH:-/etc/nginx/http.d/default.conf}"
WEBAPP_HTML_ROOT="${WEBAPP_HTML_ROOT:-/usr/share/nginx/html}"
RUNTIME_CONFIG_PATH="${RUNTIME_CONFIG_PATH:-${WEBAPP_HTML_ROOT}/runtime-config.js}"
WEBAPP_BACKEND_WAIT_INTERVAL_SECONDS="${WEBAPP_BACKEND_WAIT_INTERVAL_SECONDS:-3}"
PLATFORM_HEALTHCHECK_URL="${PLATFORM_HEALTHCHECK_URL:-$(printf '%s' "$UPSTREAM" | sed 's:/*$::')/management/health}"
ADMIN_HEALTHCHECK_URL="${ADMIN_HEALTHCHECK_URL:-$(printf '%s' "$ADMIN_BASE" | sed 's:/*$::')/management/health}"
ANALYTICS_HEALTHCHECK_URL="${ANALYTICS_HEALTHCHECK_URL:-$(printf '%s' "$ANALYTICS_API_UPSTREAM" | sed 's:/*$::')/api/health}"

render_active_nginx_config() {
  if [ -f "$NGINX_TEMPLATE_PATH" ]; then
    echo "[entrypoint] Rendering active Nginx config with UPSTREAM=$UPSTREAM ADMIN_UPSTREAM_API=$ADMIN_UPSTREAM_API ANALYTICS_API_UPSTREAM=$ANALYTICS_API_UPSTREAM"
    # shellcheck disable=SC2016
    envsubst '${UPSTREAM} ${ADMIN_UPSTREAM_API} ${ANALYTICS_API_UPSTREAM}' < "$NGINX_TEMPLATE_PATH" > "$NGINX_CONFIG_PATH"
  fi
}

render_placeholder_nginx_config() {
  cat > "$NGINX_CONFIG_PATH" <<EOF_PLACEHOLDER
# PLACEHOLDER-CONFIG
server {
    listen 80;
    server_name _;

    root ${WEBAPP_HTML_ROOT};
    index index.html;

    location = /healthz {
        default_type text/plain;
        return 200 "starting\n";
    }

    location = /runtime-config.js {
        add_header Cache-Control "no-store";
        try_files \$uri =404;
    }

    location / {
        try_files \$uri \$uri/ /index.html;
    }
}
EOF_PLACEHOLDER
}

probe_backend() {
  wget -q -T 2 -O /dev/null "$1" >/dev/null 2>&1
}

wait_for_backends() {
  while :; do
    if probe_backend "$PLATFORM_HEALTHCHECK_URL" \
      && probe_backend "$ADMIN_HEALTHCHECK_URL" \
      && probe_backend "$ANALYTICS_HEALTHCHECK_URL"; then
      echo "[entrypoint] Backend dependencies are reachable; reloading active Nginx config"
      return 0
    fi
    echo "[entrypoint] Waiting for backends: platform=$PLATFORM_HEALTHCHECK_URL admin=$ADMIN_HEALTHCHECK_URL analytics=$ANALYTICS_HEALTHCHECK_URL"
    sleep "$WEBAPP_BACKEND_WAIT_INTERVAL_SECONDS"
  done
}

stop_nginx() {
  if [ "${NGINX_PID:-}" ] && kill -0 "$NGINX_PID" 2>/dev/null; then
    kill "$NGINX_PID" 2>/dev/null || true
    wait "$NGINX_PID" 2>/dev/null || true
  fi
}

trap 'stop_nginx' INT TERM

mkdir -p "$(dirname "$NGINX_CONFIG_PATH")" "$(dirname "$RUNTIME_CONFIG_PATH")" "$WEBAPP_HTML_ROOT"
render_placeholder_nginx_config

# Ensure Koal SDK vendor assets are readable; repair permissions if needed
# Also render runtime-config.js for front-end to read runtime flags
# (Attempts are best-effort and safe to ignore if paths are missing.)
chmod -R a+rX "${WEBAPP_HTML_ROOT}/vendor" 2>/dev/null || true
if [ ! -r "${WEBAPP_HTML_ROOT}/vendor/koal/deviceOperator.js" ] && [ -d "/opt/vendor-koal" ]; then
  echo "[entrypoint] Vendor assets missing/unreadable under ${WEBAPP_HTML_ROOT}/vendor; copying fallback from /opt/vendor-koal"
  cp -a /opt/vendor-koal "${WEBAPP_HTML_ROOT}/vendor" 2>/dev/null || true
  chmod -R a+rX "${WEBAPP_HTML_ROOT}/vendor" 2>/dev/null || true
fi

# Render runtime-config.js for front-end to read runtime flags
# Supports:
#   - KOAL_PKI_ENDPOINTS: comma-separated local agent endpoints
#   - WEBAPP_PASSWORD_LOGIN_ENABLED: enable password login UI
#   - VITE_HIDE_PASSWORD_LOGIN: hide password login UI
#   - VITE_ENABLE_SQL_WORKBENCH: enable SQL Workbench at runtime
#   - PLATFORM_PUBLIC_BASE_URL: preferred absolute domain for BI links (e.g., https://bi.example.com)
RUNTIME_JS="$RUNTIME_CONFIG_PATH"
# Initialize stub to ensure file exists
printf '%s\n' '(function(w){w.__RUNTIME_CONFIG__=w.__RUNTIME_CONFIG__||{};})(window);' > "$RUNTIME_JS"
# Ensure runtime-config.js is world-readable (served by nginx worker)
chmod 0644 "$RUNTIME_JS" 2>/dev/null || true

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

if [ -n "${VITE_ENABLE_SQL_WORKBENCH:-}" ]; then
  val=$(printf '%s' "$VITE_ENABLE_SQL_WORKBENCH" | tr '[:upper:]' '[:lower:]')
  printf '%s\n' "(function(w){w.__RUNTIME_CONFIG__=w.__RUNTIME_CONFIG__||{};w.__RUNTIME_CONFIG__.enableSqlWorkbench='${val}';})(window);" >> "$RUNTIME_JS"
  echo "[entrypoint] runtime-config.js: enableSqlWorkbench=${VITE_ENABLE_SQL_WORKBENCH}"
fi

if [ -n "${PLATFORM_PUBLIC_BASE_URL:-}" ]; then
  val=$(printf '%s' "$PLATFORM_PUBLIC_BASE_URL" | tr -d '\r\n')
  printf '%s\n' "(function(w){w.__RUNTIME_CONFIG__=w.__RUNTIME_CONFIG__||{};w.__RUNTIME_CONFIG__.platformBaseUrl='${val}';})(window);" >> "$RUNTIME_JS"
  echo "[entrypoint] runtime-config.js: platformBaseUrl=${PLATFORM_PUBLIC_BASE_URL}"
fi

# Fix permissions for vendor assets so nginx workers can read them (avoid 403 -> HTML)
if [ -d "${WEBAPP_HTML_ROOT}/vendor" ]; then
  chmod -R a+rX "${WEBAPP_HTML_ROOT}/vendor" 2>/dev/null || true
fi

# Optional: explicit Koal vendor base for platform webapp (e.g., '/vendor/koal' or full https URL)
if [ -n "${KOAL_VENDOR_BASE:-}" ]; then
  printf '%s\n' "(function(w){w.__RUNTIME_CONFIG__=w.__RUNTIME_CONFIG__||{};w.__RUNTIME_CONFIG__.koalVendorBase='${KOAL_VENDOR_BASE}';})(window);" >> "$RUNTIME_JS"
  echo "[entrypoint] runtime-config.js: koalVendorBase=${KOAL_VENDOR_BASE}"
fi

nginx -g 'daemon off;' &
NGINX_PID=$!

wait_for_backends
render_active_nginx_config
nginx -s reload

wait "$NGINX_PID"
