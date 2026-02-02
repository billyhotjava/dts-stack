FROM python:3.11-slim

ARG APT_MIRROR=http://deb.debian.org/debian
ARG APT_SECURITY_MIRROR=http://deb.debian.org/debian-security
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY

ENV PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

# 常见依赖（编译部分 adapter 可能需要 gcc 等）
# 部分环境下 docker-clean 的 Post-Invoke 会报错，先移除以避免 build 失败
RUN rm -f /etc/apt/apt.conf.d/docker-clean \
 && printf '%s\n' \
    'Acquire::Retries "5";' \
    'Acquire::http::Timeout "30";' \
    'Acquire::https::Timeout "30";' \
    'Acquire::ForceIPv4 "true";' \
    > /etc/apt/apt.conf.d/99retries \
 && if [ -f /etc/apt/sources.list ]; then \
      sed -i "s|http://deb.debian.org/debian|${APT_MIRROR}|g" /etc/apt/sources.list; \
      sed -i "s|http://deb.debian.org/debian-security|${APT_SECURITY_MIRROR}|g" /etc/apt/sources.list; \
      sed -i "s|http://security.debian.org/debian-security|${APT_SECURITY_MIRROR}|g" /etc/apt/sources.list; \
    fi \
 && http_proxy="${HTTP_PROXY:-}" https_proxy="${HTTPS_PROXY:-}" no_proxy="${NO_PROXY:-}" \
    apt-get update \
 && http_proxy="${HTTP_PROXY:-}" https_proxy="${HTTPS_PROXY:-}" no_proxy="${NO_PROXY:-}" \
    apt-get install -y --no-install-recommends \
    git ca-certificates build-essential \
 && rm -rf /var/lib/apt/lists/*

RUN python -m pip install --upgrade pip \
 && pip install "dbt-core==1.11.2"

# 如果你用 Postgres / Doris / Trino 等，按需加 adapter：
# RUN pip install "dbt-postgres==1.11.2"
# RUN pip install "dbt-trino==1.11.2"

WORKDIR /workspace
ENTRYPOINT ["dbt"]
