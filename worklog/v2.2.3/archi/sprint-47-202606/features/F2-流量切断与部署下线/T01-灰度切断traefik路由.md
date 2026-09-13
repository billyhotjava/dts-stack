# T01: 灰度切断 traefik 路由

**优先级**: P0
**状态**: READY
**依赖**: F1-T03（退役决策）

## 目标
分步、可观察地切断 dts-metrics 的两条 traefik 路由，先 UI 后 API。

## 技术设计
- 先停 `dts-metrics-ui`（`PathPrefix(/metrics)`，prio 255）——前端已由 SP-3 切原生页，`/metrics` 仅旧链接（靠 SP-3 F3-T01 重定向）。观察访问日志/告警。
- 观察期无异常 → 停 `dts-metrics-api`（`PathPrefix(/api/metrics)`，prio 260）。
- 切断方式：注释/移除 `docker-compose-app.yml` 对应 traefik labels（或先在网关层禁用 router），**保留 `dts-metrics` 服务容器**（回退窗口）。
- 每步记录观察结论。

## 影响范围
- `docker-compose-app.yml`（traefik labels）；部署侧操作。

## 验证
- [ ] 停 `/metrics` 后旧链接重定向到原生页、无 503。
- [ ] 停 `/api/metrics` 后无残留调用报错（F1-T01 已确认仅 iframe）。

## 完成标准
- [ ] 两路由分步切断、观察无异常、服务镜像保留（可回退）。
