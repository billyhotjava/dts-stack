# 河图(Hetu) 7778 端口接入 Traefik（路径前缀方案）

目标：不直接暴露 `:7778`，通过 Traefik 在 `HOST_PLATFORM_UI` 域名下使用路径前缀访问河图。

## 为什么不建议用 `/dashboard`

`dts-platform-webapp` 的前端路由已经占用 `/dashboard/*`（例如 `/dashboard/workbench`），如果把河图挂到 `https://bi.xxx.com/dashboard` 会与平台路由冲突，导致平台页面不可用。

因此这里使用 **`/dashboards/*`** 作为河图入口（前端也已把 `/dashboards` 作为“外部系统直跳”路径处理）。

如果一定要带上 `dashboard` 字样，可以用 **`/dashboard/hetu/*`**（不会抢占 `/dashboard/workbench` 等平台路由）。

## 当前实现（已落地到本仓库）

- Traefik 上游：`services/dts-proxy/dynamic/traefik-dynamic.yml` 定义 `http.services.hetu`，指向 `http://host.docker.internal:7778`
- Traefik 路由：`docker-compose.yml` 在 `dts-proxy` 上增加两个 router
  - `https://$HOST_PLATFORM_UI/dashboards/*` -> **StripPrefix `/dashboards`** -> 转发到河图
  - `https://$HOST_PLATFORM_UI/screen/*` -> 直接转发到河图（兼容河图前端可能使用的绝对路径 `/screen/...`）

访问示例（分享链接）：

- `https://${HOST_PLATFORM_UI}/dashboards/screen/share/index.html#/TJxxxx...`
- `https://${HOST_PLATFORM_UI}/dashboard/hetu/screen/share/index.html#/TJxxxx...`

## 如何“真正关闭” 7778

当前默认上游是宿主机的 `:7778`，因此宿主机仍需要监听该端口；要对外不暴露：

- 推荐：把河图改为容器部署并加入 `dts-core` 网络，**不做端口映射到宿主机**，然后把上游改成 `http://dts-hetu:7778`
- 或：用防火墙仅允许本机/内网访问 `:7778`（仍然是“有端口”，只是不可从公网访问）

## 联调排查建议

如果页面能打开但有资源 404，通常是河图引用了其它绝对路径前缀（不止 `/screen`）。做法：

- 打开浏览器 DevTools -> Network，看失败的 URL 前缀（例如 `/static/`、`/assets/`、`/api/` 等）
- 若前缀不与平台冲突，可按同样方式在 `docker-compose.yml` 增加一个 `PathPrefix(...)` router 指向 `hetu@file`
- 若前缀会与平台冲突（最常见是 `/api`），建议改用 **独立子域名**（例如 `hetu.xxx.com`）承载河图，避免路径争用
