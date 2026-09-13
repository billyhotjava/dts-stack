# F3: 编辑器重构（网格编辑）

**优先级**: P0
**状态**: DONE
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
| T01 | DesignerCanvas 切换为 GridLayout 编辑模式 | P0 | DONE | F1-T02 |
| T02 | 拖放与 resize 产生 grid units（不是 px） | P0 | DONE | T01 |
| T03 | 属性面板 Size / Position 控件改 grid 单元 | P0 | DONE | T01 |
| T04 | 网格可视化 + 吸附反馈 | P1 | DONE | T01 |

## 完成标准

- [x] 编辑器打开 v2 大屏能拖放组件（grid units）— `ScreenDesignerV2Page` + `DesignerCanvasV2` + `ComponentLibraryPanel` 拖放
- [x] 属性面板显示 grid 单元数（而非 px）— `PropertyPanelV2` Size/Position 输入全部 grid units
- [x] 保存的大屏是合法的 v2 JSON — `buildV2Payload` 结构符合 F2/T01 schema，后端 F2/T03 已透传 v2Spec
- [x] 编辑态的视觉与运行态完全一致（WYSIWYG）— 编辑器与 `ResponsiveScreenLayout` 共用 react-grid-layout + 同一 `ComponentRenderer`
