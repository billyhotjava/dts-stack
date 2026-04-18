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
| national_id | 身份证号 | 长度 <= 64 |
| dept_name | 部门名称 | 长度 <= 256 |
| title | 职务 | 长度 <= 128 |
| grade | 职级 | 长度 <= 64 |

### national_id 处理

按**普通 attribute** 处理，不做加密、不做字段级权限特殊化。依据：
1. 本系统部署在内网/气隙环境，无强制合规加密要求
2. 现状 `person_profile.national_id`、`person_import_record.national_id` 本地即明文，迁入 Keycloak 不构成新增风险
3. 业务上 `person_profile.national_id` 基本不使用（下游无回读场景）

**不建** `person_sensitive_data` 表；`person_import_record.national_id` 仍保留（导入溯源，明文）。

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
- [ ] 用户 token 中不包含 national_id（默认 claim 白名单不含该 attribute）

## 完成标准
- [ ] 5 个新 attributes 注册完成

## Sprint-2 追加要求（person_security_level 规范化）

Sprint-2 的 MDM（F2）和审批引擎（F1-lite）依赖 `person_security_level` 有**稳定可解析的取值规范**：

- 取值域：**数字码 `0 / 1 / 2`**（当前存量格式）或 **语义码 `GENERAL / IMPORTANT / CORE`**，二选一即可
- **强烈建议**：迁移时统一采用 `GENERAL / IMPORTANT / CORE`，对齐 `dts-common.SecurityLevelCatalog.PersonnelSecurityLevel.code()`；老数据按 `SecurityLevelCatalog.PersonnelSecurityLevel.parse()` 归一化后再写入 Keycloak
- Keycloak User Profile 校验：`pattern` 限定在上述枚举值
- 若因存量迁移成本保留数字码，MDM 侧由 `parseMaxDataLevel` 宽松兼容，但前端/API 展示统一用语义码（通过 `labelZh()` 查 label）

> **背景**：Sprint-2 F2 MDM `/mdm/v1/persons/{username}` 对外返回的 `personnelSecurityLevel` 字段需要来自此 attribute，所有下游都会消费，不能再漂移。
