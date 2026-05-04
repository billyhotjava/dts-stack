# T02: BlockSelectorPopover 节点 + 号弹窗

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

每个节点出口 handle 旁渲染 + 号；点击 + 号弹出 BlockSelectorPopover（与 Panel 共用 BLOCKS 数据），选中节点类型 → 自动连边到新节点。

## 技术设计

### 文件

```
src/components/workflow/block-selector/BlockSelectorPopover.tsx
```

### + 号 handle 包装

```tsx
// 在 BaseNode（F3-T01）的 source handle 后渲染
function PlusHandle({ nodeId }: { nodeId: string }) {
  const [open, setOpen] = useState(false);
  return (
    <Popover
      open={open}
      onOpenChange={setOpen}
      content={<BlockSelectorPopover sourceId={nodeId} onPick={() => setOpen(false)} />}
    >
      <button className="plus-handle" aria-label="添加下一个节点">+</button>
    </Popover>
  );
}
```

### BlockSelectorPopover

```tsx
export function BlockSelectorPopover({ sourceId, onPick }: Props) {
  const addNode = useWorkflowStore(s => s.addNode);
  const addEdge = useWorkflowStore(s => s.addEdge);
  const sourceNode = useWorkflowStore(s => s.nodes.find(n => n.id === sourceId));

  const handlePick = (block: BlockDef) => {
    const newNode = createNode(block, {
      x: (sourceNode?.position.x ?? 0) + 250,
      y: sourceNode?.position.y ?? 0,
    });
    addNode(newNode);
    addEdge({ id: nanoid(), source: sourceId, target: newNode.id, type: 'custom' });
    onPick();
  };

  return (
    <div className="block-selector-popover">
      {Object.entries(groupBy(BLOCKS, b => b.category)).map(([cat, items]) => (
        <section key={cat}>
          <h5>{CATEGORY_LABEL[cat]}</h5>
          {items.map(b => <button key={b.type} onClick={() => handlePick(b)}>{b.icon} {b.label}</button>)}
        </section>
      ))}
    </div>
  );
}
```

## 影响范围

- 新增 1 个文件
- 修改 F3-T01 BaseNode 在 source handle 旁渲染 PlusHandle

## 验证

- [ ] 点 + 号弹出 Popover，分类列表完整
- [ ] 选一个类型 → 立即创建节点 + 自动连边
- [ ] 新节点位置在源右侧 250px，避免重叠
- [ ] ESC 关 Popover
- [ ] Chrome 95：Popover 定位无 `:has` 依赖

## 完成标准

- [ ] 与 Panel 共用 BLOCKS / CATEGORY_LABEL（DRY 验证：grep 不重复）
- [ ] 单元测试：handlePick 触发 addNode + addEdge
