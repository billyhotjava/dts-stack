# F4: 事件观测与策略接口预留

**优先级**: P1
**状态**: PLANNED
**目标**: 为统一事件、统一观测和后续策略闭环打底；Kafka 只作为可选基础设施，不直接绑定业务功能。

## 任务

| Task | 状态 | 内容 |
|------|------|------|
| T01 | PLANNED | 定义事件 envelope：eventId、eventType、source、occurredAt、schemaVersion、classification、payload |
| T02 | PLANNED | 设计 outbox 表和发布接口，先文档化，不强制改造业务事务 |
| T03 | PLANNED | 定义 topic 命名和 schema version 规则 |
| T04 | PLANNED | 设计 Kafka 开关：disabled/db-only/kafka-enabled 三档 |
| T05 | PLANNED | 定义观测指标：outbox pending、publish success、publish failure、DLQ count |
| T06 | PLANNED | 预留 PolicyDecision 接口，不固化审批/权限/脱敏规则 |

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
