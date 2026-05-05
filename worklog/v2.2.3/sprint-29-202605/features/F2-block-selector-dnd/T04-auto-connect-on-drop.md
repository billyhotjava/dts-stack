# T04: 落点自动连边

**优先级**: P0
**状态**: DONE
**依赖**: T03

## 目标

两种自动连边场景：
1. **从节点 + 号拖出后释放** → 自动 source → 新节点 target（T02 Popover 已实现，本任务做"拖拽" 版本）
2. **从 BlockSelectorPanel 拖到现有节点的 + 号附近** → 自动 source → 新节点 target

## 技术设计

### 场景 1：从节点 + 号拖拽

复用 reactflow 原生 connection 行为：拖出 + 号 → onConnectStart → CustomConnectionLine（T04 F1）→ onConnect 时如果落在空白处，弹出 BlockSelectorPopover：

```tsx
const onConnectEnd = (event: MouseEvent) => {
  const targetIsPane = event.target?.classList?.contains('react-flow__pane');
  if (targetIsPane && pendingSource) {
    // 在落点位置弹 Popover
    setPopoverPosition({ x: event.clientX, y: event.clientY });
    setPopoverSourceId(pendingSource);
  }
};
```

### 场景 2：拖到 + 号附近

DropZone：每个节点的 + 号扩大检测半径，drop 时记录 sourceId：

```tsx
// PlusHandle 接收 drop
function PlusHandle({ nodeId }) {
  const onDrop = (e) => {
    e.preventDefault();
    e.stopPropagation();
    const raw = e.dataTransfer.getData('application/x-workflow-block');
    if (!raw) return;
    const block = JSON.parse(raw);
    const sourceNode = nodes.find(n => n.id === nodeId);
    const newNode = createNode(block, { x: sourceNode.position.x + 250, y: sourceNode.position.y });
    addNode(newNode);
    addEdge({ id: nanoid(), source: nodeId, target: newNode.id, type: 'custom' });
  };
  return <button onDrop={onDrop} onDragOver={e => e.preventDefault()} className="plus-handle">+</button>;
}
```

## 影响范围

- 修改 WorkflowCanvas（F1-T02）注入 onConnectStart / onConnectEnd
- 修改 BaseNode PlusHandle（F3-T01）支持 drop
- 复用 T02 BlockSelectorPopover

## 验证

- [x] 场景 1：点击节点 + 号 → 弹 Popover → 选类型 → 自动连边
- [x] 场景 2：从 panel 拖到节点 + 号释放 → 直接创建并连边（无 Popover）
- [x] 场景 2 拖到节点中间（非 + 号）→ 走普通 onDrop 落点逻辑（T03，无连边）
- [ ] Chrome 95：待整体真机冒烟

## 完成标准

- [x] 两个场景视觉/操作流畅，无残留连线
- [ ] e2e 用例：拖一个 Source 到 Start + 号 → DOM 出现新节点 + 1 根边（待 IT 阶段）
- [x] 单元测试：onDrop 处理函数

## 实施记录（2026-05-05）

- `PlusHandle` 支持 `drop` 解析 `application/x-workflow-block`
- drop 后复用 `createWorkflowNodeFromBlock` 创建新节点，并添加 `sourceHandle → targetHandle` 自定义边
