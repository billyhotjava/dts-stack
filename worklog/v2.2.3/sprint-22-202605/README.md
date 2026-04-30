# Sprint-22: API 数据接入运行时与工业级验收

**时间**: 2026-05  
**状态**: PLANNED  
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
| T01 | API runtime 契约和 mock DAG 下线标准 | P0 | PLANNED |
| T02 | 凭据引用与 secrets 存储/读取链路 | P0 | PLANNED |
| T03 | HTTP runner 与错误分类 | P0 | PLANNED |
| T04 | 分页、限流、重试、熔断 | P0 | PLANNED |
| T05 | cursor 增量、checkpoint、回补 | P0 | PLANNED |
| T06 | API preview、schema snapshot、ODS raw landing | P1 | PLANNED |
| T07 | dbt source / 目录 / 血缘 / 质量链路刷新 | P1 | PLANNED |
| T08 | dev/app/legacy 三模式 E2E 验收 | P0 | PLANNED |

## 完成标准

- [ ] API full refresh 可稳定落 ODS，且不依赖 `.env` 保存每个业务系统凭据。
- [ ] API incremental cursor 可恢复、可重跑、可观测。
- [ ] 外部 API 异常不会泄露密钥、不会无限重试、能返回可行动错误分类。
- [ ] API ODS 表进入 dbt source、目录资产、血缘和质量观测链路。
- [ ] 浏览器 E2E 覆盖 API 数据源、任务创建、preview、执行、重跑和错误诊断。
- [ ] dev / app / legacy 三种模式验收通过。

