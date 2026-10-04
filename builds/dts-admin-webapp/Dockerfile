# Allow overriding Node base from internal mirror
# Alpine tags for Node.js are no longer published under the rolling `20-alpine`.
# Pin to the current LTS patch to avoid registry lookup failures.
ARG BASE_IMAGE_NODE=node:20.17.0-alpine3.20
# Declared here (before the first FROM) so it is in scope for the runtime FROM below.
ARG BASE_IMAGE_RUNTIME=alpine:3.20
FROM ${BASE_IMAGE_NODE} AS build

WORKDIR /app

# Optional proxy/registry for npm & pnpm downloads during build. Pass via
# --build-arg NPM_HTTP_PROXY=..., NPM_HTTPS_PROXY=..., NPM_REGISTRY=...
ARG NPM_HTTP_PROXY
ARG NPM_HTTPS_PROXY
ARG NPM_REGISTRY
ARG PNPM_VERSION=10.8.0
ARG WEBAPP_BUILD_CMD=build

# Never emit the bundle-analyzer report in image builds. It runs gzip *and* brotli
# over every chunk, which costs far more than the report is worth here.
# Use `pnpm build:analyze` locally when the treemap is actually needed.
ENV VITE_VISUALIZER_OPEN=false
ENV ANALYZE=false

# Recommended build context: repository root
#   docker build -t dts-admin-webapp:TAG -f builds/dts-admin-webapp/Dockerfile .
# Copy lockfile and manifest first to leverage docker layer caching
COPY source/dts-admin-webapp/pnpm-lock.yaml source/dts-admin-webapp/package.json ./
COPY source/dts-session-core/package.json /dts-session-core/package.json
COPY source/dts-session-core/src/ /dts-session-core/src/

# Install pnpm matching the repo's packageManager. Corepack bundled with Node 20.17 may lack the new signing key,
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
    npm install -g "pnpm@${PNPM_VERSION}"; \
    pnpm --version; \
    unset npm_config_registry npm_config_proxy npm_config_https_proxy http_proxy https_proxy

# Keep the pnpm content-addressable store on a BuildKit cache mount. It survives
# --no-cache (which only invalidates layers, not cache mounts), so even release
# rebuilds resolve packages from local disk instead of re-downloading them.
ENV PNPM_STORE_DIR=/pnpm-store

# Install dependencies without running lifecycle scripts (skip Lefthook, etc.)
RUN --mount=type=cache,target=/pnpm-store,sharing=locked \
    set -eux; \
    if [ -n "${NPM_REGISTRY:-}" ]; then \
        export npm_config_registry="${NPM_REGISTRY}"; \
    fi; \
    if [ -n "${NPM_HTTP_PROXY:-}${NPM_HTTPS_PROXY:-}" ]; then \
        export http_proxy="${NPM_HTTP_PROXY:-${NPM_HTTPS_PROXY:-}}"; \
        export https_proxy="${NPM_HTTPS_PROXY:-${NPM_HTTP_PROXY:-}}"; \
        export npm_config_proxy="${http_proxy}"; \
        export npm_config_https_proxy="${https_proxy}"; \
    fi; \
    pnpm config set store-dir "${PNPM_STORE_DIR}" --global; \
    pnpm install --frozen-lockfile --ignore-scripts; \
    unset npm_config_registry npm_config_proxy npm_config_https_proxy http_proxy https_proxy

# Copy the remaining project files and build the production bundle
COPY source/dts-admin-webapp/ ./
COPY builds/dts-admin-webapp/scripts /builds/dts-admin-webapp/scripts
RUN pnpm "${WEBAPP_BUILD_CMD}"

# Runtime serves static files with nginx and renders runtime-config.js with sh only —
# no Node.js is involved, so the ~130MB node base is dead weight in every offline
# image tarball. Plain Alpine keeps the exact same apk packages and the same
# /etc/nginx/http.d layout the config below expects.
ARG BASE_IMAGE_RUNTIME=alpine:3.20
FROM ${BASE_IMAGE_RUNTIME} AS runtime

# Optional proxy for apk downloads; pass via --build-arg APK_HTTP_PROXY=http://host:port (and/or HTTPS)
ARG APK_HTTP_PROXY
ARG APK_HTTPS_PROXY
ARG ALPINE_REPO_MAIN
ARG ALPINE_REPO_COMMUNITY

RUN set -eux; \
    if [ -n "${APK_HTTP_PROXY:-}${APK_HTTPS_PROXY:-}" ]; then \
        export http_proxy="${APK_HTTP_PROXY:-${APK_HTTPS_PROXY:-}}"; \
        export https_proxy="${APK_HTTPS_PROXY:-${APK_HTTP_PROXY:-}}"; \
    fi; \
    if [ -n "${ALPINE_REPO_MAIN:-}" ] || [ -n "${ALPINE_REPO_COMMUNITY:-}" ]; then \
        : > /etc/apk/repositories; \
        if [ -n "${ALPINE_REPO_MAIN:-}" ]; then echo "${ALPINE_REPO_MAIN}" >> /etc/apk/repositories; fi; \
        if [ -n "${ALPINE_REPO_COMMUNITY:-}" ]; then echo "${ALPINE_REPO_COMMUNITY}" >> /etc/apk/repositories; fi; \
    fi; \
    apk update || true; \
    apk add --no-cache nginx \
    || { \
         echo "[apk] fallback to Aliyun mirror"; \
         echo "https://mirrors.aliyun.com/alpine/v3.20/main" > /etc/apk/repositories; \
         echo "https://mirrors.aliyun.com/alpine/v3.20/community" >> /etc/apk/repositories; \
         apk update; \
         apk add --no-cache nginx; \
       }; \
    unset http_proxy https_proxy; \
    rm -f /etc/nginx/http.d/default.conf /etc/nginx/conf.d/default.conf; \
    mkdir -p /usr/share/nginx/html

WORKDIR /usr/share/nginx/html
COPY --from=build /app/dist ./
RUN set -eux; \
    if [ -d "/usr/share/nginx/html/vendor" ]; then \
      chmod -R a+rX "/usr/share/nginx/html/vendor"; \
    fi; \
    # Ensure all assets are world-readable & directories executable for nginx worker
    find /usr/share/nginx/html -type d -exec chmod 755 {} + || true; \
    find /usr/share/nginx/html -type f -exec chmod 644 {} + || true
COPY builds/dts-admin-webapp/nginx.conf /etc/nginx/http.d/default.conf
COPY builds/dts-admin-webapp/docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

EXPOSE 80

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["nginx", "-g", "daemon off;"]
