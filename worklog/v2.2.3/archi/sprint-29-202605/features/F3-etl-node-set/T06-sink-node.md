# T06: SinkNode 写入节点

**优先级**: P0
**状态**: DONE
**依赖**: T01

## 目标

数据写入节点：1 输入，无输出。配置目标库（dataset_id 或新建 dataset）+ 写入模式（append / overwrite / upsert）。

## 技术设计

### 文件

```
src/components/workflow/nodes/sink/SinkNode.tsx
```

### 实现

```tsx
export function SinkNode({ id, data }: NodeProps) {
  const block = BLOCKS.find(b => b.type === 'Sink')!;
  const target = useDatasetMeta(data.targetDatasetId);
  const mode = data.mode ?? 'append';
  return (
    <BaseNode
      nodeId={id}
      block={block}
      inputs={[{ id: 'in' }]}
      outputs={[]}
    >
      <div className="wf-node-summary">
        {target ? <div>{target.name}</div> : <em className="placeholder">未选择目标</em>}
        <span className={cn('mode-tag', `mode-${mode}`)}>
          {MODE_LABEL[mode]}
        </span>
      </div>
    </BaseNode>
  );
}

const MODE_LABEL = { append: '追加', overwrite: '覆盖', upsert: '增量更新' };
```

### 新建数据集场景

如果用户希望"运行时自动建表"：data.targetDatasetId = null，data.targetSchema = {...}。NodePanel（F4-T02）需提供"现有 / 新建"切换。

## 影响范围

- 新增 1 个文件
- F4-T02 panel 中提供 targetSchema 编辑表单

## 验证

- [x] 节点显示目标 dataset name + 模式 tag（颜色区分）
- [x] 未配置时显示占位
- [x] overwrite 模式 tag 显示警告色
- [x] 单元测试：模式渲染

## 完成标准

- [x] 节点尺寸与其它节点一致
- [x] mode-tag 配色与"运行实例"页面状态色对齐
