# T03: 图片组件 object-fit 默认 contain

**优先级**: P1
**状态**: DONE
**依赖**: 无

## 目标

图片组件（背景图 / 独立图片组件 / logo 组件）使用 `object-fit: contain`（默认）或 `cover` 配置，容器 resize 时图片不变形。

## 技术设计

### CSS

```css
.screen-image-component img {
  width: 100%;
  height: 100%;
  object-fit: var(--object-fit, contain);
  object-position: center;
}
```

`object-fit` / `object-position` Chrome 95 原生支持 ✅。

### 组件 schema

图片组件配置增加：
- `objectFit`: `contain` | `cover` | `fill` | `none`（默认 contain）
- `objectPosition`: `center` | `top` | `left` | ... （默认 center）

### 背景图

大屏背景 `screen.backgroundImage` 当前是 CSS `background-image: url()` + `background-size: cover`，保持不变（已经响应式）。

## 影响范围

- 图片类组件实现
- 组件 schema
- `v2/components/ImageComponent.tsx`（如存在）

## 验证

- [ ] 图片在容器任意尺寸下保持原始比例不变形
- [ ] 切换 `contain` / `cover` 效果符合预期
- [ ] 大图缩小后不过度模糊

## 完成标准

- [x] 图片组件默认 `object-fit: contain` — `BasicRenderer.tsx` image case
- [x] 配置可切换 — `configSchema/schemas/basic.ts` 新增 5 档 fit + 9 档 objectPosition
- [x] 背景图维持现有 cover 行为 — 未改 ResponsiveScreenLayout 背景图逻辑
