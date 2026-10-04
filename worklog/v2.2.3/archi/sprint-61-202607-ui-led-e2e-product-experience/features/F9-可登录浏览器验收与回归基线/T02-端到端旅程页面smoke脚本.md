# T02: 端到端旅程页面 smoke 脚本

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

把 Sprint-61 的 Browser Smoke 清单固化成脚本，验证用户能按同一条旅程进入关键页面。

## 技术设计

- 覆盖路径：工作台、数据源、标准包、数据元、低代码建模、SQL 建模、指标工作台、数据服务、运行实例。
- 每个页面检查上下文条、返回工作台、继续下一步。
- 失败时保存截图、console、network 关键错误。
- 与 source-contract 分工：source-contract 固定代码契约，Playwright 验证真实浏览器体验。

## 影响范围

- `source/dts-platform-webapp`
- `worklog/todo/sprint-61-202607-ui-led-e2e-product-experience/it/`

## 验证

- [ ] smoke 脚本能本地执行。
- [ ] 每个路径至少断言一个页面级可见元素。
- [ ] 失败证据写入 `it/`。

## 完成标准

- [ ] 端到端旅程可通过命令重复验证。
- [ ] 登录、接口、布局失败能被区分。
