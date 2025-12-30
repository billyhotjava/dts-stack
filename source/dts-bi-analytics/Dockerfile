######################################################################
# Proxy build args (declare inside stages for max Docker compatibility)
######################################################################

##################
# STAGE 1: builder
###################

FROM node:22-bullseye AS builder

ARG http_proxy
ARG https_proxy
ARG no_proxy
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY

ENV http_proxy=$http_proxy \
    https_proxy=$https_proxy \
    no_proxy=$no_proxy \
    HTTP_PROXY=$HTTP_PROXY \
    HTTPS_PROXY=$HTTPS_PROXY \
    NO_PROXY=$NO_PROXY

ARG MB_EDITION=oss
ARG VERSION

WORKDIR /home/node

RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends ca-certificates curl wget gnupg git; \
    rm -rf /var/lib/apt/lists/*

# Install JDK 21 (Temurin) without relying on /etc/apt/trusted.gpg.d (can break if key file isn't readable by _apt).
RUN set -eux; \
    wget -qO- https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor -o /usr/share/keyrings/adoptium.gpg; \
    chmod 0644 /usr/share/keyrings/adoptium.gpg; \
    codename="$(. /etc/os-release; echo "${VERSION_CODENAME}")"; \
    echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb ${codename} main" > /etc/apt/sources.list.d/adoptium.list; \
    apt-get update; \
    apt-get install -y --no-install-recommends temurin-21-jdk; \
    rm -rf /var/lib/apt/lists/*

# Install Clojure CLI
RUN set -eux; \
    curl -fsSLo linux-install-clojure.sh https://download.clojure.org/install/linux-install-1.12.0.1488.sh; \
    chmod +x linux-install-clojure.sh; \
    ./linux-install-clojure.sh; \
    rm -f linux-install-clojure.sh

COPY . .

# version is pulled from git, but git doesn't trust the directory due to different owners
RUN git config --global --add safe.directory /home/node

# Make git clones more reliable behind HTTP proxies (and avoid flaky HTTP/2 behavior with some proxies).
RUN set -eux; \
    if [ -n "${https_proxy:-}" ]; then git config --global http.proxy "${https_proxy}"; fi; \
    if [ -n "${HTTPS_PROXY:-}" ] && [ -z "${https_proxy:-}" ]; then git config --global http.proxy "${HTTPS_PROXY}"; fi; \
    git config --global http.version HTTP/1.1; \
    git config --global http.postBuffer 524288000

# install frontend dependencies
RUN yarn --frozen-lockfile

# NOTE: clojure `-X` args must be valid EDN. Versions like `1.0.0` must be passed as a string.
RUN INTERACTIVE=false CI=true MB_EDITION=$MB_EDITION bin/build.sh :version "\"${VERSION}\""

# ###################
# # STAGE 2: runner
# ###################

## Remember that this runner image needs to be the same as bin/docker/Dockerfile with the exception that this one grabs the
## jar from the previous stage rather than the local build

FROM eclipse-temurin:21-jre-alpine AS runner

ARG http_proxy
ARG https_proxy
ARG no_proxy
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY

ENV http_proxy=$http_proxy \
    https_proxy=$https_proxy \
    no_proxy=$no_proxy \
    HTTP_PROXY=$HTTP_PROXY \
    HTTPS_PROXY=$HTTPS_PROXY \
    NO_PROXY=$NO_PROXY

ENV FC_LANG=en-US LC_CTYPE=en_US.UTF-8

# Allow overriding APK repos during build (e.g., internal mirror)
ARG ALPINE_REPO
ARG ALPINE_COMMUNITY_REPO

# dependencies（不再下载外部 CA，必要时在运行时挂载 certs）
RUN set -eux; \
    alpine_branch="$(cut -d. -f1,2 /etc/alpine-release)"; \
    default_main="https://dl-cdn.alpinelinux.org/alpine/v${alpine_branch}/main"; \
    default_community="https://dl-cdn.alpinelinux.org/alpine/v${alpine_branch}/community"; \
    repo_main="${ALPINE_REPO:-${default_main}}"; \
    repo_community="${ALPINE_COMMUNITY_REPO:-${default_community}}"; \
    { echo "${repo_main}"; echo "${repo_community}"; } > /etc/apk/repositories; \
    apk update || true; \
    apk add --no-cache \
      bash fontconfig curl font-noto font-noto-arabic font-noto-hebrew font-noto-cjk java-cacerts \
    || { \
         echo "[apk] falling back to default ${alpine_branch} repositories"; \
         { echo "${default_main}"; echo "${default_community}"; } > /etc/apk/repositories; \
         apk update; \
         apk add --no-cache \
           bash fontconfig curl font-noto font-noto-arabic font-noto-hebrew font-noto-cjk java-cacerts; \
       }; \
    mkdir -p /app/certs /plugins; \
    chmod a+rwx /plugins

# add Metabase script and uberjar
COPY --from=builder /home/node/target/uberjar/metabase.jar /app/
COPY bin/docker/run_metabase.sh /app/

# expose our default runtime port
EXPOSE 3000

# run it
ENTRYPOINT ["/app/run_metabase.sh"]
