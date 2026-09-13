# Sprint-92：统一模型创作与双视图收敛

**时间盒**：2026-08-19 ～ 2026-09-15（20 个工作日）  
**状态**：IMPLEMENTATION_COMPLETE / E2E_PENDING（F1～F4 和迁移命令已实现；F0 实时基线、集中 E2E 与发布回滚演练待执行）  
**类型**：Architecture Convergence / Modeling Workflow / UI Productization / Compatibility Migration  
**Owner / 评审人**：xiezm  
**目标**：建模人员在同一 ModelSpec、同一草稿和同一模型工作台中自由切换可视化与代码视图；手工创建、平台生成和 dbt ZIP 导入仅记录来源，不再决定编辑权限；`PUBLISHED` 修订保持不可变，修改时显式派生新的 `DRAFT`，并继续复用既有构建、质量、发布、物化和治理证据链。

## 1. 背景与价值

Sprint-91 已经把可视化与代码入口收敛到同一模型工作台，并补齐 Monaco、dbt 草稿、依赖快照和统一发布/物化主干。但其产品行为仍建立在 `DESIGNER_GENERATED / DBT_MANAGED` “实现所有权”之上：

1. 可视化模型进入代码模式后必须执行“接管代码实现”，接管后本版本不可回退；
2. dbt/ZIP 模型进入可视化模式时整页只读；
3. 用户看到两套维护方式、两个保存心智和相互冲突的只读提示；
4. 来源信息被误用为授权判断，导致同一 ModelSpec 的字段、依赖和实现无法按实际需求协同修改。

该设计不符合当前确认的产品原则：**可视化和代码是同一模型草稿的两个视图，导入方式只是 provenance，不是 ownership 或权限边界。** 本 Sprint 不推翻 Sprint-91 已完成的编译器、草稿、发布和物化能力，而是用一个组合草稿与一个 command boundary 把这些 seam 重新编排起来。

不处理的直接后果是：ZIP 导入模型无法继续用可视化方式“精装修”；平台生成模型一旦进入代码维护就永久失去可视编辑；发布版本与物化状态虽然统一，创作阶段仍是两条互斥链路，后续 Sprint-93 的治理证据只能接收结果，无法解释同一次修改的完整来源和 pins。

## 2. 统一语言与边界

| 术语 | 本 Sprint 定义 | 禁用/兼容词 |
|---|---|---|
| ModelSpec | 模型业务定义与稳定身份；字段、来源、依赖、分层、粒度的 canonical owner | 不称“可视化模型” |
| Model implementation | 同一 ModelSpec 的可执行实现修订与 artifact | 不称“代码模型” |
| Authoring draft | 暂存 ModelSpec 快照、实现 bundle、projection 和 pins 的组合草稿 | 不新建第二本模型台账 |
| Visual view / Code view | 同一 authoring draft 的两种表现和编辑视图 | 不称两种所有权 |
| Provenance | `SYSTEM_GENERATED / MANUAL_CODE / DBT_ZIP_IMPORT` 等来源证据 | 不参与写权限判定 |
| Projection coverage | `FULL / PARTIAL / NONE`，描述代码到结构化视图的可投影程度 | 不等于整页可写/只读 |
| Published fork | 从不可变 `PUBLISHED` 修订显式派生同一 ModelSpec 身份下的新 `DRAFT` | 禁止原地修改发布修订 |

### 2.1 既有 Sprint 边界

| Sprint | 保留的历史事实 | Sprint-92 的关系 |
|---|---|---|
| Sprint-91 | 单一模型工作台、representation API、Monaco、dbt draft、compiler、依赖快照、候选/物化主干 | 替代 ADR-91-09 及 F2/F3 的永久“接管/只读”产品语义；复用其代码 seam 与已完成证据 |
| Sprint-93 | 稳定资产身份、治理质量、血缘、服务投影和二次物化证据 | 只消费相同 model/implementation/dependency pins；不按 provenance 分叉治理 |
| Sprint-94 | 已治理数据集到分析/看板的消费链 | 不进入本 Sprint；只要求发布资产身份零变化 |

## 3. 架构决策记录（ADR）

| 决策点 | 选择 | 理由与影响 |
|---|---|---|
| ADR-92-01 稳定身份与发布不可变 | ModelSpec ID 不变；`PUBLISHED` 不原地修改，点击“创建新草稿版本”后产生新 `DRAFT` head，并钉住来源修订 | 保留候选、资产、血缘和物化历史；避免编辑覆盖已上线证据 |
| ADR-92-02 provenance 与权限分离 | `implementationMode` 暂留作兼容字段；能力与 `allowedActions` 不再按其值互斥。来源由 source bundle/implementation 事实派生，只用于展示、审计和恢复 | 不做破坏性字段删除；旧数据可渐进迁移 |
| ADR-92-03 唯一组合草稿 owner | 扩展既有 `modeling_dbt_implementation_draft` 和 `DbtImplementationDraftService`，加入 ModelSpec 暂存快照与 projection 摘要；不新增 authoring 台账 | 复用既有 CAS、过期、文件容量、验证、幂等和 commit receipt |
| ADR-92-04 唯一 command boundary | 新增 `/authoring-drafts` REST facade；其内部复用 ModelSpec 校验/仓储、dbt validator/freeze/import 和 dependency resolver。旧 `/dbt-drafts`、ownership transition、convert 路由只作为兼容 adapter | UI 只调用新边界；禁止复制 parser、artifact、发布状态机 |
| ADR-92-05 两个视图一个草稿 | visual/code 切换只改变 `view` URL 和表现；保存、校验、提交共享 draftId、ETag、validatedChecksum 与 dependencyChecksum | 切换不创建 revision；dirty guard 跨视图生效 |
| ADR-92-06 安全投影而非整页锁定 | 使用 `FULL/PARTIAL/NONE` 和逐节点 editability。可结构化部分用表单/画布编辑；不能无损投影的表达式作为“原始代码节点”在同一页面编辑 | 任意 SQL 不伪装成表单；局部复杂不再导致全部业务元数据只读 |
| ADR-92-07 bundle 不丢失 | ZIP/手工代码原始 bundle、路径、checksum 和 `lossless` 事实继续冻结；visual 保存不得重排或删除未拥有文件。系统只重写明确标为 managed 的节点/区域 | 往返不可无损时 fail closed，并保留原始代码 |
| ADR-92-08 ModelSpec 仍是依赖 owner | `sourceRefs + dependsOn + dimensionRefs` 继续是业务依赖事实；dbt `source()/ref()` 解析只做实现对账并产出同一 dependency checksum | 不新增依赖表；防止 visual/code 产生两张依赖图 |
| ADR-92-09 生命周期不分叉 | authoring commit 后继续进入 Sprint-91/76 的候选、构建、工程测试、治理质量、评审、发布、物化、重试/回滚 | 代码视图不增加第二个发布按钮或快捷上线 |
| ADR-92-10 权限与审计 | 继续使用 `CATALOG_MAINTAINERS` 和现有 `read/write/export` guard；所级数据管理员按当前策略维护。写动作登记统一审计资源，审计只记 pins/provenance/checksum，不记 SQL 正文 | 不在本 Sprint发明细粒度权限；越权 fail closed |
| ADR-92-11 单一页面 owner | 唯一入口仍为 `/data-modeling/dimensions/workbench?modelSpecId={id}&view=visual|code` | 不新增菜单、模型页或 SQL IDE；Chrome 95 继续是兼容下限 |
| ADR-92-12 Expand/Migrate/Contract | 首轮只增加可空列、新 facade 和兼容委托；存量 dry-run 后分批补齐；旧字段/路由/状态值不在本 Sprint 物理删除 | 前后端可独立回滚；Contract 另立后续任务并需消费证据 |

## 4. 端到端契约链（Vertical Slice）

### 4.1 竖线 A：打开、续编或从发布版本派生草稿

| 层 | 契约/落点 | 签名要点 |
|---|---|---|
| UI | 模型工作台模式区；`[可视化] [代码]`；`PUBLISHED` 时唯一主动作“创建新草稿版本” | URL 只保存 `modelSpecId` 与 `view`；切换无写请求 |
| Read API | `GET /api/modeling/model-specs/{id}/authoring-context?modelRevision={int}&implementationRevision={int?}` | resp `AuthoringContextView {model,implementation,provenance,projection,openDraft?,allowedActions,publishedForkRequired}`；不返回无权限技术正文 |
| Command API | `POST /api/modeling/model-specs/{id}/authoring-drafts` | body `{intent:'EDIT_DRAFT'|'FORK_PUBLISHED',baseModelRevision,baseModelChecksum,baseImplementationRevision?,baseImplementationChecksum?,idempotencyKey}`；resp `AuthoringDraftView` + strong ETag |
| Service | `ModelAuthoringDraftService` 薄编排，委托 ModelSpec、dbt draft、representation 和 dependency 既有 seam | `EDIT_DRAFT` 只接受 DRAFT；PUBLISHED 必须 `FORK_PUBLISHED`；幂等重放返回同一 draft |
| 数据 | 扩展 `modeling_dbt_implementation_draft` | 新增可空 `model_spec_snapshot jsonb`、`projection_summary jsonb`、`authoring_origin varchar(32)`；不新建台账 |

### 4.2 竖线 B：跨视图保存、校验与原子提交

| 层 | 契约/落点 | 签名要点 |
|---|---|---|
| UI | 同一工具栏“保存草稿 / 校验 / 提交实现” | 两个视图共享 dirty、busy、error、ETag 和成功回执；`Ctrl/Cmd+S` 调保存草稿 |
| Save API | `PUT /api/modeling/model-specs/{id}/authoring-drafts/{draftId}` | body `{expectedEtag,modelSpecSnapshot,files[],activeView:'VISUAL'|'CODE'}`；files 继续服从 128/2 MiB/16 MiB；返回新 ETag |
| Validate API | `POST .../{draftId}/validate` | body `{expectedEtag}`；resp `{validatedChecksum,modelIssues[],codeDiagnostics[],projectionIssues[],dependencyValidation}` |
| Commit API | `POST .../{draftId}/commit` | body `{expectedEtag,validatedChecksum,dependencyChecksum,idempotencyKey}`；resp `{modelRevision,modelChecksum,implementationId,implementationRevision,implementationChecksum,dependencyChecksum,artifactCount,etag}` |
| Service | 同一事务执行 ModelSpec 校验/新 revision、静态 dbt 校验、依赖对账、bundle freeze、implementation/artifact commit、receipt 和 audit | 任一步失败不产生 model/implementation ownership mismatch；过期 pins 返回 409/412，不自动覆盖 |
| 数据 | ModelSpec revision + implementation revision + artifact + draft receipt | candidate 尚未创建；commit 只完成创作修订 |

### 4.3 竖线 C：安全的 visual/code 投影

| 层 | 契约/落点 | 签名要点 |
|---|---|---|
| UI | visual 显示结构化节点和原始代码节点；code 显示同一 bundle | `FULL` 全部结构化；`PARTIAL` 混合；`NONE` 仍允许业务元数据编辑，并将实现作为原始代码节点打开 |
| API | authoring context/draft 内 `projection` | `{coverage,lossless,managedPaths[],rawNodes[],reasons[]}`；每个 node 含 `{nodeId,kind,editable,sourcePath,line?,column?}` |
| Service | 扩展既有 `DbtSqlProjectionParser`、`AdvancedDbtDraftStaticValidator`、`ModelingDbtCompiler` adapter | 不创建第二 parser；歧义、动态宏、无法定位来源时返回 raw node，不猜测改写 |
| 保存围栏 | 受管路径/节点 checksum + bundle checksum | visual 只能改 managed 节点；unmanaged 文件 checksum 必须保持不变，否则 409 `MODEL_AUTHORING_UNMANAGED_FILE_CHANGED` |

### 4.4 竖线 D：构建、质量、发布、物化与治理

| 层 | 契约/落点 | 签名要点 |
|---|---|---|
| UI | 仍使用模型工作台现有“发布”及模型列表物化入口 | 不显示 visual/code 专属生命周期按钮 |
| API/Service | 复用 release candidate、compile/build/test/quality/review/publish、materialization plan、dispatch、retry/rollback | candidate 钉住 authoring commit 返回的 model/implementation/dependency checksums |
| 治理 | 复用 Sprint-93 `CatalogAssetKey`、资产观察、质量、血缘和 serving projection | provenance 不参与 AssetKey；二次物化只新增 candidate/attempt/observation |

## 5. 现状勘察账本（Context Ledger）

本节是本 Sprint 的一次性勘察结果。Task 必须引用账本编号，不得重新做同一轮扫描。

| # | 事实 | 证据（文件:行） |
|---|---|---|
| L01 | 唯一模型工作台已经具有 visual/code 模式按钮与 URL 状态 | `source/dts-platform-webapp/src/pages/data-modeling/prototype/ModelingWorkbenchEditor.tsx:683-694`；Sprint-91 ADR-91-01 |
| L02 | 当前 visual 是否可编辑仍由 representation capability 和 `implementationMode` 共同决定，DBT 模型显示整页只读文案 | `ModelingWorkbenchEditor.tsx:648-655,696-699` |
| L03 | 代码视图仍包含“接管代码实现”及不可逆 transition 流程 | `AdvancedDbtWorkspace.tsx:137-180,450-486` |
| L04 | 模型基础表单仍直接暴露 `DESIGNER_GENERATED / DBT_MANAGED` 选择 | `ModelImplementationBindingFields.tsx:85,203-214` |
| L05 | BUSINESS/TECHNICAL representation 已有单一读取 API，技术表示受 `CATALOG_MAINTAINERS` 保护 | `ModelRepresentationResource.java:41-60` |
| L06 | `ModelVisualizationCapabilityEvaluator` 当前显式按 ownership 分配 `EDIT_VISUAL`、`OPEN_ADVANCED_DBT` 等能力 | `ModelVisualizationCapabilityEvaluator.java:30-72` |
| L07 | dbt draft 已有 create/save/validate/commit REST 与强 ETag | `DbtImplementationDraftResource.java:38,59-95` |
| L08 | draft contract 已钉住 base model/implementation pins、文件上限、validated checksum、dependency checksum 和 commit receipt | `DbtImplementationDraftContract.java:116-176,218-297` |
| L09 | draft 持久化已有租户/计划/模型/actor 范围、幂等键、source bundle、状态、ETag、过期、验证和 commit receipt | `20260802_02_modeling_dbt_implementation_draft.xml:20-102` |
| L10 | draft 文件表已有 path 唯一、checksum、2 MiB 上限和级联删除 | `20260802_02_modeling_dbt_implementation_draft.xml:104-177` |
| L11 | DBT 字段同步已有“PUBLISHED 产生新 DRAFT，历史发布修订不变”的实现先例 | `ModelSpecApplicationService.java:485-555` |
| L12 | 现有 ownership transition 已具备 preview、CAS、幂等和审计，但只接受 DESIGNER→DBT | `ModelImplementationOwnershipTransitionService.java:62-182,203-226`；`ModelLifecycleResource.java:74-107` |
| L13 | 旧 `convert-to-designer-generated` 仍是公开后端入口，新 UI 当前不消费 | `ModelLifecycleResource.java:148-163`；Sprint-91 账本 #8/#21 |
| L14 | implementation save/validate、compile/test/timeline 与发布控制面已经存在 | `ModelLifecycleResource.java:110-188` |
| L15 | `DbtSqlProjectionParser`、`ModelConversionClassifier`、static validator、compiler 和 artifact importer 均已存在，禁止再造 parser/compiler/import owner | `service/etl/DbtSqlProjectionParser.java`；`imports/classifier/ModelConversionClassifier.java`；`AdvancedDbtDraftStaticValidator.java`；`ModelingDbtArtifactImportService.java` |
| L16 | Sprint-91 已建立 ModelSpec 依赖快照与依赖感知物化设计，ModelSpec 依赖是唯一业务事实 | Sprint-91 ADR-91-12～14、F6～F8 |
| L17 | Sprint-93 已把同一模型修订、实现修订、候选、物理 AssetKey、质量和血缘串为治理证据链 | `sprint-93-202608-modeling-governance-evidence-convergence/README.md` §3～4 |
| L18 | 2026-08-13 历史画像为 31 个 ModelSpec：28 DBT、3 DESIGNER；该分布只作为迁移下限，须由 F0/T01 刷新 | Sprint-91 `assets/domain-profile.md` §3 |
| L19 | 最近自动化证明 modeling focused tests、前端 Vitest/tsc/legacy build 可运行；真实 Chrome 95 与本 Sprint 组合草稿样本尚无证据 | Sprint-91 `it/baseline.md`；Sprint-93 `it/evidence/20260818-it07-publication-lineage-automated.md` |
| L20 | 当前权限粒度仍是 `read/write/export` 与 `CATALOG_MAINTAINERS`，本 Sprint 不能假设已有更细 action model | DTS invariant D4；Sprint-93 ADR-93-11 |

### 5.1 开放问题及关闭责任

| ID | 问题 | 是否阻断架构 | 关闭 Task |
|---|---|---|---|
| OQ-01 | 当前运行库各 provenance、implementationMode、draft 状态的真实数量与异常分布 | 不改变 ADR；阻断迁移批次与验收样本 | F0/T01 |
| OQ-02 | 代表性 ZIP/手工/平台生成 bundle 的 `FULL/PARTIAL/NONE` 实测比例 | 不改变统一入口；阻断 projection 验收基线 | F0/T02 |
| OQ-03 | 旧 ownership/convert/dbt-drafts 路由是否存在外部调用方 | 不改变新增 facade；阻断 Contract 阶段 | F5/T01 |

## 6. Gate Registry

状态取值：`PASS | GAP | BLOCKED | PENDING | N/A`。

| Gate | 项目 | 状态 | 证据 | 未过则关联 Task |
|---|---|---|---|---|
| G0 | 交付基线 | GAP | `it/baseline.md`；运行/测试已有近期开源证据，当前 schema/账号/Chrome95/组合样本待刷新 | F0/T01 |
| G0 | 领域与真实数据画像 | GAP | `assets/domain-profile.md`；历史分布可用，当前分布与 projection 样本待实测 | F0/T01、F0/T02 |
| G0 | DTS 领域不变量自检 | PASS | ADR-92-03/04/08/10/11/12 对应 A4、B1、D2/D4、G1 | - |
| G1 | 契约链贯通 | PASS | 本文 §4；无未定义层 | - |
| G1 | UI/UX 与控件矩阵 | PASS | `assets/page-capability-matrix.md`、`assets/button-component-matrix.md` | - |
| G1 | 非功能预算 | GAP | `assets/nfr-budget.md`；阈值已定，当前量级实测待 F0 | F0/T01、F5/T02 |
| G2 | 变更范围与 review | PENDING | 已逐符号 impact；2026-08-20 `detect_changes(all)` 报告 43 个 dirty 文件、326 个变更符号、0 个受影响流程、LOW。共享 worktree 含其他 Sprint 文档，正式提交前仍需按 owned scope 复查 | 提交前复查 |
| G3 | 发布安全 | PENDING | `assets/release-plan.md`；Expand/兼容/回滚步骤已定，尚未演练 | F5/T01、F5/T03 |
| G4 | 可运维性 | PENDING | `assets/runbook.md` 已定义信号和处置，待部署验证 | F5/T03 |
| G4 | DoD/集中 E2E | PENDING | `it/README.md` | F5/T02 |

## 7. Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 |
|---|---|---:|---|---|
| F0 | 交付基线与替代裁决 | 2 | P0 | READY |
| F1 | 统一创作草稿与版本边界 | 3 | P0 | IMPLEMENTED |
| F2 | 来源无关表示与安全投影 | 3 | P0 | IMPLEMENTED |
| F3 | 统一模型工作台交互 | 3 | P0 | IMPLEMENTED |
| F4 | 统一校验提交与生命周期兼容 | 3 | P0 | IMPLEMENTED |
| F5 | 迁移发布与集中验收 | 3 | P0 | IN_PROGRESS |

**Task 统计**：READY=2，IMPLEMENTED=13，IN_PROGRESS=1，PENDING=1，DONE=0，BLOCKED=0，共 17 Task。  
**依赖顺序**：F0 → F1/T01 ∥ F2/T01 → F1/T02 → F2/T02 → F1/T03 ∥ F2/T03 → F3 → F4 → F5。编码全部完成后仅执行一次集中 E2E；失败只针对断点定向重跑。

### 7.1 实施收口（2026-08-20）

- 后端已完成组合草稿、统一 facade、`PUBLISHED` fork、分级投影、rewrite 围栏、canonical 校验、原子 commit 和可回滚迁移 ledger。
- 前端已将 visual/code 收敛到同一 authoring session、草稿状态和工具栏，旧 ownership 产品心智不再暴露。
- 自动化已通过：authoring 后端 164 用例，生命周期 9 类/89 用例，前端 8 文件/88 用例，TypeScript、Biome、Spotless 和生产构建。
- 本轮按约定不执行 E2E、实时迁移、部署或重启；详见 `it/evidence/20260820-automated.md`。

## 8. 追溯矩阵（Traceability）

| 需求 | Feature / Task | 契约或测试 | 证据位置 |
|---|---|---|---|
| R92-01 来源不决定编辑权限 | F2/T01、F3/T01 | capability/source-contract | IT-03、IT-04 |
| R92-02 visual/code 共享同一草稿 | F1/T02、F3/T01、F3/T02 | authoring draft REST + UI dirty state | IT-02～IT-05 |
| R92-03 PUBLISHED 不可原地改 | F1/T03、F3/T02 | `FORK_PUBLISHED` 契约 | IT-02 |
| R92-04 ZIP 可继续视觉精修且不丢 bundle | F2/T02、F2/T03、F3/T03 | projection/managed-path checksum | IT-04、IT-05 |
| R92-05 一个保存/校验/提交心智 | F1/T02、F4/T01、F3/T02 | composite validate/commit | IT-03、IT-06 |
| R92-06 旧接管/回切不再暴露 | F3/T01、F4/T02 | source-contract + compatibility IT | IT-09 |
| R92-07 生命周期和二次物化不分叉 | F4/T03 | candidate pins/materialization regression | IT-07、IT-08 |
| R92-08 权限、CAS、幂等和审计 | F4/T01、F4/T02 | 401/403/409/412 + audit IT | IT-06 |
| R92-09 存量可迁移、可回滚 | F5/T01、F5/T03 | preview/apply/rollback | IT-10 |
| R92-10 Chrome 95 与真实页面验收 | F5/T02 | source-contract/Vitest/build/浏览器 | IT-11 |

## 9. Sprint 完成标准

- [ ] `PUBLISHED` 模型只能查看；用户点击一次“创建新草稿版本”后，在同一 ModelSpec 身份下编辑新 DRAFT。
- [ ] 平台生成、手工代码、dbt ZIP 三类来源均在同一工作台进入 visual/code，来源不造成整页只读。
- [ ] visual/code 共享 draftId、ETag、dirty、保存、校验和提交回执；切换不产生 revision。
- [ ] `PARTIAL/NONE` 投影不猜测结构、不覆盖 unmanaged 文件；原始 bundle checksum 可回溯。
- [ ] 一次 commit 原子产生相互一致的 model/implementation/dependency pins；失败不留下半提交。
- [ ] 现有候选、构建、质量、评审、发布、首次/二次物化和 Sprint-93 治理证据全部复用且身份不变。
- [ ] 旧路由兼容、存量迁移 dry-run/apply/rollback、权限/CAS/幂等/审计和回滚演练有真实证据。
- [ ] Chrome 95、现代 Chrome、桌面与窄视口的空/加载/错误/成功四态完成集中 E2E，console/network 无未解释异常。
- [ ] `gitnexus_detect_changes()` 证明未新增平行 parser、模型台账、依赖台账或发布控制面。

## 10. 非目标

- 不新增数据建模菜单、独立 SQL IDE、独立 dbt 文件页或第二个模型工作台。
- 不承诺任意 SQL、动态宏、adapter 特有表达式都能无损转换成纯表单；无法结构化的部分使用原始代码节点。
- 不允许 `PUBLISHED` 原地修改，也不覆盖历史 candidate、artifact、物化和治理证据。
- 不新建 parser、模型/依赖/artifact/发布台账；不得绕过既有 validator、compiler、release candidate 或 `CatalogAssetKey`。
- 不在本 Sprint 接入 ODS、创建业务数据、修改 `ODS → DWD DIM/FACT → DWS → ADS` 分层语义。
- 不扩展多租户产品模型或 `read/write/export` 之外的权限动作集。
- 不在首轮发布删除 `implementationMode`、旧 REST 路由或旧数据库列；物理 Contract 需独立消费盘点与灰度证据。
- 不在 Feature 未全部实现前执行 E2E；自动化模块测试不等价于 Sprint DONE。
