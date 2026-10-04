# 数据集成流程架构评审

评审日期：2026-08-27
评审范围：`dts-platform-webapp`、`dts-platform` 接入代理、`dts-ingestion` 任务/版本/执行/Airflow 链路，以及当前运行数据库与容器。
评审方式：源代码与运行态只读核验；未进行业务代码修改、构建、部署或登录后写操作。

## 1. 总体结论

“配置、发布和观察数据集成任务”是必要能力；“通用自由拖拽工作流编辑器”在当前证据下没有必要。

现有页面存在三条未闭合链路：

```text
编辑链：画布 → 前端弱校验 → localStorage / graphDsl → 结束
执行链：IngestionTask 配置 → revision/admit → Addax/Airflow → IngestionExecution
运行链：通用 Airflow DAG 列表 → 任意 dagId 触发/查询
```

真正执行已经以任务配置和 revision 为事实源。最小风险方案不是补一套 DSL 编译器，而是让页面回到现有 owner：用业务表单编辑任务配置，从指定 draft/revision 自动生成只读拓扑，并以 `taskId + revisionNumber + planChecksum` 贯通发布和运行。

## 2. 产品必要性判断

| 能力 | 当前必要性 | 结论 |
|---|---|---|
| 类型化配置数据源、目标、mapping、调度 | 高 | 现有任务的核心工作，应建设 |
| 自动拓扑、校验状态和运行定位 | 高 | 降低理解与排障成本，应建设 |
| 发布、启停、运行、重试、取消、日志 | 高 | 当前主链缺口，应闭合 |
| 自由编辑单任务 source/sink 图 | 低 | 表单更直接，且会形成双事实源 |
| 多作业依赖 DAG | 未证明 | 先做真实流程复杂度盘点 |
| 任意转换、循环、事件、回滚节点 | 当前不需要 | 缺少正式 owner、安全和恢复契约 |
| 模型/质量/审批领域流程画布 | 不合适 | 应由各领域状态机与账本负责 |

当前能力严格属于 E/L 数据集成。只有转换步骤引用已发布、版本化制品后才能称为 ELT；在图中直接保存 SQL/Python/Spark 不是可接受捷径。

## 3. 当前页面能力

| 阶段 | 当前实现 | 判定 | 关键证据 |
|---|---|---|---|
| 菜单与路由 | `/explore/etl/orchestration` 加载 `OrchestrationPage` | REAL | `portal-menu-seed.json:365-394`；`dynamic-resolver.tsx:88-92` |
| 任务绑定 | 只识别 `?taskId=`，没有稳定任务选择器 | PARTIAL | `OrchestrationPage.tsx:43-76` |
| 图编辑 | React Flow 节点、连线、序列化 | REAL（前端编辑） | `WorkflowCanvas.tsx`；`workflow-dsl/serialize.ts` |
| 图保存 | 先写全局 localStorage，再完整任务 DTO PUT | PARTIAL / 高风险 | `OrchestrationPage.tsx:78-101` |
| 后端 graphDsl | 可空 JSONB，进入 snapshot，但明确不被执行消费 | REAL（存储）/ 非执行 | `IngestionTask.java:72-74`；`20260505_01...xml:8-15` |
| 任务配置执行 | `/admit` 暂存并原子发布 DAG，revision 激活 | REAL | `IngestionTaskService.java:300-395,3598-3659` |
| 拓扑投影 | 没有从任务 draft/revision 生成的统一视图 | MISSING | 前后端调用链盘点 |
| 调度启停 | 页面按钮固定 disabled | FAKE 控件 | `OrchestrationPage.tsx:161-166` |
| 运行 | 枚举通用 Airflow DAG，不绑定任务版本 | PARTIAL / 边界过宽 | `OrchestrationRunsTab.tsx:126-293` |
| 重跑 | 仅传无人消费的 `retryRunId` conf | FAKE 语义 | 全仓字段消费者搜索 |
| 日志 | 仅 dbt 名称条件，硬编码 `dbt_run` | PARTIAL | `OrchestrationRunsTab.tsx:396-419` |
| 取消 | 无任务级命令 | MISSING | API/页面盘点 |
| 审计 | 相关动作存在，图/设计变化摘要不足 | PARTIAL | action catalog；`IngestionTaskChangeLogService.java:187-231` |

## 4. 推荐能力的前置条件

| 前置条件 | 当前状态 | 结论 |
|---|---|---|
| 用户能选择一个真实任务并看到 owner/status/revision | 仅手工 taskId | 不满足 |
| source/destination/mapping/schedule 有类型化字段与稳定 ID | 既有任务配置存在，画布表单为自由文本 | 部分满足 |
| 保存只改允许的设计字段并检测并发 | 完整 DTO PUT，无前端条件更新 | 不满足 |
| 服务端校验连接、引用、mapping、Cron、权限与密级 | admission 有部分门禁，页面无统一报告 | 部分满足 |
| 拓扑从同一 draft/revision 单向生成 | 当前图是独立 graphDsl | 不满足 |
| plan checksum 可绑定 revision、DAG、execution | revision/execution owner 已有，checksum 缺失 | 部分满足 |
| 发布复用现有 staged/atomic DAG 控制面 | 已具备 | 满足 |
| 目标端能解析为唯一 canonical 数据资产 | 接入事件可观察/补建数据集，但设计页未返回稳定 asset ref | 部分满足 |
| 接入过程校验与正式质量证据边界明确 | 代码已有 post-commit 触发，页面/契约仍称“质量策略” | 部分满足 |
| 金丝雀目标数据集有已发布、启用规则绑定 | 尚未固定并完成运行态验证 | 不满足 |
| 有可重复金丝雀、角色会话和 Chrome 95 | 当前缺失 | 不满足 |
| 多作业 DAG 有真实需求证据 | 当前没有 | 未证明，不进入 Sprint |

## 5. 推荐能力的后置条件

| 后置条件 | 当前状态 | 结论 |
|---|---|---|
| 表单重载与保存内容一致，不跨任务串状态 | 全局 localStorage/空 DSL 存在串图风险 | 不满足 |
| 拓扑、校验、发布和运行来自同一 plan | 图与执行配置分离 | 不满足 |
| 草稿与 ACTIVE/历史版本可并列识别 | revision 存在，页面未表达 | 部分满足 |
| 启用/暂停只作用于当前任务 owned DAG | 页面无任务级控制 | 不满足 |
| execution 固定 task/revision/plan checksum | 已绑定 task/revision，缺 checksum | 部分满足 |
| 重试重用冻结输入并产生关联实例 | 通用 DAG 再触发，无业务关联 | 不满足 |
| 取消进入可追踪终态 | 无命令/状态 | 不满足 |
| 所有 connector 可查看平台日志与失败原因 | 接入日志入口不完整 | 不满足 |
| 写动作可审计且不暴露敏感配置 | 动作基础存在，摘要/脱敏不足 | 部分满足 |
| 旧 DSL 可保留但不能冒充可运行设计 | 未定义兼容策略 | 不满足 |
| 接入成功后只触发一次同资产正式质量 workflow | 后端已有持久化意图、幂等与重试；端到端未验收 | 部分满足 |
| execution、workflow、run 与资产详情可双向定位 | execution 有质量 ID，资产页只展示最新结果，缺当前 execution 证据 | 部分满足 |
| 新批次不会沿用旧 `PASSED` 冒充当前可信 | 现有 reader 读取数据集最新 run，未证明与当前 execution 一致 | 不满足 |
| “可信可用”只由当前证据与消费资格派生 | 语义基础已有，接入页面尚未接线 | 部分满足 |

## 6. 关键前端问题

1. **产品形态过度**：左侧提供 transform/validate/loop/iteration 等没有正式执行 owner 的节点。
2. **双事实源**：任务配置和 graphDsl 可独立变化，页面无法证明哪个内容被执行。
3. **串状态风险**：单一 localStorage key；加载 null 时画布 store 不一定清空。
4. **整对象覆盖**：保存 `{...currentTask, graphDsl}`，会带着读取时旧字段更新任务。
5. **虚假控件**：“新建 DAG”只重置画布；启用/暂停禁用；重跑字段无消费者。
6. **运行越过 owner**：枚举通用 DAG 并客户端筛选，任务和部门归属不可证明。
7. **测试只覆盖外壳**：缺少保存冲突、任务切换、权限、深链、运行和 Chrome 95 证据。

## 7. 关键后端问题与可复用基础

### 可复用基础

- `IngestionTaskRevision` 已承接版本；runtime snapshot 已安全物化执行配置。
- admission 已有 staged DAG、post-commit reconcile、原子发布和 revision activate。
- `IngestionExecution` 在 after-commit 触发前建立 PREPARING 账本，并有 Airflow 状态同步器。
- 前端已有 task-scoped admit/execute/backfill/execution/log/retry API，可替代通用 Airflow 页面调用。
- 严格审计 action catalog 已有相关动作基础。

### 必补缺口

- 类型化 task design facade、条件保存与稳定 validation report。
- canonical plan/plan checksum，以及 draft/active topology projection。
- task-scoped schedule enable/pause 和 execution cancel。
- 外部 run 唯一身份、状态同步补偿、普通接入日志。
- 旧 graphDsl 只读兼容和 DAG 漂移对账。
- design 返回 canonical `destination.assetRef`，并保证质量引用与该目标资产一致。
- 当前 ingestion execution 的质量证据新鲜度、workflow/run 回链与资产状态投影。

## 8. 运行态事实

| 指标 | 2026-08-27 只读结果 | 架构含义 |
|---|---:|---|
| 接入任务 | 13（ACTIVE 7，删除 6） | 应全量盘点活跃任务，先证明复杂度 |
| 对象型 graphDsl | 0 | 不能用“已有 DSL 使用量”证明自由画布需求 |
| revision | ACTIVE 11 / DRAFT 2 / SUPERSEDED 30 | 版本 owner 已存在，可承接 plan identity |
| execution | 88（成功 7，失败 81） | 需先选稳定金丝雀并区分历史失败 |
| Airflow DAG | 57；其中 ingestion 34 | 调度对象已有历史积累 |
| 匹配当前任务/版本标识的 ingestion DAG | 3 | 其余 31 个先归类，禁止自动删除 |
| Airflow import errors | 0 | 调度器可加载，不代表业务闭环成立 |

## 9. 目标架构

```text
任务选择 / taskId 深链
          ↓
类型化 IngestionTaskDesign（唯一草稿事实）
          ↓
Server Validation + canonical plan + planChecksum
          ├────────────→ 只读 TopologyProjection
          ↓
Existing Revision / Admission
          ↓
staged DAG → atomic publish → ACTIVE revision
          ↓
task-scoped schedule / execution commands
          ↓
IngestionExecution + status/log/correlationId
          ↓
Catalog asset observation（唯一 asset identity）
          ↓ after commit
Post-ingestion quality intent → QualityWorkflow → QualityRun
          ↓
current quality evidence + consumption eligibility
          ↓
asset detail / execution detail 派生“可信可用”
          ↓
audit + reconciliation + rollback evidence
```

一致性条件：

`设计 planChecksum = 校验 planChecksum = 准入 planChecksum = ACTIVE 拓扑 planChecksum = execution planChecksum`

任何环节不一致即冲突，不能静默使用“最新草稿”。

质量与资产一致性条件：

```text
destination.assetRef.datasetId
  = qualityPolicyRef 中的 datasetId
  = QualityWorkflow.datasetId
  = QualityRun.datasetId

QualityEvidence.ingestionExecutionId
  = 本次已提交成功的 IngestionExecution.id
```

接入过程校验先于数据提交，正式质量验证晚于提交并异步执行。接入成功、质量结果、证据新鲜度和消费资格是四个独立维度；只有当前 execution 的证据为 `CURRENT/PASSED` 且资格为 `ELIGIBLE` 时，界面才展示“可信可用”。详见 [ADR-103-02](quality-asset-integration-contract.md)。

## 10. 优先级裁决

### P0：本 Sprint

- 真实流程复杂度与可编辑 DAG 需求门槛。
- 任务选择、类型化配置、条件保存、服务端校验。
- draft/active/history 只读拓扑投影。
- plan checksum 与 revision/admission/DAG/execution 绑定。
- 任务级调度、运行、重试、取消、状态和日志。
- 目标数据资产解析/观察、提交后正式质量触发、execution/workflow/run/asset 证据回链。
- 当前质量证据新鲜度与“可信可用”派生，禁止沿用旧批次通过结果。
- 旧 DSL 兼容、审计、DAG 对账、回滚和 Chrome 95 验收。

### P1：随主链设计

- 外部 run 去重约束、状态同步补偿、运行查询 N+1 消除。
- 质量触发失败的运维聚合、长期趋势与更细粒度的分区质量比较。

### 暂缓

- 可编辑多任务 DAG、任意 transform、循环/迭代、事件和业务回滚。
- 跨建模、质量、审批、BI 的通用 workflow 聚合。

## 11. Sprint-29 资产定位

Sprint-29 建成了 React Flow 画布和最小 graphDsl 持久化，但文档已经明确执行链路未接线。Sprint-103 保留其当前状态审计与历史 DSL 导出价值，不继续把它扩展为执行引擎；这不是删除已有资产，而是防止既有 UI 投资反向决定产品架构。
