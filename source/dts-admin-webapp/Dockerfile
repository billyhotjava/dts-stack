# Allow overriding Node base from internal mirror
# Alpine tags for Node.js are no longer published under the rolling `20-alpine`.
# Pin to the current LTS patch to avoid registry lookup failures.
ARG BASE_IMAGE_NODE=node:20.17.0-alpine3.20
FROM ${BASE_IMAGE_NODE} AS build

WORKDIR /app

# Optional proxy/registry for npm & pnpm downloads during build. Pass via
# --build-arg NPM_HTTP_PROXY=..., NPM_HTTPS_PROXY=..., NPM_REGISTRY=...
ARG NPM_HTTP_PROXY
ARG NPM_HTTPS_PROXY
ARG NPM_REGISTRY

# Install pnpm matching the repo's packageManager. Corepack bundled with Node 20.17 lacks the new signing key,
# so install via npm to avoid signature verification failures.
RUN set -eux; \
    if [ -n "${NPM_REGISTRY:-}" ]; then \
        export npm_config_registry="${NPM_REGISTRY}"; \
    fi; \
    if [ -n "${NPM_HTTP_PROXY:-}${NPM_HTTPS_PROXY:-}" ]; then \
        export http_proxy="${NPM_HTTP_PROXY:-${NPM_HTTPS_PROXY:-}}"; \
        export https_proxy="${NPM_HTTPS_PROXY:-${NPM_HTTP_PROXY:-}}"; \
        export npm_config_proxy="${http_proxy}"; \
        export npm_config_https_proxy="${https_proxy}"; \
    fi; \
    npm install -g pnpm@10.8.0 && pnpm --version; \
    unset npm_config_registry npm_config_proxy npm_config_https_proxy http_proxy https_proxy

# Copy lockfile and manifest first to leverage docker layer caching
COPY pnpm-lock.yaml package.json ./

# Install dependencies without running lifecycle scripts (skip Lefthook, etc.)
RUN set -eux; \
    if [ -n "${NPM_REGISTRY:-}" ]; then \
        export npm_config_registry="${NPM_REGISTRY}"; \
    fi; \
    if [ -n "${NPM_HTTP_PROXY:-}${NPM_HTTPS_PROXY:-}" ]; then \
        export http_proxy="${NPM_HTTP_PROXY:-${NPM_HTTPS_PROXY:-}}"; \
        export https_proxy="${NPM_HTTPS_PROXY:-${NPM_HTTP_PROXY:-}}"; \
        export npm_config_proxy="${http_proxy}"; \
        export npm_config_https_proxy="${https_proxy}"; \
    fi; \
    pnpm install --frozen-lockfile --ignore-scripts; \
    rm -rf /root/.pnpm-store/*tmp* 2>/dev/null || true; \
    unset npm_config_registry npm_config_proxy npm_config_https_proxy http_proxy https_proxy

# Copy the remaining project files and build the production bundle
COPY . .
RUN pnpm build

ARG BASE_IMAGE_NODE=node:20.17.0-alpine3.20
FROM ${BASE_IMAGE_NODE} AS runtime

# Optional proxy for apk downloads; pass via --build-arg APK_HTTP_PROXY=http://host:port (and/or HTTPS)
ARG APK_HTTP_PROXY
ARG APK_HTTPS_PROXY

RUN set -eux; \
    if [ -n "${APK_HTTP_PROXY:-}${APK_HTTPS_PROXY:-}" ]; then \
        export http_proxy="${APK_HTTP_PROXY:-${APK_HTTPS_PROXY:-}}"; \
        export https_proxy="${APK_HTTPS_PROXY:-${APK_HTTP_PROXY:-}}"; \
    fi; \
    apk add --no-cache nginx && \
    unset http_proxy https_proxy && \
    rm -f /etc/nginx/http.d/default.conf /etc/nginx/conf.d/default.conf && \
    mkdir -p /usr/share/nginx/html

WORKDIR /usr/share/nginx/html
COPY --from=build /app/dist ./
COPY nginx.conf /etc/nginx/http.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
