# T01: BlockSelectorPanel 左侧持久面板

**优先级**: P0
**状态**: READY
**依赖**: F1-T02

## 目标

画布左侧固定面板（可折叠），按分类（基础 / 数据源 / 转换 / 校验 / 写入 / 高级）展示所有可用节点。每个 item 可拖出。

## 技术设计

### 文件

```
src/components/workflow/block-selector/
├── BlockSelectorPanel.tsx
├── BlockSelectorItem.tsx
├── blocks.config.tsx           # 单一数据源
└── styles.css
```

### blocks.config.tsx

```ts
export type BlockDef = {
  type: WorkflowNodeType;
  label: string;
  category: 'basic' | 'source' | 'transform' | 'validate' | 'sink' | 'advanced';
  icon: ReactNode;
  defaultData: Record<string, unknown>;
  description: string;
};

export const BLOCKS: BlockDef[] = [
  { type: 'Start',     category: 'basic',     label: '开始',  icon: <PlayIcon />,    defaultData: { trigger: 'manual' }, description: '工作流入口' },
  { type: 'Source',    category: 'source',    label: '数据源', icon: <DatabaseIcon />, defaultData: {}, description: '从 catalog 拉取数据集' },
  { type: 'Transform', category: 'transform', label: '转换',  icon: <CodeIcon />,    defaultData: { language: 'sql', code: '' }, description: 'SQL/脚本转换' },
  { type: 'Validate',  category: 'validate',  label: '校验',  icon: <CheckIcon />,   defaultData: { rules: [] }, description: '数据质量校验' },
  { type: 'Sink',      category: 'sink',      label: '写入',  icon: <SaveIcon />,    defaultData: { mode: 'append' }, description: '写入目标库' },
  { type: 'End',       category: 'basic',     label: '结束',  icon: <StopIcon />,    defaultData: {}, description: '工作流出口' },
  // F5 加：Iteration / Loop / Note
];
```

### BlockSelectorPanel

```tsx
export function BlockSelectorPanel() {
  const [search, setSearch] = useState('');
  const grouped = useMemo(() => groupBy(filterBlocks(BLOCKS, search), b => b.category), [search]);
  return (
    <aside className="block-selector-panel" aria-label="节点库">
      <input value={search} onChange={e => setSearch(e.target.value)} placeholder="搜索节点..." />
      {Object.entries(grouped).map(([cat, items]) => (
        <section key={cat}>
          <h4>{CATEGORY_LABEL[cat]}</h4>
          {items.map(b => <BlockSelectorItem key={b.type} block={b} />)}
        </section>
      ))}
    </aside>
  );
}
```

### BlockSelectorItem 拖拽

```tsx
function BlockSelectorItem({ block }: { block: BlockDef }) {
  const onDragStart = (e: DragEvent<HTMLDivElement>) => {
    e.dataTransfer.setData('application/x-workflow-block', JSON.stringify(block));
    e.dataTransfer.effectAllowed = 'move';
  };
  return <div draggable onDragStart={onDragStart}>{block.icon} {block.label}</div>;
}
```

## 影响范围

- 新增 4 个文件（≤ 200 行/文件）
- WorkflowCanvas 在 ReactFlow 旁边渲染 BlockSelectorPanel

## 验证

- [ ] 6 类节点全部展示，按分类分组
- [ ] 搜索框过滤实时生效
- [ ] 拖出 item 时 dataTransfer 正确（被 T03 CandidateNode 接收）
- [ ] 折叠按钮：折叠后只显示窄条 icon 列
- [ ] Chrome 95：dataTransfer.setData 正常

## 完成标准

- [ ] BLOCKS 是唯一数据源，T02 Popover 复用
- [ ] 视觉：图标 + 文字 + hover 高亮，与 Dify 风格相近
- [ ] 单元测试：搜索过滤 + 分组渲染
