# T01: DesignerCanvas 切换为 GridLayout 编辑模式

**优先级**: P0
**状态**: READY
**依赖**: F1-T02

## 目标

在编辑器里用 `react-grid-layout` 替代现有绝对定位画布，编辑态的 UX 与运行态的 `ResponsiveScreenLayout` 使用同一个引擎。

## 技术设计

### 改造位置

`source/dts-platform-webapp/src/analytics/pages/screens/components/DesignerCanvas.tsx`

按 `screen.version` 分流：
- v2 → 新编辑器（GridLayout + isDraggable/isResizable=true）
- v1 → 保留现有绝对定位编辑器（只读，显示"升级到 v2"按钮）

### 编辑器核心结构

```tsx
<GridLayout
  className="designer-grid"
  cols={screen.layout.cols}
  rowHeight={rowHeight}
  width={canvasWidth}
  layout={components.map(toRGLLayout)}
  onLayoutChange={handleLayoutChange}
  onDragStop={handleDragStop}
  onResizeStop={handleResizeStop}
  isDraggable
  isResizable
  compactType={null}  // 不自动压紧（设计师完全控制）
  preventCollision={false}
  draggableHandle=".component-drag-handle"  // 仅组件头部可拖
>
  {components.map((c) => (
    <div key={c.id} data-grid={toRGLLayout(c)} className="designer-component">
      <ComponentHeader ... />
      <ComponentRenderer component={c} mode="edit" />
    </div>
  ))}
</GridLayout>
```

### `rowHeight` 策略（编辑器）

编辑器里 `rowHeight` **不用 'auto'**（那样编辑时高度不稳定），改用固定值（例如 40 px）。运行时可以 'auto'，保存的 config 里 `layout.rowHeight = 'auto' | number` 依业务选择。

### 选中态 / 多选

- 点击组件 → 选中（属性面板显示）
- Shift+click → 多选
- 选中框用 CSS outline（不重叠 react-grid-layout 的 handle）

## 影响范围

- `components/DesignerCanvas.tsx` — v2 编辑分支新增
- 新增 `v2/DesignerCanvasV2.tsx`（避免 DesignerCanvas 过长）
- 相关 store / hooks（若存在）保持 API 不变
- v1 路径保持不变（只读提示）

## 验证

- [ ] 能新建空白 v2 大屏并从组件库拖入 2-3 个组件
- [ ] 拖放组件 → 位置保存为 grid units
- [ ] resize 组件 → 尺寸保存为 grid units
- [ ] 保存后重新打开，布局恢复一致
- [ ] 打开 v1 大屏时编辑器显示只读提示

## 完成标准

- [ ] v2 编辑分支可用
- [ ] 保存 / 读取 round-trip 保持一致
- [ ] v1 兼容不破坏
