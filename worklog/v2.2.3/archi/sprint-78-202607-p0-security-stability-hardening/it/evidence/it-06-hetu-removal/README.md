# IT-06：Hetu 移除

**日期**：2026-07-31
**状态**：PASS（仓库静态 + file provider + 运行时目标态已达成）；浏览器 smoke 段 GAP（基线见 `it/baseline.md`）

## 1. 仓库静态校验

```
docker compose -f docker-compose-app.yml config -q   -> OK
docker compose -f docker-compose.dev.yml config -q   -> OK
grep -c "hetu" docker-compose-app.yml docker-compose.dev.yml -> 0, 0
```

- compose 删除：9 组 `hetu-*` router label + 3 个 hetu middleware label + `hetu.upstream` extra_hosts + 主 UI 路由规则中 8 个 hetu `!PathPrefix` 排除项（保留 `/api`、`/admin/api`、`/analytics`、`/bi/api`）。app 与 dev 两个文件同构处理。
- `init.sh`：`HETU_UPSTREAM_IP` 标记 DEPRECATED，新部署不再写入 .env；既有 `.env` 值未动。
- `services/dts-proxy/dynamic/traefik-dynamic.yml`：9 个 `hetu-*-fallback` router、`hetu` service、4 个 hetu middleware 删除；保留 security-headers / platform-forward-auth / cors-allow / tls。`.bak.bi_yuzhicloud_20260412161329` 保留作历史对照。

## 2. 上游活性核查（移除前）

```
HETU_UPSTREAM_IP=172.18.0.1
curl http://172.18.0.1:7778/ -> 000（不可达）
```

Hetu 上游在本环境已不存在，移除路由无功能性回退。

## 3. file provider 热加载（2026-07-31 22:10 +08:00）

- 保存后 Traefik 自动重载；file provider 中 hetu 路由/service 已消失。
- 日志出现预期内的 `the service "hetu@file" does not exist` ERR——来源是**运行中 dts-proxy 容器的旧 docker label**（docker provider），需受控重建 dts-proxy 后消除；file provider 配置本身无错误。

## 4. 受控重建与最终路径行为矩阵（用户批准，2026-07-31 22:1x +08:00）

执行：`docker compose -f docker-compose-app.yml up -d dts-proxy dts-platform-webapp`（主 UI 路由规则在 webapp 服务 labels 上，故两个容器一并重建；两者数秒内恢复 healthy）。

重建后验证：

- 运行时路由表 `GET /api/http/routers` 中 hetu 相关路由 = **0**；重建后日志新增 hetu 错误 = **0**。
- 最终路径矩阵（curl，Host=bi.yuzhicloud.com）：

| 路径 | 移除前 | 重建后（最终态） | 判定 |
|---|---|---|---|
| `/` | 200 | **200** | 主站正常 |
| `/bi` | 200 | **200** | 内置 BI 正常 |
| `/dashboards`、`/hetu`、`/screen`、`/system`、`/tdv`、`/account`、`/static`、`/dashboard/hetu` | 502/超时（死上游） | **200**（SPA 回落，前端路由引导） | 符合契约 |
| `/api/`、`/bi/api/` | 401 | **401** | 后端路由未受影响 |
| `/analytics` | 404 | **404** | 既有行为（compose 中仅有 `/analytics/api/public` 路由，与本次变更无关） |
| SSO `/` | 302 | **302** | Keycloak 正常 |

结论：Hetu 代理面已从交付物与运行时完全移除；旧路径不再产生网关错误，统一回落平台 SPA（历史深链由前端 `shouldRedirectHetuEntryToAnalytics` 重定向 `/bi`）。

## 5. 浏览器 smoke

GAP——共享登录基线 401 未恢复（Sprint-77 F0/T01 跟踪），以 curl 矩阵 + 前端 source-contract 测试为准，浏览器证据待基线恢复后补。

## 6. 运行时装填记录

- 2026-07-31 22:1x +08:00（用户批准）：`docker compose -f docker-compose-app.yml up -d dts-proxy dts-platform-webapp`，两容器数秒内恢复 healthy，运行时路由表达成最终态（见第 4 节）。
