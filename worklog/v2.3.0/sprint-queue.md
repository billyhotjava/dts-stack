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
