# T01: BaseNode 节点外壳

**优先级**: P0
**状态**: DONE
**依赖**: F2-T04

## 目标

抽象出所有 ETL 节点共用的"外壳"：handles 渲染、标题区（icon + label + 状态徽标 + 密级 tag）、错误徽标、密级配色、selected/dragging/error 三态视觉。子节点只关心自己的内部表单。

## 技术设计

### 文件

```
src/components/workflow/nodes/_base/
├── BaseNode.tsx
├── NodeHeader.tsx
├── NodeHandles.tsx            # 标准化 in/out handle 渲染
├── NodeStatusBadge.tsx        # idle / running / success / error
└── styles.css
```

### BaseNode

```tsx
type BaseNodeProps = {
  nodeId: string;
  block: BlockDef;
  inputs?: HandleDef[];
  outputs?: HandleDef[];
  children?: ReactNode;        // 子节点的额外内容（小预览图等）
};

export function BaseNode({ nodeId, block, inputs = [{ id: 'in' }], outputs = [{ id: 'out' }], children }: BaseNodeProps) {
  const node = useWorkflowStore(s => s.nodes.find(n => n.id === nodeId));
  const selected = useWorkflowStore(s => s.selectedNodeId === nodeId);

  return (
    <div
      className={cn('wf-node', `wf-node-${block.category}`, { selected, dragging: node?.data?.isDragging, error: node?.data?.error })}
      role="button"
      aria-label={`${block.label} 节点`}
      tabIndex={0}
    >
      <NodeHandles inputs={inputs} outputs={outputs} nodeId={nodeId} />
      <NodeHeader block={block} status={node?.data?.status} classification={node?.data?.classification} />
      {children}
      {outputs.length > 0 && <PlusHandle nodeId={nodeId} />}
    </div>
  );
}
```

### 三态视觉（CSS，不用 oklch）

```css
.wf-node                   { border: 1px solid #e5e7eb; background: #fff; border-radius: 8px; }
.wf-node.selected          { border-color: #3b82f6; box-shadow: 0 0 0 2px rgba(59,130,246,0.2); }
.wf-node.dragging          { opacity: 0.7; }
.wf-node.error             { border-color: #ef4444; }
.wf-node-source            { border-top: 3px solid #10b981; }
.wf-node-transform         { border-top: 3px solid #3b82f6; }
/* ... */
```

### 密级 tag

复用现有 ClassificationTag 组件（项目已有 zh sys.json `classification.public/internal/secret/topSecret`）。

## 影响范围

- 新增 5 个文件（≤ 150 行/文件）
- BLOCKS 配置（F2-T01）已含 category，BaseNode 通过 block 自动着色

## 验证

- [x] 6 类节点继承 BaseNode 后视觉一致（顶边色由 category 决定）
- [x] 三态视觉切换流畅（selected hover dragging error）
- [x] 节点 ARIA：role + aria-label + tabIndex 完整
- [x] 错误徽标在 data.error 有值时显示
- [x] Chrome 95：未使用 :has / oklch / color-mix / ES2023 数组 API

## 完成标准

- [x] BaseNode 文件 ≤ 150 行
- [x] CSS 类名 BEM/kebab-case，无 :has / @layer / oklch
- [x] 单元测试：BaseNode 三态渲染
