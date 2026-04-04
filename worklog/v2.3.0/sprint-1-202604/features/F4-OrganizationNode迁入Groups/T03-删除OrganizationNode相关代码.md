# T03: 删除 OrganizationNode 相关代码

**优先级**: P1
**状态**: READY
**依赖**: T01, T02

## 目标
在数据迁移完成且 kc_org_cache 稳定后，删除 OrganizationNode 相关代码。

## 技术设计

### 需要删除/修改的文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `OrganizationNode.java` | 删除 | JPA 实体 |
| `OrganizationRepository.java` | 删除 | Repository |
| `OrganizationSyncService.java` | 重写 | 改为直接操作 Keycloak groups |
| `PlatformDirectoryResource` 中 deptCode→deptName 解析 | 修改 | 改查 kc_org_cache |
| `AdminDirectoryGateway` 中组织树查询 | 修改 | admin API 已改，无需改 |

### Liquibase

```xml
<changeSet id="YYYYMMDD_01_deprecate_organization_node">
    <sql>COMMENT ON TABLE organization_node IS 'DEPRECATED since v2.3.0 - data migrated to Keycloak groups'</sql>
</changeSet>
```

## 验证
- [ ] 编译通过，无 OrganizationNode 引用残留
- [ ] 组织树接口正常返回
- [ ] 组织同步功能正常
- [ ] deptCode → deptName 解析正常

## 完成标准
- [ ] OrganizationNode 实体和 Repository 删除
- [ ] 所有引用点清理
- [ ] organization_node 表标记废弃
