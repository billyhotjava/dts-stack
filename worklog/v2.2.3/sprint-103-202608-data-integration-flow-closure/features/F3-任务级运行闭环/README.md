# F3：任务级运行闭环

**优先级**：P0
**状态**：`IMPLEMENTED_AND_DEPLOYED`（业务金丝雀与 Chrome 95 见 F4 环境说明）
**依赖**：F2 完成

## 目标

把运行 Tab 从“通用 Airflow 控制台”收敛为“当前接入任务执行账本”，完整支持分页查询、深链、运行、重试、取消、状态、日志和权限，并贯通目标数据资产与本次接入后的正式质量证据。

## 契约定义

| 类型 | 契约 | 关键字段/行为 |
|---|---|---|
| 查询 | 既有 task executions list/latest | 服务端分页/过滤；`taskId, revisionNumber, planChecksum` |
| 执行 | 既有 task execute | 固定 ACTIVE revision；幂等键；创建 PREPARING 后触发 |
| 重试 | 既有 task retry | `sourceExecutionId`、冻结原输入、状态门禁 |
| 取消 | `POST .../{taskId}/executions/{executionId}/cancel` | CANCEL_REQUESTED → CANCELLED/补偿 |
| 日志 | task execution logs | 按 executionId 分页，所有 connector 可用 |
| 目标资产 | `destination.assetRef` / catalog observation | execution 冻结同一 datasetId；成功后可深链资产详情 |
| 质量触发 | existing post-ingestion quality workflow | `datasetId + ingestionExecutionId` 幂等；提交后异步触发 |
| 质量证据 | `QualityEvidenceRef` | trigger/evidence/quality 三类状态分开；workflow/run 可回链 |
| 可信派生 | asset status + consumption eligibility | 仅 `CURRENT/PASSED + ELIGIBLE` 显示“可信可用” |
| 深链 | `?taskId={id}&executionId={id}` | dagId 仅诊断字段，不作业务身份 |

## UI/UX 规格

- 运行 Tab 必须已有 taskId；未选择任务时显示说明性空态，不枚举全部 DAG。
- 顶部展示当前 ACTIVE revision、plan checksum 和调度状态；列表默认每页 10 条。
- 筛选、分页、刷新由服务端执行；轮询保持当前页与选中实例，终态后停止。
- 实例详情展示接入与质量两条独立时间线、目标资产、版本、来源重试、失败阶段、日志和 correlationId。
- “立即运行、重试、取消”按服务端 capability/state 控制；失败不清空旧列表。
- Airflow 外链仅对有权限运维人员提供诊断，不替代平台状态。

## Tasks

| Task | 状态 | 依赖 | 产出 |
|---|---|---|---|
| [T01-收敛任务级实例查询与深链](T01-收敛任务级实例查询与深链.md) | IMPLEMENTED_AND_DEPLOYED | F2/T02 | task-scoped 分页、服务端过滤、execution 深链 |
| [T02-闭合执行重试取消与日志](T02-闭合执行重试取消与日志.md) | IMPLEMENTED_AND_DEPLOYED | F3/T01 | 幂等命令、取消状态机、关联重试、全类型日志 |
| [T03-接通接入质量与资产证据](T03-接通接入质量与资产证据.md) | IMPLEMENTED_AND_DEPLOYED | F3/T02 | post-commit 质量触发、当前证据、资产/质量双向深链与可信派生 |

## Definition of Ready

- [ ] execution 状态、分页、过滤、plan checksum 与权限契约已冻结。
- [ ] cancel adapter、可取消状态和补偿 owner 已证明。
- [ ] 通用 Airflow API 的兼容/运维定位已明确。
- [ ] 新目标资产登记时点、quality evidence 新鲜度和 asset projection DTO 已由 F0/T02 冻结。

## 完成标准

- [ ] 页面不调用通用 `/api/etl/airflow/jobs/*` 完成业务操作。
- [ ] 所有实例均能证明 task/revision/plan checksum 所属关系。
- [ ] 终态、重试来源、取消进度、日志与失败原因可回放。
- [ ] 同一 execution 可反查 dataset/workflow/run；新批次会使旧通过证据失效，质量失败不改写接入成功。
- [ ] 资产详情与 execution 详情对同一证据给出相同质量状态、资格与原因。
- [ ] Chrome 95 单旅程覆盖运行、重试、取消和日志。
