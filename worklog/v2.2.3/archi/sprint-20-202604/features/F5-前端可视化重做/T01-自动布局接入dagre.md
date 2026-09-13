# T01: dagre/elk 自动布局接入

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标

把 LineagePage 当前固定栅格布局换成 dagre 自动分层布局，保留 layer 维度作为视觉提示但不强制对齐 X。

## 技术设计

### 库选型

| 库 | 评价 |
|---|---|
| **@dagrejs/dagre** | 经典分层布局，与 reactflow 配合最简单 ✅ |
| elkjs | 更强但配置复杂 |
| 自研 | 不必 |

引入 `@dagrejs/dagre`（reactflow 官方 example 即用此库）。

### 实现方案

新增 `src/pages/catalog/lineage/utils/auto-layout.ts`：

```typescript
import dagre from '@dagrejs/dagre';
import type { Node, Edge } from '@xyflow/react';

export type Direction = 'LR' | 'TB';

export function applyDagreLayout(
  nodes: Node[],
  edges: Edge[],
  direction: Direction = 'LR',
): { nodes: Node[]; edges: Edge[] } {
  const g = new dagre.graphlib.Graph();
  g.setDefaultEdgeLabel(() => ({}));
  g.setGraph({ rankdir: direction, nodesep: 60, ranksep: 120 });

  nodes.forEach((n) => g.setNode(n.id, { width: 180, height: 60 }));
  edges.forEach((e) => g.setEdge(e.source, e.target));
  dagre.layout(g);

  const layouted = nodes.map((n) => {
    const { x, y } = g.node(n.id);
    return { ...n, position: { x: x - 90, y: y - 30 } };
  });
  return { nodes: layouted, edges };
}
```

### LineagePage 改造

- 删除现有的"按 layer 写死 X"代码（约 L223-229）
- 新增方向 toggle：横向 LR / 纵向 TB（默认 LR）
- 数据加载完成后调用 `applyDagreLayout`
- 提供"重新布局"按钮

### 兼容现有 layer 颜色

dagre 决定位置，layer 颜色仍按节点 layer 字段（ODS/DWD/DWS/ADS/DIM）填充，作为视觉提示。

### 性能

- 节点 < 300 时同步布局
- 节点 ≥ 300 时用 `requestIdleCallback` 异步布局，期间显示 loading
- 浏览器版本兜底：requestIdleCallback 不存在则 `setTimeout(0)` 替代

## 影响范围

- 新增 `src/pages/catalog/lineage/utils/auto-layout.ts`
- 修改 `src/pages/catalog/LineagePage.tsx` —— 替换布局逻辑（约 L223-229）
- 修改 `package.json` —— 引入 `@dagrejs/dagre`
- 新增 `src/pages/catalog/lineage/utils/auto-layout.test.ts`

## 验证

- [ ] 单测：50 节点 + 80 边布局后所有节点 position 不重叠
- [ ] 单测：方向 LR vs TB 节点位置正确变化
- [ ] 浏览器手测：LineagePage 切换方向、重新布局正常
- [ ] 200 节点性能：布局耗时 < 500ms
- [ ] 无环验证：dagre 不支持环，遇环 fallback 提示用户
- [ ] 视觉走查：layer 颜色仍正常显示

## 完成标准

- [ ] dagre 接入完成
- [ ] 方向 toggle 可用
- [ ] 性能达标
- [ ] 无视觉回归（与现有截图人工比对）
