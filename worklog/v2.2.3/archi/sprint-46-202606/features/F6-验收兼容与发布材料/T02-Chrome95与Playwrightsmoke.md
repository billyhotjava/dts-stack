# T02: Chrome95 与 Playwright smoke

**优先级**: P0  
**状态**: DONE  
**依赖**: T01

## 目标

用浏览器验证唯一工作台、自定义抽屉、旧入口兼容和基础视觉状态。

## Smoke 场景

- `/workbench` 正常渲染唯一首页。
- `/workbench?customize=1` 自动打开自定义抽屉。
- 勾选组件后保存，首页组件数量变化。
- 上移/下移后保存，刷新后顺序保持。
- 恢复默认后回到角色模板。
- `/workbench/data-management` 跳转到 `/workbench?section=data-management`。
- `/services/consumption` 跳转到 `/workbench?section=consumption`。
- 禁用后端或模拟错误时首页展示错误态，不显示 demo 数字。

## Chrome 95 检查

- 不出现 `structuredClone`。
- 不出现 container query。
- 不使用拖拽库。
- 构建产物不依赖新语法目标。

## 完成标准

- [x] Playwright smoke 通过。
- [x] 截图保存到 `it/evidence/`。
- [x] Chrome 95 兼容检查结果写入 IT README。
