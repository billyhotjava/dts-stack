# 逻辑建模上线单一提交流程设计

## 背景

当前逻辑建模页“上线（dbt build）”采用前端串行调用：

1. `GET /etl/dbt/dag/ready`
2. `POST /etl/dbt/quality-gate/check`
3. `POST /etl/dbt/release-gate/check`
4. `POST /etl/dbt/run`

这条链路有两个根本问题：

- 用户的一次“上线”被拆成了多次同步请求，任何一步变慢都会表现成界面长时间转圈。
- 后端真正触发 build 时仍会同步做 source refresh、DAG 查询和 trigger 前准备，导致提交动作不是快速返回的“任务提交”，而是一个阻塞式事务。

在现场局域网环境下，正常交互应当是毫秒级。如果“上线”需要等待数秒甚至 15 秒以上，问题一定在代码和接口设计。

## 目标

- 将逻辑建模页“上线”收敛成单一后端接口。
- 前端只处理三种结果：`SUBMITTED`、`WARNING`、`BLOCKED`。
- DAG 未就绪时快速失败，不能在 HTTP 请求里等待 30 秒。
- 保留现有质量门禁和发布门禁能力，但不再让前端自己编排多个接口。

## 方案

### 1. 新增统一接口

新增 `POST /api/etl/dbt/release/submit`。

请求体：

- `models`
- `target`
- `vars`
- `gitRef`
- `commitSha`
- `strictMode`
- `confirmWarnings`

返回体：

- `status`: `SUBMITTED | WARNING | BLOCKED`
- `blocking`
- `warning`
- `blockers`
- `warnings`
- `qualityGate`
- `releaseGate`
- `dagId`
- `dagRunId`
- `buildEvidence`

### 2. 后端由服务统一编排

新增 `DbtReleaseSubmissionService`，内部顺序：

1. 规范化 selector / dagSelector
2. 快速检查 DAG 是否已注册
3. 执行质量门禁
4. 执行发布门禁
5. 若有 blocker，直接返回 `BLOCKED`
6. 若有 warning 且未确认，返回 `WARNING`
7. 若允许提交，则刷新 sources、触发 Airflow DAG，并返回 `SUBMITTED`

关键点：

- DAG 未就绪直接返回，不等待注册。
- warning 二次确认由同一个接口通过 `confirmWarnings=true` 完成。
- 页面不再自己拼装 `qualityGate + releaseGate + triggerRun` 的状态机。

### 3. 前端改成单接口交互

`SqlModelingPage` 的 `submitRun` 改成：

1. 收集表单
2. 调用 `submitDbtRelease`
3. 若 `BLOCKED`，弹错误框
4. 若 `WARNING`，弹确认框，确认后带 `confirmWarnings=true` 重试
5. 若 `SUBMITTED`，更新本地 pending run summary 并刷新 run 列表

页面不再直接调用：

- `checkDagReady`
- `checkDbtQualityGate`
- `checkDbtReleaseGate`
- `triggerDbtRun`

### 4. 兼容策略

- 保留现有 `quality-gate/check`、`release-gate/check`、`dbt/run` 接口，避免影响其他调用方。
- 仅逻辑建模页切换到新接口。

## 失败与反馈策略

- DAG 未就绪：`BLOCKED`，文案明确提示稍后重试。
- 门禁阻断：`BLOCKED`，返回 blockers 列表。
- 门禁告警：`WARNING`，返回 warnings 列表。
- Airflow 触发失败：HTTP 4xx/5xx，沿用后端错误文案。

## 测试策略

后端：

- `confirmWarnings=false` 且有 warning 时返回 `WARNING`
- 有 blocker 时不触发 Airflow
- DAG 未就绪时快速失败，不等待注册
- `confirmWarnings=true` 时成功触发并返回 `SUBMITTED`

前端：

- `submitRun` 仅通过 `submitDbtRelease` 驱动
- `WARNING` 结果会触发确认后二次提交
- `BLOCKED` 结果不会进入 trigger/run 路径
