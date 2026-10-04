# Sprint-91: 数据建模双模式工作台收敛

**时间盒**: 2026-08-13 ～ 2026-08-28
**状态**: IN_PROGRESS（F1～F4、F6～F8 已完成源码实现与聚焦自动化；F0/T03 真实样本、F5 集中发布/E2E 与 Sprint-93 治理对账待执行）
**类型**: Modeling Workflow / UI Productization / Lifecycle Repair
**目标**: 建模人员在同一个模型工作台中，以“可视化模式 / 代码模式”维护同一份 ModelSpec，并可不依赖 ZIP 逐表完成 `已接入 ODS → DWD DIM/FACT → DWS → ADS` 的依赖定义、实现、单表/批量物化、质量和发布；ZIP 只是另一种 authoring adapter，三种入口最终复用同一套版本、依赖、候选、物化和治理证据。

> **2026-08-13 架构复核修订**：首版设计有三处“以为可复用、实际不可复用”的断裂，已在本版修正。要点：接管必须复用 dbt bundle freeze + artifact import seam（否则接管后无法编译）；dbt 身份一律服务端派生；DBT→DESIGNER 回切因缺少可用的子集规则而移出本 Sprint。修订依据见「架构复核结论」与账本 #16～#21。

> **2026-08-19 后续裁决（SUPERSEDED_BY_SPRINT_92）**：本 Sprint 已完成的单一工作台、Monaco、bundle、CAS、依赖、候选和物化能力继续有效；但 ADR-91-03/09 及 F2/F3 的“显式接管后不可回退 / DBT 可视整页只读”只是当时的历史实现，不再作为最终产品语义。`Sprint-92` 将 provenance 与编辑权限分离，以同一组合草稿支持 visual/code 双向维护。本文保留原决策与证据，不倒改历史；后续实现与最终验收以 `../sprint-92-202608-unified-model-authoring-convergence/README.md` 为准。

## 背景与价值

历史高级 SQL 建模页和 dbt 文件页已经退役；当前高级 dbt 能力以内嵌工作区重新实现，但入口仍表现为“代码模式”按钮与“高级 dbt 工作区”按钮并存。普通模型点击代码模式时只在进入后才得到能力拒绝，现有模型的“实现维护方式”又不可切换，用户无法从页面理解“查看代码”“接管代码实现”“发布模型”三者的区别。

本 Sprint 不恢复旧页面，而是把现有模型工作台收敛为单一入口：

1. “可视化 / 代码”是同一模型的表现视图；
2. `DESIGNER_GENERATED / DBT_MANAGED` 是实现所有权，只有显式、可审计的转换命令才能改变；
3. 代码提交只生成新的实现修订，物化与发布继续走既有 release candidate 控制面。

2026-08-18 复核发现，双模式本身并不足以形成真实数仓链：普通模型虽已能选择物理来源或上游模型，但维度引用尚未进入可执行依赖；工作台只回显已有字段映射，不能从空配置形成转换；首次 dbt 草稿对 ModelSpec 依赖不敏感；单表物化缺少依赖闭包预览。为避免“页面可编辑、下游仍孤岛”，本 Sprint 增加 F6～F8，并规定所有 Feature 共用 F0/T03 的同一条链路样本。

规范业务链路固定为：

`源系统 → 数据集成 → ODS →（技术 STG）→ DWD DIM + DWD FACT → DWS → ADS → 指标/BI/数据服务`

- ODS 由数据集成进入平台，在建模工作台只做 source binding，不重复建 ODS 表或装数。
- STG 是 dbt 技术节点，不创建业务 ModelSpec，不登记为独立业务资产。
- DIM 与 FACT 都属于 DWD；FACT 可同时使用 ODS/上游事实作为基础输入并引用 DIM 固定修订。
- DWS/ADS 通过固定 ModelSpec/implementation pins 依赖上游，不按名称或“当前最新版”浮动关联。

## 架构复核结论（2026-08-13）

对首版设计的三条“复用点”做了源码核对，结论如下。这一节是后续 ADR 与 Task 修订的依据，实施期不得绕过。

| # | 首版断言 | 核对结果 | 修订 |
|---|---|---|---|
| A | 接管时把生成的 SQL/SCHEMA 存为首个 dbt source bundle | **不成立**。DESIGNER 编译**无条件**产出 3 文件（`stg_*.sql` ephemeral、`*.sql`、`*.yml`），制品类型为 `{STG_SQL, SQL, SCHEMA, TEST}`；而 `compile()` 对 DBT_MANAGED 要求类型集合**恰好**是 `{SQL,SCHEMA}` 或 `{SQL,SCHEMA,CONFIG}`。带上 stg 则编译被拒，丢掉 stg 则主模型 `{{ ref('stg_x') }}` 在 dbt 运行期解析失败——接管后的模型无法编译、无法物化、无法发布 | 改为复用既有 draft commit 的制品映射：只为被拥有节点产 `SQL/SCHEMA/CONFIG`，stg 随整包冻结进 `CONFIG`。见 ADR-91-04 |
| B | 回切复用 `DbtCompatibilityEvaluator` 的安全子集规则 | **不成立**。该类评估的是 ZIP 包级 runtime certification / adapter 支持度，入参是 `ModelPackage`，不做 SQL→字段映射判断。真正的子集规则是 `ModelConversionClassifier`，但它①要求 `dts_semantic.yml` 提供的 `SemanticMetadata`（现网模型语义在 ModelSpec 里，不在 dbt 文件里）②`MACRO` 命中 `{{`、`CONSERVATIVE_COMPLEXITY` 命中 `with`/`(select`/`::`——而编译器生成的 SQL 恰恰全部包含。**刚接管、一字未改的模型回切预检必然 `allowed=false`** | 回切移出本 Sprint。见 ADR-91-09 与 `assets/sprint-92-back-conversion-handoff.md` |
| C | 代码模式的可见性以既有 `allowedActions` 为唯一依据 | **不成立**。`ModelVisualizationCapabilityEvaluator` 对 `TECHNICAL + 非 DBT_MANAGED` 直接返回 `BLOCKED`，`technicalActions()` 只可能返回 `OPEN_ADVANCED_DBT`，不存在 `OPEN_DBT_PREVIEW`。fail-closed 规则下 DESIGNER 模型的代码模式永远不可见，F2 整条竖线悬空 | 新增后端 capability 扩展 Task。见 ADR-91-10、F1/T03 |

## 架构决策记录 (ADR)

| 决策 | 选择 | 约束 |
|---|---|---|
| ADR-91-01 单一页面 owner | 复用 `/data-modeling/dimensions/workbench`，在模型编辑器内增加“可视化模式 / 代码模式”分段控件 | 不新增菜单、路由或独立 dbt 文件页；旧 `/studio/sql-modeling`、`/modeling/dbt-files` 继续兼容重定向 |
| ADR-91-02 视图与所有权分离 | `view=visual\|code` 只改变展示；实现所有权仍由 `implementationMode` 与 implementation revision 决定 | 切换 Tab 不落库、不创建修订、不覆盖 SQL |
| ADR-91-03 显式所有权转换 | 新增统一 ownership-transition 预检/提交 seam；模型修订与实现修订在一个事务中转换 | 预检摘要、ETag、幂等键、权限与审计缺一不可；任一步失败不得留下 ownership mismatch |
| ADR-91-04 接管制品与既有 draft commit 同构 | 生成预览复用 `ModelingDbtCompiler`；接管时把 3 个生成文件与确定性 `dbt_project.yml` 组成 4 文件 canonical project，经 `AdvancedDbtDraftStaticValidator.validate` 后再调用 `DbtProjectBundleManifest.freeze`，最后复用 `ModelingDbtArtifactImportService` 落 `SQL/SCHEMA/CONFIG` | `CONFIG` 保存完整 4 文件 bundle，`stg_*.sql` 不单独占 artifact type；禁止复制 project-file 生成规则、parser、台账或发布控制面 |
| ADR-91-05 兼容深链 | 新协议为 `?modelSpecId={id}&view=visual\|code`；既有 `?open=advanced` 规范化为 `view=code` | 刷新、复制链接和浏览器前进/后退保持选中模型及视图 |
| ADR-91-06 编辑器复用 | 复用现有 `@monaco-editor/react` 与 `configureMonacoLoader`，为 dbt SQL/Jinja/YAML 提供模型级编辑体验 | 不复制 SQL IDE 的执行、结果面板与多 Tab 控制面；**必须路由级懒加载，不得进入建模页首屏包**；沿用主线程模式，不引入 Monaco worker；Chrome 95 必须通过 |
| ADR-91-07 发布边界 | 代码模式只负责保存、校验、提交实现；“发布”仍由模型工作台现有发布入口触发统一候选链 | 不在代码编辑器内增加第二套 compile/build/publish 状态机 |
| ADR-91-08 dbt 身份服务端派生 | 接管沿用 `ModelImplementationExecutionPlanner.systemManagedDbtProjectKey/UniqueId`（`dts` / `model.dts.model_<uuid>`）；transition 请求体**不接受** `projectKey`、`dbtUniqueId` | 物化 selector 由 uniqueId 第三段推导，客户端改名会让物化目标漂移；且 `target()` 在 `base != null` 时直接拒绝 projectKey 变更（`DBT_DRAFT_PROJECT_IDENTITY_UNSUPPORTED`）。uniqueId 唯一约束不得由客户端触碰 |
| ADR-91-09 接管在本 Sprint 不可逆 | 只做 `DESIGNER_GENERATED → DBT_MANAGED` 单向接管；确认框必须明示“本版本不可回退” | 回切所需的安全子集规则在现网**不存在**（复核结论 B）；伪造一个“看起来能回切”的入口比不提供更有害。回切前置条件与移交范围见 `assets/sprint-92-back-conversion-handoff.md` |
| ADR-91-10 DESIGNER 的 TECHNICAL 只读能力 | 扩展 `ModelVisualizationCapabilityEvaluator`，为 `TECHNICAL + DESIGNER_GENERATED` 返回新能力 `DESIGNER_DBT_PREVIEW` 与动作 `OPEN_DBT_PREVIEW` | 这是后端 capability 契约扩展，由 F1/T03 拥有；`ADVANCED_DBT_IMPLEMENTATION` 的语义与既有断言不得改变，只读能力不得携带任何写动作 |
| ADR-91-11 规范分层与模型边界 | 业务主链为 `ODS → DWD DIM/FACT → DWS → ADS`；ODS 是已接入 source binding，STG 是技术节点 | 不在建模工作台重复建 ODS/装数；不把 STG 建成 ModelSpec/业务资产；DIM 与 FACT 均落 DWD |
| ADR-91-12 唯一依赖快照 | ModelSpec 的 `sourceRefs + dependsOn + dimensionRefs` 是业务事实源；dbt 解析结果只做实现证据对账，统一产出 immutable dependency snapshot/checksum | ZIP、手工 dbt、DESIGNER 必须调用同一 resolver；不新增依赖表、parser 或客户端自报依赖 |
| ADR-91-13 三种 authoring adapter 汇合 | 手工 UI+dbt、可视化生成、ZIP inspect/preview/apply 只改变实现输入方式 | 三者保持同一 ModelSpec identity、依赖图、dbt uniqueId、candidate pins、target selector 与 CatalogAssetKey；ZIP 不是发布/物化旁路 |
| ADR-91-14 单表物化是依赖计划 | “物化当前表”先预览依赖闭包：精确且已验证的上游 `REUSE`，缺失上游 `BUILD`，stale/冲突 `BLOCK` | candidate 创建时服务端重算并核对 plan checksum；不得用裸 `+selector` 或前端删依赖绕过 pins/质量/发布围栏 |
| ADR-91-15 分两层补齐手工能力 | P0 先完成 ModelSpec 依赖 + 手工 dbt + 统一物化；同 Sprint 的 F7 再补齐结构化映射/join/aggregation 生成 | 未完成 F7 不得把“可视化模式”宣称为完整 DWD/DWS/ADS 设计器；超出白名单显式接管代码 |

## 端到端契约链 (Vertical Slice)

### 竖线 A：打开模型并切换表现视图

| 层 | 契约/落点 | 签名要点 |
|---|---|---|
| UI | 模型工作台字段区顶部 Segmented：“可视化模式 / 代码模式” | URL `modelSpecId: UUID`、`view: visual\|code`；旧 `open=advanced` 只做一次兼容归一化 |
| API | `GET /api/modeling/model-specs/{id}/lifecycle`；`GET /api/modeling/model-specs/{id}/representations` | **注意路径为复数 `representations`**；query 必填 `modelRevision:int` 与 `representationScope:BUSINESS\|TECHNICAL`，可选 `implementationRevision:int`。TECHNICAL scope 需 `CATALOG_MAINTAINERS`，否则不返回技术投影 |
| Service | `ModelRepresentationService` + `ModelVisualizationCapabilityEvaluator`（经 F1/T03 扩展） | `allowedActions` 是控件可用性的唯一依据，不再只判断“模型已保存”。DESIGNER 的 TECHNICAL scope 由 `OPEN_DBT_PREVIEW` 授权只读 |
| 数据 | `modeling_model_spec`、`modeling_model_implementation` | 只读，不产生新修订 |
| 迁移 | 无 | 不新增表/列 |

### 竖线 B：可视化模型查看生成代码并显式接管

| 层 | 契约/落点 | 签名要点 |
|---|---|---|
| UI | `DESIGNER_GENERATED` 模型进入代码模式：先只读预览，主动作“接管代码实现” | 二次确认明确说明：接管后可视化字段转为只读、**本版本不可回退**、发布入口不变 |
| API | `GET /api/modeling/model-specs/{id}/implementation/dbt-preview` | query: `modelRevision:int`、`implementationRevision:int`；resp: `{modelRevision, modelChecksum, implementationRevision, implementationChecksum, ownership, files:[{path,content,checksum,nodeKind,artifactTypes[]}], previewChecksum, readOnly:true}`。**`files` 必含 3 项**（`stg_*.sql`/`*.sql`/`*.yml`），前端按 `nodeKind` 区分主节点与系统 STG |
| API | `POST /api/modeling/model-specs/{id}/implementation/ownership-transitions/validate` | headers: `If-Match`、`If-Match-Implementation`；严格 body: `{targetOwnership:'DBT_MANAGED'}`；resp: `{allowed,reasons,previewChecksum,sourceOwnership,targetOwnership,baseModelRevision,baseImplementationRevision,previewFileCount:3,bundleFileCount:4,reversible:false}` |
| API | `POST /api/modeling/model-specs/{id}/implementation/ownership-transitions` | headers 同上；严格 body: `{targetOwnership:'DBT_MANAGED',previewChecksum,idempotencyKey}`——未知字段（包括 `projectKey`/`dbtUniqueId`）返回 400；resp: `{transitionId,model,implementation,artifactTypes:['SQL','SCHEMA','CONFIG'],previewFileCount:3,bundleFileCount:4,sourceOwnership,targetOwnership}` |
| Service | 新增薄编排 seam；复用 canonical project 静态校验、`DbtProjectBundleManifest.freeze` 与 `ModelingDbtArtifactImportService.importArtifacts` | 同事务：锁定 pins → 编译 3 文件 → 补确定性 `dbt_project.yml` → 静态校验 4 文件 project → 建 DBT implementation（`ACTIVE/GENERATED` + 单一 DBT generator config）→ 冻结 bundle → 导入 `SQL/SCHEMA/CONFIG` → receipt + audit |
| 数据 | 复用既有 model spec / implementation revision / dbt artifact / command receipt / audit | 不新增表、不新增 artifact 类型；transition id 写既有幂等回执与审计 payload |
| 迁移 | 无 | 数据结构已具备 |

> **接管后首个草稿的顺序约束**：`AdvancedDbtDraftStaticValidator.nodes()` 把 ephemeral stg 也标为 `nodeKind="MODEL"`，而 `DbtImplementationDraftService.target()` 在 `base == null` 时要求恰好一个 MODEL 节点。因此接管**必须先创建 implementation**，使后续建草稿时 `base != null`、`target()` 按 `dbtUniqueId` 过滤，stg 自然退为普通可编辑 bundle 文件。顺序颠倒会撞 `DBT_DRAFT_MODEL_SELECTION_UNSUPPORTED`。

### 竖线 C：手工 dbt 模型的可视化只读投影

| 层 | 契约/落点 | 签名要点 |
|---|---|---|
| UI | `DBT_MANAGED` 模型的可视化模式**恒为只读**投影：基本信息、字段、来源、依赖 | 不提供“转为可视化维护”按钮（ADR-91-09）；顶部状态“当前由代码维护”，并说明回切能力的现状与去向 |
| API | 复用 `GET .../representations?representationScope=BUSINESS` | DBT_MANAGED 得到 `BUSINESS_VISUAL_READ` + `allowedActions=['OPEN_VISUAL']`；投影不可信时为 `BLOCKED` + `capabilityReasons[]` |
| Service | `ModelRepresentationService`（**零改动**） | 本竖线不引入任何写路径 |
| 数据 | 只读 | 不产生修订 |
| 迁移 | 无 | - |

> 旧 `POST .../convert-to-designer-generated` **原样保留、不改、不委托**。它当前不解析 SQL，只翻转 ownership 并接受调用方传入的 `SaveImplementationCommand`；本 Sprint 既不加固也不下线它，避免在没有子集规则的前提下制造“已治理”的假象。

### 竖线 D：实现提交后沿用统一发布

| 层 | 契约/落点 | 签名要点 |
|---|---|---|
| UI | 代码模式“保存文件 → 校验 → 提交实现”；返回模型设计后使用现有“发布” | 不增加代码模式专属发布按钮 |
| API | 复用 dbt draft create/save/validate/commit 与现有 release candidate API | 每个请求继续携带 expected ETag、validated checksum、idempotency key |
| Service | `DbtImplementationDraftService` → `ModelLifecycleService` → release candidate owner | 发布候选必须钉住最新 model + implementation revision/checksum |
| 数据 | 复用 draft/artifact/release candidate 表 | 发布失败不回滚已提交实现，只允许重试/新候选/显式回滚 |
| 迁移 | 无 | - |

### 竖线 E：不依赖 ZIP 的手工全链路实现

| 层 | 契约/落点 | 签名要点 |
|---|---|---|
| UI | 同一模型工作台维护「基础来源 / 维度引用 / 上游模型 / 代码实现」 | FACT 可同时表达 ODS 基础来源与 DIM 固定修订；DWS/ADS 选择上游固定修订；不新增手工建模页面 |
| API | 复用 ModelSpec save/confirm 与 dbt draft create/save/validate/commit | 所有写请求携带 ETag；依赖由服务端解析为 `ModelImplementationDependencySnapshot`，commit 钉住 `dependencyChecksum` |
| Service | F6 唯一 dependency resolver + 既有 static validator/draft commit | 对账 ModelSpec `sourceRefs/dependsOn/dimensionRefs` 与实现 `source()/ref()`；未声明、缺失、stale、成环 fail closed |
| 数据 | 复用 model revision、implementation config/artifact、candidate pins | 不新增依赖台账；STG/proxy 只存在 canonical bundle，不成为业务模型或资产 |
| 下游 | F8 planner、F5 release、Sprint-93 governance | 三者消费相同 model/implementation/dependency pins |

### 竖线 F：可视化配置生成同一 dbt 实现

| 层 | 契约/落点 | 签名要点 |
|---|---|---|
| UI | 结构化字段映射、cast、filter、dedup、join、groupBy、aggregation | 只允许白名单结构，不接受自由 SQL 表达式；来源/字段必须命中 F6 快照 |
| API | 复用 ModelSpec implementation save/preview/commit seam | designer settings、dependency、model pins 一并参与 preview/implementation checksum |
| Service | 扩展既有 `ModelingDbtCompiler` | source/ref 由 dependency snapshot 生成；产物经同一 validator/freeze/import seam 落 `{SQL,SCHEMA,CONFIG}` |
| 数据 | 同一 implementation revision/artifact owner | 不新增 designer 私有执行记录或发布控制面 |
| 下游 | 与手工 dbt/ZIP 共用 F8/F5 | 允许 SQL 文本不同，不允许依赖图、目标身份、层级、粒度、资产身份分叉 |

### 竖线 G：依赖感知的单表/批量物化与治理交接

| 层 | 契约/落点 | 签名要点 |
|---|---|---|
| UI | 模型列表单条「物化/再次物化」和多选「批量物化」打开同一 preview | 展示 requested、按拓扑排序的 BUILD/REUSE 和 blockers；默认 `WITH_MISSING_UPSTREAMS` |
| API | `POST /api/modeling/plans/{planId}/materialization-plans/preview`；候选创建可携带 `materializationPlanChecksum` | 客户端只提交目标与策略，不提交可篡改的 ordered entries；create 时服务端重算 |
| Service | F8 planner 编排 F6 pins、既有 relation observation 与 release candidate | 精确 verified relation 才 REUSE；缺失进入同一 candidate BUILD；stale/cycle/source unavailable fail closed |
| 执行 | 复用 build/test/quality/review/publish、dispatch、retry/rollback | 一个 batch correlation；每个 BUILD 节点独立 entry/attempt/observation；显式命令推进，不自动审批/发布 |
| 治理 | 向 Sprint-93 提供 pins、artifact、relation、quality、publication、lineage correlation | 二次物化只新增候选/执行/观察历史，不复制 ModelSpec、implementation 或 CatalogAssetKey |

## 现状勘察账本 (Context Ledger)

一次勘察事实如下，下游 Task 直接引用编号，禁止重复扫描。

| # | 事实 | 证据 |
|---|---|---|
| 1 | 当前模型工作台已使用 `modelSpecId`、`open` URL 状态，并把高级 dbt 工作区内嵌在同一页面 | `ModelingWorkbenchPage.tsx:78-84,480-485,586-617` |
| 2 | 字段区“代码模式”只判断 `Boolean(selectedModel)`，未消费 TECHNICAL capability | `ModelingWorkbenchEditor.tsx:105-142`；`ModelFieldEditorTable.tsx:228-244` |
| 3 | 工具栏还存在第二个“高级 dbt 工作区”入口 | `ModelingWorkbenchEditor.tsx:739-746` |
| 4 | 已保存模型的“实现维护方式”选择器被禁用，当前 UI 无显式 ownership transition | `ModelImplementationBindingFields.tsx:197-218` |
| 5 | capability evaluator 已明确把实现所有权与展示能力分开；DBT_MANAGED 可 `OPEN_ADVANCED_DBT`，DESIGNER_GENERATED 可 `EDIT_VISUAL` | `ModelVisualizationCapabilityEvaluator.java:11,30-60`；`ModelRepresentationService.java:473-484` |
| 6 | 高级 dbt 草稿 REST 已真实支持 create/save/validate/commit，且要求 `CATALOG_MAINTAINERS` | `DbtImplementationDraftResource.java:36-96` |
| 7 | 高级工作区当前使用 `<textarea>`，已有文件列表、ETag 冲突、校验和提交状态 | `AdvancedDbtWorkspace.tsx:86-195,325-455` |
| 8 | 现有 dbt→可视化接口存在但前端零消费，且要求 canonical ModelSpec 先变为 DESIGNER_GENERATED | `ModelLifecycleResource.java:107-122`；`ModelLifecycleService.java:327-385` |
| 9 | `claim` 要求 command ownership 与 canonical ModelSpec 完全一致，不能独立承担 ownership conversion | `ModelLifecycleService.java:389-440` |
| 10 | compile 已按 ownership 共用一套链路：DESIGNER 由 compiler 生成制品，DBT_MANAGED 使用已提交 SQL/SCHEMA | `ModelLifecycleService.java:443-483` |
| 11 | 草稿限制已统一为最多 128 文件、单文件 2 MiB、总计 16 MiB，并拒绝敏感文件名 | `DbtImplementationDraftContract.java:23-58,110-135` |
| 12 | Webapp 已依赖 Monaco 且有共享 loader；当前 SQL IDE 编辑器不可整套复制造成第二控制面 | `package.json:38`；`components/monaco/configureMonaco.ts:1-10`；`components/sql-ide/editor/SqlEditor.tsx:1-45` |
| 13 | 2026-08-13 实测：31 个 canonical ModelSpec；DBT_MANAGED 28、DESIGNER_GENERATED 3；当前 implementation ownership mismatch=0；dbt 草稿 COMMITTED=50、VALIDATED=1 | `assets/domain-profile.md` §3 |
| 14 | 运行实例容器健康，平台健康端点返回 UP；前端兼容构建已通过，真实登录与 Chrome 95 浏览器验收尚未在本轮完成 | `it/baseline.md` |
| 15 | 历史高级 SQL 页和 dbt 文件页分别在 `382d0e87f`、`dbe773050` 删除；旧发布按钮并未形成有效统一发布链 | Git 历史；本 Sprint 不恢复旧文件 |
| 16 | DESIGNER 编译**无条件**产 3 文件（`stg_<name>.sql` ephemeral、`<name>.sql`、`<name>.yml`），主模型体内为 `{{ ref('stg_<name>') }}`；输出目录含 `/v{modelRevision}/i{implementationRevision}` | `ModelingDbtCompiler.java:92-113` |
| 17 | 上述文件映射出的制品类型为 `{STG_SQL, SQL, SCHEMA, TEST}`；`compile()` 对 DBT_MANAGED 要求类型集合**恰好**为 `{SQL,SCHEMA}` 或 `{SQL,SCHEMA,CONFIG}`，否则抛 `MODEL_DBT_IMPORT_REQUIRED` | `CanonicalModelLifecycleCompilerAdapter.java:79-83`；`ModelLifecycleService.java:462-476` |
| 18 | 既有 draft commit 的制品映射只为被拥有节点产 `SQL/SCHEMA/CONFIG`，其中 `CONFIG` 是 `.dts/dbt-project-bundle.json` 冻结的整包清单——**恰好落在 compile 门禁的接受集合内** | `DbtImplementationDraftService.java:819-852`；`DbtProjectBundleManifest.freeze` |
| 19 | 静态校验器把 ephemeral stg 也标为 `nodeKind="MODEL"`；首个草稿（`base == null`）要求恰好一个 MODEL 节点，`base != null` 时按 `dbtUniqueId` 过滤且拒绝 projectKey 变更 | `AdvancedDbtDraftStaticValidator.java:464`；`DbtImplementationDraftService.java:781-800` |
| 20 | 回切的安全子集规则在现网**不存在**：`DbtCompatibilityEvaluator` 只做 ZIP 包级 runtime certification；`ModelConversionClassifier` 依赖 `dts_semantic.yml` 的 `SemanticMetadata`，且 `MACRO`/`CONSERVATIVE_COMPLEXITY` 必然命中编译器生成的 `{{ }}`、`with`、`(select`、`::` | `DbtCompatibilityEvaluator.java:35-90`；`ModelConversionClassifier.java:37-42,96-117,124-150` |
| 21 | `convertToDesignerGenerated` 不解析 SQL，只翻转 ownership + 接受调用方传入的 `SaveImplementationCommand`，并强制把身份重置为 `systemManagedDbtProjectKey/UniqueId`；selector 由 uniqueId 第三段推导并被物化 runtime spec 消费 | `ModelLifecycleService.java:326-385`；`ModelImplementationExecutionPlanner.java:62-71,180-195`；`ModelMaterializationRuntimeSpecService.java:191,281` |
| 22 | 表示 API 实际路径是 `GET /{id}/representations`（复数），`modelRevision` 与 `representationScope` 为必填 query，TECHNICAL scope 需 `CATALOG_MAINTAINERS` | `ModelRepresentationResource.java:41-60` |
| 23 | 仓库内**无** `MonacoEnvironment`/`getWorker` 配置，Monaco 运行在主线程；`monaco-editor@0.52` 目前只被 sql-ide 引用，建模页首屏尚未包含 | 全仓 grep 为空；`package.json:38,68`；`components/sql-ide/editor/SqlEditor.tsx:1-9` |
| 24 | `DbtProjectBundleManifest.freeze` 不能直接消费编译器的 3 文件；它要求 `ValidatedProject`，而静态校验与草稿恢复均要求 bundle 内存在 `dbt_project.yml` | `AdvancedDbtDraftStaticValidator.java:57-99`；`DbtImplementationDraftService.java:295-320,943-950,960-998` |
| 25 | `ModelingDbtArtifactImportService` 要求 implementation 为 `ACTIVE + DBT_MANAGED + GENERATED`，且 `inputs_json` 恰有一个 DBT generator，其 `projectKey/dbtUniqueId` 与 implementation 一致 | `ModelingDbtArtifactImportService.java:81-120` |
| 26 | 发布状态机保留维护、评审、发布三种 duty 与显式命令；当前 interim policy 允许具备 operator duty 的所级管理员从 `QUALITY_PASSED/REVIEW_PENDING` 走自服务发布，若实际进入独立评审则继续强制提交者/评审者/发布者分离 | `ReleaseDutyResolver`；`ModelLifecycleContract.DeliveryAction.isAllowedFor`；`ModelReleaseCandidateService.auditForTransition` |
| 27 | ownership transition、3 文件预览、4 文件冻结 bundle、严格字段解码、专用 CAS、幂等回执和审计动作已实现；modeling 聚焦测试 147/147 通过 | `ModelImplementationOwnershipTransitionServiceTest` 等 10 个聚焦测试类；`it/baseline.md` |
| 28 | 双模式、只读投影、显式接管、Monaco 懒加载、诊断适配及 409/412 写锁已实现；Vitest 5 文件 15 条、TypeScript 检查和 legacy browser build 通过 | `it/baseline.md` |
| 29 | 既有 release candidate 聚焦回归 80 条中 79 条通过；`builtCandidateCannotBeCancelled` 暴露基线语义冲突：状态机允许 `BUILT → CANCELLED`，旧测试仍期望禁止。相关源文件未被本 Sprint 修改 | `ModelReleaseCandidateServiceTest:840-866`；`ModelLifecycleContract.DeliveryStatus.transitions()` |
| 30 | 当前实现输入选择器把 `PHYSICAL_ASSET` 与 `UPSTREAM_MODEL` 作为基础来源二选一；维度引用是独立业务关系，但尚未进入同一可执行依赖快照 | `ModelImplementationBindingFields.tsx`；ModelSpec `dimensionRefs` |
| 31 | 工作台保存会透传既有 `implementationBase.fieldMappings`，但当前 UI 没有从空状态创建字段映射的控件 | `modelWorkbenchService.ts`；`ModelImplementationBindingFields.tsx` |
| 32 | 当前 compiler projection 主要消费 implementation inputs；`dimensionRefs` 未被投影为可执行 source/ref，因此 FACT 的 ODS+DIM 关系可能只停留在 ModelSpec | `ModelSpecCompilerProjection.projectImplementationSources` |
| 33 | 首次手工 dbt 草稿保存为 `InputMode.GENERATED` + 单一 DBT generator，未形成显式的 source/upstream/dimension 固定依赖快照 | `DbtImplementationDraftService.commit` |
| 34 | 首次草稿目标选择要求恰好一个 MODEL；受管 proxy/STG 若与 owned target 混在同一 bundle 会触发 `DBT_DRAFT_MODEL_SELECTION_UNSUPPORTED` | `DbtImplementationDraftService.target`；账本 #19 |
| 35 | 静态 validator 已能识别 dbt dependencies，但 commit 尚未将解析结果与 ModelSpec `sourceRefs/dependsOn/dimensionRefs` 做严格双向对账 | `AdvancedDbtDraftStaticValidator`；`DbtImplementationDraftService.commit` |
| 36 | 物化 repository 已能同时读取 `dependsOn` 与 `dimensionRefs` 的固定 artifact，并要求同环境/目标存在精确 verified relation；缺失时返回 `MODEL_UPSTREAM_MATERIALIZATION_REQUIRED` | `ModelMaterializationBuildRepository.loadPinnedDependencyArtifacts` |
| 37 | 现状因此只能依赖人工拓扑顺序或同一候选中恰好包含全部上游；产品尚无 BUILD/REUSE/BLOCK 依赖闭包预览，也无法解释“物化当前表”将执行哪些上游 | 物化 UI、candidate create 与 repository 调用边界复核 |
| 38 | source availability guard 主要沿 implementation inputs 检查；`GENERATED` 路径可能跳过只存在于 ModelSpec 的 ODS binding，导致两种 ownership 围栏不一致 | implementation execution/source availability guard 复核 |
| 39 | 既有 `ModelingDbtCompiler` 已支持字段映射、cast、join、dedup 的部分结构，但尚不具备完整 filter/groupBy/aggregation 产品契约；不得把现状描述为完整可视化 DWS/ADS 设计器 | `ModelingDbtCompiler` settings/compile 分支复核 |
| 40 | PJM 手册曾写“普通模型无来源/上游编辑器”，与当前 UI 已有基础选择器的事实不完全一致；真实缺口是维度可执行依赖、从空映射、转换白名单和依赖计划 | `worklog/v2.2.3/s10/v4/pjm/dm/README.md`；F6/F7/F8 范围 |

## 2026-08-13 实施与验证证据

| 范围 | 结果 | 证据/边界 |
|---|---|---|
| 后端核心竖线 | PASS | 10 个聚焦测试类共 147 条全部通过；覆盖 capability、preview、严格 REST、ModelSpec wrapper、implementation CAS、bundle/artifact import、幂等与审计 payload |
| 审计字典 | PASS | 三份 runtime mirror 一致；`AuditActionCatalogResourceTest` 5/5 通过。`services/dts-admin/init` 是旧版非 runtime mirror，本 Sprint 不改 |
| 前端双模式与编辑器 | PASS | Vitest 5 文件 15 条、`tsc --noEmit`、`pnpm build` 均通过；构建产物中 `DbtCodeEditor`/Monaco 为独立懒加载 chunk |
| Chrome 95 静态门禁 | PASS_WITH_GAPS | legacy build 与禁用 API/CSS 扫描通过；尚缺目标 Chrome 95 登录态下的 console/network/screenshot smoke，不能据此宣称真实浏览器通过 |
| 统一发布回归 | BLOCKED | 80 条聚焦测试中 1 条既有状态机/测试语义冲突；真实显式命令 `PUBLISHED` 与物理 `ONLINE` 仍未执行 |
| 变更范围 | PASS_WITH_GAPS | GitNexus `detect_changes(scope=all)` 为 LOW、无 affected process；新增未跟踪文件尚未进入索引，以聚焦测试和构建补证 |

## Gate Registry

| Gate | 项目 | 状态 | 证据 | 未过则关联 Task |
|---|---|---|---|---|
| G0 | 交付基线 | GAP | `it/baseline.md` P2/P6；P7 自动化构建已关闭 | F0/T01 |
| G0 | 编译产物与子集实测 | PASS_WITH_GAPS | P9 聚焦编译/bundle/transition 测试通过；真实库接管样本仍待 F0/T01 登录路径 | F0/T02 |
| G0 | 手工全链路样本 | GAP | 链路对象/依赖/清理契约已定义，真实 ODS source binding 与 UI 样本未归档 | F0/T03 |
| G0 | 领域与数据画像 | PASS | `assets/domain-profile.md` | - |
| G0 | DTS 不变量自检 | PASS | ADR-91-01/03/04/07/08/09/10/11/12/13/14/15 | - |
| G1 | 契约链贯通 | CODE_COMPLETE | 双模式 bundle、统一 dependency snapshot、结构化转换和物化 plan checksum 已接入同一 ModelSpec/implementation/candidate 链 | F6/T01～T03、F7/T01～T03、F8/T01～T03 |
| G1 | 非功能预算 | IN_PROGRESS | 64 根/256 闭包、有界批量查询和前端懒加载已落实；真实延迟、事务、浏览器与发布预算待集中验证 | F8/T01、F5/T02 |
| G2 | 变更范围守卫 | PASS_WITH_GAPS | 预编辑 impact 已执行；最终 detect changes=LOW、0 affected process，新文件待纳入索引 | - |
| G3 | 发布安全 | IN_PROGRESS | dependency/plan checksum、来源围栏与二次物化身份边界已实现；发布、retry/rollback 与回滚演练待 F5 | F8/T03、F5/T02 |
| G4 | 可运维性 | PENDING | 复用既有建模运行观测并补充转换审计说明 | F5/T02 |
| G4 | DoD 验收 | PENDING | `it/` | F5/T01、F5/T02 |

## Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 | Task 状态明细 |
|---|---|---:|---|---|---|
| F0 | 交付基线与双模式样本 | 3 | P0 | IN_PROGRESS | T01 READY、T02 IN_PROGRESS、T03 DRAFT（待真实 ODS binding） |
| F1 | 模型双模式工作台 | 3 | P0 | IN_PROGRESS | T03/T01/T02 源码与自动化完成，浏览器 IT 待补 |
| F2 | 可视化实现代码预览与接管 | 3 | P0 | IN_PROGRESS | T01/T02/T03 源码与自动化完成，真实事务/浏览器 IT 待补 |
| F3 | dbt 实现可视只读投影 | 1 | P1 | IN_PROGRESS | T02 源码与中文 fail-closed 文案完成；真实浏览器待补，回切已移交 Sprint-92 |
| F4 | 工程级代码编辑体验 | 2 | P1 | IN_PROGRESS | T01/T02 Monaco、marker adapter、冲突写锁与 build 完成；真实诊断位置/Chrome 95 待补 |
| F5 | 统一发布回归与交付验收 | 2 | P0 | BLOCKED | T01/T02 BLOCKED |
| F6 | 手工模型依赖与 dbt 实现闭环 | 3 | P0 | CODE_COMPLETE / E2E_PENDING | T01/T02/T03 源码与聚焦自动化完成 |
| F7 | 可视化转换与系统生成 dbt 补齐 | 3 | P0 | CODE_COMPLETE / E2E_PENDING | T01/T02/T03 源码与聚焦自动化完成 |
| F8 | 依赖感知的单表与批量物化 | 3 | P0 | CODE_COMPLETE / E2E_PENDING | T01/T02/T03 源码与聚焦自动化完成 |

**当前实施态**: 共 23 个 Task：CODE_COMPLETE=9、IN_PROGRESS=10、READY=1、DRAFT=1、BLOCKED=2。F6～F8 的源码能力已经闭合，但 CODE_COMPLETE 不等于真实链路交付；F0/T03 样本、F5 发布/E2E、物理结果与 Sprint-93 治理证据完成前，Sprint 仍不得标记 DONE。

**首版 `READY=5` 已作废**：F2/T01、F2/T02 与原回切 Task 的契约前提被复核证伪（结论 A/B），F1/T01 的能力前提被结论 C 证伪，均不满足 DoR。

**唯一实施依赖顺序**：

`F0/T01 → F0/T03 → F6/T01 → (F6/T02 ∥ F6/T03) → F7/T01 → F7/T02 → F7/T03 → F8/T01 → F8/T02 → F8/T03 → F5/T01 → F5/T02`

F0/T02 与 F2/F4 剩余浏览器/事务证据可在 F6 编码期并行补齐，但不得提前执行集中 E2E。F5/T01 同时依赖既有 BUILT 取消语义裁决。每个 Task 的输出必须被下一段消费；禁止为赶进度跳过 F6 直接在 F7/F8 内重建依赖图。

> F5 编号属于首版历史，保留以避免已有链接失效；扩围后它的语义明确为最终集成门，实际执行顺序在 F6～F8 之后。

**关键前置**：F0/T02 只验证本 Sprint 范围内的接管链，不再重开回切范围。它必须证明 3 文件预览补齐 `dbt_project.yml` 后可被静态校验、冻结为 4 文件 bundle，并以 `{SQL,SCHEMA,CONFIG}` 通过 compile 门禁。

## 追溯矩阵 (Traceability)

| 需求点 | Feature / Task | 验收证据 |
|---|---|---|
| 编译产物类型与 canonical bundle 的现网事实 | F0/T02 | IT-00 |
| DESIGNER 模型的 TECHNICAL 只读能力可用 | F1/T03 | IT-01 |
| 同一模型页提供可视化模式和代码模式 | F1/T01、F1/T02 | IT-01 |
| 普通模型可先查看生成代码（含 STG），切换视图不改所有权 | F2/T01、F2/T03 | IT-02 |
| 接管显式、原子、可审计，且身份服务端派生 | F2/T02、F2/T03 | IT-03 |
| **接管后的模型仍能编译、物化、发布** | F2/T02、F5/T01 | IT-03、IT-06 |
| dbt 模型可视化只读投影不含伪编辑入口 | F3/T02 | IT-04 |
| dbt 多文件可专业编辑并准确显示诊断 | F4/T01、F4/T02 | IT-05 |
| 三种 authoring 方式共用物化与发布架构 | F5/T01 | IT-06、IT-07、IT-15 |
| 只读账号在两种模式下的四态 | F1/T03、F2/T03、F5/T02 | IT-09 |
| Chrome 95、bundle 体积、并发冲突、旧深链无回归 | F1/T02、F4/T01、F4/T02、F5/T02 | IT-08 |
| 不依赖 ZIP 的 ODS→DWD DIM/FACT→DWS→ADS 样本 | F0/T03 | IT-10 |
| ModelSpec 业务依赖与 dbt source/ref 形成唯一快照 | F6/T01、F6/T03 | IT-11、IT-15 |
| FACT 可在同一模型中表达 ODS 基础来源与 DIM 引用 | F6/T02 | IT-11 |
| 可视化字段映射、过滤、关联、聚合生成可执行 dbt | F7/T01、F7/T02、F7/T03 | IT-12 |
| 手工、系统生成、ZIP 三种 authoring 语义等价 | F7/T03 | IT-15 |
| 单表物化自动解释缺失/已验证上游 BUILD/REUSE/BLOCK | F8/T01、F8/T02 | IT-13 |
| 多选批量物化按同一依赖闭包拓扑执行 | F8/T01、F8/T02、F8/T03 | IT-14 |
| 二次物化不复制模型/资产，并向治理链交付完整证据 | F8/T03、F5/T01 | IT-16；Sprint-93 IT |

## 完成标准

- [ ] 架构：ownership transition 契约测试证明 ModelSpec 与 implementation 同事务一致，重复幂等请求不产生第二个修订。
- [ ] 架构：接管产出的制品类型集合**等于** `{SQL,SCHEMA,CONFIG}`，且 `CONFIG` bundle 内含 `stg_*.sql`；接管后立即 `compile` 成功（不得出现 `MODEL_DBT_IMPORT_REQUIRED`）。
- [ ] 架构：transition 请求体不含 `projectKey`/`dbtUniqueId`；接管前后 `dbtUniqueId` 与物化 selector 逐字符不变。
- [ ] UI：同一页面完成可视化/代码切换、只读预览、显式接管、dbt 保存校验提交；空/加载/错误/成功四态有真实证据。
- [ ] 依赖：每个 committed implementation 均有可重建的 dependency snapshot/checksum；ModelSpec 与 dbt declared/parsed 依赖一致，stale/环/未声明依赖 fail closed。
- [ ] 手工链：不使用 ZIP，从已有 ODS 通过 UI 建成 DWD DIM/FACT、DWS、ADS，并逐表完成实现提交。
- [ ] 可视化链：字段映射、过滤、关联、聚合均以结构化白名单配置编译；超出范围显式接管代码，不生成伪实现。
- [ ] 物化：单选/多选共用 plan preview；BUILD/REUSE/BLOCK 可解释，candidate 重算 checksum，按拓扑构建并验证物理字段/行数。
- [ ] 切片：DESIGNER_GENERATED 与 DBT_MANAGED 各跑通一次“编辑实现 → 候选 → 构建 → 测试/质量 → 显式评审/发布 → ONLINE”，候选钉住 model/implementation/dependency checksum。
- [ ] 二次物化：只新增 candidate/execution/observation/audit 历史，不新增 ModelSpec、implementation identity 或 CatalogAssetKey。
- [ ] 治理交接：Sprint-93 能沿相同 correlation 查询资产、元数据、血缘、质量与 publication evidence，不做人工补台账。
- [ ] 兼容：`open=advanced`、只读账号、ETag 冲突、Chrome 95、建模页首屏体积均有自动化或浏览器证据。
- [ ] 范围：无新菜单、无独立高级建模页、无第二 parser/依赖台账/compiler/candidate/发布状态机、无新增 artifact 类型。

## 非目标

- **不做 `DBT_MANAGED → DESIGNER_GENERATED` 回切**（ADR-91-09）。现网缺少可用的安全子集规则，移交说明见 `assets/sprint-92-back-conversion-handoff.md`。
- 不加固、不下线、不改写旧 `POST .../convert-to-designer-generated`；保持原样。
- 不恢复历史 `SqlModelingPage.tsx` 或 `DbtFileBrowserPage.tsx`。
- 不在代码模式提供任意 SQL 即时执行、查询结果表格或第二套 SQL IDE。
- 不改变 dbt ZIP 的 `inspect → preview → apply` 安全边界；ZIP apply 后仅落入同一代码模式。
- 不把 ZIP 作为手工全链路验收的前置；ZIP 仅参加 IT-15 等价性回归。
- 不在建模工作台创建/装载 ODS，也不把 STG 注册为业务 ModelSpec/资产；数据接入仍由数据集成负责。
- 不在本 Sprint 建可视化自由 SQL、复杂窗口函数、任意宏或 adapter 无关表达式；超出白名单走代码维护。
- 不扩展细粒度权限模型；沿用现有 command guard。所级数据管理员可按当前授权执行显式生命周期命令，但前端不得自动审批/发布；部门账号继续受所属范围约束。
- 不重做 release candidate 状态机、调度器或数据资产登记流程。
- 不引入 Monaco worker、不做 YAML 智能补全。
