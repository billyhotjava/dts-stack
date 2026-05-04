# T05: ValidateNode 校验节点（双输出）

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

数据质量校验节点：1 输入，**2 输出（pass / fail）**。摘要显示规则数量 + 类型分布。pass 走主流程，fail 进入兜底分支（清洗/告警/写入死信）。

## 技术设计

### 文件

```
src/components/workflow/nodes/validate/ValidateNode.tsx
```

### 实现

```tsx
export function ValidateNode({ id, data }: NodeProps) {
  const block = BLOCKS.find(b => b.type === 'Validate')!;
  const rules = data.rules ?? [];
  return (
    <BaseNode
      nodeId={id}
      block={block}
      inputs={[{ id: 'in' }]}
      outputs={[
        { id: 'pass', label: '✓ 通过', color: '#10b981' },
        { id: 'fail', label: '✗ 失败', color: '#ef4444' },
      ]}
    >
      <div className="wf-node-summary">
        <div>{rules.length} 条规则</div>
        <div className="muted">{summarizeRuleTypes(rules)}</div>
      </div>
    </BaseNode>
  );
}

function summarizeRuleTypes(rules: ValidateRule[]) {
  const counts = countBy(rules, r => r.type);
  return Object.entries(counts).map(([t, n]) => `${RULE_TYPE_LABEL[t]}×${n}`).join(' · ');
}
```

### NodeHandles 扩展

T01 BaseNode 的 NodeHandles 需支持 outputs 多个 handle + 自定义 label/color 显示。

## 影响范围

- 新增 1 个文件
- 修改 NodeHandles（T01）支持 outputs 数组中带 label/color

## 验证

- [ ] 节点底部 2 个 handle 可视化区分（绿/红 + 文字）
- [ ] 规则数 0 时显示"未配置规则"占位
- [ ] 规则类型分布正确：完整性×3 · 范围×2 · 正则×1
- [ ] 单元测试：规则汇总函数 + 节点渲染

## 完成标准

- [ ] 双 handle 视觉清晰（位置不重叠）
- [ ] CustomEdge / canConnect 支持多输出 handle
