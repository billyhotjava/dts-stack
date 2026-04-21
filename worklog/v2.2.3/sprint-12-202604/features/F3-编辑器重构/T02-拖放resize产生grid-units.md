# T02: 拖放与 resize 产生 grid units

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

`react-grid-layout` 的 `onLayoutChange` / `onDragStop` / `onResizeStop` 回调拿到的是 grid 坐标，把这些值写回 `ComponentV2.layout.{x,y,w,h}`。

## 技术设计

### RGL Layout 类型对接

```ts
import type { Layout as RGLLayout } from 'react-grid-layout'

function fromRGLLayout(rgl: RGLLayout): GridLayoutCell {
  return {
    x: Math.max(0, Math.floor(rgl.x)),
    y: Math.max(0, Math.floor(rgl.y)),
    w: Math.max(1, Math.floor(rgl.w)),
    h: Math.max(1, Math.floor(rgl.h)),
    minW: rgl.minW,
    minH: rgl.minH,
    maxW: rgl.maxW,
    maxH: rgl.maxH,
  }
}

function toRGLLayout(c: ComponentV2): RGLLayout {
  return {
    i: c.id,
    x: c.layout.x,
    y: c.layout.y,
    w: c.layout.w,
    h: c.layout.h,
    minW: c.layout.minW,
    minH: c.layout.minH,
    maxW: c.layout.maxW,
    maxH: c.layout.maxH,
    static: c.static ?? false,
  }
}
```

### onLayoutChange 回调

```tsx
const handleLayoutChange = useCallback((newLayouts: RGLLayout[]) => {
  setComponents((prev) =>
    prev.map((c) => {
      const updated = newLayouts.find((l) => l.i === c.id)
      return updated ? { ...c, layout: fromRGLLayout(updated) } : c
    }),
  )
}, [])
```

注意：`onLayoutChange` 在拖动中连续触发，**只在 `onDragStop` / `onResizeStop` 时才写入草稿存储**（避免高频写后端）。

### Undo / Redo

使用现有的 zustand store（若存在）记录 snapshots。新增 action `updateComponentLayout(id, layout)`。

## 影响范围

- `v2/DesignerCanvasV2.tsx` — 回调挂接
- store / hooks — 新增 updateComponentLayout action
- 草稿保存节流（现有机制复用）

## 验证

- [ ] 拖动组件 → 属性面板 x/y 跟随刷新
- [ ] Resize 组件 → 属性面板 w/h 跟随刷新
- [ ] 快速拖动多次后只在 onDragStop 时触发持久化（查看 network 调用次数）
- [ ] Undo 能恢复到拖动前位置

## 完成标准

- [ ] RGL 回调能写回 ComponentV2.layout
- [ ] 节流 / undo 生效
- [ ] 数值总是合法的整数 grid units
