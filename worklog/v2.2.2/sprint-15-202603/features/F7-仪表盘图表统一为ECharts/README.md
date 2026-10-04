# F7: 仪表盘图表统一为 ECharts

**优先级**: P1
**状态**: READY

## 目标
将仪表盘的 SVG 自研图表（LineChart/BarChart/PieChart/AreaChart/ScalarChart）替换为 ECharts 渲染，与大屏编辑器共享同一套图表引擎，统一主题和视觉风格

## 现状
- 仪表盘: 自研 SVG 图表（2,779 行，6 种类型）
- 大屏: ECharts（1,082 行 + ECharts 库，14+ 种类型）
- 数据格式: 两者均使用 CardData `{cols, rows}`（已统一）
- 差异: 仅渲染层不同

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | LineChart + AreaChart → ECharts | P1 | READY | - |
| T02 | BarChart → ECharts | P1 | READY | - |
| T03 | PieChart → ECharts | P1 | READY | - |
| T04 | ScalarChart → number-card 复用 | P2 | READY | - |
| T05 | ChartRenderer 适配 + 主题统一 | P1 | READY | T01-T04 |

## 完成标准
- [ ] 仪表盘所有图表使用 ECharts 渲染
- [ ] 视觉风格与大屏编辑器一致
- [ ] 仪表盘图表响应 CSS Variables 主题
- [ ] 现有仪表盘功能无回归
