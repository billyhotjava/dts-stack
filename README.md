# DTS Stack 使用说明（简版）

只保留两种常用模式：挂载开发模式（本地联调）与部署模式（容器运行）。

【准备】
- 首次初始化（生成 .env、证书、起基础依赖）：
  - `./init.sh single 'Strong@2025!' dts.local`

【挂载开发模式（本地联调）】
- 启动（源码挂载 + 热更新）：
  - `./dev-up.sh --mode local`
- 访问：
  - Admin 前端：`http://localhost:18011`
  - Platform 前端：`http://localhost:18012`
  - 后端 API（通过 Traefik）：`https://api.${BASE_DOMAIN}/admin`、`https://api.${BASE_DOMAIN}/platform`
- 停止：
  - `./dev-stop.sh --mode local`

【部署模式（容器运行，推荐上线/演示）】
- 设置应用镜像（在 `imgversion.conf` 中）：
  - `IMAGE_DTS_ADMIN=...`
  - `IMAGE_DTS_PLATFORM=...`
  - `IMAGE_DTS_ADMIN_WEBAPP=...`
  - `IMAGE_DTS_PLATFORM_WEBAPP=...`
- 启动（同域、无 CORS）：
  - `./init.sh single 'Strong@2025!' dts.local`
  - `docker compose -f docker-compose.yml -f docker-compose-app.yml up -d`
- 访问：
  - Admin：`https://biadmin.${BASE_DOMAIN}`（同域 `/api` → dts-admin）
  - Platform：`https://bi.${BASE_DOMAIN}`（同域 `/api` → dts-platform）
- 更新代码后：
  - 仅重建改动服务镜像并重启该服务（示例）：
    - `docker build -t dts-admin:NEW_TAG -f source/dts-admin/Dockerfile source && docker compose -f docker-compose.yml -f docker-compose-app.yml up -d dts-admin`
    - `docker build -t dts-platform:NEW_TAG -f source/dts-platform/Dockerfile source && docker compose -f docker-compose.yml -f docker-compose-app.yml up -d dts-platform`
    - `docker build -t dts-admin-webapp:NEW_TAG source/dts-admin-webapp && docker compose -f docker-compose.yml -f docker-compose-app.yml up -d dts-admin-webapp`
    - `docker build -t dts-admin:NEW_TAG source/dts-admin` 与 `docker build -t dts-platform:NEW_TAG source/dts-platform` 依旧可用；如未包含 `dts-common`，Dockerfile 将自动注入轻量版审计依赖，仅影响容器内构建。
  - 使用固定标签时可：`up -d --force-recreate <服务名>`（不推荐长期使用）

【说明】
- 基础依赖（Traefik/Keycloak/Postgres/MinIO/Nessie/Trino）由 `docker-compose.yml` 管理；
  应用由 `docker-compose-app.yml` 管理；开发联调由 `docker-compose.dev.yml` 管理。
- 如需变更域名，修改 `.env` 的 `BASE_DOMAIN` 后可重跑 `./init.sh`。
- 认证预留：Admin 侧已加入可配置的 PKI 登录占位入口（默认关闭，不影响现有用户名/密码登录）。详见 `docs/pki-auth.md`。
- 审计记录真实客户端 IP：在 `.env` 中设置 `TRUSTED_PROXY_CIDRS`（例如 `192.168.8.200/32`），让 Traefik 仅信任指定反向代理来源并重写 `X-Forwarded-For`，后端会读取该值并过滤伪造请求头。

【安全与权限矩阵】
- 角色登录准入、菜单可见性、RBAC×ABAC 判定与错误码说明，见 `worklog/permissions-matrix.md`。

【变更请求优化：角色菜单批量申请】
- 在“角色管理”页面，针对同一角色一次性勾选/取消多个菜单后，系统将合并为一条变更请求（`PORTAL_MENU`/`BATCH_UPDATE`），审批一次即可全部生效。
- 审批中心会显示每个菜单的前后差异（allowedRoles before/after）。
- 如需查看提交记录：`Admin → 我的申请` 列表中类别显示为 `PORTAL_MENU`。

【快速健康检查】
- 查看容器状态：`docker compose ps`
- Trino 探活：`docker compose exec dts-trino wget -qO- http://localhost:8080/v1/info`
- SSO 探活：`curl -k https://sso.${BASE_DOMAIN}`

【openEuler 适配说明】
- 已在 Compose 清单中为所有本地目录挂载添加了 SELinux 友好配置（z/Z 标签）。
- 在 openEuler 上的安装与注意事项，请参考 docs/openeuler.md。

## 常见问题

### docker compose 报 “Invalid interpolation format … trustedIPs…”
部署脚本使用了 Compose v2 才支持的 `${VAR:-default}` 语法。请安装 Docker Compose v2（`docker compose version` 有输出）或在无法升级 Docker Engine 时下载 `docker-compose 1.29.2` ARM64/x86_64 二进制放到 `/usr/local/bin/docker-compose`。

### Postgres 初始化时报 “could not execute ... postgres -V: Operation not permitted”
银河麒麟 / 鲲鹏主机通常启用 IMA Appraisal（`/sys/kernel/security/ima/policy` 中含 `appraise func=BPRM_CHECK`）。容器镜像里的 `/usr/lib/postgresql/17/bin/postgres` 没有 `security.ima` 签名，会被 IMA 拒绝执行，`initdb` 因此提示找不到 `postgres`。处理方式：

1. 在 GRUB 中关闭或放宽 IMA（例如加 `ima_appraise=off`），然后 `grub2-mkconfig` 并重启。
2. 将 Docker 数据目录迁移到未启用 IMA 审计的分区，或采用支持签名的存储驱动（如 LVM thinpool）。
3. 仅用于定位问题时，可以临时 `sudo setenforce 0`（若启用 SELinux）并将 IMA 设为测量模式，确认容器可启动后再做永久配置。

`./init.sh` 会在检测到 IMA appraisal policy 时给出 WARNING，出现该提示时请优先调整宿主机配置。
