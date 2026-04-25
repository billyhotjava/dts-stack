# Sprint-17: 大屏访问对接 Leader-Overview

**时间**: 2026-04
**状态**: READY
**类型**: Implementation（跨服务对接 + 前端埋点 + 数据同步）
**目标**: 让 `dts-bi` 后端的"大屏管理"中的大屏访问能体现在工作台"我的概览"的"我常用的报表"中，闭环 Sprint-15 在 v2.2.3 上线后用户实际看到列表为空的设计断层。

## 背景

Sprint-15 把工作台首页改成 leader-overview，"我常用的报表" / "TOP 报表" 都依赖 `bi_report_visit` 这张访问表。

线上验证后发现：
- 用户在 UI 上能接触到的"报表"入口只有"BI 分析 → 大屏管理"（路由 `/bi/screens`，前端组件 `ScreensPage.tsx`，后端 `dts-analytics` 服务）。
- `BiReportLink`（dts-platform 的 `bi_report_link`）+ `bi_report_visit` 这条访问统计链路，只在管理员通过未发布的 `BiLinksPage`（无路由）入口建报表时才生效，正常用户无法触发任何 visit。
- `analytics_screen_audit_log` 是 actor CRUD 审计，并不是 visit 计数。
- 结果：所有用户打开"我的概览"都看不到任何报表/常用项，看起来像 bug。

本 Sprint 在 dts-bi **零改动**前提下打通这条链路：用 dts-platform 定时拉取 dts-bi 的 `/bi/api/screens` 列表做 reconcile（A2 方案），并在 ScreenPreviewPage 加载且停留 ≥3s 时调用 `reportsService.visit()`（B2 方案），把数据写回 `bi_report_visit`。

## 决策

| 决策 | 选项 | 理由 |
|---|---|---|
| 同步策略 | **A2 拉（reconcile，每小时）** | dts-bi 零改动；服务边界清晰；最终一致可接受 |
| 埋点时机 | **B2 ScreenPreviewPage 加载 + 3s 停留** | 与"我常用的报表"语义贴合；防止误触虚高 |
| 字段对齐 | code=`screen-{id}`、type=`SCREEN`、url=`/bi/screens/{id}/preview`、deptCodes=`{ownerDeptCode}` | 用 prefix 区分手工 vs 自动同步 |
| 防误删 | `bi_report_link.source` 列（`MANUAL` / `SCREEN_SYNC`）| reconcile 仅触碰 `SCREEN_SYNC` 行 |

## Feature 列表

| ID | Feature | Task 数 | 状态 |
|----|---------|---------|------|
| F1 | 后端 Screen 同步与配置 | 5 | READY |
| F2 | 前端 Preview 埋点 | 3 | READY |
| F3 | 验证与回归 | 2 | READY |

## 完成标准

- [ ] dts-platform 启动后 60s 内完成首次 reconcile，`bi_report_link` 中出现 `code` 以 `screen-` 开头的同步记录
- [ ] dts-bi 中创建/归档大屏，下次 reconcile（≤1h）后 dts-platform 侧 `bi_report_link.enabled` 同步翻转
- [ ] 用户登录后打开任意 `ScreenPreviewPage`，停留 3s+，`bi_report_visit` 出现一条记录（同 id 30s 内重复打开仅记一条）
- [ ] "我的概览"在测试账号至少打开过 1 条大屏后，"我常用的报表"列表非空且按 `lastVisitedAt` 倒序
- [ ] 既有 `BiReportLink` 手工记录（`source IS NULL` 或 `MANUAL`）不被 reconcile 误删/覆盖
- [ ] 后端 4 个新单元测试 + 1 集成测试通过；前端埋点 3 个单测通过；旧 Sprint-15 测试无回归

## 非目标

- 不改 dts-bi 任何代码（包括不在 dts-bi 端建访问日志表）
- 不引入消息队列做实时推送（A1 方案不在本 sprint 范围）
- 不改 leader-overview 现有查询逻辑（`bi_report_visit` 表结构稳定）
- 不引入更复杂的"分类访问统计"（仍用现有 visit 一行 = 一次访问的语义）

## 风险

| 风险 | 缓解 |
|---|---|
| dts-bi 没有 service-account 鉴权 | 复用现有 `internal-token` 头；若不存在，降级为 fail-closed（reconcile 跳过 + 告警） |
| 已有手工报表被 reconcile 覆盖 | `source` 列防护；首次启动只插不删 |
| 大屏量级猛增（>1k）reconcile 慢 | 加批量 upsert + 仅同步增量（updatedAt > last_sync）|
| Preview 页埋点风暴（开关切换） | 30s 同 id 防抖；卸载清 timer |

## 设计文档

- `worklog/v2.2.3/sprint-15-202604/README.md`（前置 leader-overview 设计）
- `worklog/v2.2.3/sprint-17-202604/features/F1-后端Screen同步与配置/README.md`
- `worklog/v2.2.3/sprint-17-202604/features/F2-前端Preview埋点/README.md`
