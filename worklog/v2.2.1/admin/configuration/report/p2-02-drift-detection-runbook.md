# P2-02 `.env` 与 DB 配置漂移检测 Runbook

## 脚本

- `worklog/v2.2.1/admin/configuration/scripts/check-config-drift.sh`

## 前置条件

- `psql` 可用
- 可访问 `dts_admin` 库
- 设置环境变量:
  - `PGHOST`
  - `PGPORT`
  - `PGDATABASE`
  - `PGUSER`
  - `PGPASSWORD`

## 执行

```bash
PGPASSWORD='***' \
PGHOST=127.0.0.1 \
PGPORT=5432 \
PGDATABASE=dts_admin \
PGUSER=dts_admin \
./worklog/v2.2.1/admin/configuration/scripts/check-config-drift.sh \
  ./.env \
  ./worklog/v2.2.1/admin/configuration/raw
```

## 输出

- `worklog/v2.2.1/admin/configuration/raw/config-drift-report.tsv`
- 状态含义:
  - `same`: `.env` 与 DB 一致
  - `drift`: 同 key 值不一致
  - `db_only`: DB 中存在但 `.env` 无此 key

## 处理建议

1. `drift` 且 `scope=BOOTSTRAP`: 以 `.env` 为准。
2. `drift` 且 `scope=RUNTIME/RUNTIME_RESTART`: 以 Admin DB 为准，补齐 `.env` 说明项。
3. 处理后重跑脚本，确保 `drift` 为 0。
