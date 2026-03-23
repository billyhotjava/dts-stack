# T02: KPI 卡片下钻

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
所有 KPI 卡片（HealthScoreCard）点击后打开 DrillDownDrawer，展示对应维度的明细任务列表

## 技术设计

### 1. HealthScoreCard 增加 onClick
- 为 `HealthScoreCard` 组件增加 `onDrill?: () => void` prop
- 有 `onDrill` 时显示 `cursor: pointer` 和悬停效果
- 点击时调用 `onDrill()`

### 2. 各 View 接入下钻
在 `OverviewTrendView` / `ExecutionView` / `RiskAttributionView` 中：
- 为每个 KPI 卡片绑定 `onDrill` → `setDrillState({ target, params })`
- 下钻目标映射：

| KPI | target | params |
|-----|--------|--------|
| 高风险节点数 | `high-risk` | `{ riskLevel: '高' }` |
| 延期节点数 | `overdue` | `{ status: 'overdue' }` |
| 节点完成率 | `completion` | `{ }` |
| 里程碑完成率 | `milestone` | `{ nodeType: 'milestone' }` |

### 3. DrillDownDrawer 内容渲染
按 `target` 渲染对应 Table：
- `high-risk`: 节点名、所属项目、风险等级、负责部门、延期天数
- `overdue`: 节点名、计划日期、实际日期、延期天数、延期原因
- `completion` / `milestone`: 节点名、状态、进度%、计划日期

## 影响范围
- `components/HealthScoreCard.tsx` — 增加 onDrill prop
- `views/OverviewTrendView.tsx` — 绑定下钻
- `views/ExecutionView.tsx` — 绑定下钻
- `views/RiskAttributionView.tsx` — 绑定下钻
- `components/DrillDownDrawer.tsx` — 增加按 target 的 Table columns 配置

## 验证
- [ ] 点击"高风险 8" → Drawer 展示 8 条高风险节点
- [ ] 点击"延期 17" → Drawer 展示 17 条延期节点
- [ ] Drawer 内 Table 可排序、可搜索
- [ ] 无 KPI 数据时卡片不可点击

## 完成标准
- [ ] 所有 KPI 卡片均支持下钻
- [ ] Drawer 数据与 KPI 数值一致
