FROM python:3.11-slim

ENV PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

# 常见依赖（编译部分 adapter 可能需要 gcc 等）
# 部分环境下 docker-clean 的 Post-Invoke 会报错，先移除以避免 build 失败
RUN rm -f /etc/apt/apt.conf.d/docker-clean \
 && apt-get update \
 && apt-get install -y --no-install-recommends \
    git ca-certificates build-essential \
 && rm -rf /var/lib/apt/lists/*

RUN python -m pip install --upgrade pip \
 && pip install "dbt-core==1.11.2"

# 如果你用 Postgres / Doris / Trino 等，按需加 adapter：
# RUN pip install "dbt-postgres==1.11.2"
# RUN pip install "dbt-trino==1.11.2"

WORKDIR /workspace
ENTRYPOINT ["dbt"]
