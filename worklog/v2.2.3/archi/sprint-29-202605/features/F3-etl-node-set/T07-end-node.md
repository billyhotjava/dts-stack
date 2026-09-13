# T07: EndNode

**优先级**: P0
**状态**: DONE
**依赖**: T01

## 目标

工作流出口节点：1 输入，无输出。配置结束策略（success 通知 / 失败回滚 / 静默）。

## 技术设计

### 文件

```
src/components/workflow/nodes/end/EndNode.tsx
```

### 实现

```tsx
export function EndNode({ id, data }: NodeProps) {
  const block = BLOCKS.find(b => b.type === 'End')!;
  const strategy = data.strategy ?? 'success';
  return (
    <BaseNode
      nodeId={id}
      block={block}
      inputs={[{ id: 'in' }]}
      outputs={[]}
    >
      <div className="wf-node-summary">
        <span className={cn('strategy-tag', `strategy-${strategy}`)}>
          {STRATEGY_LABEL[strategy]}
        </span>
      </div>
    </BaseNode>
  );
}

const STRATEGY_LABEL = {
  success: '成功通知',
  rollback: '失败回滚',
  silent: '静默结束',
};
```

### 注册总入口

完成 T07 后更新 `src/components/workflow/nodes/index.ts`：

```ts
export const nodeTypes = {
  Start: StartNode,
  Source: SourceNode,
  Transform: TransformNode,
  Validate: ValidateNode,
  Sink: SinkNode,
  End: EndNode,
};
```

WorkflowCanvas 注入 nodeTypes。

## 影响范围

- 新增 1 个文件
- 修改 nodes/index.ts 完成全部 6 类注册
- 修改 WorkflowCanvas（F1-T02）注入 nodeTypes

## 验证

- [x] EndNode 显示策略 tag
- [x] 整个 6 类节点拖入画布后能正常显示
- [x] 6 类节点 grep BLOCKS 配置 1:1 对应（无遗漏、无重复）
- [x] 单元测试：strategy 渲染

## 完成标准

- [x] 节点视觉风格与 6 类一致
- [x] BlockSelector 中 6 类节点全可见、可拖
- [x] 完成 F3 整体验证：拖入 6 类节点 + 互连 + 全部 selected/dragging/error 三态正常
