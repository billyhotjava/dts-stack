# T03: CandidateNode 拖拽虚影

**优先级**: P0
**状态**: DONE
**依赖**: T01

## 目标

从 BlockSelectorPanel 拖出 item 时，跟随光标显示半透明候选节点虚影（"幽灵节点"），让用户知道"我现在拖的是 Source / Transform / ..."。

## 技术设计

### 思路

1. Panel item dragstart 时记录 dataTransfer，并通过 `setDragImage` 设置自定义虚影 DOM
2. WorkflowCanvas 注册 onDragOver / onDrop 处理落点
3. 落点：把 dragImage 转成真节点

### CandidateNode

```tsx
// src/components/workflow/candidate-node.tsx
export function CandidateNode({ block }: { block: BlockDef }) {
  return (
    <div className="candidate-node" data-type={block.type}>
      {block.icon}
      <span>{block.label}</span>
    </div>
  );
}
```

实际实现采用浏览器原生 `dataTransfer.setDragImage` + `.candidate-node-ghost` 离屏 DOM，避免为了虚影单独引入 React portal 状态；落点创建由 `createWorkflowNodeFromBlock` 统一生成节点。

### dragImage 注入

```tsx
const onDragStart = (e, block) => {
  e.dataTransfer.setData('application/x-workflow-block', JSON.stringify(block));
  // 渲染虚影到 off-screen DOM
  const ghost = document.createElement('div');
  ghost.className = 'candidate-node-ghost';
  ghost.innerText = block.label;
  document.body.appendChild(ghost);
  e.dataTransfer.setDragImage(ghost, 50, 25);
  setTimeout(() => document.body.removeChild(ghost), 0);
};
```

### Canvas onDrop

```tsx
const onDrop = (e: DragEvent) => {
  e.preventDefault();
  const raw = e.dataTransfer.getData('application/x-workflow-block');
  if (!raw) return;
  const block: BlockDef = JSON.parse(raw);
  const position = reactFlowInstance.screenToFlowPosition({ x: e.clientX, y: e.clientY });
  addNode(createNode(block, position));
};
```

## 影响范围

- 新增 `src/components/workflow/candidate-node.tsx`
- 修改 BlockSelectorItem dragstart（T01 文件）
- 修改 WorkflowCanvas 注入 onDragOver / onDrop

## 验证

- [x] 从 panel 拖出 item，光标旁显示虚影
- [x] 拖到画布释放 → 节点出现在落点（屏幕坐标转 flow 坐标准确）
- [x] 拖到 panel 自身释放 → 不创建节点（默认行为）
- [x] ESC / 拖出窗口外 → 不创建
- [ ] Chrome 95：dragImage 兼容（注：iOS Safari 不支持，但桌面 Chrome OK；待 F2 收尾手工冒烟）

## 完成标准

- [x] 虚影样式半透明 + 阴影，和 Dify 接近
- [x] 落点坐标通过 `screenToFlowPosition` 转换
- [x] 单元测试：drag payload 解析 + `createWorkflowNodeFromBlock` 节点生成

## 实施记录（2026-05-05）

- `BlockSelectorItem` dragstart 写入 `application/x-workflow-block` 并设置 `.candidate-node-ghost` drag image
- `WorkflowCanvas` 接入 `onDragOver` / `onDrop`，释放到画布后创建真实节点
- 新增 `block-selector/create-node.ts`，供 drop / 后续 popover 共用
