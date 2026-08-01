# Sprint-83：dbt 双向可视化建模与外部项目接入

**时间**：2026-08  
**状态**：DRAFT（架构评审完成，产品决策与契约尚未冻结；禁止开始编码）  
**类型**：Architecture / Product Design / dbt Integration / Full-stack  
**目标**：让建模人员在同一个 canonical 模型上下文中查看和维护业务模型、dbt SQL、表结构、依赖关系与物化结果，并把外部 dbt 项目通过可审计的预检、冲突处理和幂等应用导入为 `ModelSpec DRAFT + DBT_MANAGED Implementation Revision`，而不产生第二套模型、解析、发布或运行控制面。

## 背景与价值

Sprint-80 已把原型页面迁入正式数据建模界面，Sprint-81 已把后台收敛为：

```text
WarehousePlan → ModelSpec v2/revision → StageGate → Lifecycle
  → ReleaseCandidate → Materialization → DbtExecutionGateway → Airflow/dbt
```

当前 dbt 结合存在两个断点：

1. 旧“高级建模”路由已被重定向，新模型工作台仍是静态展示；用户无法在同一模型修订上同时查看真实 dbt SQL、结构投影、依赖和物化表数据。
2. “逆向建模”页面仍是从数据库表发现模型的硬编码原型；后台虽然已经具备 dbt ZIP inspect、preview、apply、retry 和 canonical ModelSpec/Implementation 落库，但前端没有消费这些契约。

本 Sprint 的核心不是重写 dbt，也不是把任意 SQL 变成可编辑画布，而是建立**一套版本固定的模型表示层**，让高级编辑与外部导入成为同一模型生命周期的两个入口。

## 架构决策记录（第一版提案）

| ID | 决策 | 理由 | 状态 |
|---|---|---|---|
| ADR-83-01 | `ModelSpec v2` 继续拥有业务语义；`Implementation Revision` 拥有技术实现；Airflow/dbt artifact 与 relation observation 拥有运行事实 | 三类事实不能互相冒充，也不能新建第二套模型台账 | PROPOSED |
| ADR-83-02 | `DESIGNER_GENERATED`：可视化结构可编辑，dbt 制品是生成物；`DBT_MANAGED`：SQL/Jinja 是技术源，结构视图只读，业务语义仍通过 ModelSpec 维护 | 明确字段级所有权，避免隐式双向覆盖 | PROPOSED |
| ADR-83-03 | P0 可视化包含“逻辑表结构、dbt 依赖图、物理表结构/样例数据”三个视图；不承诺任意 SQL/Jinja 到可编辑转换画布的无损往返 | 用户可以看见表及运行结果，同时不制造虚假可编辑能力 | PROPOSED |
| ADR-83-04 | 高级建模作为现有模型工作台的“高级模式”，复用同一模型列表与上下文；不新增顶级菜单或第二个模型中心 | 遵守 DTS 信息架构和禁造平行入口约束 | PROPOSED |
| ADR-83-05 | dbt 导入继续复用 `dts.model-package/v1`、archive inspector、preview/apply/retry 和现有 import run/apply 表 | 后台主体已存在，禁止新建第二套导入器和任务台账 | PROPOSED |
| ADR-83-06 | `manifest.json` 表示 dbt 结构快照，`catalog.json`/relation observation 表示物理观测，compiled SQL 只作诊断；统一投影不得反写任意 SQL | 明确证据来源及其可信度 | PROPOSED |
| ADR-83-07 | 外部项目重新导入使用 `tenant + projectKey + dbtUniqueId + artifactChecksum + ModelSpec/Implementation ETag` 判断 SKIP/UPDATE/CONFLICT；禁止静默覆盖 | 保留内部语义补充和外部变更，解决双边漂移 | PROPOSED |
| ADR-83-08 | 首期只接收 ZIP 快照，不接 Git 凭据、在线依赖下载或 Git push；inspect/preview 不执行模型 SQL | 降低供应链、凭据和离线部署风险 | PROPOSED |
| ADR-83-09 | 导入只能创建/更新 DRAFT；高级编辑提交只创建新的 Implementation Revision；发布和物化仍只经 StageGate、ReleaseCandidate 与 DbtExecutionGateway | 任何 UI 快捷操作都不能绕过 Sprint-81 控制面 | PROPOSED |
| ADR-83-10 | 所有 inspect/preview/apply/retry/SQL checkpoint/commit/conflict 动作接入公共审计 outbox 和 dts-admin 动作字典；权限只复用现有 `read/write` 与建模维护者 authority | 导入和 SQL 修改属于重要治理操作，且本 Sprint 不假设新的权限体系 | PROPOSED |
| ADR-83-11 | 部分成功不物理删除历史；retry 继续幂等，撤销采用前向 ModelSpec/Implementation 修订。是否首期提供“整包撤销”由产品决策冻结 | 现有 apply 是逐模型事务，必须诚实展示部分结果 | PROPOSED |
| ADR-83-12 | 通用 `/api/etl/dbt/files` 只可作为受控 staging 工具，不能成为建模事实源；证明建模调用方完成迁移后再决定删除或保留其非建模用途 | 共享可变文件树不具备修订固定语义 | PROPOSED |

## 三层表示与所有权

```text
┌──────────────────────────────────────────────────────────────┐
│ 逻辑设计：ModelSpec v2 revision                              │
│ 业务名称/定义、类型/分层、粒度、字段角色、标准绑定、来源映射 │
└──────────────────────────────┬───────────────────────────────┘
                               │ revision + checksum 固定
┌──────────────────────────────▼───────────────────────────────┐
│ 技术实现：Implementation Revision + immutable dbt artifacts │
│ projectKey、dbtUniqueId、SQL/Jinja、config、refs、tests      │
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
| `FULL_EDITABLE` | `DESIGNER_GENERATED` | 逻辑表、字段、关系、生成 SQL、物化结果 | 可视化结构与业务语义可编辑；SQL 生成物只读 |
| `STRUCTURE_VIEW_ONLY` | 可解析的 `DBT_MANAGED` | 表、字段、ref/source 依赖、测试、物化方式、SQL、物化结果 | SQL/Jinja 编辑；可视化技术结构只读；业务语义可编辑 |
| `BLOCKED` | 动态引用、缺失证据或无法安全解析 | 原始文件、问题、恢复建议 | 禁止伪造结构或进入发布；修正包/SQL 后重新预检 |

## 端到端契约链（提案，尚未冻结）

| 层 | 契约/落点 | 关键字段与约束 |
|---|---|---|
| 模型入口 | `/data-modeling/dimensions/workbench?modelSpecId={uuid}&mode=advanced` | 复用模型工作台；URL 固定 `modelRevision`、`implementationRevision` 时可复现历史视图 |
| 逆向入口 | `/data-modeling/dimensions/reverse?source=dbt` | 第一步明确“数据库表/视图”与“dbt 项目包”；本 Sprint 实施 dbt ZIP 分支 |
| 表示读模型 | `GET /api/modeling/model-specs/{id}/representations?modelRevision={int}&implementationRevision={int}` | 返回逻辑、技术、运行三个分区及各自 provenance/checksum/capability/drift |
| 高级草稿 | `POST /api/modeling/model-specs/{id}/implementation-drafts` | body 固定 base revision/ETag；返回隔离 draftId、过期时间和 workspace scope |
| 草稿校验 | `POST /api/modeling/implementation-drafts/{draftId}/validate` | 只 parse/compile；不得运行模型 SQL；返回结构投影和 diagnostics |
| 提交实施版本 | `POST /api/modeling/implementation-drafts/{draftId}/commit` | CAS 校验 base revision/checksum；成功创建新的 immutable Implementation Revision |
| 物理预览 | `GET /api/modeling/model-specs/{id}/implementations/{revision}/physical-preview?candidateId={uuid}&limit={1..500}` | 必须绑定成功 candidate/relation evidence；列级权限、密级和脱敏 fail-closed |
| dbt ZIP inspect | 复用 `POST /api/modeling/model-spec-imports/dbt/archive/inspect` | 仅安全解压与解析，返回规范化 `ModelPackage` |
| 导入预检 | 复用 `POST /api/modeling/model-spec-imports/dbt/preview` | `planId/domainMappings/sourceMappings/selectedUniqueIds`；返回 CREATE/UPDATE/SKIP/CONFLICT/BLOCKED |
| 导入应用 | 复用 `POST /api/modeling/model-spec-imports/dbt/apply` | `runId/previewHash/selectedUniqueIds/idempotencyKey`；逐项持久化并可恢复 |
| 重试/恢复 | 复用 `POST /api/modeling/model-spec-imports/{runId}/retry` 与查询接口 | 不重复成功项；服务重启后可恢复；不得静默覆盖冲突 |
| 发布与物化 | 复用 StageGate/Lifecycle/ReleaseCandidate/Materialization | UI 不直接调用 `/etl/dbt/run`；执行只通过 `DbtExecutionGateway` |
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

勘察到此冻结。后续 Task 必须引用本账本；若出现新事实，仅追加账本，不重复宽扫。

## Gate Registry

| Gate | 项目 | 状态 | 证据 | 未过则关联 Task |
|---|---|---|---|---|
| G0 | 交付基线 | PENDING | `it/baseline.md` | F0/T03 |
| G0 | 领域与真实 dbt 包画像 | GAP | `assets/domain-profile.md`：尚无客户脱敏 dbt 包 | F0/T02 |
| G0 | DTS 领域不变量 | PASS | ADR-83-01～12；复用 ModelSpec/Catalog/Audit/Release/dbt gateway | - |
| G1 | 产品决策与所有权 | DRAFT | `assets/decision-register.md` | F0/T01、F0/T04 |
| G1 | 端到端契约链 | DRAFT | 本文“端到端契约链” | F0/T04、F1/T01 |
| G1 | 非功能预算 | GAP | `assets/nfr-budget.md` | F0/T02、F5/T01～T04 |
| G3 | 发布/回滚安全 | PENDING | 编码前创建 `assets/release-plan.md` | F4、F5 |
| G4 | 可运维性 | PENDING | 编码前创建 `assets/runbook.md` | F5 |
| G4 | 最终 DoD | PENDING | `it/README.md` | F6 |

## Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 |
|---|---|---:|---|---|
| F0 | 架构基线与产品决策冻结 | 4 | P0 | DRAFT |
| F1 | 统一 dbt 快照与可视化投影 | 4 | P0 | DRAFT |
| F2 | 高级建模 SQL/可视化双视图 | 5 | P0 | DRAFT |
| F3 | 外部 dbt 包逆向建模产品化 | 5 | P0 | DRAFT |
| F4 | 发布、物化与资产证据闭环 | 4 | P0 | DRAFT |
| F5 | 安全、审计与可运维收敛 | 4 | P0 | DRAFT |
| F6 | 真实端到端验收与旧入口退役 | 4 | P0 | DRAFT |

**总计**：30 个 Task，全部 DRAFT。  
**依赖顺序（Task DAG）**：F0 → F1 → F2/T01～T03 与 F3 → F4/T01～T03 → F2/T04～T05 与 F4/T04；F5 从 F1 起并行守卫，最后 F6。F4 不依赖整个 F2，只消费 F2/T03 产出的固定 Implementation Revision。  
**编码门禁**：F0/T04 未通过前，任何实现 Task 不得转为 READY。

## 追溯矩阵

| 需求 | Feature/Task | 验收证据 |
|---|---|---|
| 高级建模同时看 SQL 与可视化表 | F1、F2 | IT-02、IT-03 |
| 查看表结构、依赖和物化数据但不混淆证据 | F1/T03～T04、F2/T02～T04 | IT-01、IT-03 |
| 外部 dbt ZIP 导入为可治理模型 | F3 | IT-04 |
| 重复导入、冲突、部分失败可恢复 | F3/T03～T05 | IT-05 |
| 两个入口统一发布和物化 | F4 | IT-06 |
| 审计、安全、租户与脱敏 | F5 | IT-07 |
| 旧高级路由和通用文件写入口不再成为建模 owner | F6/T04 | IT-08 |

## 编码前必须确认的产品决策

详见 [`assets/decision-register.md`](assets/decision-register.md)。首轮建议确认：

1. “可视化”P0 是否按逻辑表结构、依赖图、物理表/样例数据三个视图交付，不做任意 SQL 的可编辑转换画布。
2. `DBT_MANAGED` 是否采用“SQL 技术权威、结构只读、业务语义可编辑”。
3. 高级建模是否作为模型工作台高级模式，而不是新增菜单和模型列表。
4. 首期是否只支持 ZIP 快照，不接 Git 同步/推送和在线依赖安装。
5. 重新导入是否永不自动覆盖；外部和内部均变化时必须 CONFLICT。
6. 本 Sprint 是否明确不做 ZIP 导出；若未来需要，另建 Feature，且只允许修订固定快照，不做 Git push。
7. 部分成功是否允许并展示逐项结果；撤销是否采用前向修订。
8. 草稿 ModelSpec 是否只在建模域可见，发布/物化后才登记为可消费 Catalog 资产。

## Definition of Ready

- [ ] 八项产品决策均已确认并在 decision register 标记 `ACCEPTED`。
- [ ] 至少三类脱敏 dbt fixture 已归档：artifact-rich、source-only、complex/blocked。
- [ ] 表示读模型、草稿、预检、物理预览和审计契约字段已冻结。
- [ ] parser 收敛/删除矩阵已经评审，未新建平行解析器。
- [ ] NFR 每一行都有可执行适应度函数和归属 Task。
- [ ] G0 交付基线通过或明确关联 F0 阻断任务。

## 完成标准

- [ ] 高级模式使用同一 `modelSpecId/modelRevision/implementationRevision/checksum` 展示 SQL、结构、依赖和运行证据。
- [ ] DBT_MANAGED SQL 保存只生成新的实施修订，不直接覆盖发布中的制品。
- [ ] 外部 dbt ZIP 通过 inspect → preview → apply/retry 进入 canonical DRAFT，且所有冲突可见、可恢复。
- [ ] 两个入口均只能经 StageGate → ReleaseCandidate → DbtExecutionGateway 发布/物化。
- [ ] 安全、权限、租户、审计、脱敏、容量和故障注入验收通过。
- [ ] 全部编码完成后一次执行真实 PostgreSQL + Airflow/dbt + UI E2E，证据落入 `it/`。

## 非目标

- 不实现任意 SQL/Jinja 到完全可编辑图形的无损转换。
- 不新增第二套 ModelSpec、dbt project、import run、release candidate、pipeline run 或审计台账。
- 不编辑 compiled SQL，不从前端直接调用 Airflow/dbt。
- 不根据表名、路径或 SQL 猜测业务分类、粒度、主键、维度或指标语义。
- 首期不接 Git 凭据、Git push、在线下载 packages、执行不受信模型 SQL。
- 本 Sprint 不实现 dbt ZIP 导出，也不实现 `DBT_MANAGED → DESIGNER_GENERATED` 所有权转换；如果讨论决定纳入，必须新增具名 Feature/Task、权限契约和 IT 证据后重新过 DoR。
- 本轮只形成 Sprint 设计，不修改产品代码，不部署环境。
