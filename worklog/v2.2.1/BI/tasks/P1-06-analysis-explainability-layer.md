# P1-06 分析解释层（Explainability Layer）

`status`: `done`  
`priority`: `P1`  
`inspiration`: `Hetu(问答即洞察可解释化) + Superset(查询可追踪) + DataEase(低门槛展示)`

## 目标

把“出图结果”升级为“可解释结果”，让业务用户能看懂：这张图怎么来的、用了什么口径、受哪些筛选影响、下一步建议看什么。

## 子任务

1. 解释卡模型
- 新增 `ExplainCard` 协议：`metricDefinition/filterContext/dataLineage/querySummary/nextActions`。
- 与 `ScreenSpec v2` 兼容，支持组件级开关与展示位置配置。

2. 查询解释接口
- 后端新增解释接口（按组件/查询返回解释结构）。
- 统一错误码与审计记录，避免“解释失败但页面无提示”。

3. 筛选影响范围可视化
- 在运行态展示“当前全局变量/局部过滤器”对组件生效情况。
- 标记“被哪个筛选器影响”，支持快速定位联动来源。

4. 指标口径透出
- 透出指标定义、聚合方式、时间口径、版本号（如有）。
- 无语义层时回退为 SQL 摘要，不阻断展示。

5. 前端交互
- 组件右上角新增“解释”入口（悬浮卡 + 侧栏）。
- 支持“复制解释 JSON”，便于审计与协作沟通。

## 验收标准

- 任意图表可在 1 次点击内看到口径与筛选影响范围。
- 解释信息至少覆盖：`指标/过滤/数据来源/查询摘要` 四项。
- 解释链路异常时返回可读错误，不影响主图渲染。

## 风险与回滚

- 风险：解释接口计算开销导致交互变慢。  
- 回滚：解释卡按需懒加载 + 缓存，默认不阻塞主查询结果。

## 实现难度评估

- 难度：`中高`
- 预计周期：`1.5 ~ 2.5 周`
- 关键难点：
  - 语义层与 SQL 模式双栈对齐；
  - 联动变量影响链路可视化的一致性。

## 前置依赖

- `P1-01-datasource-unification-sql-mode.md`
- `P1-02-global-filter-interaction-engine.md`
- `P0-05-observability-compat-performance.md`

## 实现记录（2026-02-22）

- 后端新增解释服务与接口：
  - `POST /api/explain/card/{cardId}`，输出 `metricDefinition/filterContext/dataLineage/querySummary/nextActions/trace`。
  - 代码：`source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ExplainabilityService.java`、`source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ExplainabilityResource.java`。
- 解释卡支持复制 JSON：
  - `copyJson` 字段在后端返回；Card 编辑页新增 `Explain` + `Copy JSON`。
  - 代码：`source/dts-analytics-webapp/modern/src/pages/CardEditorPage.tsx`。
- 解释入口扩展到运行与设计链路（2026-02-22）：
  - Card 详情页新增 `Explain` 入口与结果面板；
  - 大屏设计器属性面板新增“解释当前组件”（Card/Metric 来源）与 JSON 复制；
  - 代码：`source/dts-analytics-webapp/modern/src/pages/CardDetailPage.tsx`、`source/dts-analytics-webapp/modern/src/pages/screens/components/PropertyPanel.tsx`。
- 指标口径透出与 Metric Lens 快捷入口：
  - `metricDefinition.metricLensUrl` 已输出；异常不阻断主图渲染。
