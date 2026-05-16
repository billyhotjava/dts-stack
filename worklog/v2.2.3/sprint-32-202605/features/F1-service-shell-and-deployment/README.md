# F1: dts-metrics 服务骨架与部署 profile

**优先级**: P0
**状态**: READY
**目标**: 新建可独立构建、启动、健康检查和按 profile 启用的 `dts-metrics` 服务骨架。

## 任务

| Task | 内容 | 验收 |
|---|---|---|
| T01 | 新建 `source/dts-metrics` Spring Boot 模块 | 可独立 `mvn package`，不依赖 platform 内部实现类 |
| T02 | Dockerfile 和镜像构建 | `builds/dts-metrics/Dockerfile` 可由 `builds/dts-build.sh --image dts-metrics` 构建 |
| T03 | compose profile | `professional`/`enterprise` 启动 metrics，`foundation` 不启动且 platform 正常 |
| T04 | 健康检查和配置 | `/actuator/health`、服务名、端口、数据库连接、platform base URL 可配置 |
| T05 | 基础日志和审计上下文 | 日志包含 trace id、tenant、operator、service name |

## 完成标准

- [ ] `dts-metrics` 容器可独立启动。
- [ ] 关闭 `dts-metrics` 不影响基础版核心链路。
- [ ] 启用 profile 后 platform-webapp 能探测 metrics capability。
