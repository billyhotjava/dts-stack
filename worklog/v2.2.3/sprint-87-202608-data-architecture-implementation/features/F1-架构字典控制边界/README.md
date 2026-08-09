# F1：架构字典控制边界

**优先级**：P0

**状态**：BLOCKED

## 目标

让六类平台架构字典通过唯一 application command boundary 写入，并以稳定 read port 被建模、资产、指标和质量消费。

## Task

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 收口 command/read boundary 与授权审计 | BLOCKED | F0/T01 |

## Feature DoD

- [ ] 兼容入口与新入口写同一 ledger/service，无 Controller/消费者直写 Repository。
- [ ] 方案 A 正向/负向授权、对象 guard 和审计全部通过。
