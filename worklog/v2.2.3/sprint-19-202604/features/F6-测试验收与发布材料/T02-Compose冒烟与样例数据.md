# T02: Compose 冒烟与样例数据

**优先级**: P1
**状态**: DONE
**依赖**: F1, F2, F3, F4, F5

## 目标

用 compose 环境完成 OpenMetadata 端到端冒烟，留存可复现样例数据和证据。

## 范围

- OpenMetadata server API 冒烟。
- ingestion 容器或脚本运行。
- 数据库接入任务样例。
- OpenMetadata 中 service/table/pipeline/lineage 检查。
- 平台 catalog 查询结果检查。

## 完成标准

- [x] 样例任务能产生 OpenMetadata 可见元数据。
- [x] 平台能查询到对应元数据或展示合理回退。
- [x] 失败和成功日志都留存证据。
- [x] 冒烟步骤可重复执行。

## 验收记录（2026-04-29）

- OpenMetadata server API 返回版本 `1.11.5`。
- Airflow webserver/scheduler/triggerer 运行，webserver healthy。
- PostgreSQL one-shot ingestion 使用 `ingestion-bot` token 执行成功：`Workflow Postgres Summary` 与 `Workflow OpenMetadata Summary` 均为 `Success %: 100.0`。
- PostgreSQL ingestion 目标库必须来自 `DTS_OPENMETADATA_INGEST_DATABASE`，默认 `biadmin`；禁止使用 `dts_platform`、`dts_admin`、`dts_common`、`dts_analytics` 等平台业务/内部库。
- 原 `hive.dts_platform.public.catalog_dataset` 冒烟样例不符合当前范围约束，后续可见性验收必须改用 `hive.${DTS_OPENMETADATA_INGEST_DATABASE}.public.<table>`。
- dbt ingestion 使用同一 token 执行成功：`Workflow dbt Summary` 与 `Workflow OpenMetadata Summary` 均为 `Success %: 100.0`。
- 无 token + no-auth 模式在当前 auth-enabled server 下会 401，这是预期的认证保护；生产应配置 token 或确认服务端真正 no-auth。
- 平台未登录 API 返回 401 属于预期；前端和 API 已增加 source/fallback 字段，登录后页面展示来源和回退原因。
