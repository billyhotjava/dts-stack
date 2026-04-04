# F4: OrganizationNode 迁入 Keycloak Groups (Phase 4)

**优先级**: P1
**状态**: READY

## 目标
OrganizationNode 表的所有查询改为走 Keycloak Groups（通过 kc_org_cache），OrganizationNode 表废弃。

## 当前 OrganizationNode 使用点

| 使用场景 | 查询方式 | 迁移方案 |
|---------|---------|---------|
| 组织树展示 | findByParentIsNull → 递归 children | kc_org_cache 树查询 |
| deptCode → deptName 解析 | findFirstByDeptCodeIgnoreCase | kc_org_cache 查 dept_code |
| deptCode → 组织节点 | findById / findByDeptCode | kc_org_cache |
| keycloakGroupId 映射 | findByKeycloakGroupId | kc_org_cache (keycloak_group_id 是主键) |
| AdminRoleAssignment.scopeOrgId | 外键引用 organization_node.id | **需要改为引用 dept_code** |

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | scopeOrgId 改为 scopeDeptCode | P1 | READY | F3 |
| T02 | 数据迁移脚本（OrganizationNode → Keycloak Groups） | P1 | READY | F2/T02 |
| T03 | 删除 OrganizationNode 相关代码 | P1 | READY | T01, T02 |

## 完成标准
- [ ] OrganizationNode 表的所有读取点改为走 kc_org_cache
- [ ] AdminRoleAssignment.scopeOrgId 改为 scopeDeptCode
- [ ] OrganizationNode 表标记废弃
