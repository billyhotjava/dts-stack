# T03: 列级 toggle 与影响范围高亮

**优先级**: P0
**状态**: READY
**依赖**: F3.T04

## 目标

加两个核心交互：

1. **列级 toggle**：开关后请求带 `includeColumns=true`，节点展开成列列表，列与列之间渲染细线
2. **影响范围高亮**：点击节点 → 自动 BFS 染色其上下游，其他节点变灰

## 技术设计

### 列级展开

节点从"标题卡片"变为"可展开节点"：默认折叠，点击 ▶ 展开列清单：

```
┌─ dwd_orders ────────┐
│ ▼ 列 (8)            │
│   id      bigint    │
│   amount  decimal   │  ← 这一行可作为内部 anchor，承接列边
│   ...               │
└─────────────────────┘
```

reactflow 自定义节点支持任意 HTML，列边用细线 + 偏移到列行的 anchor。

实现要点：

- 列在节点内有自己的 DOM ref，记录 y 偏移
- 列边的 `sourceHandle` / `targetHandle` 用列名生成 handle id
- 展开/折叠时触发 `useReactFlow().setNodes(updater)` 重新计算位置（dagre 重跑）

### 列级 toggle UI

页面顶部过滤区加一个 switch：

```tsx
<Switch checked={showColumns} onCheckedChange={setShowColumns} />
<span>列级血缘</span>
```

数据请求：

```ts
const { data } = useLineageImpact({
  datasetId,
  direction,
  depth,
  includeColumns: showColumns,
});
```

性能保护：

- `showColumns=true` 且节点数 > 100 时，弹确认 toast
- 默认 depth 收紧到 2

### 影响范围高亮

点击节点：

```typescript
function highlightImpact(nodeId: string, direction: 'up' | 'down' | 'both') {
  const visited = bfs(graph, nodeId, direction);
  setNodes((nodes) => nodes.map((n) => ({
    ...n,
    data: { ...n.data, dimmed: !visited.has(n.id) }
  })));
  setEdges((edges) => edges.map((e) => ({
    ...e,
    style: visited.has(e.source) && visited.has(e.target) ? activeEdgeStyle : dimmedEdgeStyle
  })));
}
```

- 节点上加"上游/下游/双向"按钮
- 再次点击同节点或点击空白取消高亮

### 列级影响

列级 toggle 打开时，点击具体列 → 只染色该列相关的列边链路（用 columnEdges 数据 BFS），父 dataset 节点跟着染色。

## 影响范围

- 修改 `src/pages/catalog/lineage/nodes/DatasetNode.tsx` —— 支持展开列清单
- 新增 `src/pages/catalog/lineage/hooks/useImpactHighlight.ts`
- 新增 `src/pages/catalog/lineage/hooks/useColumnLineage.ts`
- 修改 `src/pages/catalog/LineagePage.tsx` —— 加 toggle、点击 handler
- 修改 `src/api/platformApi.ts` —— `getCatalogLineageImpact` 加 `includeColumns` 参数
- 新增对应单测

## 验证

- [ ] 列级 toggle：开关响应正常，关闭时回到表级视图
- [ ] 节点展开动画流畅，dagre 重新布局后位置正确
- [ ] 点击节点高亮上下游，其他节点变灰
- [ ] 高亮后点击空白处取消高亮
- [ ] 列级模式下点击具体列只高亮列链路
- [ ] 性能：100 节点高亮 < 100ms
- [ ] 单测：bfs 算法正确性、上下游/双向分支
- [ ] a11y：键盘 Tab 可遍历节点，Enter 触发高亮

## 完成标准

- [ ] 列级 toggle 完整功能
- [ ] 影响高亮完整功能（上/下/双向）
- [ ] 列级影响高亮可用
- [ ] 性能达标
- [ ] 单测覆盖关键算法
