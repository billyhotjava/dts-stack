#!/usr/bin/env sh
set -eu

# Compute upstream from env with sensible default for local dev
# Examples:
#   -e API_PROXY_TARGET=http://host.docker.internal:8081  (host-run dev backend)
#   -e API_PROXY_TARGET=http://dts-platform-gateway:8080   (compose service)
UPSTREAM="${API_PROXY_TARGET:-http://host.docker.internal:8081}"
export UPSTREAM

TEMPLATE="/etc/nginx/conf.d/default.conf.template"
TARGET="/etc/nginx/conf.d/default.conf"

if [ -f "$TEMPLATE" ]; then
  echo "[entrypoint] Rendering Nginx config with UPSTREAM=$UPSTREAM"
  # shellcheck disable=SC2016
  envsubst '${UPSTREAM}' < "$TEMPLATE" > "$TARGET"
fi

exec nginx -g 'daemon off;'
