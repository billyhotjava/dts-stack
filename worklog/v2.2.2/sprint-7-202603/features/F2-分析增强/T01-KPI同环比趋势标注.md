# T01: KPI 同环比趋势标注

**优先级**: P1
**状态**: READY
**依赖**: 无

## 目标
KPI 卡片在数值旁显示环比变化（↑12% / ↓5%），让用户一眼看出指标变化方向

## 技术设计

### 1. 后端增加趋势数据
`getProjectCockpitSummary` 响应中，每个 KPI 指标增加：
```json
{
  "value": 8,
  "previousValue": 6,
  "changeRate": 0.333,
  "changeDirection": "up"
}
```

### 2. HealthScoreCard 增加趋势显示
- 新增 `trend?: { rate: number; direction: 'up' | 'down' | 'flat' }` prop
- 数值右侧渲染趋势箭头 + 百分比
- 颜色逻辑：
  - 正向指标（完成率）：up=green, down=red
  - 负向指标（高风险数）：up=red, down=green
  - 新增 `trendPolarity?: 'positive' | 'negative'` prop 控制

### 3. 趋势图叠加上期
- 趋势图增加虚线系列表示上期同周数据
- 通过 ApexCharts `stroke.dashArray` 区分实线（本期）和虚线（上期）

## 影响范围
- 后端 API: `ProjectCockpitSummary` DTO 增加 trend 字段
- `components/HealthScoreCard.tsx` — 增加 trend 渲染
- `views/OverviewTrendView.tsx` — 传递 trend prop + 趋势图叠加上期

## 验证
- [ ] KPI 卡片显示 ↑33% 或 ↓5% 标注
- [ ] 正向/负向指标颜色逻辑正确
- [ ] 趋势图显示上期虚线，不影响本期实线的可读性

## 完成标准
- [ ] 所有 KPI 卡片均有趋势标注
- [ ] 无上期数据时不显示趋势（graceful fallback）
