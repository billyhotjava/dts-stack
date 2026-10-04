# F2: 流量切断与部署下线

**优先级**: P0
**状态**: READY
**依赖**: F1 gate 通过

## 目标
灰度切断 dts-metrics 流量并从部署移除，保留回退窗口。原则：先切 UI 路由 → 观察 → 切 API → 保留镜像 N 天 → 移除服务。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 灰度切断 traefik 路由（`/metrics` → `/api/metrics`，分步+观察） | P0 | READY | F1-T03 |
| T02 | compose 移除 `dts-metrics` 服务 + 两条 traefik 路由（回退窗口后） | P0 | READY | T01 |
| T03 | 回退预案与验证（恢复脚本 + 切断后回归） | P0 | READY | T01 |

## 完成标准
- [ ] `/metrics` UI 路由先停、观察无异常后停 `/api/metrics`（dts-metrics-api）。
- [ ] 回退窗口内保留服务镜像；窗口后 `docker-compose-app.yml` + `docker-compose.legacy.yml` 移除 `dts-metrics` 服务与 `dts-metrics-api`/`dts-metrics-ui` 路由。
- [ ] 回退脚本就绪（恢复路由 + 重启服务）；切断后语义建模全走原生页、无 503。
