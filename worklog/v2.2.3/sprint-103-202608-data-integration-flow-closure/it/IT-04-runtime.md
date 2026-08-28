# IT-04：任务级运行账本

检查日期：2026-08-28。判定：`PASS_SOURCE_DEPLOYED`。

## 已验证

- 列表、latest、detail、log、cancel、retry 均为 task-scoped API；页面不再把通用 Airflow DAG 列表作为业务运行入口。
- 列表使用服务端分页、状态过滤和 5 秒 active polling；终态不触发持续轮询。
- 平台执行详情与列表批量投影目标资产、workflow/run、证据新鲜度和消费资格；27 个平台聚焦测试全部通过。
- execution 与资产/质量深链由服务端 ID 构造，路径参数均编码。

## 保留门禁

本轮最终浏览器旅程只做只读验收，不向现有客户任务发起执行命令。
