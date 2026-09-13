# Sprint-22 API Runtime Runbook

## 配置原则

- API 凭据写入数据源 secrets 或运行时环境变量引用，不写入 `.env`、compose 或任务 JSON 明文。
- ODS 只落 raw JSON 和 DTS 技术字段，不在 ODS 做业务口径展开。
- 复杂字段展开、类型转换和业务字段命名从 dbt `stg` 层开始。

## 运行时能力

- HTTP method：`GET`、`POST`。
- Auth：anonymous、bearer token、apiKey、basic；secret 通过 `secretRefs` 指向环境变量名。
- Pagination：page/pageSize、offset/limit、next token、next URL。
- Retry：网络、5xx、429 可重试；4xx 非认证/限流错误快速失败。
- Cursor：按 resource 维护 `dts_api_ingestion_checkpoint`，支持回补窗口覆盖。
- Landing：每个 resource 写入 `targetTable`，包含 `_dts_raw_record` 和 DTS 技术字段。

## 发布前检查

1. `dts-ingestion` 编译和 API 相关单测通过。
2. Airflow worker 具备 `psycopg2`，且能访问目标 ODS PostgreSQL。
3. Airflow 运行时设置 `DTS_TARGET_DB_PASSWORD`，避免密码写入 DAG 文件。
4. 如使用 bearer/apiKey/basic，设置任务需要的 secret env，如 `DTS_API_BEARER_TOKEN`。
5. 使用 `api-runtime-smoke.sh` 验证 contract 和 auth providers。

## 回滚

1. 禁用 API ingestion task 的调度或将任务状态改回 draft。
2. 保留已创建的 ODS raw 表和 `dts_api_ingestion_checkpoint`，便于排查和恢复。
3. 如需回退代码，回退 `dts-ingestion` 镜像；无需修改 platform 资产门户和已有数据库/文件接入链路。

## 常见故障

| 现象 | 排查 |
|---|---|
| `API_RUNTIME_SECRET_MISSING` | 检查 `auth.secretRefs` 指向的环境变量是否在 Airflow worker 中存在 |
| `API_RUNTIME_AUTH` | token/basic/apiKey 是否过期，是否放错 header/query |
| `API_RUNTIME_RATE_LIMIT` | 降低 `requestsPerSecond`，检查 429 响应和重试次数 |
| `API_RUNTIME_SCHEMA` | `recordPath` 是否指向 list/object |
| checkpoint 不推进 | DAG 是否成功完成，cursor field 是否存在于 record 中 |
| ODS 表无数据 | `targetTable`、目标库连接环境变量、Airflow 日志中的 endpoint 和 response |
