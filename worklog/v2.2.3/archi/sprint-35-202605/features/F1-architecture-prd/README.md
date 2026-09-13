# F1: 整体架构与 PRD 契约

**优先级**: P0
**状态**: READY

## 目标

把 Sprint-32 的 React Flow 指标工作台重构为明确的 ELT 分层产品契约：默认从 DWS/ADS 可视化，DWD 只作为高级建模上游，platform 继续作为资产、权限、治理、审计和 dbt 发布事实源。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | Sprint-32 差距评审与继承边界 | P0 | READY | - |
| T02 | ELT 分层入口 PRD | P0 | READY | T01 |
| T03 | 数据流与状态机设计 | P0 | READY | T02 |
| T04 | DWD/DWS/ADS 准入规则 | P0 | READY | T02 |
| T05 | 架构 review gate 固化 | P0 | READY | T01-T04 |

## 完成标准

- [ ] 架构文档明确 DWD/DWS/ADS 的入口和限制。
- [ ] PRD 区分用户承诺与实现细节。
- [ ] 状态机覆盖 graph、contract、dbt、review、publish、consume。
- [ ] 架构评审清单成为后续 feature 的准入门。
