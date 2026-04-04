# T01: Keycloak User Profile 配置新 attributes

**优先级**: P1
**状态**: READY
**依赖**: 无

## 目标
在 Keycloak User Profile 中注册 PersonProfile 迁移需要的新 attributes。

## 技术设计

### 当前已有的 Keycloak User Profile attributes

| Attribute | Display name |
|-----------|-------------|
| username | ${username} |
| fullName | ${profile.attributes.fullName} |
| email | ${email} |
| phone | ${profile.attributes.phone} |
| person_security_level | ${profile.attributes.person_security_level} |
| dept_code | ${profile.attributes.dept_code} |
| allowed_ip | ${profile.attributes.allowed_ip} |

### 需要新增的 attributes

| Attribute | Display name | Validation |
|-----------|-------------|------------|
| person_code | 人员编码 | 长度 <= 64 |
| national_id | 身份证号 | 长度 <= 64，需加密存储 |
| dept_name | 部门名称 | 长度 <= 256 |
| title | 职务 | 长度 <= 128 |
| grade | 职级 | 长度 <= 64 |

### 安全考虑

- `national_id` 是敏感数据，Keycloak attributes 不加密
- **方案 A**: 不迁入 national_id，保留在本地加密表
- **方案 B**: 迁入但使用 Keycloak 的字段级权限控制（仅 admin 可读）
- **推荐方案 A**，national_id 保留在 `person_sensitive_data` 表（新建）

### 配置方式

通过 Keycloak Admin API 或 Realm Export/Import 配置 User Profile：

```json
{
  "attributes": [
    {
      "name": "person_code",
      "displayName": "人员编码",
      "permissions": { "edit": ["admin"], "view": ["admin", "user"] }
    }
  ]
}
```

## 验证
- [ ] Keycloak 管理控制台可见新 attributes
- [ ] 通过 Admin API 可设置和读取新 attributes
- [ ] 用户 token 中不包含 national_id（安全验证）

## 完成标准
- [ ] 5 个新 attributes 注册完成
- [ ] national_id 处理方案确定并实施
