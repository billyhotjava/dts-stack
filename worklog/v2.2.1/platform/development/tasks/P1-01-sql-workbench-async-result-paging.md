# P1-01 SQL Workbench 异步执行与结果分页

- 优先级：P1
- 状态：planned

## 范围

- 将 SQL 执行改为异步任务模型，避免长查询阻塞 Web 请求。
- 提供服务端分页读取结果，替代一次性装载。

## 子任务

- `SqlExecutionService` 拆分 submit 与 execute worker。
- 增加结果分页 API（按 executionId + page/pageSize）。
- 前端 `SqlWorkbenchExperimental.tsx` 改为轮询状态 + 分页渲染。
- 引入查询超时、最大返回行数与取消机制。

## 验收标准

- submit API 在短时间内返回 executionId。
- 1000+ 行结果可分页浏览，不出现前端卡顿。
- 取消查询可及时生效。

## 风险与回滚

- 风险：任务状态一致性问题。
- 回滚：保留同步执行开关用于紧急切换。
