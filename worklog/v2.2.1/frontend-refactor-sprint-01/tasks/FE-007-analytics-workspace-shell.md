# FE-007

## 标题

重构 `analytics modern` 工作区壳。

## 范围

- `source/dts-analytics-webapp/modern/src/pages/screens/ScreenDesignerPage.tsx`
- `source/dts-analytics-webapp/modern/src/pages/screens/ScreenDesigner.css`
- `ScreenHeader`
- `CanvasToolbar`
- `PropertyPanel`
- `LayerPanel`
- `PageManagerPanel`

## 目标

- 设计器页与统一产品风格对齐
- 保留高密度工作区属性，不把设计器拍扁成普通后台页
- 控件栏、页签、侧栏、属性区全部进入统一视觉语言

## 交付

- 一套新的 workspace shell
- 更稳定的 panel、toolbar、header 层级

## 验收

- `pnpm -C source/dts-analytics-webapp/modern build`
- 设计器页结构清晰，面板/工具栏/属性区风格统一

## 风险

- 工作区组件内联样式较多，重构时要避免引入新的局部漂移
