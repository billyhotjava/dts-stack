# Sprint-103：数据集成流程可视化与运行闭环（202608）

**时间**：2026-08-27 ～ 2026-09-30
**状态**：`PASS_WITH_ENV_NOTE`（功能已实现并部署；Chrome 95、三角色与安全业务金丝雀待现场复验）
**类型**：Architecture + UI Productization + Vertical Slice
**目标**：数据开发人员围绕一条真实接入任务，通过业务表单完成数据源、目标数据资产、字段映射、调度和接入后质量验证配置；系统从同一任务版本自动生成拓扑，并闭合校验、发布、运行、资产登记、质量证据、重试/取消、日志和审计。

## 背景与价值

当前“任务编排”页面提供自由画布，但 `graphDsl` 只持久化、不被执行链消费；真正执行仍由 `IngestionTask → IngestionTaskRevision → admission → Airflow DAG → IngestionExecution` 完成。运行页又绕过任务域，直接操作通用 Airflow DAG，造成“所见、所发、所运行”不能证明一致。

本 Sprint 不继续扩建通用工作流编辑器，而是把已有页面收敛为“数据集成流程”：任务配置是唯一事实源，拓扑是自动生成的只读投影。这样既保留可视化价值，又避免建设第二套工作流引擎、DSL 编译器和节点状态机。

## 产品范围裁决

| workflow 类型 | 本 Sprint 处理方式 | 原因 |
|---|---|---|
| 单任务数据集成：源、目标资产、映射、调度、接入后质量验证 | `IN_SCOPE`：表单配置 + 自动拓扑 + 运行/资产证据闭环 | 与现有接入、目录和质量 owner 一致 |
| 多任务依赖：分支、汇聚、跨作业前后置 | `CONDITIONAL`：仅完成需求盘点，不建设编辑器 | 当前没有真实复杂依赖证据 |
| 模型发布、质量处置、审批等领域流程 | `OUT_OF_SCOPE`：继续由各领域状态机/账本负责 | 禁止用通用画布替代领域 owner |
| 任意 SQL/Python/Spark、循环、事件、业务回滚 | `OUT_OF_SCOPE` | 缺少制品、沙箱、补偿与安全契约 |

当前能力严格称为“数据集成流程”，不是完整 ELT。未来只有当转换步骤引用已发布、版本化的 SQL/dbt/模型制品时，才可演进为 ELT。

详细决策见 [assets/product-scope-decision.md](assets/product-scope-decision.md)。

## 架构决策记录（ADR）

| 决策点 | 选择 | 理由 | 影响 |
|---|---|---|---|
| 业务 owner | 复用 `IngestionTask + IngestionTaskRevision + IngestionExecution` | 现有版本、准入、DAG 发布和运行账本已存在 | 不新增 workflow 表或第二执行器 |
| 编辑事实源 | 类型化任务配置草稿 | 当前真实执行正是从任务配置生成 | `graphDsl` 降为历史兼容数据，不驱动执行 |
| 可视化 | 服务端从 draft/revision 生成只读 `TopologyProjection` | 避免表单与图两份事实漂移 | 首版不支持拖拽改拓扑和持久化坐标 |
| 一致性身份 | `taskId + revisionNumber + planChecksum` | 绑定保存、校验、发布和运行 | 任一环节 checksum 不一致返回 409 |
| 页面形态 | 同一路由改为“左侧配置步骤 + 右侧拓扑预览 + 运行 Tab” | 线性任务表单效率高，拓扑用于理解和诊断 | 不新增菜单或平行工作台 |
| 发布控制面 | 复用既有 `/admit` 和 DAG 暂存/原子发布 | A4 禁止平行发布实现 | 页面不直接控制任意 dagId |
| 目标资产身份 | 复用 `CatalogAssetType.DATASET + CatalogAssetKey` 并关联 `CatalogDataset.id` | 质量绑定和资产投影都需要同一稳定身份 | 不以库表字符串或质量引用再造资产 |
| 质量时序 | 接入过程只做准入/暂存校验；execution 完整提交后触发正式质量工作流 | 部分数据不能形成完整质量结论；现有触发已经是 after-commit | 质量失败不改写接入成功事实 |
| 质量边界 | 兼容字段 `qualityPolicyRef` 固定为 `dataset:<uuid>`；UI 表达为“接入后质量验证” | 质量执行和重试已有独立 owner，规则按目标资产正式绑定 | 不复制规则 JSON，不允许跨资产选择策略 |
| 资产可信表达 | 保留质量状态与消费资格正交语义；“可信可用”只派生展示 | 已有五轴、质量和权限共同计算 eligibility | 不新增 `TRUSTED/VERIFIED` 生命周期状态 |
| 转换能力 | 本 Sprint 不开放 | 当前接入链只有 E/L，没有正式转换制品契约 | 后续独立 capability 决策后再加入 |
| 历史 DSL | 可导出、不可发布，迁移期只读兼容 | 防止丢失用户草稿，又不让其冒充执行事实 | 后续版本再决定清理字段 |
| 权限 | 沿用现有 `read/write/export` 粒度并显式登记限制 | 细粒度权限仍是既有 M05 缺口 | 不宣称已有独立发布/运行权限 |

## 端到端契约链（Vertical Slice）

以下为 Sprint 目标契约；F0/T02 必须用当前 DTO/owner 核对字段后才能将 F1～F4 置为 `READY`。

| 层 | 契约/落点 | 签名要点 |
|---|---|---|
| UI 入口 | `/explore/etl/orchestration?taskId={id}` | 菜单/页头改为“数据集成流程”；任务选择器、配置步骤、拓扑预览、运行 Tab |
| 读取设计 | `GET /api/ingestion/tasks/{taskId}/design` | 返回 `taskId, taskName, revisionNumber, revisionState, source, destination.assetRef, mappings, schedule, postIngestionQuality, qualityPolicyRef, planChecksum, validation, topology` |
| 保存设计 | `PUT /api/ingestion/tasks/{taskId}/design` + `If-Match` | 仅更新类型化配置；冲突 409；不得整对象覆盖任务状态或权限字段 |
| 校验 | `POST /api/ingestion/tasks/{taskId}/design/validate` | 按 `expectedPlanChecksum` 校验连接、对象、映射、Cron、目标资产解析、质量引用同资产、权限与密级 |
| 拓扑投影 | `GET /api/ingestion/tasks/{taskId}/topology?view=DRAFT|ACTIVE` | 返回只读 `nodes, edges, planChecksum, projectionVersion`；不接受任意节点写入 |
| 准入发布 | 既有 `POST /api/ingestion/tasks/{taskId}/admit` | 增加 `X-Expected-Plan-Checksum`；复用 revision、DAG staging/publish/activate |
| 调度 | `POST .../{taskId}/schedule/enable|pause` | 服务端解析 owned DAG；幂等；客户端不传任意 dagId |
| 运行 | 既有 task execution list/execute/retry/log + 新增 cancel | 绑定 `taskId, revisionNumber, planChecksum, executionId, correlationId, qualityWorkflowId, qualityRunId`；接入状态与质量状态分开 |
| 资产登记 | 既有 catalog observation / ingestion lineage seam | execution 成功观察目标 `DATASET`；复用 `CatalogAssetKey`，不新建资产台账 |
| 质量触发 | 既有 post-ingestion workflow boundary | after-commit 按 `datasetId + executionId` 幂等触发；`RETRY_WAIT/EXHAUSTED` 可见 |
| 资产投影 | 既有 quality status + consumption eligibility | 当前 execution 证据才可标 `CURRENT`；旧证据 `STALE`；满足全部门禁才展示“可信可用” |
| 数据 | 既有 task/revision/execution、runtime snapshot、质量 workflow/run 与资产语义投影 | plan checksum 和目标资产引用进入冻结快照；不新建通用 workflow 聚合或可信状态表 |
| 迁移 | expand/contract | 旧 `graph_dsl` 可空兼容；新增字段先可空、回填、双读，再收敛 |

### 目标页面线框

```text
┌ 数据集成流程 ─ [任务选择] ─ 状态/版本 ───────────────┐
│ [流程设计] [运行实例]                                  │
│ ┌ 配置步骤 ─────────┐ ┌ 自动生成拓扑 ──────────────┐ │
│ │ 1 数据源           │ │ 数据源 → 写入 → 登记资产 │ │
│ │ 2 目标资产与字段映射│ │          → 接入后质量验证?│ │
│ │ 3 调度             │ │ 只读；按校验状态高亮      │ │
│ │ 4 接入后质量验证   │ └───────────────────────────┘ │
│ └────────────────────┘ [保存草稿] [校验] [提交发布]  │
└───────────────────────────────────────────────────────┘
```

## 现状勘察账本（Context Ledger）

下游 Task 直接引用本账本，禁止重复全仓扫描。

| # | 事实 | 证据 |
|---|---|---|
| C01 | 菜单与动态路由已存在，不需新增入口 | `portal-menu-seed.json:365-394`；`dynamic-resolver.tsx:88-92` |
| C02 | 页面只识别 `taskId`，无稳定任务选择器 | `OrchestrationPage.tsx:43-76` |
| C03 | 当前保存先写全局 localStorage，再完整 DTO PUT | `OrchestrationPage.tsx:20,78-101` |
| C04 | `graphDsl` 实体注释明确“仅持久化，执行链路暂不消费” | `IngestionTask.java:72-74` |
| C05 | 任务配置已进入 revision/runtime snapshot | `IngestionAccessContractService.java:527-638,823-850` |
| C06 | `/admit` 已有暂存、补偿、DAG 原子发布和 revision 激活 | `IngestionTaskService.java:300-395,3598-3659`；`AirflowDagService.java:94-204` |
| C07 | execution 已绑定任务/revision 并 after-commit 触发 | `IngestionTaskService.java:864-965` |
| C08 | 前端已有 task-scoped admit/execute/backfill/execution/log/retry API | `source/dts-platform-webapp/src/api/ingestion.ts:787-951` |
| C09 | 运行 Tab 当前枚举通用 Airflow DAG 并直接触发 | `OrchestrationRunsTab.tsx:126-293`；`EtlResource.java:252-315` |
| C10 | 当前任务 13 条、活跃 7 条；对象型 DSL 为 0 | `it/baseline.md` 2026-08-27 运行快照 |
| C11 | execution 88 条，失败 81、成功 7 | `it/baseline.md` 2026-08-27 运行快照 |
| C12 | 34 个 ingestion DAG 仅 3 个匹配当前任务/版本标识 | `it/baseline.md` 2026-08-27 运行快照 |
| C13 | Sprint-29 只完成画布与最小 DSL 持久化，执行未接线 | `sprint-29-202605/features/F4-panel-and-dsl/README.md:28-53` |
| C14 | 质量工作流有独立执行 owner，不能被通用画布接管 | Sprint-102 `QualityRunService` / `QualityWorkflowOrchestrator` |
| C15 | 自动化登录态、代表性金丝雀和 Chrome 95 验收仍阻塞 | `it/baseline.md` |
| C16 | `qualityPolicyRef` 已有稳定格式 `dataset:<uuid>`，并冻结到 revision/execution | `IngestionAccessContractService.java:159-160,194-200,378-396,747-760` |
| C17 | 接入成功事务提交后才触发正式质量工作流，触发失败有有限次补偿 | `AirflowExecutionSyncService.java:608-638`；`PostIngestionQualityWorkflowAttemptStore.java:36-112` |
| C18 | 正式质量工作流按目标 `datasetId` 读取已发布、已启用规则绑定 | `QualityWorkflowOrchestrator.java:163-185,491-580,642-667`；`GovRuleBinding.java:25-31` |
| C19 | 接入 execution 已持有 `qualityWorkflowId/qualityRunId/qualityWorkflowStatus` | `IngestionExecution.java:46-70`；`IngestionExecutionDTO.java:23-29` |
| C20 | 目录已有唯一资产观察 owner，接入证据可将目标资产标记为已核实且服务健康 | `CatalogAssetRegistrationService.java:13-43`；`IngestionLineageWriter.java:245-293` |
| C21 | 资产质量状态从最新正式、非试跑质量运行派生，并参与消费资格计算 | `JdbcCatalogAssetQualityStatusReader.java:16-70`；`CatalogAssetStatusViewService.java:155-201` |
| C22 | 资产语义已批准五轴 + 质量/权限派生消费资格，禁止新增互斥“可信”生命周期状态 | `CatalogAssetSemanticsContract.java:10-15,58-98,294-335` |
| C23 | 资产详情已有“质量与SLA”和消费资格界面，但 legacy/asset-v2 映射及当前 execution 证据仍未统一 | `DatasetDetailPage.tsx:430-440,672-739`；`AssetDeliveryStatusPanel.tsx:5-35` |

**开放问题**：

- 当前 7 条活跃任务是否存在表单无法表达的多作业依赖？由 F0/T01 全量盘点。
- `IngestionTaskDesign` 如何精确映射现有 connector DTO？由 F0/T02 冻结，不在编码中发现。
- 新建目标表在 design/admission 哪一时点通过既有 catalog observation seam 获得 `CatalogDataset.id`？F0/T02 按 connector 类型冻结，不允许实施期临时创建第二套登记逻辑。
- 资产投影如何判定最新质量证据属于当前 ingestion execution，并在触发失败时使旧 `PASSED` 失效？由 F0/T02 冻结 additive DTO/查询契约。
- plan checksum 是扩展 revision snapshot 还是新增显式列？需结合查询路径与 migration 预算决定。

## Gate Registry

| Gate | 项目 | 状态 | 证据 | 未过则关联 Task |
|---|---|---|---|---|
| G0 | 交付基线 | PASS_WITH_ENV_NOTE | `it/baseline.md`；真实登录/本地 Chrome 150 已通过，Chrome 95 待现场复验 | F4/T02 |
| G0 | 领域与数据画像 | PASS | `assets/domain-profile.md`；`assets/workflow-complexity-evidence.md` | - |
| G0 | DTS 领域不变量 | PASS | 本文 ADR：A4/B1/B6/D2/D4/D6/G1 | - |
| G1 | 产品范围裁决 | PASS | `assets/product-scope-decision.md` | - |
| G1 | 契约链贯通 | PASS | 本文 §端到端契约链 + `assets/quality-asset-integration-contract.md`；`effectiveConfigChecksum` 复用为 plan checksum | - |
| G1 | 非功能预算 | PASS | `assets/nfr-budget.md`；聚焦测试绑定各预算项 | F1～F4 |
| G2 | 变更范围与评审 | PASS | 精确 diff、契约链和敏感信息扫描通过 | - |
| G3 | 发布与回滚 | PASS_WITH_ENV_NOTE | 三镜像顺序部署成功且旧摘要已打回滚标签；未执行客户数据回滚切换 | F4/T02 |
| G4 | 可运维性 | PASS | `assets/runbook.md`；健康、日志、容量与故障处置已记录 | - |
| G4 | DoD 集中验收 | PASS_WITH_ENV_NOTE | 聚焦验证、部署与本地 Chrome 150 通过；Chrome 95、三角色与安全金丝雀保留 | F4/T02 |

## Feature / Task

| Feature | 优先级 | Task 数 | 状态 | 结果 |
|---|---|---:|---|---|
| F0-交付基线与契约冻结 | P0 | 2 | DONE | 真实流程盘点、可重复基线、产品/API 契约；环境缺口显式保留 |
| F1-任务配置与拓扑投影 | P0 | 2 | IMPLEMENTED_AND_DEPLOYED | 类型化配置、并发保存、服务端校验、只读拓扑 |
| F2-版本准入与调度发布 | P0 | 2 | IMPLEMENTED_AND_DEPLOYED | plan checksum、revision、DAG 和调度状态一致 |
| F3-任务级运行闭环 | P0 | 3 | IMPLEMENTED_AND_DEPLOYED | 任务级列表、执行、重试、取消、日志，以及接入质量与资产证据深链 |
| F4-治理迁移与集中验收 | P0 | 2 | PASS_WITH_ENV_NOTE | 审计、兼容、对账、发布与回滚准备完成；现场门禁保留 |

**统计**：DONE=2，IMPLEMENTED_AND_DEPLOYED=8，PASS_WITH_ENV_NOTE=1（共 11 Task）。

**执行顺序**：`F0/T01 → F0/T02 → F1/T01 → F1/T02 → F2/T01 → F2/T02 → F3/T01 → F3/T02 → F3/T03 → F4/T01 → F4/T02`。

全部 Feature 实现后只做一次集中构建、E2E 和部署/回滚演练；Chrome 150 结果以环境说明交付，不能替代 Chrome 95 现场复验。

## 追溯矩阵（Traceability）

| 需求点 | Feature / Task | 契约或界面 | 验收证据位置 |
|---|---|---|---|
| R1 业务表单配置真实接入任务 | F1/T01 | task selector + design GET/PUT | `it/IT-01-design.md` |
| R2 所见拓扑来自同一任务版本 | F1/T02、F2/T01 | topology projection + plan checksum | `it/IT-02-projection.md` |
| R3 保存、校验、发布严格分门 | F1/T01～F2/T02 | If-Match、validate、admit | `it/IT-03-release.md` |
| R4 调度和运行不接收任意 dagId | F2/T02、F3/T01 | task-scoped schedule/execution | `it/IT-04-runtime.md` |
| R5 重试、取消、日志可追踪 | F3/T02 | execution state + correlationId | `it/IT-05-recovery.md` |
| R6 旧数据、审计、DAG 漂移可治理 | F4/T01 | migration/audit/reconciliation | `it/IT-06-governance.md` |
| R7 客户环境可操作 | F4/T02 | Chrome 95 单旅程 | `it/IT-07-chrome95.md` |
| R8 是否需要可编辑 DAG 有证据裁决 | F0/T01 | workflow complexity report | `assets/workflow-complexity-evidence.md` |
| R9 接入后形成可追踪的当前资产质量证据 | F0/T02、F1/T02、F2/T01、F3/T03、F4/T02 | destination asset ref + post-commit quality + asset eligibility | `it/IT-08-asset-quality.md` |

## Sprint Definition of Ready

- [ ] 全量盘点 7 条活跃任务，并取得客户侧真实流程样本；没有多作业证据时保持只读拓扑。
- [ ] 三档验收账号/会话、金丝雀任务和 Chrome 95 环境可用。
- [ ] design/topology/admit/execution DTO、错误码、并发与权限契约已冻结。
- [ ] 目标资产解析、`dataset:<uuid>` 同资产校验、execution→workflow/run 关联和证据新鲜度契约已冻结。
- [ ] plan checksum 的存储、索引、迁移和兼容策略完成影响评估。
- [ ] 31 个未匹配 DAG 已只读归类，明确保留、接管或人工退役策略。

## Sprint Definition of Done

- [ ] 用户在现有页面通过类型化表单配置任务，不能通过自由图节点创造后端不支持的能力。
- [ ] 拓扑完全从指定 draft/revision 生成，与 `planChecksum` 一致，不单独持久化业务语义。
- [ ] 保存、校验、准入、调度和运行绑定同一 task/revision/plan checksum。
- [ ] 运行页不枚举或控制任意 Airflow DAG；所有操作由任务权限和部门边界保护。
- [ ] 接入成功后目标资产可追踪；接入状态与质量状态独立，当前 execution 的 workflow/run 可双向深链。
- [ ] 新一批接入完成后旧质量证据不再显示为当前可信；只有当前质量通过且消费资格为 `ELIGIBLE` 时展示“可信可用”。
- [ ] 重试具有原实例关联，取消可观测且命令幂等；普通接入任务可查看日志。
- [ ] 旧 `graphDsl` 可安全读取/导出但不能发布；迁移和 DAG 对账可回滚。
- [ ] 审计记录操作者、任务、revision、plan checksum、动作、结果和 correlationId，不记录凭据或脚本正文。
- [ ] 集中测试、部署、回滚、真实运行和 Chrome 95 旅程均有证据。

## 非目标

- 通用工作流平台、可编辑多作业 DAG、循环/迭代/条件分支。
- 新建顶级菜单、第二套任务表、第二套发布器或第二套运行账本。
- 把 `graphDsl` 或 React Flow store 升格为执行事实源。
- 任意 SQL/Python/Spark 内联执行；恢复已退役直接 dbt selector。
- 自动删除 31 个未匹配 Airflow DAG。
- 用构建成功或现代浏览器截图替代登录、权限、运行结果和 Chrome 95 验收。

## 资产

- [产品范围裁决](assets/product-scope-decision.md)
- [架构评审](assets/architecture-review.md)
- [接入质量与资产证据契约](assets/quality-asset-integration-contract.md)
- [页面能力矩阵](assets/page-capability-matrix.md)
- [按钮与组件矩阵](assets/button-component-matrix.md)
- [缺口登记表](assets/gap-register.md)
- [领域与数据画像](assets/domain-profile.md)
- [NFR 预算](assets/nfr-budget.md)
- [发布与回滚计划](assets/release-plan.md)
- [运行手册](assets/runbook.md)
- [DAG 对账](assets/dag-reconciliation.md)
- [集成验收计划](it/README.md)
- [G0 基线证据](it/baseline.md)
