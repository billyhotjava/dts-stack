# Sprint-22: API 数据接入运行时与工业级验收

**时间**: 2026-05  
**状态**: IN_PROGRESS（API runtime 核心已落地，E2E 现场证据待归档）
**类型**: Implementation（API 入湖运行时 + 凭据引用 + 增量/分页 + ODS 契约复用）

## 背景

Sprint-18 已把数据库、Excel、CSV 收敛到统一 ODS、batch、execution、dbt source 和 stg 建模边界。API 当前只具备配置草稿和 mock DAG 能力，不能作为“工业级四类源统一入 ODS”的完成项。

本 Sprint 目标是把 API 从草稿/Mock 能力推进为正式接入运行时，同时复用 Sprint-18 已固化的 ODS 契约与观测规则。

## 范围

- API 数据源 provider metadata 和凭据引用。
- API 任务 preview、schema snapshot 和 ODS raw landing。
- HTTP runner：GET/POST、headers、query/body、超时、重试、限流、错误分类。
- 分页：page/pageSize、offset/limit、next token、next URL。
- 增量：cursor 字段、checkpoint、重跑和回补。
- ODS 技术字段：`_dts_batch_id`、`_dts_execution_id`、`_dts_task_id`、`_dts_import_time`、`_dts_endpoint`、`_dts_cursor_value`。
- dbt source、目录资产、血缘和质量观测链路衔接。
- dev / app / legacy 三模式验收，不改初始化后的 `.env` 或 compose。

## 非目标

- 不引入 Airbyte / SeaTunnel。
- 不把 API 凭据写入 `.env`、compose 或任务 JSON 明文。
- 不在 ODS 层做业务字段展开和口径加工；复杂 JSON 展平从 dbt `stg` 开始。

## Task 列表

| ID | Task | 优先级 | 状态 |
|---|---|---|---|
| T01 | API runtime 契约和 mock DAG 下线标准 | P0 | DONE |
| T02 | 凭据引用与 secrets 存储/读取链路 | P0 | DONE |
| T03 | HTTP runner 与错误分类 | P0 | DONE |
| T04 | 分页、限流、重试、熔断 | P0 | DONE |
| T05 | cursor 增量、checkpoint、回补 | P0 | DONE |
| T06 | API preview、schema snapshot、ODS raw landing | P1 | DONE |
| T07 | dbt source / 目录 / 血缘 / 质量链路刷新 | P1 | IN_PROGRESS |
| T08 | dev/app/legacy 三模式 E2E 验收 | P0 | IN_PROGRESS |

## 完成标准

- [x] API full refresh 可稳定落 ODS，且不依赖 `.env` 保存每个业务系统凭据。
- [x] API incremental cursor 可恢复、可重跑、可观测。
- [x] 外部 API 异常不会泄露密钥、不会无限重试、能返回可行动错误分类。
- [ ] API ODS 表进入 dbt source、目录资产、血缘和质量观测链路。
- [ ] 浏览器 E2E 覆盖 API 数据源、任务创建、preview、执行、重跑和错误诊断。
- [ ] dev / app / legacy 三种模式验收通过。

## 当前落地切片

- `dts-ingestion` 已提供 `/api/ingestion/api/contract` 和 `/auth-providers`，返回 contract version、source aliases、默认 reader、raw landing 契约和 auth provider 字段。
- API task 创建路径已按 `api_http` connector 归一化 source config，API task 不再生成 Addax job，执行阶段走 PythonOperator DAG。
- API DAG 支持多 resource、GET/POST、default headers、bearer/apiKey/basic secretRef、请求超时、重试、限流、page/offset/token/nextUrl 分页、cursor checkpoint 和 backfill window。
- ODS raw landing 自动建表，包含 `_dts_raw_record`、`_dts_batch_id`、`_dts_execution_id`、`_dts_endpoint`、`_dts_cursor_value` 等技术字段；checkpoint 落 `dts_api_ingestion_checkpoint`。
- 新增验收入口：`it/scripts/api-runtime-smoke.sh`、`it/samples/api-source-config.json`、`it/runbook.md`。

## 剩余收口项

- T07 需要把 API ODS raw 表自动刷新到 dbt source、catalog asset、Sprint-20 lineage/quality 链路，并归档 API 样例资产。
- T08 需要在 dev/app/legacy 三模式用真实或 mock API 执行 `api-runtime-smoke.sh`，归档 Airflow 日志、ODS 行数、checkpoint 和浏览器截图。
