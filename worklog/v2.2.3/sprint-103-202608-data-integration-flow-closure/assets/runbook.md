# 数据集成流程运行手册

适用范围：Sprint-103 的任务设计、版本发布、任务级运行、接入后质量验证和资产可信证据投影。

## 运行事实与定位键

排障时按同一链路核对，不用页面文案代替账本：

`taskId → revisionNumber/effectiveConfigChecksum → ingestion execution id/executionRunId → targetDatasetId → qualityWorkflowId/qualityRunId → asset consumptionEligibility`

严格审计另有 `auditOperationId`。当前没有跨所有服务自动透传的单一 correlationId；不得把任一局部 ID 宣称为全链路 correlationId。

日志和审计禁止记录密码、token、连接串凭据、完整 DSL 或脚本正文。

## 健康与容量基线

| 信号 | 正常 | 告警/人工介入阈值 | Owner |
|---|---|---|---|
| ingestion/platform health | `UP` | 连续 2 次、间隔 30s 非 UP | 平台运维 |
| Airflow health/import errors | scheduler/database healthy；import error=0 | 任一 import error 或 scheduler 非 healthy 持续 2 分钟 | 调度运维 |
| design 保存 | 单请求 ≤1 MiB、mapping ≤1000 | 413/422 持续出现或 p95 >1s 持续 5 分钟 | 接入服务 |
| topology | 单任务固定 4～5 节点 | p95 >500ms 持续 5 分钟 | 接入服务 |
| execution 查询 | 默认 10 条分页，服务端过滤 | p95 >2s 或错误率 >5% 持续 5 分钟 | 接入/平台 |
| 前端轮询 | 存在活动实例时每 5s；终态停止 | 同任务终态后仍持续请求 1 分钟 | 前端 |
| 质量触发 | 成功 execution 后出现 workflow 关联 | `RETRY_WAIT` 超过既定重试窗，或任一 `EXHAUSTED` | 数据质量 |
| 当前证据 | 仅最新 execution 的精确 triggerRef 可为 CURRENT | 新 execution 后仍显示旧批可信，立即 P0 | 平台/质量 |
| 取消 | PREPARING 直接取消；运行态向 Airflow 严格提交 | `CANCEL_REQUESTED` 超过 30s | 接入/调度 |
| 对账差异 | 未审批 DAG 全部 KEEP，import error=0 | DAG 数或暂停状态无发布单却变化 | 调度运维 |

以上阈值是值守判据；若监控平台尚未配置对应规则，先使用健康端点、日志和只读 SQL 执行人工检查，并将自动告警接线作为运维项，不得宣称已自动告警。

容量边界：按 10,000 任务、每任务 10,000 execution 的服务端分页设计；页面不全量加载 execution。目标资产/质量证据按页批量读取，禁止逐行 latest-run 查询。

## 常见故障处置

### 1. 保存返回冲突

现象：`TASK_DESIGN_CONFLICT` / HTTP 409。

动作：保留用户本地输入；重新读取 design 和 `planChecksum`，由用户确认差异后再保存。禁止移除 `If-Match` 或直接覆盖任务整 DTO。

### 2. 校验为 422

按 issue code 处理：

- `TARGET_ASSET_UNRESOLVED`：核对明确 `CatalogDataset.id`、enabled、部门和密级可见性。
- `QUALITY_ASSET_MISMATCH`：核对 mapping/destination 物理表与所选 dataset 为同一对象。
- `QUALITY_BINDING_UNAVAILABLE`：在质量模块发布并启用该 dataset 的正式规则，或明确关闭“接入后质量验证”。
- `SCHEDULE_INVALID`：使用 Spring 六段 Cron。
- `TABLE_MAPPING_LIMIT_EXCEEDED` / `DESIGN_PAYLOAD_TOO_LARGE`：拆分任务，不提高在线上限。

不要用同名库表猜测 datasetId；存在 `POSTGRES/POSTGRESQL` 重复身份时必须让业务 owner 明确选择。

### 3. 发布失败或拓扑不一致

核对 DRAFT/ACTIVE topology 的 `planChecksum`、revision 状态、DAG staging/publish 日志。ACTIVE topology 必须来自 ACTIVE revision，不得把最新 draft 当作执行版本。失败时保持旧 ACTIVE，不手工改表。

### 4. 启停失败

命令只接受 taskId，由服务端解析 owned DAG。`TASK_SCHEDULE_DAG_MISSING` 时先修复/重新准入该任务；禁止从浏览器传任意 dagId。Airflow 不可达时保留任务原状态，恢复后重试。

### 5. 运行提交结果不确定

浏览器重试必须复用同一个 `Idempotency-Key`。按 taskId + batch-id 哈希查 execution；唯一索引确保同一命令只有一条账本。禁止在网络超时后立即生成新 key 重试。

### 6. 重试或取消失败

- 重试：只有 FAILED/EXHAUSTED/CANCELLED 可重试；按 `parentExecutionId` 查唯一子实例。
- 取消：PREPARING/PENDING 本地收敛；RUNNING/QUEUED 必须有 airflowDagId 与 executionRunId。Airflow 严格取消失败时保留非终态并告警，不伪造 CANCELLED。

### 7. 接入成功但质量未开始

接入成功事实不回滚。核对 execution 的 `qualityPolicyRef`、`targetDatasetId`、触发 attempt、`qualityWorkflowStatus`。`RETRY_WAIT` 由既有补偿继续；`EXHAUSTED` 需要质量 owner 处理。未形成精确 workflow 时证据保持 PENDING/MISSING/TRIGGER_FAILED。

### 8. 旧 PASS 冒充当前可信

立即按 P0 处理：核对页面所选 execution 是否为任务最新 DB id，workflow `triggerRef` 是否严格等于 `ingestion:<executionId>`，datasetId 是否一致。任一无法证明时投影必须为 STALE/MISSING 且 `trustedUsable=false`。

### 9. 资产存在但不可消费

“可信可用”不是生命周期状态；必须同时满足 CURRENT、质量 PASSED、资产 `consumptionEligibility=ELIGIBLE`。按 `eligibilityReasons` 修复质量、权限、密级或资产治理缺口，禁止直接写 `TRUSTED` 状态。

### 10. 日志缺失

按 taskId/execution DB id 访问 task-scoped logs；再核对 airflowDagId、executionRunId 和 try number。普通任务不回退到通用 DAG 枚举或客户端拼接日志路径。

### 11. legacy DSL 或 DAG 漂移

旧 `graphDsl` 仅只读兼容，不参与发布。DAG 差异按 [DAG 对账](dag-reconciliation.md) 默认 KEEP；只有精确对象、调用方证明和批准单齐全后才允许接管或退役。

### 12. 平台定时任务出现 `machineActor is not trusted`

该错误在 Sprint-103 发布前已存在，发布后仍会由 `QualityWorkflowReconciler` 周期性记录；它不等同于 ingestion/platform 健康失败，也不是本次任务编排请求产生的错误。值守时分别核对 `/management/health`、用户请求 requestId 与定时线程日志；不要为消除日志而放宽 machine actor 信任边界。单独修复其服务身份配置并保留审计证据。

## 安全降级

- 平台证据 join 异常：阻断可信结论，保留 ingestion 原始状态。
- 质量服务异常：接入可成功，质量保持待验证/触发失败，不提升资产可消费性。
- Airflow 异常：暂停受影响任务的调度操作和新运行，不修改历史账本。
- 前端异常：先回滚 webapp；后端 additive API 可继续保留。

## 日常只读检查

- 服务：Compose 状态、ingestion/platform `/management/health`、Airflow `/health`。
- schema：`databasechangelog`、3 个 `target_dataset_id` 列、两个 execution 唯一索引。
- 账本：按 task/revision/checksum/execution/dataset/workflow/run 七层核对。
- 安全：扫描日志和审计 metadata，不得出现凭据或完整配置正文。
- DAG：比较注册清单、暂停状态、最后运行和 [基准清单](dag-reconciliation.md)。
