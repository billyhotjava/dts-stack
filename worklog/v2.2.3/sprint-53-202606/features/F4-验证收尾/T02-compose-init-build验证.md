# T02: compose/init/build 验证

**优先级**: P0  
**状态**: DONE  
**依赖**: F2

## 目标

验证默认部署与构建链路不再包含 `dts-metrics`。

## 技术设计

- `docker compose -f docker-compose-app.yml config` 检查服务和 Traefik routers。
- 检查 `init.sh` 默认输出变量。
- 检查 `builds/dts-build.sh` default all 逻辑。
- 检查 `imgversion*.conf` 默认项。

## 影响范围

- repo root deployment/build files.

## 验证

- [x] 默认 compose config 不含 `dts-metrics:`。
- [x] 默认 compose config 不含 `dts-metrics-api` / `dts-metrics-ui`。
- [x] 默认 all build 不调用 metrics webapp/module。

## 完成标准

- [x] 默认运行面和构建面退役有机器可复核证据。
