# Metabase 部署说明（草案）

- 镜像：由 `IMAGE_METABASE` 决定（默认 `metabase/metabase:v0.49.15`），可替换自构建镜像。
- 元数据库：PostgreSQL，环境变量 `MB_DB_CONNECTION_URI=postgres://metabase:pass@dts-pg:5432/metabase`.
- 认证：禁用匿名与自助注册，开启 OIDC/SAML（Keycloak），在 env 中配置 `MB_OIDC_*`。组/角色与 dept_code/person_security_level 可通过 claim 映射到 Metabase 组。
- 插件/驱动：DM JDBC 驱动、Inceptor/Hive 驱动放在 `services/dts-metabase/plugins/`；容器用 `MB_PLUGINS_DIR=/plugins`.
- 日志：挂载到宿主机，按 100MB 轮转（logrotate）。
- HTTPS/证书：反代层处理，证书与信任链统一放在 `services/certs/`，通过 compose 挂载到容器 `/certs`（如需客户端校验）。

## RLS / 数据权限示例
- OIDC claims：`roles`（过滤掉 `offline_access`、`uma_authorization`、`default-roles-*`）、`dept_code`、`person_security_level`。
- 组映射：在 Metabase 管理后台将业务角色（如 EMPLOYEE）映射到 Metabase 组，组权限矩阵按需配置；忽略默认/系统角色。
- 用户属性：在 “Admin → People → Attributes” 中新增 `dept_code` 和 `person_security_level`，并绑定到相应的 OIDC claim，以便在数据分段/字段权限/RLS 参数中使用。
- 查询/段过滤：在自定义段或字段权限条件中引用用户属性，例如 `{{user.attributes.dept_code}}` 或使用参数化查询把密级比较逻辑下推到数据库视图/SQL。
- DM 数据源：将 DM JDBC 驱动（如 `DmJdbcDriver18.jar`）放入 `services/dts-metabase/plugins/`，URL 示例 `jdbc:dm://HOST:PORT/DB`，驱动类 `dm.jdbc.driver.DmDriver`。为 Inceptor/Hive 同理放置对应 JDBC。
