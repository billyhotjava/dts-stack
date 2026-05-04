# T04: 快捷键

**优先级**: P0
**状态**: READY
**依赖**: T03

## 目标

抄 Dify 编辑器快捷键集：

| 键 | 行为 |
|----|------|
| Ctrl/Cmd+C | 复制选中节点（含子流程） |
| Ctrl/Cmd+V | 粘贴（光标位置或最近选中节点右侧） |
| Ctrl/Cmd+X | 剪切 |
| Delete / Backspace | 删除选中节点/边 |
| Ctrl/Cmd+A | 全选 |
| Ctrl/Cmd+Z | 撤销（依赖 T05） |
| Ctrl/Cmd+Shift+Z | 重做 |
| Ctrl/Cmd+S | 保存（触发 onSave 回调） |
| Space (按住) | 平移模式 |
| Ctrl/Cmd+0 | fit view |
| Ctrl/Cmd+= / - | zoom in/out |

## 技术设计

### 文件

```
src/components/workflow/shortcuts/
├── useShortcuts.ts              # 主 hook
├── clipboard.ts                 # 复制粘贴 store-side 实现
└── platform.ts                  # macOS Cmd vs others Ctrl
```

### useShortcuts

```ts
export function useShortcuts() {
  const store = useWorkflowStore();
  const [clipboard, setClipboard] = useState<ClipboardPayload | null>(null);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      // 表单输入框中不响应（focus 在 input/textarea）
      const target = e.target as HTMLElement;
      if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable) return;

      const meta = e.metaKey || e.ctrlKey;
      if (meta && e.key === 'c') { copySelection(store, setClipboard); e.preventDefault(); }
      else if (meta && e.key === 'v') { pasteClipboard(store, clipboard); e.preventDefault(); }
      else if (meta && e.key === 'x') { copySelection(store, setClipboard); deleteSelection(store); e.preventDefault(); }
      else if (e.key === 'Delete' || e.key === 'Backspace') { deleteSelection(store); e.preventDefault(); }
      else if (meta && e.key === 'a') { selectAll(store); e.preventDefault(); }
      else if (meta && e.key === 'z' && !e.shiftKey) { store.undo(); e.preventDefault(); }
      else if (meta && (e.key === 'Z' || (e.key === 'z' && e.shiftKey))) { store.redo(); e.preventDefault(); }
      else if (meta && e.key === 's') { onSaveRef.current?.(); e.preventDefault(); }
      // ...
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [store, clipboard]);
}
```

### 复制粘贴实现

```ts
function copySelection(store: WorkflowStore, setClipboard: (p: ClipboardPayload) => void) {
  const selectedNodeIds = new Set(store.selectedNodeIds);
  const nodes = store.nodes.filter(n => selectedNodeIds.has(n.id));
  const edges = store.edges.filter(e => selectedNodeIds.has(e.source) && selectedNodeIds.has(e.target));
  setClipboard({ nodes, edges });
}

function pasteClipboard(store: WorkflowStore, clipboard: ClipboardPayload | null) {
  if (!clipboard) return;
  const idMap = new Map<string, string>();
  const newNodes = clipboard.nodes.map(n => {
    const newId = nanoid();
    idMap.set(n.id, newId);
    return { ...n, id: newId, position: { x: n.position.x + 40, y: n.position.y + 40 } };
  });
  const newEdges = clipboard.edges.map(e => ({
    ...e, id: nanoid(),
    source: idMap.get(e.source)!,
    target: idMap.get(e.target)!,
  }));
  store.addNodes(newNodes);
  store.addEdges(newEdges);
}
```

## 影响范围

- 新增 3 个文件
- ui-slice 增 selectedNodeIds（数组，T03 多选已用）
- nodes-slice 增 batch action：addNodes、deleteSelection 等

## 验证

- [ ] 11 个快捷键全部可用
- [ ] 表单 input 中不抢快捷键（输入 v 时不粘贴）
- [ ] 跨平台：macOS Cmd / Linux Win Ctrl 均生效
- [ ] 复制后粘贴：节点 id 全新，位置 +40 偏移
- [ ] 单元测试：每个快捷键至少 1 用例（mock keydown）

## 完成标准

- [ ] 快捷键说明文档（可选 i18n /帮助按钮）
- [ ] 在表单内 ESC 不触发画布操作
- [ ] Chrome 95 真机一遍
