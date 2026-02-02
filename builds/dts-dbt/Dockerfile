FROM --platform=linux/arm64 python:3.11-slim

ENV PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

# 常见依赖（编译部分 adapter 可能需要 gcc 等）
RUN apt-get update && apt-get install -y --no-install-recommends \
    git ca-certificates build-essential \
 && rm -rf /var/lib/apt/lists/*

RUN python -m pip install --upgrade pip \
 && pip install "dbt-core==1.11.2"

# 如果你用 Postgres / Doris / Trino 等，按需加 adapter：
# RUN pip install "dbt-postgres==1.11.2"
# RUN pip install "dbt-trino==1.11.2"

WORKDIR /workspace
ENTRYPOINT ["dbt"]
