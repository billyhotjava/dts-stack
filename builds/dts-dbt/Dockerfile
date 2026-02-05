FROM python:3.11-slim

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
    PIP_NO_INPUT=1

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

RUN python -m pip install --no-cache-dir --progress-bar off pip==26.0 \
 && pip install --no-cache-dir --progress-bar off "dbt-core==1.11.2"

# 安装数据库 adapter
RUN pip install --no-cache-dir --progress-bar off "dbt-postgres==1.11.2"
# RUN pip install "dbt-trino==1.11.2"

WORKDIR /workspace
ENTRYPOINT ["dbt"]
