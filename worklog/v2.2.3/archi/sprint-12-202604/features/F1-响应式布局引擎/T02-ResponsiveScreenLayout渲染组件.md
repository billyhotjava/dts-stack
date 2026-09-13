# T02: ResponsiveScreenLayout 渲染组件（预览/展示共用）

**优先级**: P0
**状态**: DONE（2026-04-21）
**依赖**: T01

## 执行纪要

- 新建 `src/analytics/pages/screens/v2/ResponsiveScreenLayout.tsx`
- 用 `react-grid-layout` 只读模式（`isDraggable/isResizable/static=true`）渲染 v2 大屏
- `ResizeObserver` 监听容器尺寸，`rowHeight='auto'` 时按 `(containerHeight - totalGap) / maxRow` 计算，保证大屏铺满一屏
- 每个组件通过 `v2ToV1Component` 适配为 `ScreenComponent` shape 后交给现有 `ComponentRenderer`（不重写渲染管道）
- 空组件提示避免白屏
- `npx tsc --noEmit` 新文件无类型错误

## 目标

创建 v2 大屏的统一渲染组件 `ResponsiveScreenLayout`，**只读模式**（用于预览页、公开展示页、编辑器的"预览"按钮）。编辑器的可编辑版本由 F3 做。

## 技术设计

### API

```tsx
interface ResponsiveScreenLayoutProps {
  screen: ScreenConfigV2  // v2 schema（F2 定义）
  theme?: ScreenTheme
  deviceMode?: DeviceMode
  onComponentClick?: (id: string) => void  // 可选，给未来用
}

export function ResponsiveScreenLayout(props: ResponsiveScreenLayoutProps): JSX.Element
```

### 内部结构

```tsx
<div className="screen-root" style={{ height: '100vh', width: '100vw' }}>
  <GridLayout
    className="screen-grid"
    cols={screen.layout.cols}  // default 12
    rowHeight={screen.layout.rowHeight}  // default calculated from viewport
    width={viewportWidth}
    isDraggable={false}
    isResizable={false}
    margin={[screen.layout.gap, screen.layout.gap]}
  >
    {screen.components.map((c) => (
      <div key={c.id} data-grid={c.layout}>
        <ComponentRenderer component={c} theme={theme} />
      </div>
    ))}
  </GridLayout>
</div>
```

### rowHeight 策略

- 固定 `rowHeight`：row 高度是常量 px（大屏高度随组件数量变化）
- **自动 `rowHeight`**：根据 `viewportHeight / rowCount` 计算（大屏始终铺满一屏高度）

**选自动**（更符合大屏语义）：

```ts
const rowHeight = (viewportHeight - totalVerticalGap) / maxRowCount
```

`maxRowCount` = 组件 y + h 的最大值。

### ResizeObserver 驱动重新计算

```tsx
useResizeObserver(containerRef, (entry) => {
  setContainerWidth(entry.contentRect.width)
  setContainerHeight(entry.contentRect.height)
})
```

在 Chrome 95 原生支持，不需要 polyfill。

### 主题 & 背景

`screen.backgroundColor` / `backgroundImage` 继续用于根 div 背景（不进 grid）。

## 影响范围

- 新增 `source/dts-platform-webapp/src/analytics/pages/screens/v2/ResponsiveScreenLayout.tsx`
- 新增 `source/dts-platform-webapp/src/analytics/pages/screens/v2/responsiveLayout.css`
- 复用 `ComponentRenderer`（现有组件渲染器不动，F4 会改它内部响应式）
- 复用主题 helpers（`themeVars`、`themeBg` 等）

## 验证

- [ ] 单元测试：给定 v2 ScreenConfig，组件数量 ≥ 3，渲染后 DOM 结构正确
- [ ] 视觉测试：在 1366×768 / 1920×1080 / 3840×2160 三种 viewport 下布局合理
- [ ] ResizeObserver 触发时无过度 re-render（debounce 必要时）
- [ ] Chrome 95 可用

## 完成标准

- [ ] 组件导出可引用
- [ ] 单元测试通过
- [ ] 在 SmokePage 里把 v2 demo 大屏渲染出来（3 种 viewport 截图存 assets/）
