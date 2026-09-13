# T05: 前端 source-contract 与 Chrome95 基线

**优先级**: P0  
**状态**: READY  
**依赖**: T02/T03/T04

## 目标

为 F0 的页面承载面重构建立测试和浏览器基线，防止后续标准/dbt 联动实现再次引入假按钮、错乱表格或 Chrome95 不兼容交互。

## 技术设计

- 使用 source-contract 覆盖页面标题、主按钮、路由交接、禁用原因和接口契约。
- 使用 focused build 或 module build 作为前端构建验证。
- 使用 Chrome/Playwright smoke 覆盖 1366x768 关键页面截图。
- 避免引入 Chrome95 不支持的语法、CSS 或复杂交互。

## 影响范围

- `source/dts-platform-webapp/src/pages/modeling/*.source-contract.test.ts`
- `source/dts-platform-webapp/src/pages/governance/*.source-contract.test.ts`
- `worklog/v2.2.3/sprint-50-202606/it/README.md`
- `worklog/v2.2.3/sprint-50-202606/it/scripts/*`

## 验证

- [ ] source-contract 先红灯再实现。
- [ ] `pnpm build` 或等效模块构建通过。
- [ ] Chrome smoke 截图覆盖 SQL 建模、数据元、公共码表、dbt 文件浏览。

## 完成标准

- [ ] F0 具备可复查的测试和浏览器证据。
- [ ] 后续 F1-F6 可以复用同一验证基线。

