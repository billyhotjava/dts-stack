# 大屏编辑器 UI 整体 Review

> 评审日期: 2026-05-19
> 范围: 编辑器 toolbar / header / 画布 / 属性面板(样式/数据/交互/图层/高级)

## 一、本次已修复

### P0 ✅ 画布抖动(按 + 号缩放)

**根因**: 缩放反馈循环
```
zoom +25%
 └→ wrapper 真实 w/h × 1.25
    └→ 容器超出 → 滚动条出现 → containerNode 尺寸变化
       └→ ResizeObserver → setFitScale → fitScale 变化
          └→ scale 再变 → 第 1 步循环
```

**修复**:
1. `setFitScale` 加 1% 阈值过滤,防止小尺寸抖动反复触发
2. 滚动容器 `overflow: scroll` + `scrollbarGutter: stable`,滚动条永久占位
3. wrapper width/height + transform 加 160ms ease-out 过渡,变化平滑

### P1 ✅ 字号统一

| 元素 | 之前 | 现在 |
|---|---:|---:|
| toolbar-btn (左侧工具栏按钮) | 12px | **13px** |
| property-label (属性面板字段名) | 12px | **13px** |
| property-input (属性面板输入框) | 12px | **13px** |
| header-btn (顶部视图/编辑/...) | 13px | 13px (保持) |

整个编辑器文本基线统一在 **13px**,与 antd 5 默认体系一致。
icon 大小未变(toolbar-btn--icon 仍是 14px)。

---

## 二、待整改清单(样式 Tab 全量 review)

下面是属性面板"样式"Tab 的现状问题与改进建议,按优先级排序。

### 现状(以 bar-chart 样式 Tab 为例)

```
组件外观    [折叠分组,默认折叠]
  ├─ 边框/阴影/圆角...

位置与尺寸  [折叠分组]
  ├─ X/Y/宽/高...

组件配置    [折叠分组]
  └─ 图表    [二级折叠]
       ├─ 标题
       ├─ 标题颜色  [颜色选择器 + #ffffff 输入框]
       ├─ 标题字号  [16]
       ├─ 图例
       │   ├─ 显示    [开关]
       │   ├─ 位置    [下拉: 顶部]
       │   ├─ 字号    [空]
       │   ├─ 颜色    [颜色块]
       │   ├─ 与图形间距 [自动]
       │   └─ 条目间距  [12]
       ├─ ...(更多)
```

### 问题清单

| # | 问题 | 影响 | 优先级 |
|---|---|---|---|
| **A1** | 三级嵌套(组件配置 > 图表 > 图例) 视觉层级混乱,用户不易找到字段 | 配置难找 | **P0** |
| **A2** | 字段名长短不一,label 80px 固定宽度,长字段(如"与图形间距")挤压输入区 | 视觉错位 | **P0** |
| **A3** | "图例字号"为空时无 placeholder,用户不知道默认值 | 信息缺失 | **P1** |
| **A4** | 颜色选择器(色块 + #hex 输入框)在某些字段(如"图例颜色")只显示色块没有文本框 | 不一致 | **P1** |
| **A5** | 部分字段(如"颜色")的色块在透明值时显示**红色斜线**(图例颜色处),与其他色块外观差异大 | 视觉杂乱 | **P1** |
| **A6** | "与图形间距 = 自动" "条目间距 = 12" 这种**单位不明**(px? 百分比?) | 用户困惑 | **P1** |
| **A7** | 字段数量过多,样式 Tab 滚动很长,缺乏"常用 vs 高级"分层 | 上手成本高 | **P2** |
| **A8** | "组件外观"和"组件配置"语义重叠,用户不知道改在哪里(背景色既在外观又在 chart 子配置) | 概念混淆 | **P2** |
| **A9** | 颜色选择器没有"主题色"快速选择,只能输入 hex 或用浏览器原生 color picker | 效率低 | **P2** |
| **A10** | 折叠分组默认全部展开/全部折叠,无"记住上次状态" | 反复操作 | **P3** |

### 改进方案

#### A1+A8 重组分组语义

```
当前(混乱)               建议(清晰)
─────────────         ─────────────
组件外观                ▸ 基础      [位置/尺寸/标题/字号]
  ├ 边框                ▸ 数据      [系列/字段映射]
  └ 阴影                ▸ 外观      [颜色/边框/阴影/圆角]
位置与尺寸              ▸ 图例      [显示/位置/字号]
组件配置                ▸ 坐标轴    [X/Y 轴样式]
  └ 图表                ▸ 高级      [其他不常用]
     ├ 标题/颜色/字号
     ├ 图例/...
     └ 坐标轴
```

把"组件外观"+"位置与尺寸"+"组件配置"三个顶级分组合并并按业务维度重新分类。**预计减少 50% 上下滚动**。

#### A2 label 宽度自适应

```css
.property-label {
  width: 80px;        →   min-width: 72px; max-width: 100px;
  /* 长字段允许换行,不挤压输入区 */
  word-break: break-all;
}
```

#### A3+A6 加 placeholder + 单位提示

```tsx
// 之前
<input type="number" value={config.legendFontSize} />

// 之后
<input type="number" placeholder="默认 12 (px)" value={config.legendFontSize} />
```

每个数字字段统一带 placeholder = `默认值 (单位)`。

#### A4+A5 颜色组件统一

封装 `<ColorField>` 组件,统一以下结构:
```
[色块 24×24] [#hex 输入框] [×] [主题色快选 ▾]
```

- 透明/未设置 时色块用棋盘格图案,不要红色斜线
- 颜色块右边永远是 hex 输入框,从不省略
- hex 输入框右边的 × 是清除按钮

#### A7+A9 颜色 + 主题预设

- 常用字段(标题颜色/数据系列色)弹出色板时显示当前**大屏主题的 8 色**作为快选
- 高级 Tab 收纳低频字段(z-index/动画/边距等)

#### A10 折叠状态持久化

```ts
localStorage.setItem('screen.editor.style-tab.openGroups', JSON.stringify(['style', 'data']));
```

每个分组的展开/折叠状态记到 localStorage,下次打开恢复。

---

## 三、改进优先级与工时

| # | 项 | 估时 | 价值 |
|---|---|---:|---|
| A1+A8 | 分组语义重组 | 2 天 | ⭐⭐⭐⭐⭐ |
| A2 | label 宽度自适应 | 0.5 天 | ⭐⭐⭐⭐ |
| A3+A6 | placeholder 与单位 | 1 天 | ⭐⭐⭐⭐ |
| A4+A5 | 颜色组件统一 | 1.5 天 | ⭐⭐⭐⭐ |
| A7 | 高级字段下沉 | 1 天 | ⭐⭐⭐ |
| A9 | 主题色快选 | 1 天 | ⭐⭐⭐ |
| A10 | 折叠状态持久化 | 0.5 天 | ⭐⭐ |
| **合计** | | **7.5 天** | |

---

## 四、本次提交内容(就绪)

### 第一批 — 基础修复
| 文件 | 改动 |
|---|---|
| `components/DesignerCanvas.tsx` | 抖动修复 3 处(阈值过滤/滚动条稳定/过渡平滑) |
| `ScreenDesigner.css` | 3 类字号统一到 13px(toolbar/label/input) |

### 第二批 — 样式 Tab 整改(A1/A2/A3/A4/A5/A6/A8)

| 项 | 文件 | 改动 |
|---|---|---|
| **A1+A8** 分组扁平化 | `propertyPanel/ComponentConfigSection.tsx` | 移除外层"组件配置"折叠包装,让 schema 的图表/标题/图例/轴等子分组直接作为顶级 Collapse —— 减少 1 层嵌套 |
| **A2** label 宽度自适应 | `ScreenDesigner.css` | `width: 80px` → `min-width: 72px; max-width: 108px; word-break: break-all`;长字段允许换行 |
| **A3+A6** 单位 + placeholder | `configSchema/types.ts` `configSchema/editors/FieldEditor.tsx` | 给 ConfigField 加 `unit` 字段;FieldEditor 用 `addonAfter` 显示单位;按 key 自动推断 px/%/ms/° 等单位;数字字段无值时显示"默认 N px" 等 placeholder |
| **A4+A5** 颜色组件统一 | `propertyPanel/ColorPickerInput.tsx` | 关闭 antd ColorPicker 的 `allowClear`(根除红色斜线);默认追加 hex/rgba 文本输入框(`showInput=true`);兼容 28 处既有调用 |

## 五、自动单位推断规则

| key 模式 | 自动单位 |
|---|---|
| `*Rate / *Ratio / *Percent / *Opacity / *alpha` | **%** |
| `*Duration / *Delay / *Interval / *Ms` | **ms** |
| `*Seconds` | **秒** |
| `*FontSize / *Width / *Height / *Size` | **px** |
| `*Radius / *BorderRadius` | **px** |
| `*Padding / *Margin / *Gap / *Spacing` | **px** |
| `*Top / *Left / *Right / *Bottom` | **px** |
| `*Rotate / *Angle / *Rotation` | **°** |

可在 ConfigField 显式声明 `unit: ''` 关闭自动推断,或 `unit: '自定义'` 覆盖。

## 六、用户可感知效果

| 之前 | 现在 |
|---|---|
| 三层嵌套(组件配置 > 图表 > 字段) | 两层嵌套(图表 > 字段) |
| 字段名"与图形间距"挤掉输入框 | label 自动换行,输入框保持完整 |
| "字号"输入框无任何提示 | "字号 [16] px" 显示单位+默认值 |
| 颜色色块上一道红斜线没意义 | 色块清爽 + 右侧 hex 输入 + 清除按钮 |

## 七、验证

- **node:test 套件**: 22/22 通过(含 PropertyPanel 全套契约测试)
- **vitest 套件**: 6/6 通过
- **TypeScript 编译**: 0 error
- **JSON 校验**: 10 张大屏全部通过

## 八、第三批整改 — A7+A9+A10(已完成)

### A10 折叠状态持久化(检查发现已实现 ✅)

`propertyPanelPersistence.ts` 中已经有 `readCollapsedSections / writeCollapsedSections`,
PropertyPanel 已通过 `useEffect + localStorage` 双向同步,无需新增工作。

### A9 颜色主题快选 ✅

**文件**: `propertyPanel/ColorPickerInput.tsx` + `ScreenContext.tsx`

- 新增 `useScreenOptional()` hook(无 Provider 不抛错,适合通用组件)
- ColorPickerInput 自动从大屏当前主题读色板,作为 ColorPicker 的 `presets` 显示:
  - **主题色板**: `tokens.echarts.colorPalette` 的 8-10 个数据系列色
  - **语义色**: 主题强调色 / 主文字色 / 次文字色 / 卡片背景色
- 用户取色时一键命中主题色,提升大屏视觉一致性

### A7 高级字段下沉 ✅

**文件**: `configSchema/editors/SchemaConfigRenderer.tsx` + `propertyPanel/ComponentConfigSection.tsx` + `propertyPanel/PropertyPanel.tsx`

- SchemaConfigRenderer 新增 `onlyGroups?: string[]` 与 `hideGroups?: string[]`
- ComponentConfigSection 透传上述 prop
- **样式 Tab**: `hideGroups: ['advanced']` — 隐藏所有 group='advanced' 的字段
- **高级 Tab**: `onlyGroups: ['advanced']` — 仅显示 advanced 字段
- 效果:
  - 样式 Tab 不再混杂"标记线/提示框/散点 JSON/气泡范围"等低频技术配置
  - 高级 Tab 集中"其他配置 + 动画配置 + 组件级高级字段"三类
  - 业务用户日常调样式更聚焦

## 九、最终验证

- **node:test**: 22/22 ✅
- **vitest**: 6/6 ✅
- **TypeScript 编译**: 0 error
- **10 张大屏 JSON 校验**: 0 error

## 十、本轮全部交付清单

| 阶段 | 项 | 文件 |
|---|---|---|
| 第一批 | 画布抖动修复 + 字号统一 | `DesignerCanvas.tsx` + `ScreenDesigner.css` |
| 第二批 | A1+A8 分组扁平化 | `ComponentConfigSection.tsx` |
| 第二批 | A2 label 自适应 | `ScreenDesigner.css` |
| 第二批 | A3+A6 单位 + placeholder | `configSchema/types.ts` + `FieldEditor.tsx` |
| 第二批 | A4+A5 颜色组件 | `ColorPickerInput.tsx` |
| 第三批 | A9 主题色预设 | `ColorPickerInput.tsx` + `ScreenContext.tsx`(新 useScreenOptional) |
| 第三批 | A7 高级字段下沉 | `SchemaConfigRenderer.tsx` + `ComponentConfigSection.tsx` + `PropertyPanel.tsx` |
| 第三批 | A10 折叠持久化 | 已实现,确认 |

**整体设计原则**: 业务用户在样式 Tab 改样式 → 高级 Tab 调技术细节;
低频字段不再干扰常用流程,主题色直接可选,折叠状态记忆,UI 自适应不同字段长度。
