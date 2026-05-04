# T04: CustomConnectionLine 拖拽连线虚影

**优先级**: P0
**状态**: READY
**依赖**: T02

## 目标

用户从节点 handle 拖出连线时，跟随光标显示自定义虚线（替代 reactflow 默认实线）。源 handle 高亮，hover 到合法 target handle 时变色。

## 技术设计

### 文件

```
src/components/workflow/custom-connection-line.tsx
```

```tsx
export function CustomConnectionLine({ fromX, fromY, toX, toY, connectionStatus }: ConnectionLineComponentProps) {
  const stroke = connectionStatus === 'valid' ? '#10b981'
              : connectionStatus === 'invalid' ? '#ef4444'
              : '#94a3b8';
  return (
    <g>
      <path
        d={`M${fromX},${fromY} L${toX},${toY}`}
        stroke={stroke}
        strokeWidth={1.5}
        strokeDasharray="4 4"
        fill="none"
      />
      <circle cx={toX} cy={toY} r={4} fill={stroke} />
    </g>
  );
}
```

### 注册

```tsx
<ReactFlow
  connectionLineComponent={CustomConnectionLine}
  isValidConnection={({ source, target, sourceHandle, targetHandle }) =>
    canConnect(source, target, sourceHandle, targetHandle)
  }
/>
```

`canConnect` 在 edges-slice 已实现（T01）。

## 影响范围

- 新增 `src/components/workflow/custom-connection-line.tsx`（≤ 80 行）
- WorkflowCanvas 注入 props

## 验证

- [ ] 从 handle 拖出连线时显示虚线 + 跟随光标圆点
- [ ] 拖到合法 target 时变绿色，非法时红色，空白时灰色
- [ ] 拖到合法 target 释放鼠标 → 创建边
- [ ] 拖到非法处释放 → 不创建，无残留
- [ ] Chrome 95 拖拽过程中不抛 `structuredClone is not defined`（F0 已修）

## 完成标准

- [ ] 三种连接状态视觉清晰
- [ ] 单元测试 ≥ 2 个
