# T01: 修复 Keycloak 写入 -- 只写 attributes.fullName

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标
`toRepresentation()` 不再把 fullName 塞进 Keycloak 的 firstName 字段，统一只写 `attributes.fullName`。

## 技术设计

### 1. KeycloakAdminRestClient.toRepresentation() (行 698-731)

**当前代码**:
```java
if (dto.getFullName() != null && !dto.getFullName().isBlank()) {
    rep.put("firstName", dto.getFullName());  // BUG
} else {
    if (dto.getFirstName() != null) rep.put("firstName", dto.getFirstName());
    if (dto.getLastName() != null) rep.put("lastName", dto.getLastName());
}
```

**修改为**:
```java
// 不再写入 firstName/lastName，fullName 统一通过 attributes 传递
// Keycloak User Profile 已配置 fullName 为自定义属性，firstName/lastName 未启用
```

保留 `attributes.put("fullName", ...)` 的逻辑（已有，无需改）。

### 2. PersonnelImportService.provisionKeycloakUser() (行 304-384)

删除两处 `setFirstName(fullName)`:
- 行 325: `existing.setFirstName(payload.fullName());` → 删除
- 行 352: `dto.setFirstName(payload.fullName());` → 删除

### 3. 数据修正脚本

需要一个 Keycloak Admin API 脚本，批量清理现有用户的 firstName 字段（如果 firstName == attributes.fullName，则清空 firstName）。

## 影响范围

| 文件 | 行号 | 改动 |
|------|------|------|
| `dts-admin/.../KeycloakAdminRestClient.java` | 702-707 | 删除 firstName 写入逻辑 |
| `dts-admin/.../PersonnelImportService.java` | 325, 352 | 删除 setFirstName |

## 验证
- [ ] 创建新用户后 Keycloak 中 firstName 为空，attributes.fullName 有值
- [ ] 更新用户 fullName 后 Keycloak attributes.fullName 更新，firstName 不变
- [ ] 人员导入后 Keycloak 数据正确

## 完成标准
- [ ] toRepresentation() 不再写入 firstName
- [ ] PersonnelImportService 不再设置 firstName
- [ ] 数据修正脚本准备就绪
