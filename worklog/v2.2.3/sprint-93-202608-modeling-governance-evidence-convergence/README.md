# Sprint-93：数据建模与数据治理证据闭环

**时间盒**：2026-08-17 ～ 2026-09-04（15 个工作日）
**状态**：IMPLEMENTATION_COMPLETE / FINAL_E2E_PENDING（F0～F5 已完成实现与模块验证；F6 集中 E2E、发布/回滚演练和 Chrome 95 证据尚未执行）
**类型**：Architecture Convergence / Governance Evidence / Compatibility Migration / UI Productization
**目标**：让建模人员从 ModelSpec 构建、物化、质量检查到发布后，能够在同一个稳定资产身份下继续查看业务元数据、表/字段血缘、治理质量和消费资格；二次物化不产生重复模型或重复资产，所有状态均可追溯、可重试、可对账。

## 1. 背景与价值

平台已经具备资产概览、资产目录、技术元数据、业务元数据、血缘、质量规则和模型发布能力，也存在“模型发布后登记物理表、字段和表级血缘”的真实主干。但是当前主干之后仍存在三类断链：

1. Sprint 启动基线中，`catalog_dataset`、`modeling_catalog_model_serving_projection` 与 `catalog_asset_semantic_projection` 三个读写模型没有被同一条生产链稳定驱动：367 个目录资产对应 0 条语义投影，43 个模型服务投影全部停留在 `SYNC_PENDING`。2026-08-17 收口复查已变为 308 条语义投影、59 条待补齐，43 条服务投影全部 `SYNCED`，剩余工作是受控迁移和故障演练而非人工改状态。
2. 模型发布把 `dbt build/test` 当作“质量通过”，而治理质量的事实来自钉定的 `gov_rule_version`、`gov_rule_binding` 和 `gov_quality_run`。两者含义不同，不能互相替代。
3. 模型发布已经写入验证过的表级血缘，但当前运行环境字段级血缘为 0；OpenMetadata、dbt 导入和本地血缘各自有入口，尚未形成同一资产身份下的证据链。

不处理这些问题的直接后果是：建模页面显示“物化/发布成功”，资产目录却无法稳定解释其治理、服务和质量状态；资产概览数字可能与目录不一致；下游指标、报表和数据服务也无法可靠判断资产是否真正可消费。

本 Sprint **不重做已有页面和模块**，而是在 Sprint-86/87 的资产语义基础上，消费 Sprint-88～91 的既有成果，收敛后端事实源和现有页面之间的证据链。

## 2. 范围与既有 Sprint 边界

| 既有 Sprint | 已拥有的范围 | Sprint-93 只做什么 |
|---|---|---|
| Sprint-86/87 | 资产身份、五轴状态、Producer/Evidence、统计投影、数据架构控制面 | 修复生产写入、对账和跨域消费，不重议 ADR |
| Sprint-88 | 资产概览页面布局、图表和唯一目录出口 | 统一概览与目录的统计事实，不重做布局 |
| Sprint-89 | 采集来源 locator、稳定 ID、Schema 漂移和建模来源重确认 | 消费其来源身份与失效语义，不建设第二个元数据采集流程 |
| Sprint-90 | 血缘事实正确性、人工登记/核验和血缘运营 UI | 只补模型物化到既有血缘写入 seam 的证据，不重复血缘页面 |
| Sprint-91 | 同一 ModelSpec 的可视化/dbt 双模式和统一发布入口 | 在现有工作台增加资产、质量和同步深链，不增加发布控制面 |
| Sprint-92（预留） | `DBT_MANAGED → DESIGNER_GENERATED` 安全回切 | 本 Sprint 不占用该编号，也不进入回切范围 |

详细边界见 `assets/dependency-boundary.md`。

## 3. 架构决策记录（ADR）

| 决策点 | 选择 | 理由与影响 |
|---|---|---|
| ADR-93-01 物理资产身份 | 继续使用 `CatalogAssetType.DATASET + CatalogAssetKey`；`catalog_dataset` 是目录身份/技术事实，语义投影只承载正交状态与消费资格 | 禁止新建第二本资产台账或按模型类型建立关系表 |
| ADR-93-02 语义模型身份 | 使用 `CatalogAssetType.SEMANTIC_MODEL + CatalogAssetKey.semanticModel(modelSpecId)`；模型 revision/candidate/observation 连接物理资产 | 模型和物理表不是同一资产，但必须可双向追溯 |
| ADR-93-03 唯一登记边界 | 所有稳定物理关系的发现、物化和发布观察统一进入 `CatalogAssetRegistrationService.observe`；内部批量入口沿用 `/api/internal/catalog/asset-observations` | 采集、dbt、物化不得直接维护另一套五轴状态 |
| ADR-93-04 稳定关系准入 | 表、视图、物化视图是资产；temporary/ephemeral 排除；`DIM` 归一为 `DWD + DIMENSION_TABLE` | 延续 Sprint-86 D01/D02，不改旧 `warehouse_layer` 值 |
| ADR-93-05 两类质量 | `dbt compile/build/test` 是工程质量；`ruleVersion/binding/run` 是治理数据质量 | 发布策略可要求两者同时通过，但 UI、DTO 和审计必须分开展示 |
| ADR-93-06 质量证据持久化 | 不复制治理质量结果；发布命令的既有 append-only `response_snapshot` 只保存钉定引用与 checksum | `latest` 漂移不能改变历史候选结论，重试生成新 run |
| ADR-93-07 血缘事实 | 声明、验证、人工血缘共用既有表，以来源、验证状态和有效期区分；采集不得覆盖人工 `VERIFIED` | 消费 Sprint-90 guard，不新增血缘表 |
| ADR-93-08 OpenMetadata 边界 | OpenMetadata 是技术元数据引擎/缓存；DTS 是资产身份、业务治理、发布和消费资格事实源 | OM 不可用时资产仍存在，只显示同步异常 |
| ADR-93-09 UI 形态 | 不新增菜单或工作台；复用资产概览、目录、详情、元数据、血缘、质量和模型工作台 | 新能力以状态区块、Tab 和深链进入现有页面 |
| ADR-93-10 统一用词 | Catalog 的 `domainId` 对外称“业务归属数据域”；建模 `subjectDomainId` 仍称“应用主题域” | 禁止继续用“主题域”混指两种概念 |
| ADR-93-11 权限边界 | 沿用现有 `read/write/export` guard；`ROLE_ADMIN`、`ROLE_OP_ADMIN`、`ROLE_INST_DATA_OWNER` 可写平台全局资产语义；部门角色按部门范围治理和查看 | 不在本 Sprint 发明细粒度权限模型；所级数据管理员可自助发布质量通过的模型 |
| ADR-93-12 二次物化 | 同一 ModelSpec、同一物理 AssetKey；新增 candidate/attempt/observation，成功后推进 servingRef | 不覆盖历史，不重复创建模型或资产 |

## 4. 端到端契约链（Vertical Slice）

```text
数仓规划
  → ModelSpec revision / implementation revision
  → release candidate
  → dbt compile/build/test（工程质量）
  → materialization dispatch / physical observation
  → CatalogAssetRegistrationService.observe
  → catalog_dataset + asset semantic projection
  → table/column lineage + metadata evidence
  → GovRuleVersion + GovRuleBinding + GovQualityRun（治理质量）
  → publish/self-publish
  → semantic model projection + servingRef + SYNCED
  → 资产概览/目录/详情/元数据/血缘/质量
  → 指标、报表、数据服务按 consumptionEligibility 消费
```

| 层 | 契约/落点 | 签名要点 |
|---|---|---|
| UI 入口 | `/data-modeling/dimension` 等模型工作台、`/catalog/assets`、`/catalog/search`、`/catalog/datasets/:id`、`/catalog/metadata`、`/catalog/metadata-management`、`/catalog/lineage`、`/governance/quality` | 不新增菜单；模型页提供“查看资产/质量证据/目录同步”深链；资产详情汇总现有各域事实 |
| 资产观察 API | `POST /api/internal/catalog/asset-observations` | body 为 `ObservationCommand[]`，1～500 条；SERVICE_INTERNAL；返回逐条 admitted/excluded/reason/receipt |
| 资产语义 API | 复用 `GET /api/catalog/assets-v2/semantics`、`GET /stats-projection`、`POST /stats-projection/reconcile` | list/overview 响应兼容扩展五轴、eligibility、projectionUpdatedAt；旧字段保留 |
| 目录 API | `GET /api/catalog/assets-v2`、`GET /api/catalog/assets-v2/overview`、`GET /api/catalog/datasets/{id}/governance-health` | 同一 AssetKey 解析目录、语义、质量和服务状态；分页仍为 0-based |
| 资产服务 | `CatalogAssetRegistrationService.observe/updateGovernance/find/stats/reconcile` | 作为稳定物理资产的唯一 command boundary；不得被新的写服务绕开 |
| 服务投影事件 | 既有 outbox `eventType=MODELING_CATALOG_PROJECTION` | payload 钉定 `modelSpecId/modelRevision/implementationRevision/candidateId/candidateVersion/catalogAssetType/catalogAssetKey/projectionVersion/outcomeCode` |
| 服务投影回执 | `markSyncSucceeded(tenantId, modelSpecId, expectedVersion)` / `markSyncFailed(..., errorCode, nextAttemptAt)` | CAS；重复消费幂等；旧版本不得覆盖新 servingRef |
| 质量证据 Port | `QualityEvidenceRequest {assetType,assetKey,ruleVersionIds,asOf,maxAgeSeconds}` → `QualityEvidence[]` | 每项包含 `ruleId,ruleVersionId,bindingId,runId,status,finishedAt,evidenceChecksum`；missing/running/failed/expired/mismatch 均 fail-closed |
| 发布命令快照 | 既有 `modeling_model_release_candidate_command.response_snapshot` | 保存工程证据摘要与治理质量引用/checksum，不复制规则和运行结果 |
| 血缘 | 既有 `catalog_dataset_lineage`、`catalog_column_lineage` | 同一 datasetId；记录来源、relationType、verificationStatus、validFrom/validTo 和候选/调用证据 |
| 数据迁移 | 扩展既有 `CatalogAssetNormalizationMigrationService` 的 preview/apply/rollback owner | 每批 ≤500；previewHash + batchId；有歧义或缺 producer/evidence 的行进入 issue，禁止猜测回填 |
| 审计 | 复用 dts-admin 审计资源字典 | 观察、对账、回填、质量门禁、同步重试、人工治理均有分类动作和 correlationId |

## 5. 现状勘察账本（Context Ledger）

本表是本 Sprint 的一次性勘察结果。下游 Task 直接引用 `Lxx`，禁止重复扫描。

| ID | 事实 | 证据 |
|---|---|---|
| L01 | 稳定物理资产观察的唯一边界已存在，内部批量接口限制 1～500 条 | `CatalogAssetObservationResource.java:15-33` |
| L02 | `ObservationCommand` 已包含身份、关系类型、数据域、分层/角色、Producer、Evidence、五轴、质量和权限门禁 | `CatalogAssetSemanticsContract.java:146-166` |
| L03 | 准入规则已排除临时/ephemeral，并把 DIM 归一为 DWD + DIMENSION_TABLE | `CatalogAssetSemanticsContract.java:200-291` |
| L04 | 消费资格由 discovery/governance/publication/serving/lifecycle 加质量、权限共同计算 | `CatalogAssetSemanticsContract.java:294-335` |
| L05 | `CatalogAssetRegistrationService` 已声明为 single command boundary，并提供 observe/find/stats/reconcile | `CatalogAssetRegistrationService.java:13-69` |
| L06 | 生产源码中仅归一化迁移创建 `ObservationCommand`；采集、dbt、物化和发布没有生产 adapter 调用 | 2026-08-16 `rg "new ObservationCommand"` 结果 |
| L07 | 模型发布已经创建/更新 `catalog_dataset`、字段、建模血缘和目录血缘 | `CandidatePublicationRepository.java:47-106,542-656,881-999` |
| L08 | 语义模型服务投影会发布 `MODELING_CATALOG_PROJECTION` outbox，且 payload 已钉定模型/实现/候选/投影版本 | `CatalogModelServingService.java:59-76,167-209` |
| L09 | serving 只有当前候选、构建、dispatch 和物理关系证据全部匹配才可推进 | `CatalogModelServingProjectionRepository.java:149-223,396-455` |
| L10 | `markSyncSucceeded/Failed` 已存在但无生产调用方 | `CatalogModelServingProjectionRepository.java:345-374`；2026-08-16 全仓调用检索 |
| L11 | 模型 StageGate 当前把 lifecycle TEST 同时作为 tests 与 quality | `ModelSpecStageGateService.java:573-631` |
| L12 | 发布质量 reconciler 当前以完成的 dispatch + BUILT pipeline 证明质量，并未读取治理质量运行 | `ModelPublicationQualityEvidenceRepository.java:8-13,25-147` |
| L13 | 治理质量执行已经强制使用启用规则、已发布版本、版本所属绑定和 datasetId | `QualityRunService.java:149-195,486-528` |
| L14 | 资产详情治理健康已按 datasetId 聚合质量运行、问题单和深链 | `CatalogGovernanceResource.java:102-239` |
| L15 | Sprint-81 已规划 `QualityEvidenceRequest → QualityEvidence`，但 Task 仍为 PLANNED | `sprint-81.../F3.../T02-建立QualityEvidence到StageGate桥接.md:1-35` |
| L16 | 当前目录列表从 `/api/catalog/assets-v2` 读取，概览端点仍最多扫描 200 条 | `CatalogAssetPortalResource.java:71-128,173-190` |
| L17 | 现有 SOURCE/DIM 归一化迁移已经具备 previewHash、≤500 apply、batchId 和版本防漂移 rollback | `CatalogAssetNormalizationMigrationService.java:27-176,179-234` |
| L18 | 统计投影已有每日 reconciliation scheduler | `CatalogAssetStatsReconciliationScheduler.java:6-19` |
| L19 | 2026-08-16 启动基线：目录资产 367、语义投影 0、服务投影 43 且全部 SYNC_PENDING、字段血缘 0、质量运行仅覆盖 1 个 dataset | `assets/domain-profile.md` §3 |
| L20 | 本地 367 个目录资产全部为 PENDING_GOVERNANCE；源码发布路径预期写 PUBLISHED，存在源码/部署/数据状态漂移，根因尚未确认 | `assets/domain-profile.md` §3；`CandidatePublicationRepository.java:542-656` |
| L21 | 资产概览、元数据治理和目录仍有“主题域”文案；Catalog 语义应为“业务归属数据域” | `AssetOverviewPage.tsx`、`MetadataManagementPage.tsx`、`DataSearchPage.tsx` |
| L22 | 2026-08-14 已允许所级/部门范围数据管理员从质量通过状态自助发布，并保留 actor/timestamp | `20260814_01_model_release_candidate_self_service.xml:7-72` |
| L23 | 2026-08-17 实现收口复查：目录资产 367、语义投影 308、待补齐 59、服务投影 43 且全部 SYNCED（最大 attempt=3）、当前表级血缘 51、字段血缘 0、质量运行 134 且全部 FAILED/仅 1 个 dataset | `assets/domain-profile.md` §3.1 |

**开放问题**：

- OQ-01：当前已有 308/367 条语义投影，剩余 59 条必须继续经 preview 判定 producer/evidence；F1/T02 不得据此直接全量 apply。
- OQ-02：字段血缘为 0 是缺少 manifest 输入、解析跳过还是部署链未触发，交由 Sprint-90 F1 与本 Sprint F4/T01 联合判定；不得先发明第二个字段解析器。
- OQ-03：现有所有资产为 `PENDING_GOVERNANCE` 的具体写入者尚未定位；F1/T01 先用契约测试和审计日志确定，不按结果批量改状态。
- OQ-04：xiezm 已在本地 Chrome 150 完成真实登录、菜单、关键 API、console/Network smoke；Chrome 95、写命令和部门越权负向尚未执行，F5/F6 最终验收保持 BLOCKED。

## 6. Gate Registry

| Gate | 项目 | 状态 | 证据 | 未过则关联 Task |
|---|---|---|---|---|
| G0 | 交付基线 | READY_FOR_FINAL_E2E | `it/baseline.md` | F6/T01 |
| G0 | 领域与真实数据画像 | PASS_WITH_GAPS | `assets/domain-profile.md` §3.1 | F6/T01 |
| G0 | DTS 不变量自检 | PASS | ADR-93-01/03/07/08/09/11；A3/A4/B1/C3/D1/D4 | - |
| G1 | 契约链贯通 | PASS_WITH_GAPS | 本文 §4；字段血缘入口等待 OQ-02 | F4/T01 |
| G1 | 非功能预算 | PASS_WITH_GAPS | `assets/nfr-budget.md` | F0/T01、F6/T02 |
| G2 | 变更范围守卫 | PASS_WITH_SHARED_WORKTREE_WARNING | Sprint-93 逐符号 impact；全工作区 detect_changes=CRITICAL（含并行 Sprint-94） | 提交前隔离 owned files |
| G3 | 发布安全 | READY_FOR_REHEARSAL | `assets/release-plan.md` | F6/T02 |
| G4 | 可运维性 | IMPLEMENTED / REHEARSAL_PENDING | `assets/runbook.md` | F6/T02 |
| G4 | DoD 验收 | READY_FOR_FINAL_E2E | `it/README.md` | F6/T01 |

## 7. Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 |
|---|---|---:|---|---|
| F0 | 交付基线与事实对账 | 1 | P0 | BASELINE_READY / FINAL_E2E_PENDING |
| F1 | 物理资产登记与语义投影收敛 | 2 | P0 | IMPLEMENTATION_COMPLETE / MODULE_VERIFIED |
| F2 | 模型服务投影闭环 | 1 | P0 | IMPLEMENTATION_COMPLETE / MODULE_VERIFIED |
| F3 | 治理质量证据桥接 | 2 | P0 | IMPLEMENTATION_COMPLETE / MODULE_VERIFIED |
| F4 | 元数据与血缘证据贯通 | 1 | P0 | IMPLEMENTATION_COMPLETE / REAL_FIELD_EVIDENCE_PENDING |
| F5 | 资产治理界面收敛 | 1 | P1 | IMPLEMENTATION_COMPLETE / MODULE_VERIFIED |
| F6 | 发布安全与端到端验收 | 2 | P0 | READY_FOR_FINAL_E2E |

**执行顺序**：F0 → F1/T01 ∥ F2 ∥ F3/T01 → F1/T02 → F3/T02 → F4 → F5 → F6。
Sprint-89 F3/F4 与 Sprint-90 F1/F5 是外部依赖，任务不得复制其源码范围。

### 7.1 2026-08-17 实现收口

- F1 的唯一资产登记、语义投影补齐、稳定 preview hash、限批 apply、版本防漂移 rollback 和审计已完成；真实首批迁移留给 IT-03。
- F2 的耐久 claim、CAS 回执、分级退避、终态问题化/审计、人工幂等重试和前端状态块已完成；当前运行库 43 条服务投影均为 `SYNCED`。
- F3 已用独立 Port 桥接钉定的规则版本、绑定和运行；工程质量与治理质量已在候选快照、发布门禁和 UI 中分栏，重跑生成新 runId。
- F4 已把 dbt 物化、表/字段血缘、漂移 issue 和 OM 降级接入既有 seam；当前运行库字段血缘仍为 0，最终验收必须展示真实跳过原因或补齐真实证据，禁止伪造边。
- F5 已统一概览、目录、详情和模型页的批量状态读取、筛选、深链与术语；相关 Vitest 10 项、Node 源码契约 35 项通过。
- dts-platform 定向后端测试、Spotless 和三份 Compose 配置通过；前端 Vite Legacy 产物构建成功。仓库并行修改的 `DashboardEditorPage.tsx` 仍有 TypeScript 未使用变量，未纳入 Sprint-93 修改范围。
- 按执行约束，浏览器 E2E、真实迁移 apply/rollback、故障注入和发布演练在 F0～F5 全部实现后集中执行，本节不把模块测试写成运行态 PASS。

## 8. 追溯矩阵（Traceability）

| 需求点 | Feature / Task | 验收证据 |
|---|---|---|
| 建模物化后自动形成同一物理资产，不重复建账 | F1/T01 | IT-01、IT-02 |
| 现有目录资产安全补齐语义投影 | F0/T01、F1/T02 | IT-03、迁移 dry-run/apply/rollback 记录 |
| 模型服务投影不再永久 SYNC_PENDING | F2/T01 | IT-04、同步重试/失败注入证据 |
| 工程测试与治理质量口径分开 | F3/T01、F3/T02 | IT-05、IT-06 |
| 发布候选钉定具体规则版本、绑定和运行 | F3/T01 | IT-05 |
| 表级与字段级血缘沿同一 datasetId 查询 | F4/T01 | IT-07 |
| OpenMetadata 不可用时资产仍可查 | F4/T01 | IT-08 |
| 资产概览、目录、详情状态一致 | F5/T01 | IT-09 |
| “主题域”与“业务归属数据域”不再混用 | F5/T01 | UI source-contract + IT-09 |
| 二次物化不复制模型/资产且推进 servingRef | F6/T01 | IT-10 |
| xiezm 以所级数据管理员完成自助发布与治理 | F6/T01 | IT-11 |

## 9. Sprint Definition of Done

- [ ] 同一稳定物理关系在首次物化、发布和二次物化后始终解析到同一 AssetKey、同一 datasetId。
- [ ] 模型服务投影能够从 `SYNC_PENDING` 进入 `SYNCED`；失败可重试并保留错误码、attempt 和 correlationId。
- [ ] 资产概览、目录和详情读取同一语义投影，数字与筛选结果可对账，并明确显示投影新鲜度。
- [ ] 发布面板将工程质量与治理数据质量分开展示，治理质量证据钉定 rule/version/binding/run/checksum。
- [ ] 表级与字段级血缘均有真实模型链样本；字段血缘缺失时显示具体跳过原因，不伪造边。
- [ ] OM 不可用不导致 DTS 资产消失；同步状态正确降级。
- [ ] 存量补齐全程具备 preview/apply/rollback，漂移或歧义 fail-closed，不覆盖用户治理结果。
- [ ] 资产详情可回溯模型 revision、candidate、materialization attempt、quality run 和 lineage evidence。
- [ ] Chrome 95、xiezm 真实登录、空/加载/错误/成功四态及网络/控制台检查有证据落入 `it/`。
- [ ] 各 Task 的契约测试、模块构建、迁移验证、集中 E2E、发布与回滚证据完整；无占位证据。
- [ ] 提交前 `gitnexus_detect_changes()` 证明没有新增平行资产、质量、血缘或发布 owner。

## 10. 非目标

- 不新增数据治理一级/二级菜单，不建设新的资产、元数据、血缘或质量工作台。
- 不重做 Sprint-88 的资产概览布局，不恢复独立资产台账页面。
- 不重做 Sprint-89 的来源身份、Schema drift 和来源重确认。
- 不重做 Sprint-90 的人工血缘、血缘核验或采集运营 UI。
- 不实现 Sprint-92 的 dbt 回切，也不改造 SQL/dbt parser。
- 不引入多租户产品模型；当前按平台全局与部门数据范围运行。
- 不扩展 `read/write/export` 之外的权限动作集。
- 不把业务标签与密级 `classification` 合并。
- 不删除既有表、字段、路由和事件；首轮只允许 Expand/兼容读写。
