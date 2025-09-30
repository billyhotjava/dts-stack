# DTS Stack Quick Guide (Traefik + Keycloak + MinIO + Trino + Nessie)

本版本已移除 Airflow，并取消 Keycloak 自动初始化；请在 Keycloak 控制台手动创建 Realm 与客户端。默认最小暴露端口，仅通过 Traefik 对外服务。

**快速初始化**
- 生成环境并启动核心栈（单机，内置 Postgres）
  - `./init.sh single 'Strong@2025!' dts.local`
- 作用：生成 `.env`、签发自签证书（单机）、启动基础服务（Traefik、Keycloak、MinIO、Trino、Nessie、Postgres）。
- 重要域名（写入 `.env`）
  - `HOST_SSO=sso.${BASE_DOMAIN}`
  - `HOST_MINIO=minio.${BASE_DOMAIN}`
  - `HOST_TRINO=trino.${BASE_DOMAIN}`
  - `HOST_NESSIE=nessie.${BASE_DOMAIN}`
  - `HOST_API=api.${BASE_DOMAIN}`
  - `HOST_ADMIN_UI=admin.${BASE_DOMAIN}`
  - `HOST_PLATFORM_UI=platform.${BASE_DOMAIN}`
- OIDC 客户端密钥：`OAUTH2_ADMIN_CLIENT_SECRET`、`OAUTH2_PLATFORM_CLIENT_SECRET` 默认等于统一密码 `${SECRET}`；如需调整，直接编辑 `.env`。

**Keycloak 手动配置（默认 Realm: S10）**
- 访问 `https://sso.${BASE_DOMAIN}`（默认管理员：`KC_ADMIN=admin`，密码为 `SECRET`）。
- 创建 Realm：与 `.env` 的 `KC_REALM` 一致（默认 `S10`）。
- 创建两个 Confidential 客户端，并开启 Direct Access Grants：
  - Admin: `OAUTH2_ADMIN_CLIENT_ID`（默认 `dts-admin`），密钥填入 `.env: OAUTH2_ADMIN_CLIENT_SECRET`
  - Platform: `OAUTH2_PLATFORM_CLIENT_ID`（默认 `dts-platform`），密钥填入 `.env: OAUTH2_PLATFORM_CLIENT_SECRET`
- OIDC 发行者（issuer）：`https://sso.${BASE_DOMAIN}/realms/${KC_REALM}`。

**开发模式（init.sh 之后）**
- 镜像构建联调
  - 启动：`./dev-up.sh --mode images`
  - 停止：`./dev-stop.sh --mode images`
  - 访问：
    - API（Traefik）：`https://api.${BASE_DOMAIN}/admin`、`https://api.${BASE_DOMAIN}/platform`
    - Web 前端（本机端口）：`http://localhost:18011`（admin UI）、`http://localhost:18012`（platform UI）
  - 说明：默认不构建 Web 前端镜像（避免上游 TS 构建校验失败）。如需强制构建，请去掉 `--no-webapp` 或设置 `WITH_WEBAPP=1`。
- 本地源码热更新
  - 启动：`./dev-up.sh --mode local`
  - 停止：`./dev-stop.sh --mode local`
  - 可选跳过 webapp 容器：添加 `--no-webapp`（前端自行 `pnpm dev`）
  - 访问：
    - API（Traefik）：`https://api.${BASE_DOMAIN}/admin`、`https://api.${BASE_DOMAIN}/platform`
    - Web 前端（本机端口）：`http://localhost:18011`（admin UI）、`http://localhost:18012`（platform UI）

**后端热更新（最佳实践）**
- 方案 A（推荐：在主机直接运行后端，性能最佳，自动重载更顺畅）
  1) 仅启动依赖（不含后端、前端容器）
     - `docker compose -f docker-compose.yml up -d dts-pg dts-proxy dts-keycloak dts-minio dts-nessie dts-trino`
  2) 编译共享模块（一次）
     - `cd source/dts-common && mvn -q -Pdev -DskipTests install`
  3) 启动 dts-admin（开启 devtools 自动重载）
     - `cd source/dts-admin`
     - `mvn -q -Pdev -DskipTests spring-boot:run \
        -Dspring-boot.run.jvmArguments="-Djavax.net.ssl.trustStore=$(pwd)/../../services/certs/truststore.jks -Djavax.net.ssl.trustStorePassword=changeit"`
     - 修改代码后，IDE 构建或执行 `mvn -q -Pdev -DskipTests compile` 会触发重启
  4) 启动 dts-platform（注意：该模块禁用了 devtools 自动重启，变更后需重启进程）
     - `cd source/dts-platform`
     - `mvn -q -Pdev -DskipTests spring-boot:run \
        -Dspring-boot.run.jvmArguments="-Djavax.net.ssl.trustStore=$(pwd)/../../services/certs/truststore.jks -Djavax.net.ssl.trustStorePassword=changeit"`
     - 修改代码后，按 Ctrl-C 停止，再重复上条命令重启（或用 IDE 运行配置）

- 方案 B（容器内运行后端，简单稳定）
  - 仅重启受影响服务：
    - `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --force-recreate dts-admin`
    - 或：`docker compose -f docker-compose.yml -f docker-compose.dev.yml restart dts-admin`
  - dts-platform 同理：将服务名替换为 `dts-platform`
  - 说明：容器方案使用绑定挂载，重建容器会重新编译并加载最新代码，较主机直跑稍慢，但无需本机安装 JDK/Maven。

**正式部署（最小暴露端口）**
- 使用预构建镜像（推荐，新的服务器无需源码/构建）
  1) 编辑 `imgversion.conf` 设置应用镜像标签（已默认使用 `:1.0.0` 标签，可按需改为你的仓库地址）：
     - `IMAGE_DTS_ADMIN=dts-admin:1.0.0`
     - `IMAGE_DTS_PLATFORM=dts-platform:1.0.0`
     - `IMAGE_DTS_ADMIN_WEBAPP=dts-admin-webapp:1.0.0`
     - `IMAGE_DTS_PLATFORM_WEBAPP=dts-platform-webapp:1.0.0`
  2) 运行 `./init.sh single 'Strong@2025!' dts.local`（会把镜像变量写入 `.env`）
  3) 启动（仅暴露 80/443，由 Traefik 统一转发）：
     - `docker compose -f docker-compose.yml -f docker-compose-app.yml up -d`
- 如需从源码构建（非推荐，需完整前端/后端源码且可能受 TS 校验影响）
  - `docker compose -f docker-compose.yml -f docker-compose-app.yml up -d --build`
- 访问入口
  - Admin UI：`https://admin.${BASE_DOMAIN}`
  - Platform UI：`https://platform.${BASE_DOMAIN}`
  - API：`https://api.${BASE_DOMAIN}/admin`、`https://api.${BASE_DOMAIN}/platform`
- Postgres 外部端口保留便于调试（如需收紧可后续调整 `docker-compose.yml`）。

**常用命令**
- 核心栈启动/停止：`./start.sh single`、`./stop.sh single --remove-orphans`
- 查看服务：`docker compose -f docker-compose.yml config --services`
- 查看状态与日志：`docker compose ps`、`docker compose logs -f dts-trino`
- 健康检查示例：`docker compose exec dts-trino wget -qO- http://localhost:8080/v1/info`

**提示与安全**
- 镜像版本集中在 `imgversion.conf`；修改后重跑 `./init.sh` 生效。
- 不提交真实密钥；`.env` 由 `init.sh` 生成，分享前请脱敏。
- 生产请使用 CA 签发证书替换 `services/certs/server.crt` / `server.key`，并使用真实 `BASE_DOMAIN`。
