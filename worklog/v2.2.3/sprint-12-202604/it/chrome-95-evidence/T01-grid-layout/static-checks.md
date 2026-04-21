# T01 静态兼容性检查证据（2026-04-21）

## 1. 版本选型

项目已安装：
- `react-grid-layout@^1.5.3`
- `@types/react-grid-layout@^1.3.6`

无需新增依赖。放弃原 task 描述里的 "需要 pnpm add"。

## 2. Chrome 95 静态兼容检查

### 2.1 源代码 grep（未命中 = 通过）

```bash
grep -rlnE '@container|:has\(' \
  node_modules/react-grid-layout/ \
  node_modules/react-resizable/ \
  node_modules/react-draggable/

grep -rlnE 'structuredClone|requestIdleCallback\(' \
  node_modules/react-grid-layout/build/ \
  node_modules/react-resizable/build/
```

两个命令均无命中，证明依赖未使用 Chrome 95 不支持的 API。

### 2.2 CSS 检查

`node_modules/react-grid-layout/css/styles.css` 仅用：
- `position: relative/absolute`
- `transition: all/transform/width/height`
- `will-change: width/height`
- `user-select: none`
- `pointer-events: none`

全部 Chrome 95 原生支持。

## 3. 现有用法参考

项目已有 3 处使用 RGL：
- `src/analytics/components/DashboardGrid/DashboardGrid.tsx`
- `src/analytics/pages/dashboard/DashboardEditorGrid.tsx`
- `src/analytics/pages/DashboardEditorPage.tsx`

这些是老 Metabase fork 的 Dashboard 编辑器，Sprint-11 等已在客户 Chrome 95 环境运行过，间接证实 RGL 在 Chrome 95 可工作。

## 4. v2 smoke 页

路径：`src/analytics/pages/screens/v2/__dev__/GridLayoutSmoke.tsx`
路由：`/bi/__dev__/grid-smoke`（仅 `import.meta.env.DEV` 为 true 时挂载）

布局：4 个组件（2 个 KPI 卡 + 1 图表 + 1 表格）在 12 列 grid 上。支持拖动和 resize。

## 5. 实机验证（留给 IT 阶段）

静态检查通过 → F1/T02 可继续开工。
客户 Chrome 95 实机验证推迟到 Sprint 阶段 5（IT）统一跑，证据放 `evidence/TC-*/`。

本 task 不阻塞后续 Feature。
