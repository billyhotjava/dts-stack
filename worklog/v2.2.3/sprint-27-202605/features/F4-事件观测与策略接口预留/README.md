# F4: 事件观测与策略接口预留

**优先级**: P1
**状态**: IN_PROGRESS
**目标**: 为统一事件、统一观测和后续策略闭环打底；Kafka 只作为可选基础设施，不直接绑定业务功能。

## 任务

| Task | 状态 | 内容 |
|------|------|------|
| T01 | DONE | 定义事件 envelope：eventId、eventType、domain、sourceApp、aggregate、action、severity、status、occurredAt、actor、correlationId、traceId、auditActionCode、policyRef、payload |
| T02 | DONE | 落地 `platform_event_outbox` 表和 `/api/platform/events` 发布/查询接口，不强制改造业务事务 |
| T03 | IN_PROGRESS | 定义 topic 命名和 schema version 规则；默认 topic 为 `dts.platform.events` |
| T04 | DONE | 设计 Kafka 开关：默认 `db-only`，`DTS_PLATFORM_EVENTS_KAFKA_ENABLED=true` 后启用 dispatcher |
| T05 | DONE | 定义观测指标：outbox pending、sent、failed、skipped |
| T06 | IN_PROGRESS | 将指标、语义模型、dbt 发布关键动作接入 outbox，保持业务操作不依赖 Kafka |
| T07 | DONE | 预留 PolicyDecision 接口，不固化审批/权限/脱敏规则 |

## 当前落地

- `dts-platform` 新增平台事件 outbox：
  - Entity: `PlatformEventOutbox`
  - Repository: `PlatformEventOutboxRepository`
  - Service: `PlatformEventOutboxService`
  - API: `/api/platform/events`
- 新增 Liquibase: `20260502_01_platform_event_outbox.xml`
- 新增 Kafka dispatcher:
  - `PlatformEventKafkaDispatcher`
  - 默认关闭；开启后按批次发送 `PENDING` outbox 到 Kafka topic。
- 新增 Micrometer gauge:
  - `dts.platform.events.outbox.pending`
  - `dts.platform.events.outbox.sent`
  - `dts.platform.events.outbox.failed`
  - `dts.platform.events.outbox.skipped`
- 新增前端事件观测页：
  - `/ops/events`
  - `/platform/events`
  - 已按统一控制台操作逻辑补齐总览卡片、分发生命周期、风险分布、域快捷过滤、事件列表筛选和 ELT/指标跳转入口。
  - 页面已改为消费 `/api/platform/sprint27/events-console` 聚合 API。
- 关键业务动作已开始写入 outbox：
  - 指标发布、废止、校验。
  - 语义模型运行触发、运行状态更新、dbt 产物发布、BI 数据集注册、血缘注册。
  - dbt 操作触发、dbt release 提交。
- 新增策略决策口子：
  - `PolicyDecision`
  - `PolicyDecisionContext`
  - `PolicyDecisionService`
  - 默认实现 `NoopPolicyDecisionService` 只返回 `ALLOW/policy-not-configured`，不固化审批、权限、脱敏规则。
- 审计字典新增：
  - `PLATFORM_EVENT_LIST`
  - `PLATFORM_EVENT_SUMMARY`
  - `PLATFORM_EVENT_PUBLISH`
  - `SPRINT27_ELT_CONSOLE_VIEW`
  - `SPRINT27_METRIC_OPERATIONS_VIEW`
  - `SPRINT27_EVENTS_CONSOLE_VIEW`
  - `SPRINT27_AUDIT_EVIDENCE_VIEW`
  - `SPRINT27_RELEASE_GOVERNANCE_VIEW`

## API 契约

- `GET /api/platform/events`
  - 查询最近事件，支持 `domain`、`eventType`、`aggregateType`、`aggregateId`、`status`、`dispatchStatus` 过滤。
- `GET /api/platform/events/summary`
  - 返回 total、pending、sent、failed、skipped 以及 domain/status/severity 分布。
- `POST /api/platform/events`
  - 写入统一事件 outbox，并同步记录审计动作。
- `GET /api/platform/sprint27/events-console`
  - 返回事件概要和分页列表，作为 Sprint-27 事件观测页稳定后端视图模型。

## 已接入事件类型

| Event Type | Domain | 触发点 |
|------------|--------|--------|
| `METRIC.INDICATOR.PUBLISHED` | `METRICS` | 指标发布 |
| `METRIC.INDICATOR.ARCHIVED` | `METRICS` | 指标废止 |
| `METRIC.INDICATOR.VALIDATED` | `METRICS` | 指标校验 |
| `METRIC.SEMANTIC_MODEL.RUN_TRIGGERED` | `METRICS` | 语义模型运行触发 |
| `METRIC.SEMANTIC_MODEL.RUN_UPDATED` | `METRICS` | 语义模型运行状态更新 |
| `METRIC.SEMANTIC_MODEL.PUBLISHED_DBT` | `METRICS` | 语义模型发布 dbt 产物 |
| `METRIC.SEMANTIC_MODEL.REGISTERED_BI_DATASET` | `METRICS` | 注册 BI 数据集 |
| `METRIC.SEMANTIC_MODEL.REGISTERED_LINEAGE` | `METRICS` | 注册血缘 |
| `ELT.DBT.OPERATION_TRIGGERED` | `ELT` | dbt run/test/build/docs/source 等操作 |
| `ELT.DBT.RELEASE_SUBMITTED` | `ELT` | dbt release 提交 |

## Kafka 处理原则

- `v2.3.0` 只引入 Kafka 容器，没有业务接入，本 Sprint 不做直接 merge。
- Kafka compose 可参考 `v2.3.0`，但必须默认可关闭。
- 业务代码优先依赖 `DomainEventPublisher` 接口，而不是直接依赖 Kafka 客户端。
- Kafka 不可用时，业务操作不失败，事件保留在 outbox 或审计表。

## 验收标准

- 事件契约可被 platform、ingestion、analytics 共用。
- 后续接 Kafka 不需要重改审计字段和事件字段。
- smoke 可验证 Kafka 关闭时主流程仍正常。

## 非目标

- 不建设完整事件总线治理平台。
- 不实现所有业务事件发布。
- 不实现复杂 DLQ 运维页面。
