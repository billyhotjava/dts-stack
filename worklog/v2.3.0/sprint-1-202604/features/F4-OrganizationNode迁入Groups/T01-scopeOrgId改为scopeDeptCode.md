# T01: scopeOrgId 改为 scopeDeptCode

**优先级**: P1
**状态**: READY
**依赖**: F2

## 目标
AdminRoleAssignment.scopeOrgId (Long, 引用 organization_node.id) 改为 scopeDeptCode (String, 引用 Keycloak group 的 dept_code attribute)，解除对本地 OrganizationNode 表的外键依赖。

## 技术设计

### 实体改动

```java
// 旧
@Column(name = "scope_org_id")
private Long scopeOrgId; // null => institute scope

// 新
@Column(name = "scope_dept_code", length = 128)
private String scopeDeptCode; // null => institute scope, 非空 => 部门级
```

### 数据迁移

```sql
-- 先添加新列
ALTER TABLE admin_role_assignment ADD COLUMN scope_dept_code VARCHAR(128);

-- 迁移数据：scopeOrgId → dept_code
UPDATE admin_role_assignment a
SET scope_dept_code = (
    SELECT o.dept_code FROM organization_node o WHERE o.id = a.scope_org_id
)
WHERE a.scope_org_id IS NOT NULL;

-- 验证后删除旧列
ALTER TABLE admin_role_assignment DROP COLUMN scope_org_id;
```

### 影响的业务逻辑

所有通过 `scopeOrgId` 查 OrganizationNode 再获取 deptCode 的地方，改为直接使用 `scopeDeptCode`。

## 影响范围

| 文件 | 改动 |
|------|------|
| `AdminRoleAssignment.java` | 字段改名 |
| `AdminRoleAssignmentRepository.java` | 查询方法适配 |
| 使用 scopeOrgId 的 Service 层 | 改为 scopeDeptCode |
| Liquibase changelog | 迁移脚本 |

## 验证
- [ ] 现有角色分配数据迁移后 scopeDeptCode 正确
- [ ] scopeOrgId 为 null 的记录（院级）迁移后 scopeDeptCode 也为 null
- [ ] 权限校验逻辑使用 scopeDeptCode 工作正常

## 完成标准
- [ ] scopeOrgId 列删除
- [ ] scopeDeptCode 列添加并迁移
- [ ] 所有引用点更新
