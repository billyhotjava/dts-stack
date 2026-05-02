# F3: 审计一致性与证据链

**优先级**: P0
**状态**: PLANNED
**目标**: 在审批、权限、脱敏策略未最终确定前，先统一三类服务的审计语义，保证后续安全策略落地时有证据链基础。

## 任务

| Task | 状态 | 内容 |
|------|------|------|
| T01 | PLANNED | 定义统一审计字段：actor、service、module、action、resource、stage、result、requestId、traceId、policyContext |
| T02 | PLANNED | 盘点 platform、ingestion、analytics 关键操作审计覆盖率 |
| T03 | PLANNED | 统一 ingestion 执行、回滚、重试、预检、schema drift 的审计口径 |
| T04 | PLANNED | 统一 analytics 查询、导出、大屏发布、共享、权限变更的审计口径 |
| T05 | PLANNED | 增加审计查询/导出证据接口或复用现有审计入口 |
| T06 | PLANNED | 增加审计一致性单测或 smoke |

## 审计字段预留

| 字段 | 说明 |
|------|------|
| `policyContext` | 预留审批、权限、脱敏策略命中信息 |
| `dataClassification` | 预留数据密级 |
| `approvalRef` | 预留客户审批单引用 |
| `maskingDecision` | 预留脱敏决策摘要 |
| `eventId` | 预留事件外送和 outbox 关联 |

## 验收标准

- 三个服务对“开始、成功、失败、拒绝、回滚”有一致 stage/result 口径。
- 关键操作可以按 requestId 或 actor 追踪。
- 审计数据不要求 Kafka 外送，但保留 eventId 和 policyContext。

## 非目标

- 不实现客户最终审批流。
- 不实现完整端到端权限。
- 不强制所有数据出口脱敏。
