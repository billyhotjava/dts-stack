# P3-01 组合图与高级图表类型扩展

`status`: `done`
`priority`: `P3`
`sprint`: `Sprint 1 - 核心图表扩展`
`inspiration`: `DataEase(40+ 图表类型) + 商业 BI 交付覆盖度要求`

## 目标

将图表类型从 8 种扩展到 18+ 种，补齐组合图和高频业务图表，达到行业中游水平。

## 差距分析

| 图表类型 | DTS 当前 | DataEase | GoView | 优先级 |
|---------|:---:|:---:|:---:|:---:|
| 柱线组合 | ❌ | ✅ | ❌ | 🔴 必须 |
| 双 Y 轴 | ❌ | ✅ | ❌ | 🔴 必须 |
| 堆叠柱状图 | ❌ | ✅ | ❌ | 🔴 必须 |
| 瀑布图 | ❌ | ✅ | ❌ | 🟡 重要 |
| 词云 | ❌ | ✅ | ❌ | 🟡 重要 |
| 旭日图 | ❌ | ✅ | ❌ | 🟡 重要 |
| 矩形树图 | ❌ | ✅ | ❌ | 🟡 重要 |
| 桑基图 | ❌ | ✅ | ❌ | 🟢 可选 |
| K 线图 | ❌ | ✅ | ❌ | 🟢 可选 |
| 水平条形图 | ❌ | ✅ | ✅ | 🔴 必须 |

## 子任务

### 1. 组合图（柱线混合 + 双 Y 轴）

**新增组件类型**: `combo-chart`

**文件改动**:
- `types.ts` — 新增 `combo-chart` 到组件类型联合
- `componentLibrary.ts` — 新增组合图入口与默认配置
- `renderers/shared/chartUtils.ts` — 新增双 Y 轴配置生成辅助函数
- `ComponentRenderer.tsx` — 新增 `combo-chart` case，支持：
  - 多系列类型混合（bar + line）
  - 左右双 Y 轴独立刻度
  - 系列与 Y 轴绑定关系配置
- `PropertyPanel.tsx` — 新增组合图属性区：系列类型选择、Y 轴绑定

**配置结构**:
```typescript
{
  type: 'combo-chart',
  config: {
    title: string,
    xAxisData: string[],
    series: Array<{
      name: string,
      type: 'bar' | 'line',
      yAxisIndex: 0 | 1,  // 左轴 or 右轴
      data: number[],
    }>,
    yAxis: [
      { name: string, min?: number, max?: number },
      { name: string, min?: number, max?: number },
    ],
  },
}
```

### 2. 堆叠柱状图 / 堆叠面积图

**方案**: 在现有 `bar-chart` 和 `line-chart` 上增加 `stack` 属性，无需新增组件类型。

**文件改动**:
- `ComponentRenderer.tsx` — bar-chart/line-chart case 读取 `config.stackMode`
- `PropertyPanel.tsx` — 新增堆叠模式选项（`off` / `stack` / `percent-stack`）
- `componentLibrary.ts` — 新增堆叠柱状图/堆叠面积图快捷入口

### 3. 水平条形图

**方案**: 在现有 `bar-chart` 基础上增加 `horizontal` 布尔属性，X/Y 轴互换。

**文件改动**:
- `ComponentRenderer.tsx` — bar-chart case 根据 `config.horizontal` 交换 xAxis/yAxis
- `PropertyPanel.tsx` — 新增"水平方向"开关
- `componentLibrary.ts` — 新增水平条形图快捷入口

### 4. 词云图

**新增组件类型**: `wordcloud-chart`

- 引入 `echarts-wordcloud` 插件（ECharts 扩展）。
- 支持数据绑定：`[{name, value}]` 数组。
- 配置项：字体范围、旋转角度范围、形状（circle / cardioid / square）。

### 5. 矩形树图 / 旭日图

**新增组件类型**: `treemap-chart`、`sunburst-chart`

- 均为 ECharts 内置类型，无需额外依赖。
- 数据结构：树形 `{name, value, children: []}` 。
- 支持层级下钻（ECharts 内置 drilldown 能力）。

### 6. 瀑布图

**新增组件类型**: `waterfall-chart`

- 基于 ECharts bar 系列实现（透明底座 + 正负增量叠加）。
- 支持标记"合计"列。

### 7. 桑基图 / K 线图（后续批次）

- `sankey-chart`：流量关系图，ECharts 内置。
- `candlestick-chart`：K 线图，ECharts 内置。
- 优先级较低，可延后。

## Chrome 95 兼容性

- 所有新增图表基于 ECharts 5.x，Chrome 95 兼容 ✅。
- `echarts-wordcloud` 需确认最低浏览器支持。

## 验收标准

- 组合图支持柱/线混合 + 双 Y 轴，预览与导出一致。
- 堆叠模式在 bar-chart / line-chart 上可切换。
- 水平条形图可正确翻转轴。
- 词云/矩形树/旭日图可通过数据源绑定渲染。
- 所有新图表在 Chrome 95 下正常渲染。
- `bun x tsc --noEmit` 与 `bun run build` 通过。

## 风险与回滚

- 风险：新图表类型增多导致 ComponentRenderer 再次膨胀。
- 回滚：新图表全部在独立 renderer 文件中实现（`renderers/ComboChartRenderer.tsx` 等），ComponentRenderer 仅做分发。
