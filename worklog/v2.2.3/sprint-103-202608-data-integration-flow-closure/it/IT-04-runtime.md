# IT-04：任务级运行账本

检查日期：2026-08-28。判定：`PASS_SOURCE_DEPLOYED`。

## 已验证

- 列表、latest、detail、log、cancel、retry 均为 task-scoped API；页面不再把通用 Airflow DAG 列表作为业务运行入口。
- 列表使用服务端分页、状态过滤和 5 秒 active polling；终态不触发持续轮询。
- 运行列表默认按当前受控版本查询，`revisionNumber` 与 taskId、status、failureCategory 在 repository 共同过滤；用户可显式切换到全部历史版本。
- repository、service、resource 的版本过滤聚焦测试共 5 项通过，真实浏览器确认 task 13 当前版本请求携带 `revisionNumber=1`，全部历史请求不携带该参数。
- 平台执行详情与列表批量投影目标资产、workflow/run、证据新鲜度和消费资格；27 个平台聚焦测试全部通过。
- execution 与资产/质量深链由服务端 ID 构造，路径参数均编码。

## 保留门禁

本轮最终浏览器旅程只做只读验收，不向现有客户任务发起执行命令。
