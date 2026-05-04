# T02: StartNode

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

工作流入口节点：无输入 handle，1 个输出 handle。配置触发方式（手动 / cron / 事件）。

## 技术设计

### 文件

```
src/components/workflow/nodes/start/StartNode.tsx
```

### 实现

```tsx
export function StartNode({ id, data }: NodeProps) {
  const block = BLOCKS.find(b => b.type === 'Start')!;
  return (
    <BaseNode
      nodeId={id}
      block={block}
      inputs={[]}
      outputs={[{ id: 'out' }]}
    >
      <div className="wf-node-summary">
        触发方式：<strong>{TRIGGER_LABEL[data.trigger ?? 'manual']}</strong>
      </div>
    </BaseNode>
  );
}

const TRIGGER_LABEL = { manual: '手动', cron: 'Cron 定时', event: '事件触发' };
```

### 注册

```ts
// src/components/workflow/nodes/index.ts
export const nodeTypes = {
  Start: StartNode,
  // T03..T07
};
```

## 影响范围

- 新增 1 个文件
- 修改 nodes/index.ts 注册

## 验证

- [ ] 拖出 Start 节点：图标、标题"开始"、默认触发"手动"展示正常
- [ ] 无输入 handle，仅 1 个输出 handle
- [ ] data.trigger 改变时摘要文本同步
- [ ] 单元测试：3 种 trigger 渲染

## 完成标准

- [ ] 节点视觉与 Dify Start 风格相近
- [ ] 摘要区一目了然（不进 NodePanel 也能看出关键配置）
