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
| T06 | PLANNED | 预留 PolicyDecision 接口，不固化审批/权限/脱敏规则 |

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
- 审计字典新增：
  - `PLATFORM_EVENT_LIST`
  - `PLATFORM_EVENT_SUMMARY`
  - `PLATFORM_EVENT_PUBLISH`

## API 契约

- `GET /api/platform/events`
  - 查询最近事件，支持 `domain`、`eventType`、`aggregateType`、`aggregateId`、`status`、`dispatchStatus` 过滤。
- `GET /api/platform/events/summary`
  - 返回 total、pending、sent、failed、skipped 以及 domain/status/severity 分布。
- `POST /api/platform/events`
  - 写入统一事件 outbox，并同步记录审计动作。

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
