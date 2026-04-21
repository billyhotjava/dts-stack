# F4: 组件内部响应式

**优先级**: P0
**状态**: READY
**依赖**: F1

## 目标

每个组件在容器尺寸变化时**内部自适应**，而不是靠外层 `transform: scale()` 拉伸。核心手段：

- **图表（ECharts）**：监听容器 resize，调用 `chart.resize()`
- **文字 / KPI 数值**：字号用 `clamp()` 或根据容器宽度计算
- **图片**：`object-fit: contain/cover`
- **Table**：列宽、行高、分页按容器适应

## Chrome 95 兼容

- `ResizeObserver` Chrome 95 原生支持 ✅
- `clamp() / min() / max()` ✅
- `@container queries` ❌ **禁用**，统一用 ResizeObserver + React state 来驱动

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | ECharts 组件 autoResize（共用 hook） | P0 | READY | — |
| T02 | 文字 / KPI 自适应字号 | P0 | READY | — |
| T03 | 图片组件 object-fit 默认 contain | P1 | READY | — |
| T04 | Table 响应式（列宽 / 行高 / 分页） | P1 | READY | — |

## 完成标准

- [ ] 所有图表类组件在父容器 resize 时自动 chart.resize()
- [ ] 文字类组件字号按宽度自适应，不会溢出容器
- [ ] 图片保持比例、不变形
- [ ] Table 不横向溢出且可滚动
