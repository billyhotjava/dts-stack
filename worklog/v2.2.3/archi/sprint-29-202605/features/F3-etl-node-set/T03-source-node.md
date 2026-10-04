# T03: SourceNode 数据源节点

**优先级**: P0
**状态**: DONE
**依赖**: T01

## 目标

数据源节点：无输入，1 输出。配置数据集 ID（复用现有 catalog/datasets 的 selector 组件）。摘要显示 dataset name + 行数估算 + 密级 tag。

## 技术设计

### 文件

```
src/components/workflow/nodes/source/SourceNode.tsx
```

### 复用

- `src/pages/catalog/components/DatasetSelector.tsx`（已存在）
- 密级 tag 直接从 dataset 元数据继承

### 实现

```tsx
export function SourceNode({ id, data }: NodeProps) {
  const block = BLOCKS.find(b => b.type === 'Source')!;
  const dataset = useDatasetMeta(data.datasetId);  // 已有 hook
  return (
    <BaseNode
      nodeId={id}
      block={block}
      inputs={[]}
      outputs={[{ id: 'out' }]}
    >
      <div className="wf-node-summary">
        {dataset ? (
          <>
            <div>{dataset.name}</div>
            <div className="muted">≈{dataset.rowCount?.toLocaleString() ?? '?'} 行</div>
          </>
        ) : (
          <em className="placeholder">未选择数据集</em>
        )}
      </div>
    </BaseNode>
  );
}
```

数据集详细配置（schema/分区/字段映射等）走 NodePanel（F4-T02）。

## 影响范围

- 新增 1 个文件
- 复用 useDatasetMeta hook（如不存在则在 hooks 下补一个，调用现有 catalog API）

## 验证

- [x] 节点未配置 dataset 时显示占位符
- [x] 配置后摘要显示 name + 行数
- [x] 数据集密级可从节点 data.classification 继承
- [x] 节点显示对应密级 tag（公开/内部/秘密/绝密）
- [x] Chrome 95：本任务未引入新请求 hook

## 完成标准

- [x] 节点摘要信息丰富但不溢出
- [x] 单元测试：有/无 dataset 两态渲染
