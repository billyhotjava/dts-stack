# T02: 文字 / KPI 自适应字号

**优先级**: P0
**状态**: DONE
**依赖**: 无

## 目标

文字、KPI 大数字、标题等组件的**字号**跟随容器宽度自动缩放，避免容器变小后文字溢出，或容器变大后文字过小。

## 技术设计

### 方案 A（CSS-only，简单）

```css
.screen-component-title {
  font-size: clamp(14px, 2cqw, 32px);  /* 2% of container width */
}
```

但 `cqw`（container query unit）Chrome 95 **不支持**（需要 Chrome 105+）。

**禁用 cqw/cqh**，改用方案 B。

### 方案 B（ResizeObserver + React state） — Chrome 95 兼容

新增 hook：

```ts
// src/analytics/pages/screens/v2/hooks/useContainerFontSize.ts

export function useContainerFontSize(
  containerRef: React.RefObject<HTMLElement | null>,
  opts: {
    min: number   // px
    max: number   // px
    ratio: number // fontSize = min(max, max(min, containerWidth * ratio))
  },
): number {
  const [fontSize, setFontSize] = useState(opts.min)

  useEffect(() => {
    const el = containerRef.current
    if (!el) return

    const observer = new ResizeObserver((entries) => {
      const width = entries[0].contentRect.width
      const size = Math.max(opts.min, Math.min(opts.max, width * opts.ratio))
      setFontSize(size)
    })
    observer.observe(el)
    return () => observer.disconnect()
  }, [containerRef, opts.min, opts.max, opts.ratio])

  return fontSize
}
```

使用：

```tsx
function KPICard() {
  const ref = useRef<HTMLDivElement>(null)
  const fontSize = useContainerFontSize(ref, { min: 16, max: 72, ratio: 0.15 })
  return <div ref={ref}>
    <span style={{ fontSize }}>{value}</span>
  </div>
}
```

### 组件 schema 暴露参数

KPI / Text 组件 schema 增加：
- `fontSizeMin` (px)
- `fontSizeMax` (px)
- `fontSizeRatio` (相对容器宽度)

默认值针对不同组件调优：
- KPI 大数字：min=16, max=96, ratio=0.15
- 标题：min=14, max=40, ratio=0.05
- 正文：min=12, max=20, ratio=0.03

## 影响范围

- 新增 `v2/hooks/useContainerFontSize.ts`
- 改动 KPI / Text / Title 组件内部实现
- 组件 schema（配置面板）增加字号自适应参数

## 验证

- [ ] KPI 大数字在容器从 200→800px 时按比例变大
- [ ] 字号不超过 max / 不低于 min
- [ ] 快速 resize 不卡顿
- [ ] 视觉：不同尺寸下文字都适配容器，无溢出

## 完成标准

- [x] hook 可用 — `src/analytics/pages/screens/v2/hooks/useContainerFontSize.ts`
- [x] KPI/Text/Title 组件接入 — `renderers/basic/ResponsiveText.tsx` 里的 TitleBasic / NumberCardBasic / StatCardBasic 分别用 hook 驱动字号
- [x] schema 参数暴露到属性面板 — `configSchema/schemas/basic.ts`（title/number-card）与 `enterprise.ts`（stat-card）新增 `fontSizeRatio` / `fontSizeMin` / `fontSizeMax`（以及 title/value 前缀的 number-card/stat-card 变体）。默认 ratio=0 保持旧行为（固定字号），用户在属性面板把比例调 >0 即进入自适应模式
