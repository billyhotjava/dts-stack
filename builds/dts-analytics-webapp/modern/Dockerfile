# dts-analytics-webapp-modern
# - React19/Vite webapp (gradual replacement of Metabase legacy UI)
# - Served by nginx (same build pattern as dts-platform-webapp)

ARG BASE_IMAGE_NODE=node:20.17.0-alpine3.20
FROM ${BASE_IMAGE_NODE} AS builder

WORKDIR /app

ARG NPM_HTTP_PROXY
ARG NPM_HTTPS_PROXY
ARG NPM_REGISTRY
ARG PNPM_VERSION=10.8.0

RUN set -eux; \
    if [ -n "${NPM_REGISTRY:-}" ]; then export npm_config_registry="${NPM_REGISTRY}"; fi; \
    if [ -n "${NPM_HTTP_PROXY:-}${NPM_HTTPS_PROXY:-}" ]; then \
        export http_proxy="${NPM_HTTP_PROXY:-${NPM_HTTPS_PROXY:-}}"; \
        export https_proxy="${NPM_HTTPS_PROXY:-${NPM_HTTP_PROXY:-}}"; \
        export npm_config_proxy="${http_proxy}"; \
        export npm_config_https_proxy="${https_proxy}"; \
    fi; \
    npm install -g "pnpm@${PNPM_VERSION}"; \
    pnpm --version; \
    unset npm_config_registry npm_config_proxy npm_config_https_proxy http_proxy https_proxy

# Recommended build context: repository root
#   docker build -t dts-analytics-webapp-modern:TAG -f builds/dts-analytics-webapp/modern/Dockerfile .
COPY source/dts-analytics-webapp/modern/package.json ./

RUN set -eux; \
    if [ -n "${NPM_REGISTRY:-}" ]; then export npm_config_registry="${NPM_REGISTRY}"; fi; \
    if [ -n "${NPM_HTTP_PROXY:-}${NPM_HTTPS_PROXY:-}" ]; then \
        export http_proxy="${NPM_HTTP_PROXY:-${NPM_HTTPS_PROXY:-}}"; \
        export https_proxy="${NPM_HTTPS_PROXY:-${NPM_HTTP_PROXY:-}}"; \
        export npm_config_proxy="${http_proxy}"; \
        export npm_config_https_proxy="${https_proxy}"; \
    fi; \
    pnpm install --no-frozen-lockfile --ignore-scripts; \
    unset npm_config_registry npm_config_proxy npm_config_https_proxy http_proxy https_proxy

COPY source/dts-analytics-webapp/modern/ ./
RUN pnpm build

ARG BASE_IMAGE_NODE=node:20.17.0-alpine3.20
FROM ${BASE_IMAGE_NODE} AS runtime

RUN set -eux; \
    apk add --no-cache nginx; \
    rm -f /etc/nginx/http.d/default.conf /etc/nginx/conf.d/default.conf; \
    mkdir -p /usr/share/nginx/html

WORKDIR /usr/share/nginx/html
COPY --from=builder /app/dist ./
COPY builds/dts-analytics-webapp/modern/nginx.conf /etc/nginx/http.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
