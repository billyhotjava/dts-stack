# Sprint-10: 容器自恢复与启动编排稳态化

**时间**: 2026-03
**状态**: IN_PROGRESS
**目标**: 解决服务器重启后容器不能自动恢复、前端容器依赖后端启动顺序脆弱的问题，尤其收敛 `dts-platform-webapp` 需要人工二次启动的现场故障。

## 背景

当前 `legacy` 现场已经具备平滑升级能力，但运行稳态仍有明显短板：

- 宿主机重启后，并非所有关键容器都会自动恢复
- `dts-platform-webapp` 在后端服务尚未可解析/可访问时会直接退出
- 现有 compose 里的 `depends_on` 大量使用 `service_started`，只能表达“进程启动”，不能表达“服务就绪”
- 现场运维仍可能需要人工二次 `up -d dts-platform-webapp`，不符合交付要求

因此该工作从“升级编排”切换为“运行时自恢复与启动编排稳态化”，单独立为 `Sprint-10`。

## Feature 列表

| ID | Feature | Task 数 | 状态 |
|----|---------|---------|------|
| F1 | 容器自恢复策略 | 3 | DONE |
| F2 | 启动顺序与健康门禁 | 4 | IN_PROGRESS |
| F3 | platform-webapp 自等待机制 | 3 | DONE |
| F4 | 重启回归与现场验收 | 3 | IN_PROGRESS |

## 本轮已实现
- `docker-compose-app.yml` 与 `docker-compose.legacy.yml` 为关键长跑服务补齐 `restart: unless-stopped`，并保留一次性任务 `restart: "no"`。
- `dts-admin`、`dts-platform`、`dts-ingestion`、`dts-analytics` 补齐可执行 healthcheck，`dts-platform-webapp` 改为依赖后端 `service_healthy`。
- `legacy/offline` 场景下，`dts-admin`、`dts-platform`、`dts-ingestion`、`dts-analytics` 的 healthcheck 现在支持无 `curl` fallback，不再因运行时镜像缺少探活工具被误判为 `unhealthy`。
- `builds/dts-admin/Dockerfile`、[builds/dts-platform/Dockerfile](/opt/prod/s10/s10-stack/builds/dts-platform/Dockerfile)、[builds/dts-ingestion/Dockerfile](/opt/prod/s10/s10-stack/builds/dts-ingestion/Dockerfile)、[builds/dts-analytics/Dockerfile](/opt/prod/s10/s10-stack/builds/dts-analytics/Dockerfile) 运行时镜像补齐 `curl`，确保 compose healthcheck 可执行。
- [builds/dts-platform-webapp/docker-entrypoint.sh](/opt/prod/s10/s10-stack/builds/dts-platform-webapp/docker-entrypoint.sh) 改为“先起占位 Nginx、持续等待后端、就绪后切正式配置并 reload”，不再因上游暂时未就绪直接退出。

## 已通过验证
- `bash tests/test_compose_restart_policy.sh`
- `bash tests/test_compose_health_gates.sh`
- `bash tests/test_runtime_healthcheck_tools.sh`
- `bash tests/test_platform_webapp_waits_for_backends.sh`
- `bash tests/test_dts_upgrade_compose_compat.sh`
- `bash tests/test_dts_upgrade_e2e.sh`
- `bash tests/test_dts_upgrade_preflight.sh`
- `bash tests/test_dts_upgrade_mode_and_pg_checks.sh`
- `bash -n builds/dts-platform-webapp/docker-entrypoint.sh`

## 完成标准
- [x] 宿主机重启后，关键运行容器具备自动恢复策略
- [x] `legacy` 与 `normal` 模式下的重启策略一致且可验证
- [x] `dts-platform-webapp` 不再因后端暂时未就绪而退出
- [x] Compose 依赖关系清晰区分 `service_started` 与 `service_healthy`
- [ ] 补齐自动化回归与现场验收文档
