# F1: dts-metrics 服务骨架与默认部署

**优先级**: P0
**状态**: DONE
**目标**: 新建可独立构建、启动、健康检查并随应用栈默认部署的 `dts-metrics` 服务骨架。

## 任务

| Task | 内容 | 验收 |
|---|---|---|
| T01 | 新建 `source/dts-metrics` Spring Boot 模块 | 可独立 `mvn package`，不依赖 platform 内部实现类 |
| T02 | Dockerfile 和镜像构建 | `builds/dts-metrics/Dockerfile` 可由 `builds/dts-build.sh --image dts-metrics` 构建 |
| T03 | compose 默认部署 | `docker compose -f docker-compose-app.yml up -d dts-metrics` 可直接启动，不依赖 profile |
| T04 | 健康检查和配置 | `/actuator/health`、服务名、端口、数据库连接、platform base URL 可配置 |
| T05 | 基础日志和审计上下文 | 日志包含 trace id、tenant、operator、service name |

## 完成标准

- [x] `dts-metrics` 容器可独立启动。
- [x] 默认应用栈包含 `dts-metrics`。
- [x] platform-webapp 能探测 metrics capability，license 接入前不做版本禁用。

## 证据

- `source/dts-metrics`
- `builds/dts-metrics/Dockerfile`
- `docker-compose-app.yml`
- `worklog/v2.2.3/sprint-32-202605/it/evidence/default-metrics/README.md`
