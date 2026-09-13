# T02: 修复 Keycloak 读取 -- 从 attributes 取 fullName

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
`toUserDto()` 从 `attributes.fullName` 读取显示名称，不再依赖 firstName+lastName 拼接。

## 技术设计

### KeycloakAdminRestClient.toUserDto() (行 758-785)

**当前代码**:
```java
dto.setFullName(stringValue(map.get("fullName"))); // 顶级字段，永远为 null
if (fullName 为空 && (first != null || last != null)) {
    dto.setFullName(first + " " + last); // 从 firstName 拼
}
```

**修改为**:
```java
// 优先从 attributes 读取 fullName
String attrFullName = extractAttributeValue(attributes, "fullName");
if (StringUtils.hasText(attrFullName)) {
    dto.setFullName(attrFullName.trim());
} else {
    // 兼容旧数据：尝试 firstName（可能存储了 fullName）
    String first = stringValue(map.get("firstName"));
    String last = stringValue(map.get("lastName"));
    String combined = combineName(first, last);
    if (StringUtils.hasText(combined)) {
        dto.setFullName(combined);
    }
}
```

### KeycloakAuthService.buildUserProfile() (行 319-375)

**当前代码** (行 332-341):
```java
String fullName = firstNonBlank(
    claims.get("fullname"), claims.get("fullName"),
    user.get("fullname"), user.get("fullName"),
    user.get("name"), claims.get("name"),
    givenName, username
);
```

**修改为**:
```java
String fullName = firstNonBlank(
    claims.get("fullName"),     // Keycloak Token Mapper (如果配了)
    user.get("fullName"),       // userinfo 中的 attribute
    user.get("name"),           // OIDC standard name claim
    username                    // 最终 fallback
);
```

去掉 `fullname`（小写 n）变体，去掉 `givenName` fallback（givenName 本身可能是错的）。

## 影响范围

| 文件 | 行号 | 改动 |
|------|------|------|
| `dts-admin/.../KeycloakAdminRestClient.java` | 758-785 | 改 fullName 读取逻辑 |
| `dts-admin/.../KeycloakAuthService.java` | 332-341 | 简化 fullName 优先级 |

## 验证
- [ ] 用户登录后 `loginResult.user().get("fullName")` 返回正确的中文姓名
- [ ] Keycloak Admin API 查询用户后 `dto.getFullName()` 返回正确值
- [ ] attributes.fullName 有值时，不再走 firstName+lastName 拼接逻辑

## 完成标准
- [ ] toUserDto() 从 attributes 读取 fullName
- [ ] buildUserProfile() 优先级简化为 4 级
