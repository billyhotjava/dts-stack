# F1: 响应式布局引擎（核心）

**优先级**: P0
**状态**: READY
**依赖**: —

## 目标

引入网格化响应式布局引擎（`react-grid-layout`），建立新的渲染管道，让 v2 大屏在任意 viewport 下按 grid units 自动计算组件位置和尺寸。本 Feature 只做**渲染侧**，不碰编辑器。

## 背景

v1 用 `transform: scale()` 整体缩放，本质是图像拉伸。v2 用 grid 布局，组件在浏览器原生布局流中摆位，viewport 变化时组件位置和尺寸**各自重算**，内部 DOM 是真实像素（图表文字都清晰）。

`react-grid-layout` 是 Grafana/Superset/Redash 都用的行业标准，成熟、社区大、Chrome 95 兼容（验收项）。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 引入 react-grid-layout + Chrome 95 验证 + 选型 | P0 | READY | — |
| T02 | 创建 ResponsiveScreenLayout 渲染组件（预览/展示共用） | P0 | READY | T01 |
| T03 | 集成到 ScreenPreviewPage / PublicScreenPage，v2 分支走新渲染 | P0 | READY | T02, F2 |

## 完成标准

- [ ] `react-grid-layout`（或等价）选型敲定并通过 Chrome 95 测试
- [ ] `ResponsiveScreenLayout` 组件能吃 v2 ScreenConfig 渲染出完整大屏
- [ ] ScreenPreviewPage / PublicScreenPage 按 `screen.version` 分流到 v1 或 v2 渲染
- [ ] v2 大屏在 viewport resize 时组件自动重新排布（不是整体 scale）
