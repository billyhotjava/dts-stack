# IT-07：本地 Chrome 集中旅程

检查日期：2026-08-28。判定：`PASS_WITH_ENV_NOTE`。

## 目标环境与制品

- 浏览器：本机 `/usr/bin/google-chrome`，版本 `150.0.7871.128`。
- Chrome 95 executable：不可用，因此本记录不能作为客户现场 Chrome 95 通过证据。
- 代码提交：RED `17f357e05`、GREEN `0be59c528`。
- 运行镜像：ingestion `312bf5e27191…`、platform `c741b8c1ef23…`（本次未替换）、webapp `095997b4ccc6…`。
- 回滚镜像：`dts-ingestion:rollback-sprint103-orchestration-20260828-124641`、`dts-platform-webapp:rollback-sprint103-orchestration-20260828-124641`。

## 真实只读旅程

使用真实平台会话和新增长期回归用例 `e2e/sprint103-orchestration-landing-real.spec.ts` 完成：

1. 不带 `taskId` 打开任务编排，默认进入服务端分页任务列表，不再自动打开第一条任务。
2. 列表请求为 `page=0&size=10`，真实返回 6 个当前账号可访问的 DTS 接入任务；页面明确说明未关联接入任务的 Airflow DAG 不进入列表。
3. 从 task 13 进入设计页并返回列表，URL 的 `taskId/tab` 深链和返回行为正确。
4. 从 task 13 进入运行实例；默认请求携带 `revisionNumber=1`，切换“全部历史版本”后请求不再携带版本参数。
5. 所有 execution 请求均位于 `/api/ingestion/tasks/13/executions...`；没有请求 Airflow jobs/dags 全局列表。
6. 1366×768 与 390×844 两种视口通过，窄屏由表格内部横向滚动承载，document 未发生横向溢出。

最终结果：`1 passed (10.0s)`；page error 0、console error 0、failed request 0、目标站点 HTTP 4xx/5xx 0。没有点击保存、发布、启停、立即运行、重试或取消。

截图：`/tmp/sprint103-e2e/task-list-1366x768.png`、`/tmp/sprint103-e2e/task-runs-1366x768.png`、`/tmp/sprint103-e2e/task-list-390x844.png`。本次没有截取含连接参数的设计表单；本次生成的认证 `user.json` 在测试进程退出时删除，凭据未落盘。

## Harness 说明

首次定位因 Ant Design 两字按钮的无障碍名称为“设 计”而超时；第二次因虚拟 Select 的隐藏 option 被命中而超时。回归用例分别改为容忍空白的按钮名称和可见 dropdown 定位，最终定向复跑通过。两次失败均已完成认证，但未触发任何业务写命令。

## 保留门禁

Chrome 95、三角色隔离会话、并发保存及安全金丝雀写入仍需客户现场复验。
