# F5: 安全、评审机制与 IT 准入

**优先级**: P0
**状态**: IN_PROGRESS

## 目标

把安全、审计、评审和验收从功能任务中抽出成独立准入门，确保 `dts-metrics` 的预览、生成、验证、发布、撤销和回滚都通过 platform 控制面，且所有高风险行为有证据。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | service-auth、RBAC 和权限边界 | P0 | READY | F2,F4 |
| T02 | RLS/masking policy 一致性 | P0 | READY | T01 |
| T03 | DSL/SQL 安全与注入防护 | P0 | DONE | F3,F4 |
| T04 | 审计、审批和 review event | P0 | READY | T01-T03 |
| T05 | IT admission 与回滚 runbook | P0 | READY | F1-F4 |

## 完成标准

- [ ] 预览、验证、发布阶段使用同一权限和策略口径。
- [x] 任意 SQL 默认入口被禁止。
- [ ] 所有关键动作有 audit event 和 review evidence。
- [ ] IT 证据覆盖成功、失败、降级和回滚。
