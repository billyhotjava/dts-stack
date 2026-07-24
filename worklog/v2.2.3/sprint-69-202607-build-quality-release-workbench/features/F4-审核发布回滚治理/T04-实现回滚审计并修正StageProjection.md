# T04：实现回滚审计并修正 StageProjection

**优先级**：P0
**状态**：READY
**依赖**：T03

## 目标

提供可审计回滚，并让九站 StageProjection 第六步只依据当前有效发布事实完成。

## 技术设计

- 回滚创建 ROLLBACK_STARTED/STEP_RESULT/ROLLED_BACK 事件，不删除 publication。
- 回滚锁定目标发布、原因、操作人、影响步骤和恢复策略。
- 第六步完成策略读取计划当前有效 PUBLISHED 候选；PARTIAL、STALE、READY、ROLLED_BACK 均不满足。
- 回滚后允许创建新候选或重发，不复用旧批准。

## 影响范围

- `ModelLifecyclePublicationService.java`
- `WarehousePlanDownstreamEvidenceAdapter.java`
- `WarehousePlanStageProjectionService.java`
- rollback/projection tests

## 实施步骤

1. 先写 READY 被误判、PARTIAL、rollback 后降级和重发测试。
2. 实现回滚协调与发布事实投影。
3. 编写 lifecycle、warehouse stage projection 测试源码并完成静态审查；运行验证留到 F6。

## 完成标准

- [ ] 页面刷新后 Stage 6 状态与真实发布/回滚事件一致。
- [ ] 回滚历史完整，失败回滚进入显式状态并可恢复。
- [ ] **UI 契约验收**：Stage 6、发布时间线和回滚对话框覆盖 published、rollback running、rolled back、rollback failed；回滚后刷新必须立即降级。
