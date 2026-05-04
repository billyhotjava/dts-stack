# T01: NodePanel 抽屉壳

**优先级**: P0
**状态**: READY
**依赖**: F3-T07

## 目标

画布右侧抽屉容器：选中节点时打开，显示节点对应配置表单（具体表单在 T02）。支持锁定（防止切换节点时丢失编辑）、关闭、按 ESC 关闭。

## 技术设计

### 文件

```
src/components/workflow/panel/
├── NodePanel.tsx              # 抽屉容器
├── PanelHeader.tsx            # 标题 + 锁定 / 关闭按钮
└── styles.css
```

### NodePanel

```tsx
export function NodePanel() {
  const selectedNodeId = useWorkflowStore(s => s.selectedNodeId);
  const node = useWorkflowStore(s => s.nodes.find(n => n.id === selectedNodeId));
  const setPanelOpen = useWorkflowStore(s => s.setPanelOpen);
  const open = useWorkflowStore(s => s.panelOpen) && !!node;
  const [locked, setLocked] = useState(false);

  // ESC 关
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !locked) setPanelOpen(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, locked]);

  if (!node) return null;
  const block = BLOCKS.find(b => b.type === node.type)!;
  const FormComponent = NODE_FORMS[node.type];          // T02 注册

  return (
    <Drawer
      open={open}
      onClose={() => !locked && setPanelOpen(false)}
      mask={false}
      width={420}
      destroyOnClose={false}
    >
      <PanelHeader block={block} locked={locked} onLockToggle={() => setLocked(v => !v)} />
      <FormComponent nodeId={node.id} />
    </Drawer>
  );
}
```

### 选中联动

监听 ReactFlow `onSelectionChange` → `setSelectedNodeId(node.id)` → 自动 `setPanelOpen(true)`。

## 影响范围

- 新增 3 个文件（≤ 200 行）
- WorkflowCanvas 渲染 `<NodePanel />`
- 修改 ui-slice 增 panelOpen state（已在 F1-T01 预留）

## 验证

- [ ] 选中节点 → 抽屉打开 → 显示对应 FormComponent
- [ ] 切换其它节点 → 自动切换表单
- [ ] 锁定后点击外部不关闭，必须手动关
- [ ] ESC 关闭（锁定时不响应）
- [ ] 关闭后选中状态清除

## 完成标准

- [ ] mask={false} 不挡画布
- [ ] 锁定 icon 视觉清晰
- [ ] 单元测试：选中切换 + ESC 关
