# T03: 统一 resolveFullName 优先级

**优先级**: P0
**状态**: READY
**依赖**: T02

## 目标
消除 4 处不同的 displayName 解析逻辑，统一为一套优先级规则。

## 技术设计

### 统一优先级

```
attributes.fullName > dto.fullName > username
```

不再检查 `fullname`/`display_name`/`displayName` 等变体，不再拼接 firstName+lastName。

### 需要修改的 4 处实现

#### 1. AdminUserService.resolveFullName() (行 2296-2314)

**当前**: 4 种 attribute 别名 + buildName(first,last) + dto.fullName + username
**改为**:
```java
private String resolveFullName(KeycloakUserDTO dto) {
    if (dto == null) return "";
    String attrName = extractSingle(dto, "fullName");
    return StringUtils.firstNonBlank(
        StringUtils.trimToNull(attrName),
        StringUtils.trimToNull(dto.getFullName()),
        dto.getUsername()
    );
}
```

同时删除 `buildName()` 方法（不再需要）。

#### 2. PlatformDirectoryResource.toSummary() (行 204-224)

**当前**: fullName > attributes[4种] > firstName+lastName > username
**改为**:
```java
String displayName = firstNonBlank(
    user.getFullName(),
    firstAttribute(user.getAttributes(), "fullName"),
    username
);
```

#### 3. AdminDirectoryGateway.toSummary() (行 280-288)

**当前**: fullName > firstName+lastName > username
**改为**:
```java
String displayName = firstNonBlank(
    user.getFullName(),
    firstAttribute(user.getAttributes(), "fullName"),
    username
);
```

#### 4. KeycloakAuthResource.resolveUserDisplayName() (行 745-756)

**当前**: fullName > displayName > nickname > name > username
**改为**:
```java
private String resolveUserDisplayName(Map<String, Object> user) {
    return firstNonBlank(
        stringValue(user.get("fullName")),
        stringValue(user.get("name")),
        stringValue(user.get("username"))
    );
}
```

去掉 `displayName`/`nickname`（admin 不会返回这些字段，是 platform 自己写入的冗余）。

## 影响范围

| 文件 | 模块 | 改动 |
|------|------|------|
| `dts-admin/.../AdminUserService.java` | admin | 简化 resolveFullName + 删除 buildName |
| `dts-admin/.../PlatformDirectoryResource.java` | admin | 简化 toSummary |
| `dts-platform/.../AdminDirectoryGateway.java` | platform | 对齐 toSummary |
| `dts-platform/.../KeycloakAuthResource.java` | platform | 简化 resolveUserDisplayName |

## 验证
- [ ] 4 处解析逻辑对同一用户返回相同的 displayName
- [ ] 用户 attributes.fullName 为空时正确回退到 username
- [ ] 不再出现 firstName 被当作 fullName 显示的情况

## 完成标准
- [ ] 全链路统一为 `attributes.fullName > dto.fullName > username`
- [ ] buildName(firstName, lastName) 方法删除
- [ ] attribute 别名只保留 "fullName" 一个
