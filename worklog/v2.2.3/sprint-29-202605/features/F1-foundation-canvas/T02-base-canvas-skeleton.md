# T02: WorkflowCanvas 基础壳

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

实现 `WorkflowCanvas` 组件：ReactFlowProvider + ReactFlow 实例 + Background + MiniMap + Controls，节点/边数据全部从 useWorkflowStore 取，viewport 持久化到 localStorage。

## 技术设计

### 文件

```
src/components/workflow/
├── index.tsx                  # 对外 export
├── WorkflowCanvas.tsx         # ReactFlow 主体
├── context.tsx                # 透传 readonly / onSave / projectId
└── styles/canvas.css          # 配色 + 边框（无 :has / @layer / oklch）
```

### WorkflowCanvas 主体

```tsx
export function WorkflowCanvas({ readonly = false }: Props) {
  const { nodes, edges, setNodes, setEdges, setViewport } = useWorkflowStore();

  return (
    <ReactFlowProvider>
      <div className="workflow-canvas">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={changes => applyNodeChanges(changes, setNodes)}
          onEdgesChange={changes => applyEdgeChanges(changes, setEdges)}
          onMoveEnd={(_, vp) => setViewport(vp)}
          nodeTypes={nodeTypes}      // F3 注入
          edgeTypes={edgeTypes}      // T03 注入
          connectionLineComponent={CustomConnectionLine}  // T04
          fitView
          minZoom={0.2}
          maxZoom={2}
        >
          <Background gap={16} />
          <MiniMap />
          <Operator />              // T06
          <HelpLine />              // T05
        </ReactFlow>
      </div>
    </ReactFlowProvider>
  );
}
```

### 配色（CSS 变量 + 不用 oklch）

```css
.workflow-canvas {
  --wf-bg: #fafbfc;
  --wf-grid: #e5e7eb;
  --wf-edge: #94a3b8;
  --wf-edge-selected: #3b82f6;
}
```

## 影响范围

- 新增 `src/components/workflow/WorkflowCanvas.tsx` 等 4 个文件
- 暂未集成到 OrchestrationPage（T07 才接入）

## 验证

- [ ] WorkflowCanvas 在临时 Storybook 或 demo 路由能渲染
- [ ] 缩放（鼠标滚轮）/ 平移（拖拽）/ fit view 正常
- [ ] viewport 改变后刷新页面位置保留
- [ ] Chrome 95 真机渲染不崩

## 完成标准

- [ ] WorkflowCanvas 文件 ≤ 200 行
- [ ] CSS 不用任何禁用 API（grep `:has(\|@layer\|oklch(` 命中 0）
- [ ] 透传 props 完整：`readonly`、`onSave`、`initialDsl`
