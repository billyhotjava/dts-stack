# Sprint-83：dbt 双向可视化建模与外部项目接入

**时间**：2026-08  
**状态**：IN_PROGRESS（S0 工程准入已通过，S1/S2 正在编码；H83-01 只阻断 S3 materialization；客户脱敏包只阻断客户兼容声明，不阻断通用实现）
**类型**：Architecture / Product Design / dbt Integration / Full-stack  
**目标**：让建模人员在同一个 canonical 模型上下文中完成业务可视化设计、选择或确认 dbt 物化实现并查看依赖与物化结果；SQL/Jinja 不出现在普通可视化界面，只在显式的高级 dbt 实现视图中受控维护。外部 dbt 项目通过可审计的预检、冲突处理和幂等应用导入为 `ModelSpec DRAFT + DBT_MANAGED Implementation Revision`，不产生第二套模型、解析、发布或运行控制面。

## 背景与价值

Sprint-80 已把原型页面迁入正式数据建模界面，Sprint-81 已把后台收敛为：

```text
WarehousePlan → ModelSpec v2/revision → StageGate → Lifecycle
  → ReleaseCandidate → Materialization → DbtExecutionGateway → Airflow/dbt
```

当前 dbt 结合存在两个断点：

1. 旧“高级建模”路由已被重定向，新模型工作台仍是静态展示；普通可视化、显式高级 dbt 实现与物化结果尚未在同一模型修订下分层贯通。
2. “逆向建模”页面仍是从数据库表发现模型的硬编码原型；后台虽然已经具备 dbt ZIP inspect、preview、apply、retry 和 canonical ModelSpec/Implementation 落库，但前端没有消费这些契约。

本 Sprint 的核心不是重写 dbt，也不是把任意 SQL 变成可编辑画布，而是建立**一套版本固定的模型表示层**：普通用户只操作业务可视化模型并在物化阶段选择合适的 dbt 实现；技术人员显式进入高级 dbt 实现；外部导入仍进入同一模型生命周期。

## 架构决策记录

| ID | 决策 | 理由 | 状态 |
|---|---|---|---|
| ADR-83-01 | `ModelSpec v2` 继续拥有业务语义；`Implementation Revision` 拥有技术实现；Airflow/dbt artifact 与 relation observation 拥有运行事实 | 三类事实不能互相冒充，也不能新建第二套模型台账 | ACCEPTED（2026-08-02） |
| ADR-83-02 | 实现所有权与执行引擎正交：`DESIGNER_GENERATED` 由可视化设计生成隐藏的 dbt 制品；`DBT_MANAGED` 由高级/外部 dbt SQL/Jinja 维护。两者都可经统一 dbt 物化链执行 | “使用 dbt 物化”不等于“SQL 成为业务模型真值” | ACCEPTED（2026-08-01） |
| ADR-83-03 | 普通可视化只显示逻辑表/字段、业务或模型依赖、物理结构与受控样例数据，隐藏 SQL/Jinja、macro、project path、compiled SQL 和完整 dbt DAG；高级 dbt 实现是互斥的显式技术视图 | 保持业务界面简洁，同时保留技术维护入口 | ACCEPTED（2026-08-01） |
| ADR-83-04 | 高级 dbt 实现放在现有模型详情的“数据实现”阶段，复用同一模型列表与上下文；不新增菜单、独立模型清单或第二个模型中心 | 遵守 DTS 信息架构和禁造平行入口约束 | ACCEPTED（2026-08-01） |
| ADR-83-05 | dbt 导入继续复用 `dts.model-package/v1`、archive inspector、preview/apply/retry 和现有 import run/apply 表 | 后台主体已存在，禁止新建第二套导入器和任务台账 | ACCEPTED（2026-08-02） |
| ADR-83-06 | `manifest.json` 表示 dbt 结构快照，`catalog.json`/relation observation 表示物理观测，compiled SQL 只作诊断；统一投影不得反写任意 SQL | 明确证据来源及其可信度 | ACCEPTED（2026-08-02） |
| ADR-83-07 | 外部项目重新导入以最近一次已接受合并检查点为 base，对 current Implementation 与 incoming ZIP 做逐节点技术三方比较；ModelSpec 业务语义不参与技术覆盖，始终保留并重验映射。无外部变化保留 current、仅 incoming 变化显式 UPDATE、双边分歧 CONFLICT，缺失/重命名不自动删除或猜测映射 | 区分业务语义变更与竞争性技术变更，解决双边漂移且避免无谓冲突 | ACCEPTED（2026-08-01） |
| ADR-83-08 | 首期只接收用户上传 ZIP 快照，不接 Git 地址/凭据、clone/sync/push 或在线依赖下载；inspect/preview 不执行模型 SQL | 降低供应链、凭据和离线部署风险 | ACCEPTED（2026-08-01） |
| ADR-83-09 | 导入只能创建/更新 DRAFT；普通可视化生成或高级 dbt 编辑都只创建新的 Implementation Revision；发布与物化对话框只能选择已固定的实现修订和物化策略，执行仍只经 StageGate、ReleaseCandidate 与 DbtExecutionGateway | 任何 UI 快捷操作都不能绕过 Sprint-81 控制面或在物化时静默改变所有权 | ACCEPTED（2026-08-02） |
| ADR-83-10 | 所有 inspect/preview/apply/retry/SQL checkpoint/commit/conflict 动作接入公共审计 outbox 和 dts-admin 动作字典；权限只复用现有 `read/write` 与建模维护者 authority | 导入和 SQL 修改属于重要治理操作，且本 Sprint 不假设新的权限体系 | ACCEPTED（2026-08-02） |
| ADR-83-11 | 允许 PARTIAL；每个 FAILED/BLOCKED 项返回稳定失败码、阶段、安全原因、retryable、恢复动作和 correlationId；retry 不重放成功项；撤销采用受资格约束的前向 ModelSpec/Implementation 修订，不物理删除历史 | 现有 apply 是逐模型事务，必须诚实展示部分结果并让用户可恢复 | ACCEPTED（2026-08-01） |
| ADR-83-12 | 通用 `/api/etl/dbt/files` 只可作为受控 staging 工具，不能成为建模事实源；证明建模调用方完成迁移后再决定删除或保留其非建模用途 | 共享可变文件树不具备修订固定语义 | ACCEPTED（2026-08-02） |
| ADR-83-13 | Catalog 使用稳定逻辑资产的 `latestPublishedRef` 与 `servingRef` 双指针：PUBLISHED 可发现，成功 MATERIALIZED 才切换可消费 serving；旧 serving 在新版本失败/stale 时保持 | 发布治理事实和物理可用性不能用一个状态冒充 | ACCEPTED（2026-08-01） |
| ADR-83-14 | S3 物化切片提供显式加载、默认 100/最大 500 的受控样例；普通视图只预览 serving，高级维护者可预览成功 candidate 并标记非正式；历史 revision 仅看结构；密级/列策略 fail-closed，样例不缓存或导出 | 防止自动敏感读取、历史错配和候选结果冒充正式资产 | ACCEPTED（2026-08-01） |
| ADR-83-15 | dbt 兼容拆分为 inspect、import projection、materialization 三轴；只有精确 Core+adapter+数据源+image digest 的真实矩阵可标记 materialization CERTIFIED | “包能读”或“模型能导入”不能冒充“当前运行时可物化” | ACCEPTED_POLICY / CERTIFICATION_BLOCKED（2026-08-02） |
| ADR-83-16 | source-only 只有 enforced schema contract（字段 `name+data_type` 完整）、literal 依赖闭包和业务语义齐备时可导入；其余结构最多只读且 apply BLOCKED。独立合格闭包可单独选择；PARTIAL 只来自已选合格项启动后的逐项失败；成功项 ownership 固定为 DBT_MANAGED | 防止从 SQL/不完整 YAML 猜结构、把预览混合结果冒充 PARTIAL 或静默改变所有权 | ACCEPTED（2026-08-02） |
| ADR-83-17 | 治理发布先推进 `latestPublishedRef`，物理消费只由成功 evidence CAS 推进 `servingRef`；新 revision 构建到版本化 shadow relation，成功后原子切换 serving indirection，失败/stale 不得污染旧 serving | 双指针若共用同一可变目标表，只保留引用无法保护旧服务版本 | ACCEPTED IMPLEMENTATION CONSTRAINT（2026-08-02） |
| ADR-83-18 | 兼容证据分为 `G0-RUNTIME`、`G1-PARSER`、`G4-REGRESSION/CUSTOMER`：RT-01 认证精确运行时；按切片归档、重新构造或许可固定的 FX-01～05 允许对应工程切片编码；客户脱敏包只控制客户兼容声明和现场验收 | 避免客户样本不可得导致编码门禁死锁，同时禁止用合成 fixture 冒充客户适配证据 | ACCEPTED（2026-08-02） |
| ADR-83-19 | 按可独立验收的竖切片拉取任务；只有当前 P0 切片必须满足 DoR，P1/P2 后续切片可保持 DRAFT，不再要求 30+ Task 同时 READY | 让优先级重新表达交付顺序，消除 F0/T04 与 F1/F6 的依赖环 | ACCEPTED（2026-08-02） |
| ADR-83-20 | `R-DBT-LEGACY-DAG` 同时包含旧 generator 与仓库内两份可触发的 mutable/privileged BashOperator DAG；后者确有无 Token sync 和 `|| true`。caller=0 后必须连同部署副本物理删除 | 以实际部署实体为准，不把 generator 删除误判成执行面退役 | ACCEPTED（2026-08-02；事实修正并完成退役） |
| ADR-83-21 | 旧 `GET /api/etl/dbt/preview` 是具名残余风险 `R-DBT-LEGACY-PREVIEW`；新建模链禁止调用。无非建模 owner 时物理删除；仍有 owner 时必须先迁入同一 evidence/classification/masking 服务并封闭原端点 | 旧接口缺少 model/revision/candidate/evidence pin 和密级/列策略，保留会绕过新 physical-preview 控制面 | ACCEPTED_SECURITY_BOUNDARY（2026-08-02） |
| ADR-83-22（D13） | inspect 成功后签发 30 分钟 `inspectionProof`，签名绑定 tenant、canonical actor、规范化技术包 checksum 与 expiry；preview 只接受原技术包加白名单 context/selection/semanticOverrides，无效/过期稳定 fail-closed。复用平台托管签名能力，不新建 inspect 表、不自造 secret | 阻断客户端篡改技术事实、跨主体重放或用公开 checksum 伪造“已检查”状态，同时避免第二套会话事实源 | ACCEPTED_SECURITY_BOUNDARY（2026-08-02） |

## 三层表示与所有权

```text
┌──────────────────────────────────────────────────────────────┐
│ 逻辑设计：ModelSpec v2 revision                              │
│ 业务名称/定义、类型/分层、粒度、字段角色、标准绑定、来源映射 │
└──────────────────────────────┬───────────────────────────────┘
                               │ revision + checksum 固定
┌──────────────────────────────▼───────────────────────────────┐
│ 技术实现：Implementation Revision + immutable dbt artifacts │
│ DESIGNER_GENERATED 生成物或 DBT_MANAGED 源文件、配置与测试  │
└──────────────────────────────┬───────────────────────────────┘
                               │ candidate/version/attempt 固定
┌──────────────────────────────▼───────────────────────────────┐
│ 运行事实：manifest/catalog/run evidence/relation observation│
│ compiled SQL、实际字段、样例数据、运行状态、物理血缘         │
└──────────────────────────────────────────────────────────────┘
```

### 可视化能力等级

| 等级 | 适用模型 | 可见能力 | 可编辑边界 |
|---|---|---|---|
| `BUSINESS_VISUAL_EDIT` | `DESIGNER_GENERATED` | 逻辑表、字段、业务/模型关系、物化配置与物理结果；不显示 SQL/dbt 技术细节 | 可视化结构与业务语义可编辑；系统生成隐藏的 dbt 制品 |
| `BUSINESS_VISUAL_READ` | 普通界面中的 `DBT_MANAGED` | ModelSpec 语义、解析后的结构/依赖和物理结果；不显示 SQL | 业务语义可编辑；技术结构只读 |
| `ADVANCED_DBT_IMPLEMENTATION` | 显式进入的 `DBT_MANAGED` 数据实现 | SQL/Jinja、dbt DAG、测试、materialization 与诊断 | 仅技术维护者可编辑；提交产生新 Implementation Revision；不与业务可视化同屏 |
| `BLOCKED` | 动态引用、缺失证据或无法安全解析 | 问题、损失项和恢复建议 | 普通界面不展示原始 SQL；技术维护者进入高级实现或修正包后重新预检 |

## 端到端契约链（已冻结）

| 层 | 契约/落点 | 关键字段与约束 |
|---|---|---|
| 模型入口 | `/data-modeling/dimensions/workbench?modelSpecId={uuid}` | 这是现有“维度建模”功能域下覆盖多类 ModelSpec 的通用模型工作台，不表示仅支持 DIMENSION；复用现有模型详情且不新增菜单/列表。普通可视化为默认，显式高级 dbt 实现位于“数据实现”阶段；历史视图固定 model/implementation revision |
| 逆向入口 | `/data-modeling/dimensions/reverse?source=dbt` | 第一步明确“数据库表/视图”与“dbt 项目包”；本 Sprint 实施 dbt ZIP 分支 |
| 表示读模型 | `GET /api/modeling/model-specs/{id}/representations?modelRevision={int}&implementationRevision={int}` | 返回逻辑、技术、运行三个分区及各自 provenance/checksum/capability/drift |
| 高级草稿 | `POST /api/modeling/model-specs/{id}/implementation-drafts` | body 固定 base revision/ETag；返回隔离 draftId、过期时间和 workspace scope |
| 草稿校验 | `POST /api/modeling/implementation-drafts/{draftId}/validate` | 只 parse/compile；不得运行模型 SQL；返回结构投影和 diagnostics |
| 提交实施版本 | `POST /api/modeling/implementation-drafts/{draftId}/commit` | CAS 校验 base revision/checksum；成功创建新的 immutable Implementation Revision |
| 物理预览 | `GET /api/modeling/model-specs/{id}/implementations/{revision}/physical-preview` | 请求固定 model/implementation/candidate/attempt/relation evidence/checksum 与 `scope=SERVING|CANDIDATE`；显式加载，limit 缺省 100、最大 500；表级密级和列 ALLOW/MASK/DENY fail-closed；样例 no-store、不分页/导出 |
| dbt ZIP inspect | 复用 `POST /api/modeling/model-spec-imports/dbt/archive/inspect` | P0 唯一外部来源；仅安全解压与解析，返回规范化 `ModelPackage`、30 分钟 `inspectionProof/proofExpiresAt`，不接 Git/在线 packages，不新建 inspect 表 |
| 导入预检 | 复用 `POST /api/modeling/model-spec-imports/dbt/preview` | 验证 proof 的 tenant/actor/checksum/expiry 后只接收白名单 context/selection/semanticOverrides；无效 409、过期 410；返回 CREATE/UPDATE/SKIP/CONFLICT/BLOCKED |
| 导入应用 | 复用 `POST /api/modeling/model-spec-imports/dbt/apply` | `runId/previewHash/selectedUniqueIds/idempotencyKey`；逐项持久化并可恢复；attempt/item/summary 只输出 canonical 状态，`REPLAY` 仅为幂等响应元数据 |
| 重试/恢复 | 复用 `POST /api/modeling/model-spec-imports/{runId}/retry` 与查询接口 | 不重复成功项；服务重启后可恢复；PARTIAL 逐项遵循 `assets/import-partial-result-contract.md`；不得静默覆盖冲突 |
| 发布与物化 | 复用现有“发布与物化”对话框及 StageGate/Lifecycle/ReleaseCandidate/Materialization | 选择已固定的实现来源/修订和 `table/view/incremental` 等物化策略；`DESIGNER_GENERATED` 由系统生成隐藏 dbt 制品，`DBT_MANAGED` 复用高级/外部制品；UI 不直接调用 `/etl/dbt/run`，执行只通过 `DbtExecutionGateway`。首期只有 dbt runtime 时不伪造多引擎选项 |
| Catalog 投影 | 复用 `CatalogAssetType + CatalogAssetKey` 与既有 outbox | PUBLISHED 推进 latestPublishedRef；MATERIALIZED + relation evidence/质量门禁通过后 CAS 切换 servingRef；失败/stale/build-only 保留旧 serving，不创建 revision 资产副本 |
| 审计 | `platform_audit_outbox → AuditService → dts-admin` | 记录租户、模型/修订、checksum、runId、数量、结果和错误码；不记录 SQL/ZIP/凭据正文 |

## 现状勘察账本（Context Ledger）

| # | 事实 | 证据 |
|---|---|---|
| CL-01 | Sprint-81 已实现并验证唯一建模链；共享环境尚未部署最终增量 | `../sprint-81-202607-modeling-backend-rearchitecture/assets/architecture-overview.md:1-5,57-78` |
| CL-02 | `/studio/sql-modeling` 当前只是旧路由，最终跳转模型工作台 | `source/dts-platform-webapp/src/routes/sections/dashboard/static-routes.tsx:471`、`.../LegacyDataModelingRedirect.tsx:39` |
| CL-03 | 当前模型编辑器使用静态样例，保存/提交失败关闭；“代码模式”不是 dbt SQL | `.../data-modeling/components/ModelingEditor.tsx:359,376,517` |
| CL-04 | 当前逆向向导使用硬编码 `DISCOVERED_MODELS`，明确不读取数据库、不创建模型 | `.../data-modeling/components/ReverseModelingWizard.tsx:7-29,252-277` |
| CL-05 | 前端已定义 ZIP inspect、preview、apply、query、retry API，但没有页面消费者 | `source/dts-platform-webapp/src/api/modelSpecImportApi.ts:136-182` |
| CL-06 | 后端 `ModelSpecImportResource` 已提供 inspect/preview/apply/query/retry | `source/dts-platform/src/main/java/.../web/rest/ModelSpecImportResource.java:84-127` |
| CL-07 | Preview 已表达 CREATE/UPDATE/SKIP/CONFLICT/BLOCKED 与 DESIGNER_GENERATED/DBT_BACKED/BLOCKED | `.../imports/preview/ModelSpecImportPreviewContract.java:18-30,77-92` |
| CL-08 | Apply 已具备 previewHash、dependency closure、idempotency key、逐项结果和 retry 契约 | `.../imports/apply/ModelSpecImportApplyContract.java:11-76,93-116` |
| CL-09 | archive inspector 支持 manifest/catalog、原始 dbt source project 和 legacy models.tsv | `.../DbtModelArchiveInspectService.java:51-91` |
| CL-10 | 原始项目静态解析不执行 dbt/SQL；业务类型、粒度和消费语义保持阻断待确认 | `.../DbtSourceProjectModelPackageAdapter.java:45-49` |
| CL-11 | 当前静态解析限制为单 SQL 2 MiB、总 SQL 16 MiB、宏 4 MiB、500 节点、10000 边、深度 128 | `.../DbtSourceProjectModelPackageAdapter.java:52-63` |
| CL-12 | `ModelSpec` 已区分 `DESIGNER_GENERATED` 与 `DBT_MANAGED`；ImplementationView 已固定 project/model/implementation checksum | `.../ModelSpecContract.java:293`、`.../ModelLifecycleContract.java:635` |
| CL-13 | `/api/etl/dbt/files` 可操作共享 projectDir，但没有 ModelSpec/Implementation revision pin | `.../web/rest/DbtFileResource.java:24-88` |
| CL-14 | 现有 dbt preview 能读取物化 relation 的字段与最多 500 行数据，但不是 candidate/revision 固定的 modeling 契约 | `.../service/etl/DbtPreviewService.java:54,112` |
| CL-15 | 当前 manifest/diagnostics/run/asset-sync/import 存在多套读取器，尚未指定唯一规范化投影 seam | `DbtModelPackageConverter`、`DbtManifestService`、`DbtModelDiagnosticsService`、`DbtRunResultService`、`DbtAssetSyncService`、`ModelingDbtManifestImporter` |
| CL-16 | import apply 可能逐项成功/失败；现有能力支持 retry，但没有整包撤销命令 | `ModelSpecImportApplyService`、`ModelSpecImportCandidateTransactionWorker` |
| CL-17 | inspect 使用 SafeZipExtractor；Zip Slip、压缩炸弹、重复条目和临时目录清理已有基础 | `.../imports/converter/SafeZipExtractor.java` |
| CL-18 | 当前中央审计只有 preview 专用动作，本 Sprint 范围内的 inspect/apply/retry/partial/checkpoint/commit 仍需补齐 | `.../web/rest/ModelSpecImportPreviewAudit.java:11-39` |
| CL-19 | 既有主线设计已经区分普通可视化与高级 dbt 实现：普通模式只展示系统预处理摘要，高级模式才展示完整 dbt DAG、显式 STG 和物化方式 | `../sprint-67-202607-modeling-mainline-convergence/assets/modeling-four-layer-minimal-loop-design.md:188-199` |
| CL-20 | 普通创建默认 `DESIGNER_GENERATED`；canonical compiler 会把其结构编译为 dbt SQL/YAML，而 `DBT_MANAGED` 必须走专用导入/高级实现路径 | `ModelSpecCreateRequestDecoder.java:78-82`、`CanonicalModelLifecycleCompilerAdapter.java:29-77` |
| CL-21 | Dockerfile 只固定 `dbt-postgres==1.10.0`；本地 `dts-dbt:1.10.0` 镜像实际返回 `dbt-core 2.0.0-alpha.5`，不能用镜像标签宣称 Core 兼容 | `builds/dts-dbt/Dockerfile:56-58`；2026-08-02 `docker run --rm dts-dbt:1.10.0 --version` |
| CL-22 | 当前 source-only 普通 model 的 columns/tests 为空，尚未从 schema YAML 恢复可信字段 | `DbtSourceProjectModelPackageAdapter.java:245-260` |
| CL-23 | literal 缺失 ref 和动态 ref/source 已阻断，但自定义 macro 的隐藏依赖尚未可靠传播到调用 model/downstream | `DbtSourceProjectModelPackageAdapter.java:190-223,728-755` |
| CL-24 | 当前 conversion classifier 的安全 SQL 子集可能返回 `DESIGNER_GENERATED`；D11 要求外部导入 ownership 固定为 `DBT_MANAGED`，实施时必须解耦 capability 与 ownership | `ModelConversionClassifier.java:119-122`；`assets/dbt-compatibility-and-source-only-contract.md` |
| CL-25 | 旧 generator/ready/run 路由已经退役，但仓库仍残留两份可由通用 Airflow trigger 触发的 mutable/privileged BashOperator DAG，且 sync 无 Token、使用 `|| true`；2026-08-02 查询两者运行记录均为 0 后，仓库与部署副本已物理删除 | `it/legacy-retirement-evidence.md`；`test_sprint83_legacy_dbt_surface_retirement.py` |
| CL-26 | `/data-modeling/dimensions/workbench` 是当前菜单、路由、帮助中心和模型旅程共同使用的通用模型工作台路径，不是 Sprint-83 的笔误 | `navigation.ts:60-63,123-130`；`DataModelingPage.tsx:20-27`；`helpTopics.ts:124` |
| CL-27 | 旧 `GET /api/etl/dbt/preview` 对已认证用户可达，relation 取当前 manifest 且直接返回原始行；缺少 ModelSpec/revision/candidate/evidence pin、表级密级和 ALLOW/MASK/DENY，不能作为 Sprint-83 物理预览底座 | `EtlResource.java:124`；`SecurityConfiguration.java:77`；`DbtPreviewService.java:72,103,111` |

勘察到此冻结。后续 Task 必须引用本账本；若出现新事实，仅追加账本，不重复宽扫。

## Gate Registry

| Gate | 项目 | 状态 | 证据 | 未过则关联 Task |
|---|---|---|---|---|
| G0 | 当前切片交付基线 | PASS_WITH_GAPS | `it/baseline.md`：验收通道可达；真实登录/上传/UI 旅程按用户约束留到最终 E2E | F6/T02、F6/T04 |
| G0 | 工程 fixture 与 parser 画像 | PASS（83a） | `assets/dbt-fixture-inventory.md` + `source/dts-platform/src/test/resources/fixtures/dbt-sprint83/` | - |
| G0 | 客户兼容画像 | GAP（非编码阻断） | 尚无客户脱敏 dbt 包；只阻断 CUSTOMER-VALIDATION 与客户可见声明 | 客户现场验收，不关联通用实现 Task |
| G0 | DTS 领域不变量 | PASS | ADR-83-01～12；复用 ModelSpec/Catalog/Audit/Release/dbt gateway | - |
| G1 | 产品决策与所有权 | PASS | `assets/decision-register.md`：D01～D13 已确认；D09 认证缺口单列在 G0/G1 兼容门 | - |
| G0-RUNTIME | dbt runtime 修复与 RT-01 认证 | BLOCKED（仅阻断 S3） | `assets/dbt-runtime-hotfix-prerequisite.md`：当前 PostgreSQL 候选为 NOT_CERTIFIED | H83-01、F0/T05 |
| G1 | 当前切片端到端契约链 | PASS（83a） | 本文“端到端契约链” + F0/T04 三轮复核 | - |
| G1 | 非功能预算 | GAP | `assets/nfr-budget.md` | F0/T02、F0/T05、F5/T01～T04 |
| G3 | 发布/回滚安全 | GAP | `assets/release-plan.md` 已冻结 expand/migrate/contract、shadow relation 与回退；待实现后演练 | F4、F5、F6 |
| G4 | 可运维性 | GAP | `assets/runbook.md` 已冻结信号/阈值/处置；待指标、审计和故障演练 | F5、F6 |
| G4 | 最终 DoD | PENDING | `it/README.md` | F6 |

## Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 |
|---|---|---:|---|---|
| F0 | 架构基线与产品决策冻结 | 5 | P0×5（T05 仅 S3） | DRAFT |
| F1 | 统一 dbt 快照与可视化投影 | 5 | P0×4 / P1×1 | DRAFT |
| F2 | 业务可视化与高级 dbt 实现分层 | 5 | P0×2 / P1×3 | DRAFT |
| F3 | 外部 dbt 包逆向建模产品化 | 6 | P0×4 / P1×2 | DRAFT |
| F4 | 发布、物化与资产证据闭环 | 4 | P1×4 | DRAFT |
| F5 | 安全、审计与可运维收敛 | 5 | P0×3 / P1×1 / P2×1 | DRAFT |
| F6 | 真实端到端验收与旧入口退役 | 5 | P0×1 / P1×3 / P2×1 | DRAFT |

**总计**：35 个 Task，全部 DRAFT；P0=19（其中 F0/T05 只阻断 S3），P1=14，P2=2。
**编码门禁**：F0/T04 只评审当前待拉取切片；S1/S2 不等待 H83-01/F0/T05，S3 materialization 必须等待 `G0-RUNTIME=PASS`。P1/P2 可保持 DRAFT，不得反向阻断 P0。

### 交付切片

- **Sprint-83a（当前核心交付）**：S0 + S1 + S2，完成统一表示和 artifact-rich ZIP 首次导入，形成可见用户价值；不等待 runtime 认证、source-only 或物理清理。
- **Sprint-83b（能力增强）**：S3 + S4，完成发布物化、受控物理预览、source-only/complex、重新导入漂移与前向恢复；S3 独立受 H83-01/F0/T05 阻断。
- **Sprint-83c（清理收口）**：S5，仅在 caller=0、迁移和回滚证据成立后物理退役旧执行面、不安全 preview 与重复 parser；不得阻断 83a/83b 的正确路径。

| Slice | 可独立验收的用户结果 | Task 主链 | 优先级 |
|---|---|---|---|
| S0 工程准入 | 工程 fixture、基线与当前切片契约可重复；runtime 认证独立按需消费 | F0/T01～T04；S3 另加 F0/T05 | P0 Gate |
| S1 统一表示 | 同一模型详情显示业务结构、依赖和高级技术只读投影，普通响应/界面技术正文为 0 | F1/T01～T04 → F2/T01～T02；F5/T02～T03 并行 | P0 |
| S2 artifact-rich ZIP | ZIP → inspect/mapping/preview/apply/retry → canonical DRAFT，并给出逐项可恢复结果 | F3/T01～T04 + F5/T01～T03 → F6/T02 | P0 |
| S3 发布物化 | 固定 Implementation → StageGate/Candidate/Gateway → relation/Catalog serving → 受控物理预览 | F0/T05 + F2/T03～T04 + F4 + F6/T03～T04 | P1 |
| S4 生命周期增强 | source-only/complex、重新导入三方漂移、前向撤销和完整状态 UX | F1/T05 + F3/T05～T06 + F2/T05 | P1 |
| S5 物理退役 | caller=0 后删除旧 Bash DAG、旧不安全预览、建模共享文件写、旧脚本和重复 parser | F5/T05 → F6/T05（消费 F5/T04 可观测证据） | P2 |

F5/T04 是跨切片可观测性前置证据，F6/T01 是编码后兼容回归任务；二者不构成独立用户切片，也不反向阻断 83a。F6/T01 在 83b 对应编码全部完成后统一执行，并按实际覆盖的切片消费 F0/T02；materialization 分支额外消费 F0/T05。

## 追溯矩阵

| 需求 | Feature/Task | 验收证据 |
|---|---|---|
| 普通业务可视化隐藏 SQL/dbt 技术细节 | F1、F2/T01～T02 | IT-02、IT-03 |
| 可视化模型选择/确认 dbt 实现与物化策略 | F2、F4/T01～T02 | IT-03、IT-06 |
| 高级 dbt 实现与可视化复用模型上下文但不在同一界面混排 | F2/T01～T03 | IT-02、IT-03 |
| 查看逻辑结构、业务依赖和物化数据但不混淆证据 | F1/T03～T04、F2/T02～T04 | IT-01、IT-03 |
| artifact-rich dbt ZIP 导入为可治理模型 | F3/T01～T04 | IT-04A |
| source-only/complex 静态导入保持 fail-closed 并按可信证据开放 | F3/T06 | IT-04B、IT-05B |
| 重复导入、三方漂移、部分失败与前向恢复 | F1/T05、F3/T04～T05 | IT-05A～IT-05C |
| 两个入口统一发布和物化 | F2/T03～T04、F4 | IT-06 |
| 审计、安全、租户与脱敏 | F5 | IT-07 |
| 新建模链不调用旧执行面/旧不安全 preview；旧 Bash DAG、legacy preview、共享文件写和重复 parser 按 caller=0 门禁物理退役 | F5/T04～T05、F6/T04～T05 | IT-08 |

## 编码前必须确认的产品决策

详见 [`assets/decision-register.md`](assets/decision-register.md)。已确认：

1. 普通业务可视化隐藏全部 SQL/dbt 技术细节；显式高级 dbt 实现与可视化不在同一界面混排。
2. `DBT_MANAGED` 只表示技术实现所有权；它与是否使用 dbt 执行物化是两个概念。`DESIGNER_GENERATED` 同样可以由系统生成隐藏制品后通过 dbt 物化。
3. 不新增菜单、独立模型清单或第二个模型中心；高级 dbt 实现只作为现有模型详情“数据实现”的显式技术视图。

新增确认：

4. P0 外部接入只支持 ZIP 快照，不接 Git 或在线 packages。
5. 重新导入对技术实施采用 base/current/incoming 三方比较；ModelSpec 业务语义始终保留并重验映射，不自动覆盖、删除或猜测重命名。
6. 允许部分成功，但每个失败/阻断项必须给出稳定失败原因和可执行恢复动作；retry 幂等，撤销只追加受资格约束的前向修订。
7. Catalog 采用 latestPublishedRef/servingRef 双指针；PUBLISHED 可发现，成功 MATERIALIZED 才让新修订可消费，失败时保留旧 serving。
8. S3 物化切片的样例预览显式加载，默认 100/最大 500；普通视图仅 serving，高级维护者可看成功 candidate 并标记非正式；历史无样例行，密级/脱敏 fail-closed，无缓存/导出。

9. dbt 兼容按 inspect/import/materialization 三轴判断；政策已确认，具体 materialization profile 必须精确锁定并实测，当前不宣称已认证。
10. source-only 只有 enforced schema contract（字段 `name+data_type` 完整）才具备可信字段；否则最多只读、apply BLOCKED，且不允许手工重建技术结构。动态/macro/package 缺口阻断受影响闭包；合格闭包可单独 apply，PARTIAL 只来自已选项启动后的逐项失败。
11. Sprint-83 不提供 ZIP 导出、Git push 或 ownership conversion；外部导入始终保持 `DBT_MANAGED`。
12. inspect → preview 使用 30 分钟 `inspectionProof` 绑定 tenant、actor、规范化技术包 checksum 与 expiry；preview 只开放 context/selection/semanticOverrides 白名单，无效/过期稳定 fail-closed，不新增 inspect 表或独立 secret。

## Definition of Ready

- [x] D01～D13 的产品与架构政策已确认；D09 的具体 runtime certification 由 H83-01 + F0/T05 阻断 S3，不把政策确认或 F0/T02 parser fixture 冒充运行时兼容证据。
- [x] 83a 工程 fixture 已归档：artifact-rich、基础 blocked 与 malicious 均为重构无敏感版本，带 SHA-256 和可重复契约测试。
- [x] 客户脱敏包缺失登记为 CUSTOMER-VALIDATION GAP，不作为通用工程 DoR 条件。
- [x] 83a 表示读模型、artifact-rich inspect/preview/apply/retry 与对应审计契约已冻结；S3/S4 只在各自拉取前按现有契约过门。
- [x] parser normalized seam/adapter/删除矩阵已经评审，未新建平行解析器；物理删除不是 P0 DoR。
- [x] 83a 消费的 NFR 行已有可执行适应度函数和归属 Task；后续行保持具名 GAP，不反向阻断当前切片。
- [x] 83a 交付通道为 PASS_WITH_GAPS；P4B/P8B 明确关联 owner、适用切片和解除条件，真实 E2E 不被提前执行。

## 分切片完成标准

每个切片只按自身主链和 IT 子项判定完成；未排期的 P1/P2 切片保持 DRAFT，不反向阻断 S1/S2。只有下列全部切片及物理退役均完成时，Sprint-83 才可整体标记 DONE。

- [ ] **S1**：普通业务可视化只展示逻辑结构和业务/模型依赖，任何状态下均不显示 SQL/Jinja、macro、compiled SQL 或 project path；显式高级技术只读视图复用同一固定模型上下文。
- [ ] **S2**：artifact-rich dbt ZIP 通过带 30 分钟 `inspectionProof` 的 inspect → preview → apply/retry 进入 canonical DRAFT；技术包篡改、跨 tenant/actor 重放和过期均 fail-closed；每个 FAILED/BLOCKED 项均展示稳定失败码、阶段、安全原因、retryable、恢复动作和 correlationId，且 canonical status/summary 与明细一致，`SUCCEEDED/REPLAYED` 已完成前向迁移。
- [ ] **S3**：显式高级 dbt 实现提交只生成新 Implementation Revision；两个入口均只能经 StageGate → ReleaseCandidate → DbtExecutionGateway 发布/物化；PUBLISHED 与 serving 双指针、relation evidence 和受控物理预览门禁成立；`R-DBT-LEGACY-PREVIEW` 已完成旁路遏制，普通已认证用户/建模角色无法读取旧原始行。
- [ ] **S4**：source-only/complex、三方漂移、PARTIAL、retry、前向撤销和状态 UX 分别通过 IT-04B/IT-05A～IT-05C，不把未实现能力伪装为兼容。
- [ ] **S5**：`R-DBT-LEGACY-DAG`、`R-DBT-LEGACY-PREVIEW`、建模共享文件写、旧脚本和重复 parser 在 caller=0、迁移和回滚证据成立后物理退役。
- [ ] **全局**：安全、权限、租户、审计、脱敏、容量和故障注入验收通过；全部编码任务完成后只集中执行一次真实 PostgreSQL + Airflow/dbt + UI E2E，证据落入 `it/`。

## 非目标

- 不实现任意 SQL/Jinja 到完全可编辑图形的无损转换。
- 不在普通业务可视化页面展示 SQL/Jinja、宏、compiled SQL、dbt 文件树或完整技术 DAG。
- 不新增高级建模菜单、独立模型清单或平行 SQL 模型中心。
- 不新增第二套 ModelSpec、dbt project、import run、release candidate、pipeline run 或审计台账。
- 不编辑 compiled SQL，不从前端直接调用 Airflow/dbt。
- 不根据表名、路径或 SQL 猜测业务分类、粒度、主键、维度或指标语义。
- 首期不接 Git 凭据、Git push、在线下载 packages、执行不受信模型 SQL。
- 本 Sprint 不实现 dbt ZIP 导出、Git push 或 `DBT_MANAGED ↔ DESIGNER_GENERATED` 所有权转换，当前页面不得出现相关按钮；未来如需纳入，必须另建具名 Feature、权限/审计契约和 IT 证据后重新过 DoR。
