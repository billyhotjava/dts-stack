# RTS Big Data Stack (Traefik + Keycloak + MinIO + Trino + Doris OLAP + Airbyte 1.8 + dbt RPC + OpenMetadata + Temporal)

## 快速开始
```bash
# 首次初始化（内置 Postgres）
./init.sh single 'Strong@2025!'

# 或交互式执行
./init.sh
```

- 所有镜像版本集中在 `imgversion.conf`，修改后再跑 `init.sh` 即可生效。
- `init.sh` 会生成 `.env`、在单机模式下自签 TLS 证书，并启动对应 `docker-compose*.yml`。
- `start.sh` / `stop.sh`：常规启动与停止（默认读取 `init.sh` 记录的部署模式）。

### 快速开始（dts-source 开发镜像）
用于本仓库上游 `dts-source` 的 5 个服务（dts-admin、dts-platform、dts-admin-webapp、dts-platform-webapp、dts-public-api），相对路径本地构建镜像：

```bash
cd dts-stack
# 先完成主栈初始化（生成 .env 等依赖变量）
# ./init.sh single 'Strong@2025!'
# 生成开发用 env（含镜像标签与 PG 三元组）
./init.dts-source.sh   # 或直接使用 ./dev-up.sh

# 构建并启动（推荐）
./dev-up.sh

# 仅停止开发服务（保留其它栈服务运行）
./dev-stop.sh
```

服务与端口：
- dts-admin 18081→8081（dev profile，禁用 Eureka/Config）
- dts-platform 18082→8081（dev profile，禁用 Eureka/Config）
- dts-admin-webapp 18011→80（Nginx 静态托管）
- dts-platform-webapp 18012→80（Nginx 静态托管）
- dts-public-api 18090→8090（上游文档指向容器内 http://dts-admin:8081 与 http://dts-platform:8081）

### 本地源码联调（local-dev 模式）
使用源码挂载 + 前端 dev server 热更新：

```bash
# 启动 local-dev（依赖已初始化的 .env）
./local-up.sh

# 停止（仅关掉 dev 容器）
./local-stop.sh
```

访问地址：
- 前端（admin）：http://localhost:18011 → 容器内 dev server 3001
- 前端（platform）：http://localhost:18012 → 容器内 dev server 3001

说明：web-app 开发时容器内默认监听 3001，已在 docker-compose.local-dev.yml 中将宿主机端口映射为 18011/18012。若控制台中看到 “Local/Network: 3001” 提示，属容器内端口信息，直接使用宿主机 18011/18012 访问即可。

切换 dev/prod：通过 Compose 环境变量控制（无需改 start.sh/stop.sh）。
- 默认在 `.env.dts-source` 中写入：`DTS_PROFILE=dev`、`EUREKA_CLIENT_ENABLED=false`、`SPRING_CLOUD_CONFIG_ENABLED=false`。
- 切换为 prod 示例：
  ```bash
  # 方法一：编辑 .env.dts-source
  DTS_PROFILE=prod
  EUREKA_CLIENT_ENABLED=true
  SPRING_CLOUD_CONFIG_ENABLED=true
  # 方法二：临时覆盖
  DTS_PROFILE=prod EUREKA_CLIENT_ENABLED=true SPRING_CLOUD_CONFIG_ENABLED=true \
    docker compose --env-file .env.dts-source -f docker-compose.dts-source.yml up -d --build
  ```

## 目录
- `docker-compose.yml`：single（内置 Postgres）
- `docker-compose.ha2.yml`：外部 Postgres
- `docker-compose.cluster.yml`：外部 Postgres（可按需扩展）
- `docker-compose.dts-source.yml`：dts-source 5 服务（相对路径本地构建；依赖从 `docker-compose.yml` 获取）
- `imgversion.conf`：镜像版本集中管理
- `imgversion.dts-source.conf`：dts-source 5 服务的镜像标签（dev）
- `services/<service>/init`：每个镜像的初始化脚本（如 `services/dts-pg/init/10-init-users.sh`、`services/dts-minio-init/init/init.sh`）
- `services/<service>/data`：对应镜像的数据目录（如 `services/certs`、`services/dts-minio/data`）
- `services/dts-trino/init/catalog/doris.properties`：Trino 通过 MySQL 协议接入 Doris 的 Catalog
- `dts-nessie`：基于 Apache Nessie 的表版本服务，替换原先的 Hive Metastore
- `services/dts-ranger/`：Apache Ranger 管理端持久化目录
- `services/dts-dbt/`：dbt RPC 服务配置与示例项目
- `services/dts-doris/`：Doris FE / BE / Broker 的持久化目录与说明
- `init.sh`：一键初始化脚本
- `init.dts-source.sh`：dts-source 开发栈初始化（生成 `.env.dts-source`，可选直接启动）
- `start.sh` / `stop.sh`：启动、停止 docker compose 服务

## 重要变量
- 域名：`BASE_DOMAIN`（默认 `yst.local`），各子域在 `.env` 自动生成。
- 数据卷：`services/dts-pg/data`、`services/dts-minio/data`、`services/dts-om-es/data`、`services/dts-airbyte-server/data/workspace`、`services/dts-dbt/logs`、`services/dts-doris/fe/meta`、`services/dts-doris/be/storage` 等（已预置目录并放宽权限以跑通，建议后续按需收紧）。
- Nessie：`HOST_NESSIE`（默认 `nessie.${BASE_DOMAIN}`），通过 Traefik 暴露在 19120 端口的 REST API。
- dbt 相关变量：`HOST_DBT`（默认 `dbt.${BASE_DOMAIN}`）、`DBT_RPC_PORT`、`DBT_TRINO_*`（默认指向内置 Trino，可按需改成外部仓库）。
- Doris 相关变量：`HOST_DORIS`（默认 `doris.${BASE_DOMAIN}`）、`DORIS_HTTP_PORT`（默认 8030，用于 FE Web UI / REST）、`DORIS_MYSQL_PORT`（默认 9030，FE MySQL 协议入口）、`DORIS_FE_EDIT_LOG_PORT`（默认 9010）、`DORIS_FE_RPC_PORT`（默认 9020）、`DORIS_BE_WEB_PORT`（默认 8040）、`DORIS_BE_HEARTBEAT_PORT`（默认 9050）、`DORIS_BE_BRPC_PORT`（默认 9060）、`DORIS_BROKER_PORT`（默认 8000）。

## 模式说明
- `single`：包含 `dts-pg`，本地持久化。
- `ha2` / `cluster`：不包含 `dts-pg`，请在 `.env` 中设置 `PG_HOST` 指向外部 Postgres（`init.sh` 默认写入 `your-external-pg-host`，启动前请改为真实地址）。

## 常见问题
- Postgres 认证失败：确认 `.env` 的 `PG_*` 与 `services/dts-pg/init/10-init-users.sh` 中的逻辑一致；首次启动务必清空 `services/dts-pg/data`。
- Airbyte 1.8 起不再需要 webapp 容器，UI 与 API 由 server 暴露（本包已对齐）。`INTERNAL_API_HOST`/`WORKLOAD_API_HOST` 必须是容器内可达的绝对 URL，以 `/` 结尾。

## dbt RPC 服务
- 使用镜像 `ghcr.io/dbt-labs/dbt-trino:1.8.6`，随栈一起启动 `dbt rpc`，默认暴露在 `https://${HOST_DBT}`（Traefik 转发到 `${DBT_RPC_PORT}`）。
- `services/dts-dbt/project/` 内置一个最小化 dbt 项目，可直接扩展模型与宏。`profiles.yml` 默认读取 `DBT_TRINO_*` 环境变量并连接到内置 Trino。
- Airbyte/Airflow 可通过 HTTP `POST /jsonrpc` （容器内地址 `http://dts-dbt:${DBT_RPC_PORT}/jsonrpc`）触发 dbt task，适合作为 ELT 流水线的 Transform 步骤。

## Doris OLAP 数仓
- FE、BE、Broker 分别使用镜像 `apache/doris:2.1.7-fe-x86_64` / `apache/doris:2.1.7-be-x86_64` / `apache/doris:2.1.7-broker-x86_64`。
- FE 通过 Traefik 暴露 Web UI：`https://${HOST_DORIS}`（80**30** → 443），MySQL 协议在 `dts-doris-fe:${DORIS_MYSQL_PORT}`，供 Trino/Airbyte/外部工具接入。
- FE 元数据与日志持久化在 `services/dts-doris/fe/*`，BE 存储位于 `services/dts-doris/be/storage`。
## Java/Spring Boot 对接 Keycloak（TLS 简化）
- 证书生成后，目录 `services/certs/` 包含：
  - `server.crt`（完整链：服务器 + CA）与 `server.key`（Traefik 使用）
  - `ca.crt`（客户端信任使用）
  - `truststore.jks` 与 `truststore.p12`（仅含 CA，用于客户端信任）
  - `server.p12` / `keystore.p12`（服务端密钥库，含私钥+证书链）
- Spring Boot 作为客户端，仅需信任 CA：
  - `-Djavax.net.ssl.trustStore=services/certs/truststore.p12 -Djavax.net.ssl.trustStoreType=PKCS12 -Djavax.net.ssl.trustStorePassword=changeit`
  - 或使用 JKS：把 `truststore.jks` 路径与密码传给 `-Djavax.net.ssl.trustStore*`。
- 若你的 Spring Boot 自身需要对外提供 HTTPS（一般不需要，本栈由 Traefik 终止 TLS）：
  - `server.ssl.key-store=services/certs/keystore.p12`
  - `server.ssl.key-store-type=PKCS12`
  - `server.ssl.key-store-password=changeit`
  - `server.ssl.enabled=true`
