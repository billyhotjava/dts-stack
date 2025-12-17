# Superset 部署说明（草案）

- 镜像：由 `IMAGE_SUPERSET` 决定（默认 `apache/superset:2.1.3`），可替换为自构建镜像。
- 元数据库：PostgreSQL（推荐），环境变量 `SUPERSET_DATABASE_URI`，示例 `postgresql+psycopg2://superset:pass@dts-pg:5432/superset`.
- 访问入口：生产环境通过 Traefik 同域名路径挂载为 `https://bi.xx.com/dashboards`（对外不暴露引擎名称）；Traefik 会注入 `X-Forwarded-Prefix=/dashboards`，Superset 侧已启用中间件兼容该前缀（见 `home/superset_config.py`）。
- 认证：已启用 OIDC + 自定义 Security Manager（见 `home/superset_ext/security.py`），提取 Keycloak claims：`preferred_username`、`roles`（过滤掉 `offline_access/uma_authorization/default-roles-*`）、`dept_code`、`person_security_level`（0/1/2）。会额外计算并落库：
  - `scope_all_dept`：所级角色/管理员等为 `true`（可看全所），否则按 `dept_code` 过滤
  - `max_data_security_level`：用于数据密级过滤（一般用户≤2：公开/内部/秘密；重要/核心用户≤3：可到机密）
- SQL Lab：默认关闭 DML（ALLOW_DML=False），限制危险命令。
- 日志：挂载到宿主机，采用 100MB 轮转（logrotate 或内置处理）。
- 驱动：DM JDBC、Inceptor/Hive JDBC 等统一放在 `services/dts-superset/home/drivers/` 并在 config 中加入 `JAVA_HOME`/`CLASSPATH` 或 provider extra。
- 证书：HTTPS/反代证书与信任链统一放在 `services/certs/`，通过 compose 挂载到 `/app/superset_home/certs`.
- 导出：仪表盘/数据集导入导出目录 `home/exports/`.
- OIDC 配置：复制 `home/client_secrets.example.json` 为 `client_secrets.json`，填入真实 client_id/secret 与回调地址；`superset_config.py` 默认从 `/app/pythonpath/client_secrets.json` 读取。

## RLS 使用示例
- 数据集行过滤中可引用 `current_user.extra_attributes`：
  - 按部门（所级用户看全所）：
    - `{% if current_user.extra_attributes.get("scope_all_dept") %} 1=1 {% else %} dept_code = '{{ current_user.extra_attributes.get("dept_code") }}' {% endif %}`
  - 按密级（建议落数为数值编码便于比较）：`data_security_level <= {{ current_user.extra_attributes.get("max_data_security_level") }}`
- 建议在 DM 表/视图中落字段：
  - `dept_code`（部门编码）
  - `data_security_level`（建议数值：0=公开,1=内部,2=秘密,3=机密）
- 角色过滤：仅保留业务角色，默认回退 Gamma 以保证最小权限。
