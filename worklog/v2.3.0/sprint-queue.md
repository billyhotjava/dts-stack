# v2.3.0 Sprint Queue

**前置条件**: v2.2.3 已完成 displayName 链路 bug 修复

## Sprint-1: IAM 重构 -- 取消本地用户/角色/部门管理，统一 Keycloak (202604)

| Feature | Task 数 | 状态 | Phase |
|---------|---------|------|-------|
| F1-消除双向同步 | 4 | READY | 1 |
| F2-缓存层替代快照表 | 3 | READY | 2 |
| F3-PersonProfile迁入Keycloak | 3 | READY | 3 |
| F4-OrganizationNode迁入Groups | 3 | READY | 4 |
| F5-清理废弃代码和表 | 3 | READY | 5 |

**统计**: READY=5, IN_PROGRESS=0, DONE=0, BLOCKED=0

**总 Task 数**: 16
**依赖链**: F1 → F2 → F3/F4(并行) → F5

## Sprint-2: 主数据与填报体系建设 (202605)

| Feature | Task 数 | 状态 | 设计阶段 | 优先级 | 依赖 |
|---------|---------|------|---------|--------|------|
| F0-消息事件基础设施 | 5 | READY | 骨架 | P0 | Sprint-1 |
| F1-审批引擎MVP | 6 | READY | 骨架 | P0 | F0 |
| F2-主数据管理MDM | 7 | READY | 骨架 | P0 | F1 |
| F3-轻量填报 | 6 | READY | 骨架 | P1 | F2 |
| F4-ODS范式化 | 7 | READY | 骨架 | P1 | F2 |

**统计**: READY=5, IN_PROGRESS=0, DONE=0, BLOCKED=0
**设计进度**: 骨架=5, 设计中=0, spec就绪=0, plan就绪=0, 完成=0

**总 Task 数**: 31
**前置条件**: Sprint-1（IAM 重构）完成
**依赖链**: Sprint-1 → F0 → F1 → F2 → {F3, F4 并行}

**架构决策**:
- dts-approval / dts-mdm / dts-intake 三个独立服务与 admin / platform 平级
- admin 只管人（账号/三员），不管业务；所有业务审批走 dts-approval
- Kafka（F0）作为业务事件总线 + 流批一体事件流源；outbox 模式保证强一致场景
- 每个 Feature 开工前按依赖顺序**逐个** brainstorming 产出独立 spec

**设计阶段含义**：骨架 → 设计中 → spec 就绪 → plan 就绪 → 编码中(IN_PROGRESS) → 完成(DONE)

**下一个 brainstorm 目标**: F0（当前骨架 → 设计中）
