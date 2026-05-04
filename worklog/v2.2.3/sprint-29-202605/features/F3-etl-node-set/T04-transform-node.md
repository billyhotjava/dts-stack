# T04: TransformNode 转换节点

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

通用转换节点：1 输入 1 输出，可选 SQL / Python / Spark 转换。摘要显示语言 + 代码行数 + 是否含外部依赖。

## 技术设计

### 文件

```
src/components/workflow/nodes/transform/TransformNode.tsx
```

### 实现

```tsx
export function TransformNode({ id, data }: NodeProps) {
  const block = BLOCKS.find(b => b.type === 'Transform')!;
  const lineCount = (data.code ?? '').split('\n').length;
  return (
    <BaseNode nodeId={id} block={block}>
      <div className="wf-node-summary">
        <span className="lang-tag">{(data.language ?? 'sql').toUpperCase()}</span>
        <span className="muted">{lineCount} 行</span>
        {data.hasExternalDeps && <span className="warn-tag">含外部依赖</span>}
      </div>
    </BaseNode>
  );
}
```

代码编辑（Monaco / SQL IDE 子组件）走 NodePanel（F4-T02）。

### 复用

- 现有 SQL IDE 子组件 `src/components/sql-ide/`（在 panel 中嵌入）
- 现有 codemirror / monaco（按 PR 历史确定）

## 影响范围

- 新增 1 个文件
- F4-T02 完成 panel 时引入 SQL IDE

## 验证

- [ ] 节点摘要显示语言 + 行数
- [ ] data.language 改变时 lang-tag 同步
- [ ] data.hasExternalDeps=true 时显示警告 tag
- [ ] 单元测试：3 种语言渲染

## 完成标准

- [ ] 节点摘要不超 3 行
- [ ] lang-tag 配色与 SQL IDE 一致
