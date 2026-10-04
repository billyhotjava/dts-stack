# P2-05 指标语义透视台（Metric Lens）

`status`: `done`  
`priority`: `P2`  
`inspiration`: `Hetu(指标分析可信化) + Superset/Metabase 语义建模思想`

## 目标

为业务用户提供“指标定义、口径版本、权限范围、数据血缘”的统一透视视图，解决“同名指标不同口径”与“结果不可信”问题。

## 子任务

1. 指标透视模型
- 新增 `MetricLens` 视图模型：`definition/aggregation/timeGrain/version/owner/lineage/aclScope`。
- 支持指标多版本对比（当前版 vs 历史版）。

2. 血缘与依赖展示
- 展示指标 -> 数据集 -> 表/字段依赖链。
- 显示最近变更记录与变更影响范围。

3. 口径冲突检测
- 检测同名指标在不同空间的定义差异（聚合、过滤、时间窗）。
- 提供冲突等级与修复建议（合并、弃用、重命名）。

4. 权限语义对齐
- 在透视台展示指标可见范围与继承策略。
- 与现有 ACL 同口径，避免“能看到图但看不懂权限来源”。

5. 运行态集成
- 图表“解释卡”中增加 Metric Lens 快捷入口。
- AI Copilot 建议结果附带指标版本与口径引用。

## 验收标准

- 任一指标可在 2 次点击内查看定义、版本、血缘与权限范围。
- 冲突检测可识别至少三类差异：聚合差异、过滤差异、时间口径差异。
- 口径变更后可追踪受影响图表/报告列表。

## 风险与回滚

- 风险：血缘计算复杂导致构建耗时高。  
- 回滚：先做离线增量构建，在线仅查询快照。

## 实现难度评估

- 难度：`高`
- 预计周期：`3 ~ 5 周`
- 关键难点：
  - 语义资产跨模块汇聚（analytics/platform）；
  - 冲突检测规则设计与误报控制。

## 前置依赖

- `P1-01-datasource-unification-sql-mode.md`
- `P1-06-analysis-explainability-layer.md`
- `P2-01-ai-screen-copilot.md`

## 实现记录（2026-02-22）

- 新增 Metric Lens 后端服务与 API：
  - 列表/详情：`GET /api/metric-lens`、`GET /api/metric-lens/{metricId}`；
  - 版本对比：`GET /api/metric-lens/{metricId}/compare`；
  - 冲突检测：`GET /api/metric-lens/conflicts`。
  - 代码：`source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/MetricLensService.java`、`source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/MetricLensResource.java`。
- 透视维度覆盖：定义、聚合、时间口径、版本、owner、lineage、aclScope。
- 冲突检测支持识别聚合/过滤/时间口径差异，并返回冲突等级与修复建议。
- Explainability 与 AI 结果已接入 Metric Lens 快捷引用（`metricLensUrl`/`metricLensReferences`）。
- 前端透视台已落地（2026-02-22）：
  - 新增页面：`source/dts-analytics-webapp/modern/src/pages/MetricLensPage.tsx`；
  - 新增路由：`/analytics/metric-lens`；
  - 新增导航入口：侧边栏“指标透视”；
  - 能力覆盖：指标列表、详情透视、版本对比、冲突列表可视化。
