> 状态: done-first-pass (2026-02-13)

# P0-05 稳定性与可观测（500/502可定位）

## 目标
- 错误可定位、可追踪、可复现，避免现场排障靠猜。

## 范围
- BE：统一错误码、查询链路 metrics。
- FE：统一错误层（code/requestId/retry）。
- OPS：健康检查、依赖探测、告警。
- QA：故障注入回归。

## 交付物
- 错误码手册（并入 `screen-designer-execution-board.md` 的异常处理章节）。
- 监控看板模板（成功率/P95/TopN）与采样口径。
- 现场排障最小清单（requestId 贯通、日志检索入口、依赖探测顺序）。

## 本轮进展
- 已在 v2.2.1 形成运行证据链：
  - `worklog/v2.2.1/platform/stability-24h.md`
  - `worklog/v2.2.1/platform/raw/summary-*.txt`
  - `worklog/v2.2.1/platform/raw/hourly-metrics-*.csv`
- 已补前端 requestId 可见性：`analyticsApi.HttpError` 现从响应头提取 `x-request-id/x-correlation-id` 并附加到错误消息。
- 已补前端 error code 透出：优先读取 `X-Error-Code`，并回退解析错误体 `code` 字段。
- 已补后端统一错误返回：`ApiError` 增加 `code`，`SecurityProblemSupport` 与 `GlobalExceptionHandler` 统一输出错误码。
- 已补查询链路稳定性：`/api/card/{id}/query` 与 `/api/public/**/query` 捕获外部库连接异常并返回可观测错误码，避免事务回滚覆盖业务异常。
- 已补健康探测增强：`/api/health` 增加应用库探活耗时与 `requestId`，数据库不可达返回 `503`。
- 已形成自动化采集脚本：
  - `worklog/v2.2.1/platform/scripts/collect-evidence.sh`
  - `worklog/v2.2.1/platform/scripts/backfill-first-run.sh`

## 验收
- 500/502 问题 15 分钟内可定位根因范围。
- requestId 能贯穿前后端日志。

## 回滚点
- 新错误映射异常时回退旧错误展示并保留 requestId。
