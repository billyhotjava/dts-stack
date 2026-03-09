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
- `ScreenDesigner` 左右侧 rail 与中心画布进入统一 light-first workspace frame
- `ScreenHeader` 增加大屏元信息 chips，主操作区与工具菜单收敛到统一控制台语言
- `CanvasToolbar` 增加画布/选中状态信息，工具区改成 card-strip 风格
- `PageManagerPanel`、`LayerPanel` 去除核心 inline 样式，回到 class contract
- `PropertyPanel` 保持业务编辑逻辑不变，但头部、密度态和 section 壳层进入统一视觉

## 验收

- `pnpm -C source/dts-analytics-webapp/modern build`
- 设计器页结构清晰，面板/工具栏/属性区风格统一

## 完成记录

- 2026-03-09：已完成
- 验证：`pnpm -C source/dts-analytics-webapp/modern build`
- 结果：通过，产物中 `ScreenDesigner` 相关 CSS/JS 正常生成

## 风险

- 工作区组件内联样式较多，重构时要避免引入新的局部漂移
