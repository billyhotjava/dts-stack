# dts-analytics（Java 重写）部署说明（草案）

本目录用于承载 **dts-analytics Java 服务** 的运行时挂载（plugins/data/logs/certs）与镜像构建（`Dockerfile`）。

- 镜像：`services/dts-analytics/Dockerfile`（默认监听 **3000**，便于复用原 Traefik/Compose 约定）。
- UI：由独立模块 `source/dts-analytics-webapp` 提供（前后端只通过 HTTP API 交互；后端不再内嵌/渲染任何 UI 静态资源）。
- 元数据库：将使用 PostgreSQL（后续会用 Flyway/Liquibase 建表，不保留历史数据）。
- 认证：最终走 Keycloak/OIDC/会话（当前仍是开发阶段占位实现）。
- 插件/驱动：后续将复用 `services/dts-analytics/plugins/` 目录作为 JDBC 驱动/插件加载入口。
- 日志/证书：按现有目录约定挂载（`logs/`、`../certs`）。

## RLS / 数据权限示例
- OIDC claims：`roles`（过滤掉 `offline_access`、`uma_authorization`、`default-roles-*`）、`dept_code`、`person_security_level`。
- 组映射：在 Metabase 管理后台将业务角色（如 EMPLOYEE）映射到 Metabase 组，组权限矩阵按需配置；忽略默认/系统角色。
- 用户属性：在 “Admin → People → Attributes” 中新增 `dept_code` 和 `person_security_level`，并绑定到相应的 OIDC claim，以便在数据分段/字段权限/RLS 参数中使用。
- 查询/段过滤：在自定义段或字段权限条件中引用用户属性，例如 `{{user.attributes.dept_code}}` 或使用参数化查询把密级比较逻辑下推到数据库视图/SQL。
- DM 数据源：将 DM JDBC 驱动（如 `DmJdbcDriver18.jar`）放入 `services/dts-analytics/plugins/`，URL 示例 `jdbc:dm://HOST:PORT/DB`，驱动类 `dm.jdbc.driver.DmDriver`。为 Inceptor/Hive 同理放置对应 JDBC。
