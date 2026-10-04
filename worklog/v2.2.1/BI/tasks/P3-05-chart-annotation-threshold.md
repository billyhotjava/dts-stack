# P3-05 图表标注与阈值线

`status`: `done`
`priority`: `P3`
`sprint`: `Sprint 2 - 体验优化`
`inspiration`: `DataEase(辅助线/标记区域) + Grafana(threshold) + 业务分析阈值需求`

## 目标

为折线图/柱状图等轴类图表新增辅助线（markLine）、标记区域（markArea）和条件着色能力，满足"目标达成线"、"异常区间"等业务分析场景。

## 子任务

### 1. 辅助线（markLine）

**配置新增** (`config.markLines`):
```typescript
interface ChartMarkLine {
  type: 'value' | 'average' | 'min' | 'max';  // 固定值 or 统计量
  value?: number;              // type='value' 时的固定值
  name?: string;               // 标签文本
  color?: string;              // 线条颜色
  lineStyle?: 'solid' | 'dashed' | 'dotted';
  axis?: 'x' | 'y';           // 在哪个轴上画线，默认 y
  label?: string;              // 自定义标签
}
```

**支持图表**: line-chart, bar-chart, scatter-chart, combo-chart

**实现**:
- 在 ECharts series 配置中注入 `markLine` 属性。
- `type: 'average'` 使用 ECharts 内置 `{ type: 'average' }`。
- `type: 'value'` 使用 `{ yAxis: value }` 或 `{ xAxis: value }`。

### 2. 标记区域（markArea）

**配置新增** (`config.markAreas`):
```typescript
interface ChartMarkArea {
  name?: string;
  from: number;       // 起始值
  to: number;         // 结束值
  axis?: 'x' | 'y';
  color?: string;     // 区域填充色（含透明度）
  label?: string;
}
```

**用途**: 标记"安全区间"、"目标范围"、"异常区间"。

### 3. 系列条件着色

**配置新增** (`config.conditionalColors`):
```typescript
interface SeriesConditionalColor {
  operator: '>' | '>=' | '<' | '<=' | '==' | 'between';
  value: number;
  valueTo?: number;   // between 模式的上限
  color: string;
}
```

**实现**:
- 柱状图：基于 `itemStyle.color` 回调函数，根据数据值匹配规则动态着色。
- 折线图：分段式颜色（`visualMap` pieces 映射）。

### 4. 属性面板配置 UI

**文件**: `PropertyPanel.tsx`

新增"标注"折叠区（仅对轴类图表显示）：
- 辅助线列表：增删改，每行配置类型/值/颜色/样式。
- 标记区域列表：增删改，每行配置范围/颜色/标签。
- 条件着色规则列表：增删改，每行配置条件/颜色。

### 5. 预览与导出一致性

- 标注在 ECharts 中属于原生能力，PNG/PDF 导出时自动包含。
- 确认 ECharts server-render 路径也支持 markLine/markArea。

## Chrome 95 兼容性

- ECharts markLine / markArea / visualMap 均为内置能力，Chrome 95 ✅。

## 验收标准

- 可添加至少 3 条辅助线（含平均线、固定值线）。
- 标记区域在图表上显示半透明着色区。
- 条件着色正确映射到柱状图/折线图。
- 导出 PNG 包含标注。
- Chrome 95 下正常渲染。

## 风险与回滚

- 风险：过多标注导致图表视觉噪声。
- 回滚：属性面板限制单图最多 5 条辅助线、3 个标记区域、5 条着色规则。
