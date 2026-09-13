# T02: Chrome95 与表格布局验收

**优先级**: P0
**状态**: DONE
**依赖**: T01

## 目标

定义客户现场 Chrome 95 下必须关注的 UI 风险。

## 技术设计

硬约束：

- 不引入 container query、CSS `:has()`、`structuredClone` 无 fallback、`Array.prototype.toSorted` 等新 API
- 表格必须明确 `scroll.x`、列宽、操作列宽、tag nowrap
- Drawer/Modal 必须在 1366x768 和窄屏下不遮挡主操作
- 长中文按钮必须稳定，不因 loading/disabled 改变布局

## 影响范围

- `assets/button-component-matrix.md`
- `assets/dts-frontend-refactor-rules.md`

## 验证

- [x] 与 `dts-chrome95-regression` skill 保持一致

## 完成标准

- [x] 后续浏览器验证有明确检查项
