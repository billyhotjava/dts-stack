# T02: feature/菜单/客户语言/Chrome95 skills

**优先级**: P0
**状态**: DONE
**依赖**: T01

## 目标

创建 4 个 DTS 专属 skills，用于把页面审计结果转为 sprint feature/task，并约束菜单路由、客户语言和浏览器兼容。

## 技术设计

- `dts-frontend-feature-matrix`: 页面、按钮、组件到 feature/task 的转换规则
- `dts-menu-route-convergence`: 菜单、路由、兼容跳转和重复页面收敛规则
- `dts-customer-language-polish`: 客户可见文案与 worklog 表达规则
- `dts-chrome95-regression`: Chrome 95、表格布局、控制台和浏览器验收规则

## 影响范围

- `/home/billy/.codex/skills/dts-frontend-feature-matrix/`
- `/home/billy/.codex/skills/dts-menu-route-convergence/`
- `/home/billy/.codex/skills/dts-customer-language-polish/`
- `/home/billy/.codex/skills/dts-chrome95-regression/`

## 验证

- [x] 4 个 skill 均通过 `quick_validate.py`

## 完成标准

- [x] 后续 UI 编码具备 page-first、menu-first、language-first、browser-first 的执行约束
