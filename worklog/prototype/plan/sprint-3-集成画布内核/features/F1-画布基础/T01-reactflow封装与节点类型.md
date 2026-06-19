# T01: reactflow 封装 + 5 类节点类型定义

**优先级**: P0
**状态**: READY
**依赖**: S1

## 目标

封装 `@xyflow/react` v12 为项目内 `EltCanvas` 组件，并集中定义"源表 / 清洗 / 连接(join) / 聚合 / 输出"5 类节点类型。

## 技术设计

- 库：`@xyflow/react` v12（现网同款），参考现网 `src/components/visual-canvas/VisualFlowCanvas.tsx` 的封装方式（`ReactFlow` + `ReactFlowProvider` + `useReactFlow`，`import "@xyflow/react/dist/style.css"`）。
- 文件：
  - `app/src/canvas/EltCanvas.tsx`：内层 `EltCanvasInner` 用 `useReactFlow`，外层用 `ReactFlowProvider` 包裹（对齐现网 inner/provider 拆分）。
  - `app/src/canvas/nodeTypes.ts`：注册自定义节点类型映射 `nodeTypes`（值为 F2 的卡片组件占位，本 task 先用最小占位渲染）。
  - `app/src/canvas/types.ts`：节点类型枚举常量 `ELT_NODE_KINDS = { SOURCE, CLEAN, JOIN, AGGREGATE, OUTPUT }`，节点 data 形状（kind/name/status/rowCount）。
- 关键实现点：
  - 每个画布入口模块顶部 `import "@/polyfills/legacy-browser"`（对齐现网，Chrome 95）。
  - 布局用 flex/grid 固定栏 + `ResizeObserver` 感知容器尺寸传给画布高度；**不**用 `:has()`/容器查询/subgrid。
  - 颜色/状态点走 Swiss token（HSL/hex），禁 oklch。
  - 开启 `onlyRenderVisibleElements` 为后续虚拟化打底。

## 影响范围

- 文件：`app/src/canvas/EltCanvas.tsx`、`nodeTypes.ts`、`types.ts`
- 组件：`EltCanvas`
- 依赖：S1 设计系统 token、polyfill；下游 F1-T02/T03/T04、F2 全部 task

## 验证

- [ ] `EltCanvas` 挂载后渲染出空画布（含 ReactFlowProvider），无控制台报错。
- [ ] 5 类节点类型常量可被 import，传入对应 kind 的节点能按自定义类型渲染（占位即可）。
- [ ] 容器 resize 时画布尺寸跟随（ResizeObserver 生效），无 `:has()`/容器查询。
- [ ] legacy（chrome>=95）构建通过。

## 完成标准

- [ ] `EltCanvas` 封装完成并对齐现网 inner/provider 结构。
- [ ] 5 类节点类型集中定义、命名对齐现网、注册到 `nodeTypes`。
- [ ] 无 oklch/`:has()`/容器查询/subgrid。
