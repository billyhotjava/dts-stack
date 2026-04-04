# T02: 清理 AdminKeycloakUser 相关代码

**优先级**: P2
**状态**: READY
**依赖**: F2

## 目标
删除 AdminKeycloakUser 实体及其所有相关代码。

## 技术设计

### 需要删除的文件

| 文件 | 说明 |
|------|------|
| `AdminKeycloakUser.java` | JPA 实体 |
| `AdminKeycloakUserRepository.java` | Repository |
| `AdminUserService` 中 snapshot 相关方法 | syncSnapshot, refreshSnapshotsFromProfiles, findSnapshotByUsername 等 |
| `AdminUserVM.java` | 如果只服务于快照展示 |

### 需要修改的文件

| 文件 | 改动 |
|------|------|
| `KeycloakApiResource.java` 中用户启用/禁用逻辑 | 改为直接操作 Keycloak enabled 标志 |
| `PlatformDirectoryResource.fallbackFromSnapshots()` | 已在 F3 删除 |
| 审计相关代码中 `resolveDisplayNames()` | 已在 F2/T04 改为查缓存 |

### mdmEnabled 字段处理

`AdminKeycloakUser.mdmEnabled` 是业务审批概念（MDM 导入后是否启用）。迁移方案：
- Keycloak user `enabled` 字段 = `mdmEnabled != 0`
- 如果需要区分"Keycloak 启用"和"业务审批启用"，增加 Keycloak attribute `mdm_enabled`

## 验证
- [ ] 编译通过，无 AdminKeycloakUser 引用
- [ ] 用户启用/禁用功能正常
- [ ] 审计日志中 displayName 正确

## 完成标准
- [ ] AdminKeycloakUser 实体和 Repository 删除
- [ ] 所有 snapshot 相关方法删除
- [ ] mdmEnabled 逻辑迁移到 Keycloak
