# T04: 为 legacy/offline 运行时补齐无 curl 的 healthcheck fallback

**优先级**: P0
**状态**: DONE
**依赖**: T02

## 目标

修复鲲鹏 / 麒麟离线现场中 `dts-platform` 等 offline 运行时镜像因缺少 `curl` 被 Docker 健康检查误判为 `unhealthy` 的问题。

## 技术设计

- 保留现有基于 `/management/health` 或 `/api/health` 的 HTTP 探活
- 当容器内不存在 `curl` 或 HTTP 探活不可执行时，回退到 `/proc/net/tcp` 与 `/proc/net/tcp6` 的本地端口监听检查
- 覆盖 `dts-admin`、`dts-platform`、`dts-ingestion`、`dts-analytics` 的 `app` 与 `legacy` compose 场景
- 新增回归测试，确保 compose healthcheck 始终带有 offline fallback

## 影响范围

- `docker-compose-app.yml`
- `docker-compose.legacy.yml`
- `tests/test_compose_health_gates.sh`
- `worklog/v2.2.2/sprint-10-202603/it/README.md`

## 验证

- [x] `bash tests/test_compose_health_gates.sh`
- [x] `bash tests/test_runtime_healthcheck_tools.sh`
- [x] `bash tests/test_dts_upgrade_compose_compat.sh`

## 完成标准

- [x] `legacy` / `offline` 运行时不再强依赖 `curl` 才能通过 healthcheck
- [x] `dts-platform` 在鲲鹏 / 麒麟离线环境下不会因为探活工具缺失被误判为 `unhealthy`
