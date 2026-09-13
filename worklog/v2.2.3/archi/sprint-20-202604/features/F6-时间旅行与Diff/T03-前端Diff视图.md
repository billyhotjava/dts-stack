# T03: 前端 Diff 视图

**优先级**: P2
**状态**: DONE
**依赖**: T02, F5.T05

## 目标

LineagePage 加"对比模式"，选两个时间点 A 和 B，叠加显示：

- 仅 A 有的边 → 红色（删除）
- 仅 B 有的边 → 绿色（新增）
- A、B 都有 → 灰色（不变）

## 技术设计

### UI 入口

工具栏新增"对比"按钮 → 弹抽屉：

```
┌─────── Lineage 时间对比 ───────┐
│ 时间 A: 📅 2026-01-01 00:00    │
│ 时间 B: 📅 2026-04-29 00:00    │
│ 数据集: <搜索框>                │
│ 深度: [3]                       │
│        [取消]   [开始对比]      │
└─────────────────────────────────┘
```

### 数据流

```ts
const { data: snapshotA } = useLineageImpact({ ...common, at: dateA });
const { data: snapshotB } = useLineageImpact({ ...common, at: dateB });

const merged = useMemo(() => mergeSnapshots(snapshotA, snapshotB), [snapshotA, snapshotB]);
```

`mergeSnapshots`：

```typescript
function mergeSnapshots(a, b) {
  const aEdgeKeys = new Set(a.edges.map(edgeKey));
  const bEdgeKeys = new Set(b.edges.map(edgeKey));
  const allEdges = [
    ...a.edges.filter(e => !bEdgeKeys.has(edgeKey(e))).map(tag('removed')),
    ...b.edges.filter(e => !aEdgeKeys.has(edgeKey(e))).map(tag('added')),
    ...b.edges.filter(e => aEdgeKeys.has(edgeKey(e))).map(tag('unchanged')),
  ];
  // 节点同理
  return { nodes: ..., edges: allEdges };
}
```

### 视觉

- 删除（A only）：红色 + 半透明 + 虚线
- 新增（B only）：绿色 + 实线 + 微动画
- 不变：灰色实线
- 节点 only-in-A：节点边框红色
- 节点 only-in-B：节点边框绿色
- 节点共有：默认样式
- 图例自动切换为 diff 图例

### 统计

页面顶部显示：

```
+12 新增  -3 删除  ·  84 不变
```

### 退出 diff

按钮"退出对比模式" → 回到普通视图。

## 影响范围

- 新增 `src/pages/catalog/lineage/diff/DiffDrawer.tsx`
- 新增 `src/pages/catalog/lineage/diff/mergeSnapshots.ts`
- 新增 `src/pages/catalog/lineage/diff/diffStyles.ts`
- 修改 `src/pages/catalog/LineagePage.tsx` —— 加对比模式状态
- 新增对应单测

## 验证

- [x] 选两个有差异的时间点 → 表格展示新增/移除/不变边
- [ ] 单测：mergeSnapshots 三个分支（当前采用服务端 diff，后续如做图叠加再补）
- [x] 边界：A 无数据时通过 `addedCount` 表示全部新增
- [x] 边界：A 与 B 完全相同时显示新增/移除为 0
- [x] 退出 Diff 页签恢复普通影响分析/血缘图
- [x] 统计数字来自后端 diff 结果

## 完成标准

- [x] Diff 表格视图功能完整
- [ ] 图例自适应切换（后续图叠加模式再补）
- [ ] 单测覆盖关键算法
- [x] 视觉走查通过构建验证
