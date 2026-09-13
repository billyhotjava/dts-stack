# T03：分离领域事件与审计 Outbox

**优先级**：P0
**状态**：PLANNED
**依赖**：F1/T03；F2 聚合事务

## 目标

以两张独立事务 outbox 承载领域事件和审计事件，保证业务提交与消息持久化原子、消费者重放幂等。

## 技术设计（Contract-first）

- **领域表**：`modeling_domain_event_outbox(event_id,aggregate_type,aggregate_id,event_type,payload,tenant_id,correlation_id,occurred_at,available_at,published_at,attempts,last_error)`。
- **审计表**：`modeling_audit_outbox(audit_id,action_code,stage,resource_type,resource_id,payload,actor,tenant_id,client_ip,correlation_id,occurred_at,available_at,delivered_at,attempts,last_error)`。
- **约束/索引**：主键 UUID；逻辑幂等唯一键；`published_at/delivered_at + available_at` 待投递索引；payload JSONB 有大小上限和 secret filter。
- **事务**：聚合写 + 所属 event/audit rows 同事务；dispatcher 在事务外有界 claim，HTTP/消息投递不持 DB lock。
- **错误路径**：outbox 写失败回滚业务事务；dispatcher 失败递增 attempts/next time；超过阈值进入各自 DLQ/告警。
- **禁止**：单表加 kind 字段混用；进程内 queue；日志代替审计。

## 影响范围

新 forward Liquibase、outbox repositories/dispatchers、canonical aggregate transactions。

## 验证

- [ ] transaction fault injection 原子性。
- [ ] schema 分表、唯一键、索引和 payload size tests。
- [ ] 10k backlog、并发 dispatcher、SKIP LOCKED/锁等待适应度。
- [ ] event/audit metrics 与 DLQ 完全分离。

## Definition of Done

- [ ] 所有 P0 状态变化同时产生正确 event/audit rows。
- [ ] 重放 100 次无重复聚合副作用或中央审计。
- [ ] outbox payload 不含 token/profile/secret。
