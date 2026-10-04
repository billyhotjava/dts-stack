# T01: ECharts 组件 autoResize（共用 hook）

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标

统一 ECharts 图表组件在容器尺寸变化时调 `chart.resize()`，用一个共用 hook `useEchartsAutoResize`。

## 技术设计

### Hook API

```ts
// src/analytics/pages/screens/v2/hooks/useEchartsAutoResize.ts

import { useEffect } from 'react'
import type { ECharts } from 'echarts'

export function useEchartsAutoResize(
  chartRef: React.RefObject<ECharts | null>,
  containerRef: React.RefObject<HTMLElement | null>,
): void {
  useEffect(() => {
    const container = containerRef.current
    if (!container) return
    
    const observer = new ResizeObserver(() => {
      chartRef.current?.resize()
    })
    observer.observe(container)
    return () => observer.disconnect()
  }, [chartRef, containerRef])
}
```

### 改造现有图表组件

当前大屏组件里所有用 ECharts 的组件（`LineChart`, `BarChart`, `PieChart`, `RadarChart` 等），找它们的 ref 挂钩 hook：

```tsx
function MyChart() {
  const containerRef = useRef<HTMLDivElement>(null)
  const chartRef = useRef<ECharts | null>(null)

  useEchartsAutoResize(chartRef, containerRef)

  return <div ref={containerRef} style={{ width: '100%', height: '100%' }} />
}
```

### 性能注意

- ResizeObserver 每秒可触发很多次；`chart.resize()` 内部是 requestAnimationFrame 安全
- 不需要 debounce（ECharts 自己优化）

### ECharts 初始化

很多组件初始化时用了固定 `width/height`。要改成 `width: '100%', height: '100%'` + 父容器撑满。

## 影响范围

- 新增 `v2/hooks/useEchartsAutoResize.ts`
- 所有 ECharts 相关组件（位于 `components/ComponentRenderer.tsx` 和子组件）
- 具体清单（需要 grep）：line-chart, bar-chart, pie-chart, radar-chart, funnel-chart, gauge, scatter, heatmap, ...

## 验证

- [ ] 单元测试：hook 在容器 resize 时调 chart.resize
- [ ] 手工：打开一个带图表的 v2 大屏，拖浏览器窗口 → 图表自动 resize
- [ ] 性能：快速连续 resize 不卡顿

## 完成标准

- [ ] Hook 导出
- [ ] 所有图表组件接入
- [ ] 无视觉 glitch / 过度 re-render
