# T03: 属性面板 Size / Position 控件改 grid 单元

**优先级**: P0
**状态**: DONE
**依赖**: T01

## 目标

PropertyPanel 里组件的 Position (x, y) 和 Size (width, height) 控件从"像素输入"改为"grid 单元输入"，labels、step、max 都按 grid units 表达。

## 技术设计

### 改动位置

`source/dts-platform-webapp/src/analytics/pages/screens/components/propertyPanel/PropertyPanel.tsx`

### v2 判断

```tsx
const isV2 = isScreenConfigV2(screen)

// 位置 & 尺寸 section
{isV2 ? (
  <GridLayoutEditor
    layout={selectedComponent.layout}
    cols={screen.layout.cols}
    onChange={(layout) => updateComponentLayout(selectedComponent.id, layout)}
  />
) : (
  <PixelLayoutEditor ... />  // 旧 v1 控件，保持
)}
```

### GridLayoutEditor 组件

新建 `v2/editors/GridLayoutEditor.tsx`：

```tsx
interface GridLayoutEditorProps {
  layout: GridLayoutCell
  cols: number
  onChange: (next: GridLayoutCell) => void
}

// 输入：x（0 to cols-w），y（0+），w（1 to cols-x），h（1+）
// min/max 用 InputNumber 控件 step=1
// 不显示 'px'，只显示 'col' / 'row'
```

### 约束

- `x + w <= cols`（调整 x 时 w 自动裁剪）
- `w >= 1`
- `x / y` 必须是非负整数

### 对齐助手（可选 P1）

- "水平居中" → `x = (cols - w) / 2` (向下取整)
- "贴左/贴右" → `x = 0` / `x = cols - w`
- "平铺宽度" → `w = cols, x = 0`

## 影响范围

- `components/propertyPanel/PropertyPanel.tsx` — 按 version 分流
- 新增 `v2/editors/GridLayoutEditor.tsx`
- v1 Pixel 编辑器保持

## 验证

- [ ] 属性面板在 v2 大屏下显示 grid 编辑器
- [ ] 改 x/y/w/h 数字 → 画布实时反映
- [ ] 约束生效（不会出现 x+w > cols）
- [ ] v1 大屏打开时还是 px 编辑器

## 完成标准

- [x] 新建 `v2/PropertyPanelV2.tsx`：大屏级 cols/rowHeight/gap/背景 + 组件级 name/visible/x/y/w/h/config
- [x] Size/Position 输入均为 grid units，而非 px
- [x] config JSON 编辑器（最小可用），复杂 schema 编辑留给后续迭代
