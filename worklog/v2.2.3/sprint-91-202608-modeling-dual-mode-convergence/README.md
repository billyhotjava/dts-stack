# Sprint-91: 数据建模双模式工作台收敛

**时间盒**: 2026-08-13 ～ 2026-08-28
**状态**: IN_PROGRESS（F1～F4 已完成源码实现与聚焦自动化；G0 真实登录/Chrome 95、真实事务 IT 与 F5 三角色发布验收仍待关闭）
**类型**: Modeling Workflow / UI Productization / Lifecycle Repair
**目标**: 建模人员在同一个模型工作台中，以“可视化模式 / 代码模式”查看和维护同一份 ModelSpec；模式切换不隐式改变实现所有权，显式接管后仍复用同一套版本、物化和发布链路。

> **2026-08-13 架构复核修订**：首版设计有三处“以为可复用、实际不可复用”的断裂，已在本版修正。要点：接管必须复用 dbt bundle freeze + artifact import seam（否则接管后无法编译）；dbt 身份一律服务端派生；DBT→DESIGNER 回切因缺少可用的子集规则而移出本 Sprint。修订依据见「架构复核结论」与账本 #16～#21。

## 背景与价值

历史高级 SQL 建模页和 dbt 文件页已经退役；当前高级 dbt 能力以内嵌工作区重新实现，但入口仍表现为“代码模式”按钮与“高级 dbt 工作区”按钮并存。普通模型点击代码模式时只在进入后才得到能力拒绝，现有模型的“实现维护方式”又不可切换，用户无法从页面理解“查看代码”“接管代码实现”“发布模型”三者的区别。

本 Sprint 不恢复旧页面，而是把现有模型工作台收敛为单一入口：

1. “可视化 / 代码”是同一模型的表现视图；
2. `DESIGNER_GENERATED / DBT_MANAGED` 是实现所有权，只有显式、可审计的转换命令才能改变；
3. 代码提交只生成新的实现修订，物化与发布继续走既有 release candidate 控制面。

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
| 26 | 发布状态机要求维护者、独立评审人、发布操作员三种 duty，且提交者不能审批/发布、评审人不能发布 | `ModelLifecycleContract.java:146-187`；`ModelReleaseCandidateService.java:1236-1269` |
| 27 | ownership transition、3 文件预览、4 文件冻结 bundle、严格字段解码、专用 CAS、幂等回执和审计动作已实现；modeling 聚焦测试 147/147 通过 | `ModelImplementationOwnershipTransitionServiceTest` 等 10 个聚焦测试类；`it/baseline.md` |
| 28 | 双模式、只读投影、显式接管、Monaco 懒加载、诊断适配及 409/412 写锁已实现；Vitest 5 文件 15 条、TypeScript 检查和 legacy browser build 通过 | `it/baseline.md` |
| 29 | 既有 release candidate 聚焦回归 80 条中 79 条通过；`builtCandidateCannotBeCancelled` 暴露基线语义冲突：状态机允许 `BUILT → CANCELLED`，旧测试仍期望禁止。相关源文件未被本 Sprint 修改 | `ModelReleaseCandidateServiceTest:840-866`；`ModelLifecycleContract.DeliveryStatus.transitions()` |

## 2026-08-13 实施与验证证据

| 范围 | 结果 | 证据/边界 |
|---|---|---|
| 后端核心竖线 | PASS | 10 个聚焦测试类共 147 条全部通过；覆盖 capability、preview、严格 REST、ModelSpec wrapper、implementation CAS、bundle/artifact import、幂等与审计 payload |
| 审计字典 | PASS | 三份 runtime mirror 一致；`AuditActionCatalogResourceTest` 5/5 通过。`services/dts-admin/init` 是旧版非 runtime mirror，本 Sprint 不改 |
| 前端双模式与编辑器 | PASS | Vitest 5 文件 15 条、`tsc --noEmit`、`pnpm build` 均通过；构建产物中 `DbtCodeEditor`/Monaco 为独立懒加载 chunk |
| Chrome 95 静态门禁 | PASS_WITH_GAPS | legacy build 与禁用 API/CSS 扫描通过；尚缺目标 Chrome 95 登录态下的 console/network/screenshot smoke，不能据此宣称真实浏览器通过 |
| 统一发布回归 | BLOCKED | 80 条聚焦测试中 1 条既有状态机/测试语义冲突；真实三角色 `PUBLISHED` 与物理 `ONLINE` 仍未执行 |
| 变更范围 | PASS_WITH_GAPS | GitNexus `detect_changes(scope=all)` 为 LOW、无 affected process；新增未跟踪文件尚未进入索引，以聚焦测试和构建补证 |

## Gate Registry

| Gate | 项目 | 状态 | 证据 | 未过则关联 Task |
|---|---|---|---|---|
| G0 | 交付基线 | GAP | `it/baseline.md` P2/P6；P7 自动化构建已关闭 | F0/T01 |
| G0 | 编译产物与子集实测 | PASS_WITH_GAPS | P9 聚焦编译/bundle/transition 测试通过；真实库接管样本仍待 F0/T01 登录路径 | F0/T02 |
| G0 | 领域与数据画像 | PASS | `assets/domain-profile.md` | - |
| G0 | DTS 不变量自检 | PASS | ADR-91-01/03/04/07/08/09/10 | - |
| G1 | 契约链贯通 | PASS_WITH_GAPS | 4 文件 canonical bundle 与 `{SQL,SCHEMA,CONFIG}` 自动化已通过；真实 DB/物化待 IT | F0/T02、F5/T01 |
| G1 | 非功能预算 | GAP | 自动化容量/并发/懒加载已覆盖；真实事务、浏览器与发布仍有 GAP | F0/T02、F4/T01、F5/T01 |
| G2 | 变更范围守卫 | PASS_WITH_GAPS | 预编辑 impact 已执行；最终 detect changes=LOW、0 affected process，新文件待纳入索引 | - |
| G3 | 发布安全 | IN_PROGRESS | `assets/release-plan.md` 已形成；待运行实例验证与回滚演练 | F5/T02 |
| G4 | 可运维性 | PENDING | 复用既有建模运行观测并补充转换审计说明 | F5/T02 |
| G4 | DoD 验收 | PENDING | `it/` | F5/T01、F5/T02 |

## Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 | Task 状态明细 |
|---|---|---:|---|---|---|
| F0 | 交付基线与双模式样本 | 2 | P0 | IN_PROGRESS | T01 READY、T02 IN_PROGRESS（自动化通过，真实样本待登录） |
| F1 | 模型双模式工作台 | 3 | P0 | IN_PROGRESS | T03/T01/T02 源码与自动化完成，浏览器 IT 待补 |
| F2 | 可视化实现代码预览与接管 | 3 | P0 | IN_PROGRESS | T01/T02/T03 源码与自动化完成，真实事务/浏览器 IT 待补 |
| F3 | dbt 实现可视只读投影 | 1 | P1 | IN_PROGRESS | T02 源码与中文 fail-closed 文案完成；真实浏览器待补，回切已移交 Sprint-92 |
| F4 | 工程级代码编辑体验 | 2 | P1 | IN_PROGRESS | T01/T02 Monaco、marker adapter、冲突写锁与 build 完成；真实诊断位置/Chrome 95 待补 |
| F5 | 统一发布回归与交付验收 | 2 | P0 | BLOCKED | T01/T02 BLOCKED |

**当前实施态**: F0～F4 均为 IN_PROGRESS，F5 为 BLOCKED。自动化完成不等于 Sprint DONE；真实登录、事务、Chrome 95 与三角色发布证据未补齐前保持该状态。

**首版 `READY=5` 已作废**：F2/T01、F2/T02 与原回切 Task 的契约前提被复核证伪（结论 A/B），F1/T01 的能力前提被结论 C 证伪，均不满足 DoR。

**剩余依赖顺序**: **F0/T01 真实登录/样本 → F0/T02 真实库探针 → F2/F4 浏览器与事务 IT → 修正或裁决既有 BUILT 取消语义 → F5 三角色发布/物理 ONLINE 验收**。

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
| 两种实现方式共用物化与发布架构 | F5/T01 | IT-06、IT-07 |
| 只读账号在两种模式下的四态 | F1/T03、F2/T03、F5/T02 | IT-09 |
| Chrome 95、bundle 体积、并发冲突、旧深链无回归 | F1/T02、F4/T01、F4/T02、F5/T02 | IT-08 |

## 完成标准

- [ ] 架构：ownership transition 契约测试证明 ModelSpec 与 implementation 同事务一致，重复幂等请求不产生第二个修订。
- [ ] 架构：接管产出的制品类型集合**等于** `{SQL,SCHEMA,CONFIG}`，且 `CONFIG` bundle 内含 `stg_*.sql`；接管后立即 `compile` 成功（不得出现 `MODEL_DBT_IMPORT_REQUIRED`）。
- [ ] 架构：transition 请求体不含 `projectKey`/`dbtUniqueId`；接管前后 `dbtUniqueId` 与物化 selector 逐字符不变。
- [ ] UI：同一页面完成可视化/代码切换、只读预览、显式接管、dbt 保存校验提交；空/加载/错误/成功四态有真实证据。
- [ ] 切片：DESIGNER_GENERATED 与 DBT_MANAGED 各跑通一次“编辑实现 → 物化/构建 → 发布候选 → 发布”，候选钉住正确 checksum。
- [ ] 兼容：`open=advanced`、只读账号、ETag 冲突、Chrome 95、建模页首屏体积均有自动化或浏览器证据。
- [ ] 范围：无新菜单、无独立高级建模页、无第二 parser/台账/发布状态机、无新增 artifact 类型。

## 非目标

- **不做 `DBT_MANAGED → DESIGNER_GENERATED` 回切**（ADR-91-09）。现网缺少可用的安全子集规则，移交说明见 `assets/sprint-92-back-conversion-handoff.md`。
- 不加固、不下线、不改写旧 `POST .../convert-to-designer-generated`；保持原样。
- 不恢复历史 `SqlModelingPage.tsx` 或 `DbtFileBrowserPage.tsx`。
- 不在代码模式提供任意 SQL 即时执行、查询结果表格或第二套 SQL IDE。
- 不改变 dbt ZIP 的 `inspect → preview → apply` 安全边界；ZIP apply 后仅落入同一代码模式。
- 不扩展细粒度权限模型；继续依赖现有 `CATALOG_MAINTAINERS`，权限矩阵升级另立 Sprint。
- 不重做 release candidate 状态机、调度器或数据资产登记流程。
- 不引入 Monaco worker、不做 YAML 智能补全。
