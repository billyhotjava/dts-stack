# P0-06 NL2SQL 评测集与失败回流闭环

`status`: `done`  
`priority`: `P0`  
`inspiration`: `生产级 LLM 应用的离线评测 + 在线回流闭环`

## 目标

在大模型已部署前提下，先把 NL2SQL 变成“可评估、可回归、可持续优化”的工程系统，而不是一次性演示能力。

## 子任务

1. 离线评测集
- 建立高频业务问题样本集（问题、期望 SQL、期望结果特征）。
- 样本按领域分层：销售/制造/能耗/运营。

2. 线上失败回流
- 记录 NL2SQL 失败上下文：用户问题、候选 SQL、错误码、耗时、数据源。
- 支持按错误类型聚类（语法/权限/表字段缺失/超时）。

3. 质量看板
- 输出核心指标：`可执行率/正确率/平均重试次数/P95 延迟`。
- 支持按版本对比（模型版本、Prompt 版本、词典版本）。

4. 回归机制
- 每次优化后执行离线回归，自动生成差异报告。
- 阻断条件：可执行率下降、危险 SQL 误放行。

## 当前进展（2026-02-21）

- [x] 已复用 `analytics_query_trace`，新增失败统计接口：`GET /analytics/api/query-trace/failure-summary`。
- [x] 支持按时间窗与链路聚合：`days/topN/chain`，输出失败率与 Top 错误码。
- [x] 已新增评测样本管理 API：`/analytics/api/nl2sql-eval/cases`（增删改查）。
- [x] 已新增批量评测 API：`POST /analytics/api/nl2sql-eval/run`（输出通过率、平均分、逐条检查结果）。
- [x] 在线失败样本回流已补齐：`card/public query` 会将候选 SQL、自动纠错重试过程、错误类别写入 `query_trace.context_json.executionTrace`。
- [x] 已实现回归门禁与版本对比接口：`POST /analytics/api/nl2sql-eval/run-gated`、`GET /analytics/api/nl2sql-eval/runs`、`GET /analytics/api/nl2sql-eval/compare`。
- [x] 前端评测控制台已落地（2026-02-22）：
  - 页面：`source/dts-analytics-webapp/modern/src/pages/Nl2SqlEvalPage.tsx`；
  - 路由：`/analytics/nl2sql-eval`；
  - 导航入口：侧边栏“NL2SQL评测”；
  - 功能：样例创建、批量评测、gated 评测、历史 run 查看与 run 对比。

## 验收标准

- 可执行率、正确率可按日/周追踪并可追溯到版本。
- 失败样本可自动沉淀并进入下一轮优化样本池。
- 发布前必须通过离线回归门禁。

## 风险与回滚

- 风险：采集不完整导致指标失真。  
- 回滚：先保证最小采集字段完整，再逐步扩展维度。

## 实现难度评估

- 难度：`中`
- 预计周期：`1 ~ 1.5 周`

## 前置依赖

- `P1-01-datasource-unification-sql-mode.md`
- `P0-05-observability-compat-performance.md`
