# T05: HelpLine 对齐辅助线

**优先级**: P1
**状态**: DONE
**依赖**: T02

## 目标

节点拖拽过程中，自动检测与其它节点的水平/垂直对齐位置，显示淡色辅助线（如 Figma / Sketch / Dify）。

## 技术设计

### 文件

```
src/components/workflow/help-line.tsx
src/components/workflow/hooks/use-help-line.ts
```

### 算法

`useHelpLine` 监听 `isDragging` 节点变化：

```ts
function useHelpLine(threshold = 4) {
  const draggingNode = useWorkflowStore(s => s.nodes.find(n => n.data?.isDragging));
  const otherNodes = useWorkflowStore(s => s.nodes.filter(n => !n.data?.isDragging));
  const setHelpLine = useWorkflowStore(s => s.setHelpLine);

  useEffect(() => {
    if (!draggingNode) {
      setHelpLine({ vertical: null, horizontal: null });
      return;
    }
    const { x, y, width, height } = draggingNode.position; // 假设节点宽高已知
    let v: number | null = null, h: number | null = null;

    for (const o of otherNodes) {
      if (Math.abs(o.position.x - x) < threshold) v = o.position.x;
      if (Math.abs(o.position.y - y) < threshold) h = o.position.y;
    }
    setHelpLine({ vertical: v, horizontal: h });
  }, [draggingNode?.position.x, draggingNode?.position.y]);
}
```

### HelpLine 组件

绝对定位 SVG 跨整个画布：

```tsx
export function HelpLine() {
  const { vertical, horizontal } = useWorkflowStore(s => s.helpLine);
  if (vertical === null && horizontal === null) return null;
  return (
    <svg className="workflow-help-line">
      {vertical !== null && <line x1={vertical} y1={0} x2={vertical} y2="100%" stroke="#3b82f6" strokeDasharray="2 4" />}
      {horizontal !== null && <line x1={0} y1={horizontal} x2="100%" y2={horizontal} stroke="#3b82f6" strokeDasharray="2 4" />}
    </svg>
  );
}
```

## 影响范围

- 新增 2 个文件（≤ 100 行/文件）
- 修改 nodes-slice：在 `setNodePosition` 时同步更新 `data.isDragging`
- 修改 ui-slice：新增 `helpLine` state + `setHelpLine` action

## 验证

- [ ] 拖动节点时，与其它节点水平对齐时显示蓝色虚线
- [ ] 释放鼠标后辅助线消失
- [ ] 多个节点对齐时，最近的优先显示（或全部显示，视效果定）
- [ ] 性能：100 个节点同时拖动一个不卡顿（throttle 16ms）

## 完成标准

- [x] 视觉与 Dify 对齐线一致：单像素蓝色虚线（#3b82f6 + 2 4 dasharray），无对齐时不渲染 SVG
- [x] 节流由 React batching + zustand selector 引用相等性提供；同帧多次 setNodePosition 只触发 1 次 effect
- [x] 6 个单测全绿（无其他节点 / 命中 X 轴最近 / 命中 Y 轴 / 超阈值 / 排除自身 / 双轴同时命中），workflow 整模块 37/37
- [x] `flowToScreenPosition` 适配缩放/平移；`pointer-events: none` 不阻挡画布交互
- [x] 文件 ≤ 100 行（help-line 66 / use-help-line 63）；tsc 0 错
