# Sprint-17: Drill-through 泛化 + 面向分析师的语义层

**时间**: 2026-03
**状态**: DONE
**目标**: 将大屏的 drill-through 能力扩展到 analytics 的 Card/Dashboard；构建面向分析师的语义层

## 背景

Metabase 差距分析（metabase-gap-analysis.md）中 P1 级别的两个关键差距：
1. Drill-through 仅限大屏设计器，analytics 的 Card/Dashboard 无点击下钻能力
2. 指标/维度定义是非结构化 JSON，缺乏面向分析师的语义建模体验

## 现有基础

### Drill-through
- 大屏已有完整 action 系统：drill-down(嵌套卡片)、set-variable、jump-url、open-panel、emit-intent
- hooks: useDrillDown (74行) + useDrillView (63行)
- InteractionLayer.tsx 处理 ECharts/Table 点击事件
- analytics Card 系统有 NotebookEditor 查询构建器，但 **查询结果无点击交互**

### 语义层
- AnalyticsMetric 实体存在，但 metric_json 是非结构化 blob
- AnalyticsField 有 semantic_type/display_name/visibility_type
- MetricLensPage 有冲突检测 + 版本对比
- dts-platform 的 ModelingSqlModel 有 semantic_contract 字段但与 analytics 层断连

## Feature 列表

| ID | Feature | Task 数 | 状态 |
|----|---------|---------|------|
| F1 | Card/Dashboard Drill-through | 4 | DONE |
| F2 | 语义指标/维度管理 | 4 | DONE |

### F1: Card/Dashboard Drill-through
- T01: CardViewPage 图表点击 → 自动追加 filter（点击柱状图某个值，自动 filter by 该维度值）
- T02: Dashboard 交叉过滤（点击 Card A 的维度值，联动过滤同 Dashboard 的其他 Card）
- T03: Drill-to-detail（点击聚合数据 → 查看明细记录）
- T04: 自定义点击行为配置（Card 级别配置 click action：filter/detail/url/none）

### F2: 语义指标/维度管理
- T05: MetricDefinition 结构化实体（name + 表达式 + 聚合函数 + 过滤条件 + 时间维度）
- T06: 指标管理 UI 增强（创建/编辑指标表达式，预览结果）
- T07: 字段角色标注（measure/dimension/pk/fk + 默认聚合）
- T08: 语义模型发布（从 dbt 模型发布到 analytics 层，带指标/维度定义）
