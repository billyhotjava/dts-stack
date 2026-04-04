# T01: 人员导入直写 Keycloak

**优先级**: P0
**状态**: READY
**依赖**: v2.2.3 F1 (displayName bug 修复完成后)

## 目标
PersonnelImportService 不再写 PersonProfile 表后同步到 Keycloak，改为直接通过 Keycloak Admin API 创建/更新用户。

## 技术设计

### 当前流程
```
PersonnelPayload → PersonProfile (upsert) → provisionKeycloakUser() → AdminKeycloakUser (upsert)
```

### 目标流程
```
PersonnelPayload → Keycloak Admin API (create/update user)
                  → person_import_record (仅记录导入溯源)
                  → 触发 kc_user_cache 刷新 (Phase 2)
```

### 改动点

1. **PersonnelImportService.processPayload()**: 
   - 删除 `PersonProfile` 的 upsert 逻辑
   - 保留 `person_import_record` 的记录
   - `provisionKeycloakUser()` 改为主流程（不再是副作用）

2. **provisionKeycloakUser()** 重构:
   - 只设置 `dto.setFullName()` 和 `dto.setAttributes()`
   - 不再设置 `dto.setFirstName()` (已在 F1/T01 修复)
   - 增加 Keycloak attributes: `personCode`, `nationalId`, `title`, `grade` 等原 PersonProfile 字段

3. **批量导入优化**:
   - 当前逐条操作 Keycloak API，大批量时性能差
   - 引入批量操作 + 异步队列（可选，视导入量决定）

## 影响范围

| 文件 | 改动 |
|------|------|
| `dts-admin/.../PersonnelImportService.java` | 主流程重构 |
| `dts-admin/.../PersonnelPayloadMapper.java` | 增加 Keycloak attributes 映射 |
| `dts-admin/.../KeycloakAdminRestClient.java` | 确认 attributes 写入正确 |

## 验证
- [ ] Excel 导入人员后 Keycloak 中用户创建成功，attributes 完整
- [ ] API 导入人员后 Keycloak 中用户更新成功
- [ ] PersonProfile 表不再有新数据写入
- [ ] person_import_record 仍正常记录导入批次

## 完成标准
- [ ] PersonProfile 写入逻辑移除
- [ ] 导入直接操作 Keycloak
- [ ] 导入溯源记录保留
