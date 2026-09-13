# T03: MetricCanvas + 节点/边类型

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

实现中栏 React Flow 画布，定义 BizObjectNode、MetricNode 和 MetricBindingEdge，支持拖拽、右键菜单和节点选中。

## 技术设计

```tsx
// src/pages/modeling/metric-workbench/MetricCanvas.tsx
// 使用 ReactFlow + ReactFlowProvider（参考 WorkflowCanvas 模式）
// nodeTypes: { bizObject: BizObjectNode, metric: MetricNode }
// edgeTypes: { binding: MetricBindingEdge }
// 自动布局: objects 按行排列 (x=0,y=0/180/360...), metrics 在右侧 (x=350)
// 右键菜单: 新建指标/预览/前往详情页（用 antd Dropdown）

// BizObjectNode 样式（Chrome 95 兼容）:
//   border: 2px solid hsl(220, 80%, 55%)
//   background: hsl(220, 95%, 97%)
//   内容: object.name + object.code + tableCount 徽标

// MetricNode 样式:
//   border: 2px solid hsl(142, 60%, 45%)
//   background: hsl(142, 80%, 96%)
//   DRAFT: border-color hsl(0, 0%, 70%)（灰色）
//   内容: metric.name + formulaType chip

// MetricBindingEdge: animated dashed line
```

新建文件：
- `src/pages/modeling/metric-workbench/MetricCanvas.tsx`（~200 行）
- `src/pages/modeling/metric-workbench/nodes/BizObjectNode.tsx`（~70 行）
- `src/pages/modeling/metric-workbench/nodes/MetricNode.tsx`（~70 行）
- `src/pages/modeling/metric-workbench/edges/MetricBindingEdge.tsx`（~40 行）

## 影响范围

- 新建上述 4 个文件
- `@xyflow/react/dist/style.css` 已在 WorkflowCanvas 引入，复用即可

## 验证

- [ ] 画布渲染 BizObjectNode 和 MetricNode
- [ ] MetricBindingEdge 连接两类节点
- [ ] 选中节点触发 `onNodeSelect(id)` → 右栏联动
- [ ] 右键菜单 Dropdown 出现（不崩溃即可）
- [ ] Chrome 95: 颜色全用 HSL，无 oklch
- [ ] tsc 零报错

## 完成标准

- [ ] 画布可正常渲染 20+ 节点无性能问题
- [ ] fitView 初始化后节点在视口内
- [ ] 节点颜色按 layer/status 区分
