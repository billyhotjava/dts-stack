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
  - Admin：`https://admin.${BASE_DOMAIN}`（同域 `/api` → dts-admin）
  - Platform：`https://platform.${BASE_DOMAIN}`（同域 `/api` → dts-platform）
- 更新代码后：
  - 仅重建改动服务镜像并重启该服务（示例）：
    - `docker build -t dts-admin:NEW_TAG source/dts-admin && docker compose -f docker-compose.yml -f docker-compose-app.yml up -d dts-admin`
    - `docker build -t dts-admin-webapp:NEW_TAG source/dts-admin-webapp && docker compose -f docker-compose.yml -f docker-compose-app.yml up -d dts-admin-webapp`
  - 使用固定标签时可：`up -d --force-recreate <服务名>`（不推荐长期使用）

【说明】
- 基础依赖（Traefik/Keycloak/Postgres/MinIO/Nessie/Trino）由 `docker-compose.yml` 管理；
  应用由 `docker-compose-app.yml` 管理；开发联调由 `docker-compose.dev.yml` 管理。
- 如需变更域名，修改 `.env` 的 `BASE_DOMAIN` 后可重跑 `./init.sh`。

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
