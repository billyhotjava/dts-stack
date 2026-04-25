# F1: 后端 Screen 同步与配置

**优先级**: P0
**状态**: READY

## 目标

在 dts-platform 侧加一个定时拉取服务，从 dts-analytics 的 `/bi/api/screens` 拉全量大屏元数据，upsert 到 `bi_report_link` 表，使 leader-overview 的查询能自动覆盖大屏。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | bi_report_link.source 列与 liquibase changeset | P0 | READY | - |
| T02 | DtsAnalyticsClient（HTTP 客户端 + 配置） | P0 | READY | - |
| T03 | ScreenReportLinkSyncService（reconcile 主逻辑） | P0 | READY | T01,T02 |
| T04 | ScreenReportLinkSyncScheduler（启动 + 定时） | P1 | READY | T03 |
| T05 | 单元测试 + 集成测试 | P0 | READY | T03 |

## 完成标准

- [ ] `bi_report_link.source` 列存在且默认 `MANUAL`，已有数据回填为 `MANUAL`
- [ ] DtsAnalyticsClient 可以以 service-account 身份调用 `/bi/api/screens` 并解析返回
- [ ] reconcile 逻辑：upsert 同步行；归档大屏 → `enabled=false`；手工行（`source != SCREEN_SYNC`）不被触碰
- [ ] 启动后 60s 内完成首次 reconcile；之后每小时跑一次
- [ ] 4 个单元测试 + 1 个 mock 集成测试通过
