# F1: 画布基础

**优先级**: P0
**状态**: READY

## 目标

建立可视化 ELT 画布的运行内核：封装 `@xyflow/react` v12 并定义 5 类节点类型；节点面板支持 dnd-kit 拖入画布并带键盘 a11y 兜底；画布支持原生连线/平移/缩放/网格对齐/小地图；画布的节点/边/选中态由 zustand store 统一管理。完成后画布"能装、能拖、能连、能缩、有状态"。

## 背景

对应设计文档 §7。这是画布的地基层，F2 的节点卡片渲染与连接校验直接挂在本层的 nodeTypes / store 之上，Sprint 4 的配置抽屉/运行 dock/双视图也都消费本层的 store。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | reactflow 封装 + 5 类节点类型定义 | P0 | READY | S1 |
| T02 | 节点面板 + dnd-kit 拖入画布（含键盘 a11y 兜底） | P0 | READY | T01, T04 |
| T03 | 连线 / 平移 / 缩放 / 网格对齐 / 小地图 | P0 | READY | T01 |
| T04 | 画布状态 store（节点/边/选中态，zustand） | P0 | READY | S1 |

## 完成标准

- [ ] `EltCanvas`（封装 `@xyflow/react`）渲染于集成阶段页，固定栏布局用 flex/grid + ResizeObserver，无 `:has()`/容器查询。
- [ ] 5 类节点类型常量（源表/清洗/连接join/聚合/输出）定义集中、命名对齐现网，注册到 reactflow `nodeTypes`。
- [ ] 节点面板两种落点方式可用：dnd-kit 拖拽 + 键盘兜底（Tab 选中 → Enter/"添加到画布"按钮）。
- [ ] reactflow 原生连线/平移/缩放/snap-to-grid/MiniMap 全部生效，开启 `onlyRenderVisibleElements` 虚拟化。
- [ ] zustand store 持有 nodes/edges/selectedNodeId 并暴露 addNode/connect/select/reset；样例数据经 mock service 注入。
