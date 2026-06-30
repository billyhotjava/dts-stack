# T01: compose 默认服务退役

**优先级**: P0  
**状态**: DONE  
**依赖**: F0/T03

## 目标

从默认 `docker-compose-app.yml` 路径退役 `dts-metrics` 服务和 `/api/metrics`、`/metrics` Traefik 路由。

## 技术设计

- 默认 app compose 不再包含 `dts-metrics` 服务，或将其放入显式 legacy/profile。
- 移除默认 Traefik `/api/metrics` 和 `/metrics` router。
- 移除平台 UI router 对 `/metrics` 的特殊排除，除非 legacy profile 仍需要。
- 不改 Prometheus/Traefik 自身 metrics entrypoint。

## 影响范围

- `docker-compose-app.yml`
- 如需：`docker-compose.legacy.yml` 保留旧服务。

## 验证

- [x] `docker compose -f docker-compose-app.yml config --services` 默认输出无 `dts-metrics` 服务。
- [x] ``docker compose -f docker-compose-app.yml config | rg 'dts-metrics|/api/metrics|PathPrefix\(`/metrics`\)'`` 默认无匹配。
- [x] `docker-compose.legacy.yml` 仍保留显式 `dts-metrics` 回滚面。

## 完成标准

- [x] 默认部署不依赖 dts-metrics 容器。
