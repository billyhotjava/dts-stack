# Sprint-22: 前端技术栈统一

**时间**: 2026-03
**状态**: IN_PROGRESS
**目标**: 统一两个前端应用的技术栈 — React 19.1 + ECharts + Tailwind CSS

## 背景

dts-platform-webapp 和 dts-analytics-webapp/modern 技术栈存在分裂：
- React 版本不同（18.2 vs 19.1）
- 图表库不同（ApexCharts vs ECharts）
- CSS 方案不同（Tailwind vs 纯 CSS + BEM）

统一后降低维护成本，确保全平台视觉一致。

## Feature 列表

| ID | Feature | 状态 |
|----|---------|------|
| F1 | React 19.1 升级 (platform-webapp) | DONE |
| F2 | ApexCharts → ECharts (platform-webapp) | DONE |
| F3 | Tailwind 安装配置 (analytics-webapp) | DONE |
| F4 | Design Token 对齐 | DONE |
| F5 | 通用组件 Tailwind 迁移 (29 文件) | DONE |
| F6 | 大屏编辑器 Tailwind 迁移 (~50 文件) | DONE |
| F7 | 清理旧 CSS + 页面级迁移 | DONE |

## 已完成的改动

### F1: React 19.1 升级
- `package.json`: react/react-dom/types 升级到 ^19.1.0
- 6 处 `React.FC` → 普通函数 + 类型注解
- TypeScript 编译通过

### F2: ApexCharts → ECharts
- `package.json`: 移除 apexcharts/react-apexcharts，添加 echarts/echarts-for-react
- `components/chart/chart.tsx`: 重写为 ReactECharts wrapper
- `components/chart/useChart.ts`: 重写为 ECharts 基础配置 (含暗色主题)
- `pages/ops/OpsOverviewPage.tsx`: ApexOptions → EChartsOption

### F3: Tailwind 安装配置
- `package.json`: 添加 tailwindcss + @tailwindcss/vite
- `vite.config.ts`: 添加 tailwindcss() plugin
- `styles.css`: 添加 `@import "tailwindcss" layer(utilities) theme(static)` — preflight 禁用

### F4: Design Token 对齐
- `styles.css`: 通过 `@theme` 指令将 200+ CSS Variables 映射到 Tailwind theme token
- 映射覆盖: surface 颜色、文本颜色、边框、圆角、阴影

### F5: 通用组件迁移（进行中）
已完成:
- `components/EmptyState.tsx` — inline style → Tailwind
- `components/ErrorNotice.tsx` — .card/.btn/.muted → Tailwind

待完成 (按优先级):
1. layouts/AppLayout.tsx + layout.css
2. components/PageContainer/ + PageContainer.css
3. query 组件 (9 文件): FieldPicker, StepCard, DataSourceStep, PickColumnsStep, SortLimitStep, FilterStep, SummarizeStep, TableSearchPicker, JoinStep
4. chart 组件 (11 文件): ChartLegend, ChartTooltip, ScalarChart, ChartRenderer, ChartSettings, PieChart, AreaChart, BarChart, LineChart, EChartsRuntime
5. 其他: DashboardGrid, DataTable, NotebookEditor, QueryBuilder, UploadedDataEditor, ErrorBoundary

## 边界规则 (遵循 spec/frontend-styling/01-ant-design-tailwind-boundary.md)

- AntD 管组件（表单、表格、弹窗、菜单等），不用 Tailwind 重写
- Tailwind 管布局（flex/grid/间距/容器）
- preflight 禁用
- 不使用 `important: true`
- 内联定位样式保留（动态计算的 left/top/width/height）
