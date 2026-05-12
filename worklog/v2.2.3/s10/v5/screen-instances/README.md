# v5 大屏实例生成说明

已 review v4/pjm/screen-instances：v4 包含综合、执行、质量、技术状态、风险和下钻页 JSON。与截图最接近的是 imgs/project_overview_dashboard.html，因此 v5 先生成项目总览主屏实例。

## 输出

- gpmc-project-overview-v5.json：1920x1080 可导入大屏 JSON。

## 风格处理

- 顶部采用居中大标题、双侧斜角导航、弧线与白色装饰条。
- KPI 区改为 5 张横向科技蓝指标卡，保留项目总数、完成率、质量、技术变更和高风险指标。
- 中部改为 4 + 4 面板网格，覆盖项目节点、质量趋势、技术变更趋势、项目风险、科室节点、质量占比、科室技术变更、风险类型占比。
- 底部使用项目进度详情甘特组件，复用 v4 的项目节点 SQL。

## 数据源

- KPI 与项目节点/风险/甘特复用 gpmc-overview-v3 的 SQL。
- 质量占比/趋势复用或改写 gpmc-quality-board-v3 的 SQL。
- 技术变更趋势/科室技术变更复用 gpmc-tech-state-board-v3 的数据表字段。
