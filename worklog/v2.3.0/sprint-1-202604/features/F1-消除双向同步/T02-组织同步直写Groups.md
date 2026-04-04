# T02: 组织同步直写 Keycloak Groups

**优先级**: P0
**状态**: READY
**依赖**: v2.2.3 F1

## 目标
组织架构导入/同步不再写 OrganizationNode 表后同步到 Keycloak groups，改为直接操作 Keycloak Group API。

## 技术设计

### 当前流程
```
MDM/API → OrganizationNode (upsert 树) → Keycloak groups (同步)
```

### 目标流程
```
MDM/API → Keycloak Group API (create/update/move groups)
         → org_cache 刷新 (Phase 2)
```

### Keycloak Group 设计

```json
{
  "name": "财务部",
  "attributes": {
    "dept_code": ["FIN-001"],
    "data_level": ["DATA_INTERNAL"],
    "sort_order": ["100"],
    "contact": ["张三"],
    "short_name": ["财务"],
    "org_code": ["ORG-FIN"],
    "status": ["ACTIVE"],
    "mdm_type": ["DEPARTMENT"]
  },
  "subGroups": [...]
}
```

### 关键挑战

1. **树结构操作**: Keycloak Group API 支持 parent-child，但移动节点需要先删后建
2. **批量操作**: Keycloak 没有批量 group API，大组织树需要逐个操作
3. **dept_code 唯一性**: Keycloak 不强制 group attribute 唯一，需要应用层校验

### 改动点

1. 新增 `KeycloakGroupSyncService`，封装 group CRUD 操作
2. `OrganizationSyncService` 改为调用 `KeycloakGroupSyncService`
3. 保留 `OrganizationNode` 表的读取（Phase 4 再彻底迁移）

## 影响范围

| 文件 | 改动 |
|------|------|
| `dts-admin/.../KeycloakAdminRestClient.java` | 确认 group CRUD API 完整 |
| `dts-admin/.../OrganizationSyncService.java` | 改为直写 Keycloak groups |
| 新增 `KeycloakGroupSyncService.java` | 封装 group 操作 |

## 验证
- [ ] MDM 组织同步后 Keycloak groups 树结构正确
- [ ] group attributes 包含 dept_code/data_level 等业务字段
- [ ] 组织节点移动（换父节点）后 Keycloak 反映正确
- [ ] 组织数量 > 100 时同步性能可接受（< 30s）

## 完成标准
- [ ] OrganizationNode 写入逻辑移除（读取保留到 Phase 4）
- [ ] 组织树直接操作 Keycloak groups
- [ ] 业务字段通过 group attributes 存储
