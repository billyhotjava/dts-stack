# IT-07：本地 Chrome 集中旅程

检查日期：2026-08-28。判定：`PASS_WITH_ENV_NOTE`。

## 目标环境与制品

- 浏览器：本机 `/usr/bin/google-chrome`，版本 `150.0.7871.128`。
- Chrome 95 executable：不可用，因此本记录不能作为客户现场 Chrome 95 通过证据。
- 运行镜像：ingestion `40cae5c9e830…`、platform `5ad73eb0d5e6…`、webapp `033dcc1937f9…`。

## 真实只读旅程

使用真实平台会话完成：登录 → task 13 任务设计 → DRAFT/ACTIVE 只读拓扑 → 任务级运行实例 → execution 90 资产/质量证据详情。没有点击保存、发布、启停、立即运行、重试或取消。

关键请求均为 HTTP 200：

- task list；
- `/api/ingestion/tasks/13/design`；
- `/api/ingestion/tasks/13/topology?view=ACTIVE`；
- `/api/ingestion/tasks/13/executions`；
- `/api/ingestion/tasks/13/executions/90`。

结果：page error 0、console error 0、failed request 0。页面正确显示 ACTIVE/R1、plan checksum、四步设计、只读拓扑、13 条任务级 execution，以及迁移前目标资产未解析时的 `UNKNOWN/CONDITIONAL/未形成可信结论` 降级态。

遮罩截图：`/tmp/sprint103-e2e/design.png`、`/tmp/sprint103-e2e/runs.png`。所有 input/textarea 均已遮罩，凭据未落盘。

## Harness 说明

首次脚本在登录硬跳转与目标路由之间发生竞态，未请求编排 API；第一次定向复跑因“校 验”按钮无障碍名称含空白而误判。修复临时 harness 后最终旅程通过。两次失败均未触发业务写命令。

## 保留门禁

Chrome 95、三角色隔离会话、并发保存及安全金丝雀写入仍需客户现场复验。
