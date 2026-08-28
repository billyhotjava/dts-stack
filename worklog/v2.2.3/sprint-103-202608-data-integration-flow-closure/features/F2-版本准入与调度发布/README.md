# F2：版本准入与调度发布

**优先级**：P0
**状态**：`IMPLEMENTED_AND_DEPLOYED`
**依赖**：F1 完成

## 目标

让已校验任务配置通过现有 revision/admission/DAG 发布链激活，并保证配置表单、目标资产、接入后质量配置、自动拓扑、ACTIVE revision、DAG 和调度状态使用同一 plan checksum。

## 契约定义

| 类型 | 契约 | 关键行为 |
|---|---|---|
| 版本 | `IngestionTaskRevision` / runtime snapshot | 固定 canonical plan、`destination.assetRef`、post-quality 配置与 `planChecksum` |
| 发布 | 既有 `POST /tasks/{id}/admit` | `X-Expected-Plan-Checksum`；旧 checksum 409 |
| 拓扑 | `GET /tasks/{id}/topology?view=ACTIVE` | 从已激活 revision 生成，不读取最新草稿 |
| 调度 | `POST .../schedule/enable|pause` | 幂等；服务端解析 owned DAG |
| 基础设施 | existing staged/publish/reconcile | 失败保留旧 ACTIVE revision/DAG |

## UI/UX 规格

- 设计 Tab 明确区分“保存草稿、校验、提交发布”，不能用一个“保存 DSL”替代生命周期。
- 发布抽屉展示本次 revision、plan checksum、源/目标/映射/调度摘要和校验结果。
- 发布摘要明确“接入后质量验证”只在 execution 提交成功后运行，不把它描述为同步发布门禁。
- 提交期间显示“准备运行计划 → 发布调度 → 激活版本”；失败保留旧版本并给出 correlationId。
- 启用/暂停只在 ACTIVE revision 和 owned DAG 状态满足时可用。
- 历史版本可查看自动拓扑，但不可直接修改。

## Tasks

| Task | 状态 | 依赖 | 产出 |
|---|---|---|---|
| [T01-绑定任务版本与拓扑投影](T01-绑定任务版本与拓扑投影.md) | IMPLEMENTED_AND_DEPLOYED | F1/T02 | canonical plan、plan checksum、revision/runtime/execution 身份 |
| [T02-接入准入发布与调度状态机](T02-接入准入发布与调度状态机.md) | IMPLEMENTED_AND_DEPLOYED | F2/T01 | admit 条件、原子 DAG 发布、任务级启停和补偿 |

## Definition of Ready

- [ ] canonical plan 字段集合、checksum 算法、存储位置与 migration 已冻结。
- [ ] 既有 admission 直接/间接消费者和兼容 Header 策略已完成影响分析。
- [ ] 发布抽屉与调度控件的四态、权限和错误码已命名。

## 完成标准

- [ ] 任一 execution 可反查 task/revision/plan checksum 与 ACTIVE 拓扑。
- [ ] 任一新 execution 冻结的 destination.datasetId 与 `qualityPolicyRef` 都可反查同一 ACTIVE revision。
- [ ] 发布失败不激活新 revision；启停只作用于任务 own 的 DAG。
- [ ] 页面和 API 不以通用 dagId 作为业务控制身份。
