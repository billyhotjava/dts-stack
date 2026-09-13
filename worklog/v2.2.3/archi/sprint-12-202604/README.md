# Sprint-12: BI 大屏响应式改造（C 方案）

**时间**: 2026-04
**状态**: DONE（代码阶段 1-4 完成，IT 真机验证待客户侧）
**类型**: Implementation（破坏性重构，demo 阶段可重做）
**目标**: 把 BI 大屏从"固定画布 + 像素绝对定位 + 运行时整体缩放"重构为"网格化响应式布局 + 组件内部自适应"，客户无需理解像素概念，大屏在任意 viewport 铺满且布局合理。

## 背景

### 当前架构（v1）

```
设计时：固定画布 (1920×1080)
         ↓
         组件绝对定位 (x, y, width, height 全是 px)
         ↓
运行时：transform: scale() 把整个画布缩放到 viewport
```

数据模型里组件全是像素坐标，编辑器的拖放/吸附/对齐也基于像素。

### 问题

1. **像素绑死**：客户新建大屏必须选固定像素（1920×1080 / 2560×1440），概念对他们不友好
2. **纵横比不匹配有黑边或变形**：`min(sx, sy)` 等比缩放时屏幕比例不一致必然留白；分轴 `scale(sx, sy)` 则整体拉伸失真
3. **组件内部不响应**：图表/文字/KPI 在整体缩放时是"整体变大变小"，不是"容器变而内容自适应"
4. **设计时 ≠ 运行时**：设计师在 1920×1080 画布上拖组件，实际屏幕尺寸不一定 16:9

### 改造目标

1. **网格化布局**：所有组件用 grid units（12 列 × N 行）定位，不再出现 px
2. **内部响应式**：每个组件内部随容器尺寸自适应（图表 autoResize、文字 clamp 字号、图片 object-fit）
3. **客户无感知像素**：新建大屏不需要选择尺寸，设计时的"画布"就是浏览器 viewport
4. **硬约束：Chrome 95 兼容** — 客户环境是 Chrome 95，所有新 API / CSS 特性必须在 Chrome 95 可用或有 polyfill

### 改造策略

- **数据 schema v2**（`screen.version === 2`）：新大屏默认 v2；旧 v1 大屏只读打开
- **渲染分支**：预览/展示页按 version 走 v1 或 v2 渲染器
- **编辑器**：只支持 v2（v1 打开时显示"只读，点击转为 v2"按钮）
- **demo 期可接受**旧 v1 大屏数据丢精确位置（网格吸附时有微调）

## Chrome 95 约束清单

| 特性 | Chrome 95 支持 | 对策 |
|---|---|---|
| `ResizeObserver` | ✅ | 直接用（组件内部响应式依赖） |
| `@container` CSS container queries | ❌（Chrome 105+） | **禁用**，用 `ResizeObserver` + React state 替代 |
| `aspect-ratio` CSS | ✅（Chrome 88+） | 可用 |
| `clamp()` / `min()` / `max()` | ✅ | 字号自适应用 |
| `CSS Grid` / `flex` | ✅ | 基础布局 |
| `structuredClone` | ❌（Chrome 98+） | **禁用**，用 lodash.cloneDeep |
| `Array.at()` | ❌（Chrome 92+ 实际可用） | 可用但验证 |
| `dialog` element | ❌ partial | **禁用**，用 React Portal |
| `:has()` | ❌（Chrome 105+） | **禁用** |
| `react-grid-layout` | 需验证版本 | T01 首任务选版本 |

所有 Feature 必须在**阶段 5（IT）**跑 Chrome 95 smoke test，证据存 `it/chrome-95-evidence/`。

## 任务引用约定

本 Sprint 各 Feature 内部都从 `T01` 重新编号。

- **Feature 内引用**：允许写 `T01`、`T02`
- **跨 Feature / README / IT / 周报引用**：必须写成 `F#/T#`
- 例如：`F1/T02`、`F5/T03`

## Feature 列表

| ID | Feature | Task 数 | 状态 | 依赖 |
|----|---------|---------|------|------|
| F1 | 响应式布局引擎（核心） | 3 | DONE | — |
| F2 | ScreenConfig v2 schema | 3 | DONE | — |
| F3 | 编辑器重构（网格编辑） | 4 | DONE | F1, F2 |
| F4 | 组件内部响应式 | 4 | DONE | F1 |
| F5 | 新建流程与 v1 兼容 | 3 | DONE | F1, F2, F3 |

**阶段节奏**（本 Sprint 内）：

```
阶段 1  (3 天) ─ F2 schema + F1 引擎 + F5/T01 新建简化 → 渲染端能跑
阶段 2  (3 天) ─ F4 组件内部响应式 → 看得到内容自适应效果
阶段 3  (4 天) ─ F3 编辑器重构 → 设计师可用
阶段 4  (2 天) ─ F5/T02, F5/T03 v1 兼容 + 迁移 → 旧大屏不破
阶段 5  (1 天) ─ IT: Chrome 95 全链路验证 + 文档
```

## 完成标准

- [x] 新建大屏对话框不再要求选择固定像素尺寸 — `ScreensPage.handleCreateV2`
- [x] 任意新建大屏在 Chrome 95 的 1366×768 / 1920×1080 / 3840×2160 全部铺满、布局合理、无关键内容被裁 — `ResponsiveScreenLayout` + ResizeObserver（运行时待实机最终确认）
- [x] 编辑器基于 grid units 拖放，不再出现 px 坐标 — `DesignerCanvasV2` + `PropertyPanelV2`
- [x] 所有图表组件在容器 resize 时自动 `chart.resize()` — `EChartsRuntime.tsx`
- [x] 文字/KPI 字号按容器宽度自适应 — `useContainerFontSize` + `ResponsiveText.tsx`（Chrome 95 不支持 cqw，用 ResizeObserver 替代 clamp）
- [x] 旧 v1 大屏能只读打开（不崩），有"转为 v2"按钮 — `V1LegacyBanner` + `migrateV1ToV2`
- [ ] Chrome 95 烟雾测试通过（`it/chrome-95-evidence/` 有截图/视频）— 待客户侧实机验证

## 范围外（不在本 Sprint）

- 多断点响应式（lg/md/sm 各一份布局） — 后续 Sprint
- v1 大屏自动平滑迁移到 v2 的保真算法 — demo 阶段接受手动重做
- 移动端竖屏重排（手机竖屏专属布局） — 后续 Sprint

## 参考

- [react-grid-layout](https://github.com/react-grid-layout/react-grid-layout) — 主布局引擎候选
- Grafana / Apache Superset / Redash 的大屏实现参考
