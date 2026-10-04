# T02: compose 移除 dts-metrics 服务与路由

**优先级**: P0
**状态**: READY
**依赖**: T01（回退窗口已过、无异常）

## 目标
回退窗口无异常后，从部署彻底移除 dts-metrics 服务与路由。

## 技术设计
- `docker-compose-app.yml`：移除 `dts-metrics` 服务块（L810+）+ `dts-metrics-api`/`dts-metrics-ui` traefik labels + 相关 `DTS_METRICS_*` env、`IMAGE_DTS_METRICS`。
- `docker-compose.legacy.yml`：同步移除。
- 检查 `.env`/部署变量、健康检查依赖、`depends_on` 引用 dts-metrics 处一并清理。
- opmanager 部署文档移除 dts-metrics 条目（F4-T02 统一处理或此处联动）。

## 影响范围
- `docker-compose-app.yml`、`docker-compose.legacy.yml`、部署 env。

## 验证
- [ ] compose 校验通过（`docker compose config`）；无悬空引用 dts-metrics。
- [ ] 部署起栈无 dts-metrics；平台语义建模正常。

## 完成标准
- [ ] 服务/路由/env 移除、compose 合法、起栈正常。
