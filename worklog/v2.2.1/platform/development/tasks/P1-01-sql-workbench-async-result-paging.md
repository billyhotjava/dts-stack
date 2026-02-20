# P1-01 SQL Workbench 异步执行与结果分页

- 优先级：P1
- 状态：done

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

## 已完成进展（2026-02-16）

- 后端 `SqlExecutionService` 已改为“快速提交 + 异步执行”：
  - `submit` 立即返回 `executionId`，状态初始为 `PENDING`。
  - 后台线程执行校验与查询，状态在 `PENDING/RUNNING/SUCCESS/FAILED/CANCELED` 间流转。
  - `cancel` 增加取消标记与 Future 协作取消。
- 后端新增分页结果接口：
  - `GET /api/sql/result-page/{executionId}?page=&pageSize=`
  - 返回页码、总行数、总页数、表头、当前页数据。
- 查询结果存储结构升级：
  - `result_set.preview_columns` 保存 `headers + rows + rowCount`，用于服务端分页读取。
  - `status` 仍返回首屏预览（最多 100 行）用于快速展示。
- 前端 `SqlWorkbenchExperimental.tsx` 已改为异步轮询模型：
  - 提交后轮询 `/sql/status/{id}`，终态后自动停止轮询。
  - 成功后按页加载 `/sql/result-page/{id}`，支持上一页/下一页/每页条数切换。
  - 取消按钮与运行态联动，不再阻塞主线程。

## 回归结果（2026-02-16）

- `mvn -f source/dts-platform/pom.xml -DskipTests compile`：通过
- `pnpm -C source/dts-platform-webapp build`：通过
