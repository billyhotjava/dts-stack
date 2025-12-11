# Superset 部署说明（草案）

- 镜像：由 `IMAGE_SUPERSET` 决定（默认 `apache/superset:2.1.3`），可替换为自构建镜像。
- 元数据库：PostgreSQL（推荐），环境变量 `SUPERSET_DATABASE_URI`，示例 `postgresql+psycopg2://superset:pass@dts-pg:5432/superset`.
- 认证：已启用 OIDC + 自定义 Security Manager（见 `home/superset_ext/security.py`），提取 Keycloak claims：`preferred_username`、`roles`（过滤掉 `offline_access/uma_authorization/default-roles-*`）、`dept_code`、`person_security_level`。业务角色映射：EMPLOYEE→Gamma，SYSADMIN/AUTHADMIN→Admin，AUDITADMIN/OPADMIN→Alpha，默认回退 Gamma。
- SQL Lab：默认关闭 DML（ALLOW_DML=False），限制危险命令。
- 日志：挂载到宿主机，采用 100MB 轮转（logrotate 或内置处理）。
- 驱动：DM JDBC、Inceptor/Hive JDBC 等统一放在 `services/dts-superset/home/drivers/` 并在 config 中加入 `JAVA_HOME`/`CLASSPATH` 或 provider extra。
- 证书：HTTPS/反代证书与信任链放在 `home/certs/`.
- 导出：仪表盘/数据集导入导出目录 `home/exports/`.
- OIDC 配置：复制 `home/client_secrets.example.json` 为 `client_secrets.json`，填入真实 client_id/secret 与回调地址；`superset_config.py` 默认从 `/app/pythonpath/client_secrets.json` 读取。

## RLS 使用示例
- 数据集行过滤中可引用 `current_user.extra_attributes`：
  - 按部门：`dept_code = '{{ current_user.extra_attributes.get("dept_code") }}'`
  - 按密级：`person_security_level >= '{{ current_user.extra_attributes.get("person_security_level") }}'`
- 建议在表或视图中提供数字化或有序枚举的密级字段，以便比较（如映射 GENERAL<IMPORTANT<CORE）。
- 角色过滤：仅保留业务角色，默认回退 Gamma 以保证最小权限。
