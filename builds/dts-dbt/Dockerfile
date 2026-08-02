FROM python:3.11-slim@sha256:db3ff2e1800a8581e2c48a27c3995339d47bdf046da21c7627accd3d51053a93

ARG TARGETPLATFORM
ARG TARGETARCH
ARG DBT_RUNTIME_PLATFORM=linux/amd64
ARG DBT_RUNTIME_BASE_PLATFORM_MANIFEST_DIGEST=sha256:00af38ae2ed311628970782e8a2d7f014d8909dbc63cb97bc0a158187f4db045
ARG DBT_RUNTIME_LOCK_SHA256=01d7c02b6bf4fefdfc188cbf9ef8aed4fb243c227c060103f195c4ca45af5f02
ARG DBT_RUNTIME_CANDIDATE_PROFILE_ID=H83-RT01-LINUX-AMD64-DBT11022-PG1100-LOCK-01d7c02b6bf4fefdfc188cbf9ef8aed4fb243c227c060103f195c4ca45af5f02
ARG APT_MIRROR=http://deb.debian.org/debian
ARG APT_SECURITY_MIRROR=http://deb.debian.org/debian-security
ARG INSTALL_APT_DEPS=0
ARG INSTALL_BUILD_DEPS=0
ARG INSTALL_GIT=0
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY

ENV PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_PROGRESS_BAR=off \
    PIP_NO_INPUT=1 \
    DBT_CORE_VERSION=1.10.22 \
    DBT_POSTGRES_VERSION=1.10.0 \
    DBT_RUNTIME_CERTIFICATION_STATUS=NOT_CERTIFIED \
    DBT_RUNTIME_PLATFORM=${DBT_RUNTIME_PLATFORM} \
    DBT_RUNTIME_BASE_PLATFORM_MANIFEST_DIGEST=${DBT_RUNTIME_BASE_PLATFORM_MANIFEST_DIGEST} \
    DBT_RUNTIME_LOCK_SHA256=${DBT_RUNTIME_LOCK_SHA256} \
    DBT_RUNTIME_CANDIDATE_PROFILE_ID=${DBT_RUNTIME_CANDIDATE_PROFILE_ID}

LABEL com.yuzhi.dts.dbt.runtime.candidate-profile-id="${DBT_RUNTIME_CANDIDATE_PROFILE_ID}" \
      com.yuzhi.dts.dbt.runtime.certification-status="NOT_CERTIFIED" \
      com.yuzhi.dts.dbt.runtime.platform="${DBT_RUNTIME_PLATFORM}" \
      com.yuzhi.dts.dbt.runtime.base-platform-manifest-digest="${DBT_RUNTIME_BASE_PLATFORM_MANIFEST_DIGEST}" \
      com.yuzhi.dts.dbt.runtime.requirements-lock-sha256="${DBT_RUNTIME_LOCK_SHA256}" \
      com.yuzhi.dts.dbt.runtime.core-version="1.10.22" \
      com.yuzhi.dts.dbt.runtime.postgres-adapter-version="1.10.0"

# 常见依赖（编译部分 adapter 可能需要 gcc 等）
# 部分环境下 docker-clean 的 Post-Invoke 会报错，先移除以避免 build 失败
RUN if [ "${INSTALL_APT_DEPS}" = "1" ] || [ "${INSTALL_APT_DEPS}" = "true" ]; then \
      rm -f /etc/apt/apt.conf.d/docker-clean; \
      printf '%s\n' \
        'Acquire::Retries "5";' \
        'Acquire::http::Timeout "30";' \
        'Acquire::https::Timeout "30";' \
        'Acquire::ForceIPv4 "true";' \
        > /etc/apt/apt.conf.d/99retries; \
      if [ -f /etc/apt/sources.list ]; then \
        sed -i "s|http://deb.debian.org/debian|${APT_MIRROR}|g" /etc/apt/sources.list; \
        sed -i "s|http://deb.debian.org/debian-security|${APT_SECURITY_MIRROR}|g" /etc/apt/sources.list; \
        sed -i "s|http://security.debian.org/debian-security|${APT_SECURITY_MIRROR}|g" /etc/apt/sources.list; \
      fi; \
      http_proxy="${HTTP_PROXY:-}" https_proxy="${HTTPS_PROXY:-}" no_proxy="${NO_PROXY:-}" \
        apt-get update; \
      if [ "${INSTALL_BUILD_DEPS}" = "1" ] || [ "${INSTALL_BUILD_DEPS}" = "true" ]; then \
        if [ "${INSTALL_GIT}" = "1" ] || [ "${INSTALL_GIT}" = "true" ]; then \
          http_proxy="${HTTP_PROXY:-}" https_proxy="${HTTPS_PROXY:-}" no_proxy="${NO_PROXY:-}" \
            apt-get install -y --no-install-recommends git ca-certificates build-essential; \
        else \
          http_proxy="${HTTP_PROXY:-}" https_proxy="${HTTPS_PROXY:-}" no_proxy="${NO_PROXY:-}" \
            apt-get install -y --no-install-recommends ca-certificates build-essential; \
        fi; \
      else \
        if [ "${INSTALL_GIT}" = "1" ] || [ "${INSTALL_GIT}" = "true" ]; then \
          http_proxy="${HTTP_PROXY:-}" https_proxy="${HTTPS_PROXY:-}" no_proxy="${NO_PROXY:-}" \
            apt-get install -y --no-install-recommends git ca-certificates; \
        else \
          http_proxy="${HTTP_PROXY:-}" https_proxy="${HTTPS_PROXY:-}" no_proxy="${NO_PROXY:-}" \
            apt-get install -y --no-install-recommends ca-certificates; \
        fi; \
      fi; \
      rm -rf /var/lib/apt/lists/*; \
    fi

COPY requirements.lock /opt/dts-runtime/requirements.lock
COPY verify-runtime.sh /usr/local/bin/verify-dbt-runtime
COPY entrypoint.sh /usr/local/bin/dts-dbt-entrypoint

RUN set -eu; \
    actual_lock_sha256="$(sha256sum /opt/dts-runtime/requirements.lock | awk '{print $1}')"; \
    expected_candidate_profile_id="H83-RT01-LINUX-AMD64-DBT11022-PG1100-LOCK-${DBT_RUNTIME_LOCK_SHA256}"; \
    [ "${actual_lock_sha256}" = "${DBT_RUNTIME_LOCK_SHA256}" ]; \
    [ "${DBT_RUNTIME_CANDIDATE_PROFILE_ID}" = "${expected_candidate_profile_id}" ]; \
    [ "${TARGETPLATFORM}" = "${DBT_RUNTIME_PLATFORM}" ]; \
    [ "${TARGETARCH}" = "amd64" ]; \
    [ "$(uname -m)" = "x86_64" ]; \
    [ "${DBT_RUNTIME_BASE_PLATFORM_MANIFEST_DIGEST}" = "sha256:00af38ae2ed311628970782e8a2d7f014d8909dbc63cb97bc0a158187f4db045" ]; \
    python -m pip install --no-cache-dir --progress-bar off \
      --require-hashes \
      --requirement /opt/dts-runtime/requirements.lock; \
    printf '%s\n' \
      "IMAGE_CERTIFICATION_STATUS=NOT_CERTIFIED" \
      "IMAGE_RUNTIME_PLATFORM=${DBT_RUNTIME_PLATFORM}" \
      "IMAGE_RUNTIME_ARCH=amd64" \
      "IMAGE_BASE_INDEX_DIGEST=sha256:db3ff2e1800a8581e2c48a27c3995339d47bdf046da21c7627accd3d51053a93" \
      "IMAGE_BASE_PLATFORM_MANIFEST_DIGEST=${DBT_RUNTIME_BASE_PLATFORM_MANIFEST_DIGEST}" \
      "IMAGE_REQUIREMENTS_LOCK_SHA256=${DBT_RUNTIME_LOCK_SHA256}" \
      "IMAGE_CANDIDATE_PROFILE_ID=${DBT_RUNTIME_CANDIDATE_PROFILE_ID}" \
      "IMAGE_DBT_CORE_VERSION=1.10.22" \
      "IMAGE_DBT_POSTGRES_VERSION=1.10.0" \
      > /opt/dts-runtime/runtime-metadata; \
    chmod 0444 /opt/dts-runtime/runtime-metadata; \
    chmod 0555 /usr/local/bin/verify-dbt-runtime /usr/local/bin/dts-dbt-entrypoint; \
    /usr/local/bin/verify-dbt-runtime --verify-install

WORKDIR /workspace
ENTRYPOINT ["/usr/local/bin/dts-dbt-entrypoint"]
