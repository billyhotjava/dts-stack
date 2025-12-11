# BI RLS 指南（Superset & Metabase）

## 共性
- Token claims：`dept_code`、`person_security_level`、业务 `roles`（过滤掉 `offline_access`、`uma_authorization`、`default-roles-*`）。
- DM/Inceptor/Hive 等 JDBC 驱动需放入对应挂载目录：Superset `services/dts-superset/home/drivers/`，Metabase `services/dts-metabase/plugins/`.
- 建议在底层表/视图中提供有序的密级字段，方便做 >= 比较。

## Superset
- 已启用自定义 Security Manager（`services/dts-superset/home/superset_ext/security.py`），把 `dept_code`、`person_security_level` 写入 `current_user.extra_attributes`。
- 行过滤示例（数据集级 RLS）：
  - 部门：`dept_code = '{{ current_user.extra_attributes.get("dept_code") }}'`
  - 密级：`person_security_level >= '{{ current_user.extra_attributes.get("person_security_level") }}'`
- 角色映射（默认）：EMPLOYEE→Gamma，SYSADMIN/AUTHADMIN→Admin，AUDITADMIN/OPADMIN→Alpha。

## Metabase
- 在 Admin → People → Attributes 创建 `dept_code`、`person_security_level`，绑定到 OIDC claims。
- 在 Admin → Authentication 把业务角色映射到 Metabase 组（忽略默认/系统角色）。
- 在 Segment/数据权限中引用用户属性：
  - `{{user.attributes.dept_code}}`
  - `{{user.attributes.person_security_level}}`
- DM 连接：`jdbc:dm://HOST:PORT/DB`，驱动类 `dm.jdbc.driver.DmDriver`，驱动 JAR 放 `services/dts-metabase/plugins/`.
