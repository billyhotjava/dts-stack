# Sprint-1: IAM 重构 -- 取消本地用户/角色/部门管理，统一 Keycloak

**时间**: 2026-04
**状态**: READY
**目标**: 消除 dts-admin 与 Keycloak 之间的双向同步架构，以 Keycloak 为唯一身份源，本地仅保留缓存和业务权限（ABAC），解决同步性能瓶颈和数据不一致问题。

## 背景

当前 dts-admin 维护了 6 张本地表（AdminKeycloakUser、PersonProfile、OrganizationNode、AdminCustomRole、AdminRoleAssignment、AdminRoleMember）与 Keycloak 双向同步。存在以下问题：

1. **同步性能瓶颈**: 用户/组织数量增长时全量同步导致超时，之前组织架构同步已出现过生产问题
2. **数据不一致**: PersonProfile、AdminKeycloakUser、Keycloak 三者之间的数据经常不同步
3. **架构冗余**: 6 张表中大部分数据是 Keycloak 的冗余副本

**前置条件**: v2.2.3 已修复 displayName 全链路 bug（F1）

**架构决策**: Keycloak 作为身份+组织的 source of truth，admin 改为只读查询 + TTL 缓存，业务级 ABAC 权限（scopeOrgId、datasetIds）仍保留在本地。

**约束**:
- dts-platform 消费方 API 接口签名不变（UserSummary、RoleSummary）
- 支持 Keycloak 不可用时降级到缓存
- 离线/气隙环境下 Keycloak 和 admin 同机部署，网络延迟可忽略
- 分阶段实施，每个 Phase 可独立上线

## Feature 列表

| ID | Feature | Task 数 | 状态 | 优先级 | Phase |
|----|---------|---------|------|--------|-------|
| F1 | 消除双向同步 | 4 | READY | P0 | 1 |
| F2 | 缓存层替代快照表 | 3 | READY | P1 | 2 |
| F3 | PersonProfile 迁入 Keycloak | 3 | READY | P1 | 3 |
| F4 | OrganizationNode 迁入 Groups | 3 | READY | P1 | 4 |
| F5 | 清理废弃代码和表 | 3 | READY | P2 | 5 |

## 数据归属矩阵

| 数据 | 当前位置 | 目标位置 | 说明 |
|------|---------|---------|------|
| 用户身份 (username/fullName/email/phone) | AdminKeycloakUser + PersonProfile + Keycloak | Keycloak attributes | 本地改为缓存 |
| 组织树 | OrganizationNode + Keycloak groups | Keycloak groups | 本地改为缓存 |
| 人员密级 (personSecurityLevel) | AdminKeycloakUser + Keycloak attribute | Keycloak attribute | 本地缓存 |
| 组织密级 (dataLevel) | OrganizationNode | Keycloak group attribute | 本地缓存 |
| 业务角色定义 (AdminCustomRole) | 本地 DB | **保留本地** | Keycloak 角色不支持 scope/ops |
| 业务角色分配 (AdminRoleAssignment) | 本地 DB | **保留本地** | ABAC 模型超出 Keycloak RBAC |
| 导入溯源 (import_batch/record) | 本地 DB | **保留本地** | 纯业务数据 |

## 完成标准
- [ ] Keycloak 为唯一用户/组织写入源，admin 不再回写 Keycloak 用户身份
- [ ] AdminKeycloakUser/PersonProfile/OrganizationNode 表废弃，被缓存表替代
- [ ] dts-platform 消费方 API 无感知变化（UserSummary/RoleSummary 签名不变）
- [ ] Keycloak 不可用时降级到缓存，缓存 TTL 可配置
- [ ] 每个 Phase 有独立的升级迁移脚本和回滚方案
