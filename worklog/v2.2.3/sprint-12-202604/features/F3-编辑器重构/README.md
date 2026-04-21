# F3: 编辑器重构（网格编辑）

**优先级**: P0
**状态**: READY
**依赖**: F1, F2

## 目标

大屏编辑器（ScreenDesignerPage / DesignerCanvas）从"绝对像素自由拖放"改为"Grid 单元可视化拖放"。属性面板的 Size/Position 控件从 px 改为 grid units。

## 设计原则

- **编辑器也用 `react-grid-layout`**（同一个引擎，保证所见即所得）
- 编辑模式用 `isDraggable / isResizable = true`
- 拖放产生 grid 单元坐标，实时 normalize
- 网格背景可视化（淡线条显示 12 列）
- 吸附自动由 react-grid-layout 提供（不用自己造）

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | DesignerCanvas 切换为 GridLayout 编辑模式 | P0 | READY | F1-T02 |
| T02 | 拖放与 resize 产生 grid units（不是 px） | P0 | READY | T01 |
| T03 | 属性面板 Size / Position 控件改 grid 单元 | P0 | READY | T01 |
| T04 | 网格可视化 + 吸附反馈 | P1 | READY | T01 |

## 完成标准

- [ ] 编辑器打开 v2 大屏能拖放组件（grid units）
- [ ] 属性面板显示 grid 单元数（而非 px）
- [ ] 保存的大屏是合法的 v2 JSON（通过 validate）
- [ ] 编辑态的视觉与运行态完全一致（WYSIWYG）
