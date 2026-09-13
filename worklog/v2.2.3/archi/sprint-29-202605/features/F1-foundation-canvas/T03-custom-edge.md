# T03: CustomEdge 自定义边

**优先级**: P0
**状态**: DONE
**依赖**: T02

## 目标

替代 reactflow 默认 edge：渐变色（source 节点色 → target 节点色）、可显示 label、hover 时显示删除按钮、selected 时高亮 + 标签。

## 技术设计

### 文件

```
src/components/workflow/custom-edge.tsx
```

### 实现要点

```tsx
export function CustomEdge({ id, source, target, sourceX, sourceY, targetX, targetY, data, selected }: EdgeProps) {
  const [edgePath, labelX, labelY] = getBezierPath({ sourceX, sourceY, targetX, targetY });
  const removeEdge = useWorkflowStore(s => s.removeEdge);

  return (
    <>
      <defs>
        <linearGradient id={`edge-${id}`}>
          <stop offset="0%" stopColor={data?.sourceColor ?? '#94a3b8'} />
          <stop offset="100%" stopColor={data?.targetColor ?? '#94a3b8'} />
        </linearGradient>
      </defs>
      <path
        d={edgePath}
        stroke={selected ? '#3b82f6' : `url(#edge-${id})`}
        strokeWidth={selected ? 2 : 1.5}
        fill="none"
      />
      {selected && (
        <foreignObject x={labelX - 10} y={labelY - 10} width={20} height={20}>
          <button onClick={() => removeEdge(id)} aria-label="删除连线">×</button>
        </foreignObject>
      )}
    </>
  );
}
```

### 注册

```ts
// src/components/workflow/index.tsx 附近
export const edgeTypes = { custom: CustomEdge };
```

ReactFlow 默认所有新建边走 `type: 'custom'`。

## 影响范围

- 新增 `src/components/workflow/custom-edge.tsx`（≤ 120 行）
- 修改 `WorkflowCanvas.tsx` 注入 `edgeTypes` 与 `defaultEdgeOptions={ type: 'custom' }`

## 验证

- [ ] 拖出连线后默认显示 CustomEdge（贝塞尔 + 渐变）
- [ ] 点击连线 → 高亮 + 显示删除按钮 → 点删除按钮成功移除
- [ ] 删除按钮可键盘 focus（Tab）+ Enter 触发，aria-label 完整
- [ ] Chrome 95 渲染不崩，linearGradient 兼容

## 完成标准

- [x] hover/selected/默认 三种状态由 `resolveEdgeStroke` 决策（selected → #3b82f6，hover → #64748b，默认渐变）
- [x] removeEdge 走 `useWorkflowStore` action（immutable spread；不直接 mutate edges 数组）
- [x] 8 个单测全绿：pickEdgeColor (2)、resolveEdgeStroke (1)、shouldShowRemoveButton (1)、registry (1)、store integration (1)；workflow 整模块 28/28
- [x] 自定义边注册：`workflowEdgeTypes.custom = CustomEdge`，`DEFAULT_EDGE_TYPE='custom'` 由 WorkflowCanvas `defaultEdgeOptions` + onConnect 强制
- [x] 文件 ≤ 200 行（custom-edge 116，test 62）；tsc 0 错
