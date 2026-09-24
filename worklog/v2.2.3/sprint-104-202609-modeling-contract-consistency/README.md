# Sprint-104：通用建模契约与物化一致性整改

**版本**：v2.2.3  
**规划日期**：2026-09-06  
**状态**：IN_PROGRESS  
**类型**：缺陷整改与通用场景完善  
**目标**：用户能先设计 ODS→DWD→DWS→ADS，以“设计→实现配置→物化”完成建模；数据接入、资产形成与治理、分析准备在数据模块办理。各模块复用同一模型/资产身份和版本证据，不把所有约束堆在模型页。

**当前产品边界（2026-09-07）**：新增 [F3-全层建模与数据模块边界简化](features/F3-全层建模与数据模块边界简化/README.md)，以 [F3 契约](assests/F3-modeling-data-boundary.md) 为本轮增量规范。物化完成即完成本次建模；F2 中强制继续质量配置、发布交付及模型页治理的界面条件被替代，已有服务/版本/安全规则和历史证据保留。F3-T01–T06 已进入实施及真实 Chrome 95 分项验收，尚未满足全部 DoD。

**手工测试入口**：[IT-01～IT-18 操作步骤、版本前提与结果填写表](it/手工验收用例-20260907.md)。2026-09-07 起由用户手工操作；研发补验分支单列，不以清单代替通过证据。

**最新实施状态**：F1/F2 已有主要实现，F3 的 SOURCE、逻辑依赖、结构物化、三步页面、接入目标绑定和数据接手代码已落地；正式包及真实 Chrome 95 分项验收进行中。规划内活动候选唯一约束已按冻结契约修复并通过真实PG回归；最终正式包9a5e7ce60正在部署复验，独立离线目标仍未指定，不能标记全部完成。见 [F3 运行记录](it/evidence/current-environment/f3-runtime-20260907.md)；旧记录按各自提交保留。

**2026-09-10 首次建模整改**：已 review F1–F6 的相关 feature/task，新增 [F7-T01–T04](features/F7-首次建模初始化与菜单授权一致性/README.md)，共 7 个 Feature、43 个 Task。当前建模权限按菜单控制；首次上下文由后端与模型原子保存。Review、冻结契约和 IT-44–47 见 [F7 契约](assests/F7-first-model-initialization.md)。F7 编码和定向验证已完成，正式交付与真实空库页面验收待完成；历史验收结论不变。

## 范围与来源

初始范围为五项 P1 不一致及能力边界核对，F1 含 F1-T01–T08。2026-09-06 扩展 F2“模型交付与资产治理贯通”，新增 F2-T01–T06；2026-09-07 按用户新要求新增 F3-T01–T06，全 Sprint 共 3 个 Feature、20 个 Task。F2 原有四步向导、质量闭环、目录 CAS 和精确分析注册源码及验证进展继续保留，F3 只接续其中与新模块边界冲突的部分。源码落地不计作验收完成，原进展见 [本轮实现与验证记录](it/evidence/current-environment/implementation-progress-20260907.md)。
计划复审已修正，已开始 F1-T01 源码归因与 F1-T02 契约整改；不代表部署或现场验收完成。仓库既有路径为 `worklog/v2.2.3`；用户消息中的 `workog` 按既有目录处理。

此前 `fefea6844`、`a33ba6b0a` 已处理普通明细时间必填、部分无键全量/全表聚合和已有 TYPE2 绑定保存；本 sprint 不把这些历史实现重复计为 DONE。
登记时 HEAD 为 `c96d4ff4b`；删除/归档相关改动不属于本 sprint。

## 架构决策记录（ADR）

| 决策 | 选择与约束 |
|---|---|
| 单一模型事实源 | 延续 ModelSpec、创作草稿、实现修订和发布候选 owner，不新建平行模型/质量/资产台账 |
| 分阶段规则 | 分开格式合法性、阶段完整性、治理策略；不以取消所有检查换取通用性 |
| 当前值优先 | 当前编辑→创作草稿→模型基线→新建默认；显式 FULL、[]、null 的语义由 F1-T02 固定 |
| 键与业务时间 | 全量非维度模型可无键；维度和当前增量执行保持必要键；时间语义只约束适用场景 |
| 维度历史 | 保存历史配置不等于已实现 TYPE2 历史维护；真实能力由 F1-T07 固定 |
| 权限与合规 | 不扩张权限范围，不改变租户、认证、密级、来源有效性和版本校验 |
| 存储与迁移 | 优先修正现有 DTO/Schema/JSON 配置和编译；未计划新表或改列。若证据要求迁移，先补设计，只允许前向 changeSet |
| 离线交付 | 不依赖开发目录挂载、不临时下载依赖、不手工修补容器；按用户授权从部署目录正式构建和定向更新 |
| F3 模块边界 | 模型设计/实现/物化归建模；数据接入、资产形成/质量治理/发布和分析准备归数据。当前版本真实物化成功即可完成建模，不等同资产可用或 PUBLISHED |
| F3 全层设计 | 在同一 ModelSpec 内补 ODS 逻辑类型与版本依赖；零接入可保存全链，不伪造物理来源；普通空表结构物化与数据加工结果分别记证 |
| F3 增量范围 | 首批 PostgreSQL 普通表，复用现有接入和目录；不新增 STG 建模、菜单、批量全链调度或新治理/发布 owner；技术缺口由 F3-T01 先冻结 |

## 端到端契约链

F1 字段与错误约束见 [Feature 契约](features/F1-通用建模契约与物化一致性/README.md)；本轮 ODS、结构物化、完成判定和数据交接的增量链见 [F3 K31–K36](assests/F3-modeling-data-boundary.md#3-契约链与扩展草案)。下表保留既有入口，不表示旧阶段必填全部继续前置。

| 层 | 现有落点 | 本 sprint 约束 |
|---|---|---|
| UI | 模型工作台，`/data-modeling/dimensions/workbench?modelSpecId={id}`；旧 `/modeling/models/{id}` 由 F2-T04 修复兼容跳转 | 保存、恢复、标准绑定、字段管理、加载方式、发布与物化，不新建菜单 |
| 模型 | `POST /api/modeling/model-specs`；`PUT /api/modeling/model-specs/{id}` | 完整字段和阶段规则对齐；保留 If-Match |
| 草稿 | `GET .../{id}/authoring-context`；`POST .../{id}/authoring-drafts`；`PUT .../{id}/authoring-drafts/{draftId}`；`POST .../{id}/authoring-drafts/{draftId}/validate|commit` | 完整 snapshot 往返，保留原有草稿版本/CAS 请求 |
| 阶段与实现 | `GET .../{id}/stage-gates`；`GET .../implementation/capabilities`；`PUT .../{id}/implementation` | 覆盖策略、输入/加载能力一致，保留模型与实现双版本 |
| 编译与检测 | `POST .../{id}/lifecycle/compile|tests` | 完整复合键质量产物，无键不生成假唯一性规则 |
| 物化 | `POST .../{id}/build-intents` | If-Match、Idempotency-Key；请求 `{planId:string,environment:string}` |
| Service | ModelSpecContract/StageGateService、创作草稿服务、InputPolicy、ExecutionPlanner、DbtCompiler | 在既有职责边界修复，不新增替代入口 |
| 数据 | `modeling_model_spec` 及既有修订；`modeling_model_implementation.settings_json` 及实现修订 | 保留当前模型/实现身份和历史；草稿具体表列在 F1-T01 记录，不先写迁移 |
| 运行 | 既有 dbt/PostgreSQL、质量与发布候选控制面 | 成功必须绑定当前 revision/checksum，不用旧证据代替 |

## 现状勘察账本（Context Ledger）

以下来自已完成的源码复审；实施只复用账本，文件变化时补充差异，不重新全仓扫描。路径均相对仓库根。

| 编号 | 已确认事实 | 源码证据 |
|---|---|---|
| C01 | Java 草稿更新过滤部分完整上下文要求；前端更新使用完整模型校验；Schema 缺业务过程/主题域字段 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecContract.java:1470`；`source/dts-platform-webapp/src/features/modeling/contracts/modelSpecV2Contract.ts:1025`；`source/dts-platform/src/main/resources/config/modeling/model-spec-v2.schema.json:1446` |
| C02 | 按策略选必绑字段后，专业证据适配器又遍历全字段要求绑定 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecStageGateService.java:250`；`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/GovernanceModelSpecStandardEvidenceAdapter.java:29` |
| C03 | 复合键编译为首列 unique；已用当前编译类复现 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingDbtCompiler.java:831`、`:872`；[复审证据](assests/review-evidence.md) |
| C04 | 草稿恢复只取 scdType，保存从基线取绑定/层级 | `source/dts-platform-webapp/src/pages/data-modeling/prototype/services/modelWorkbenchService.ts:523`、`:1041` |
| C05 | FULL 和显式空分区可能触发旧 policy 回退 | 同文件 `:459`–`:468` |
| C06 | 完整 Node 契约测试 37 项，24 通过、13 失败；不等于 13 个独立缺陷 | [复审证据](assests/review-evidence.md)，本次规划未重新运行 |
| C07 | 现有模型、草稿、实现、阶段接口可复用 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/ModelSpecResource.java:44`、`:135`、`:200`；`ModelLifecycleResource.java:75`、`:116`、`:171`；`ModelAuthoringDraftResource.java:43`、`:81`、`:118` |
| C08 | 当前实现以 settings_json 保存且有修订；模型台账已存在 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/modeling/ModelLifecycleRepository.java:196`；`ModelSpecRepository.java:65` |
| C09 | 历史专项回归和构建不能代替当前部署/浏览器/迁移证明 | [基线](it/baseline.md) |
| C10 | 输入方式按类型固定；执行计划明确不支持 SNAPSHOT，配置历史元数据不等于运行支持 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelImplementationInputPolicy.java:203`；`ModelImplementationExecutionPlanner.java:146` |

### 现状勘察账本 F12 增量（2026-09-22，C126–C136）

下游 F12-T03–T08 复用本表，不重复扫描。路径相对仓库根。

| 编号 | 已确认事实 | 源码证据 |
|---|---|---|
| C126 | 模型导入既有路由已具备 inspect/preview/apply/retry/forward-undo/查询，前缀 `/api/modeling/model-spec-imports`；新增编排前缀须与其共存 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/ModelSpecImportResource.java:44`、`:137`、`:163`、`:205`、`:211`、`:235`、`:241`、`:263` |
| C127 | forward-undo 对 CREATE 项直接判 blocked，返回 `CREATED_MODEL_NO_BASE_REVISION`；它不能当重置用 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/imports/reconciliation/ModelSpecImportForwardUndoWorker.java:25`、`:42`、`:112` |
| C128 | 实现命令回执为追加写，由 Liquibase 保护触发器强制；删除尝试报 `MODEL_IMPLEMENTATION_COMMAND_RECEIPT_APPEND_ONLY` | `source/dts-platform/src/main/resources/config/liquibase/changelog/20260801_06_model_implementation_command_receipt.xml`；`source/dts-platform/src/test/java/com/yuzhi/dts/platform/repository/modeling/ModelImplementationCommandReceiptLiquibaseIT.java` |
| C129 | 预览服务可复用，不必另造 dbt parser 或模型台账 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/imports/preview/ModelSpecImportPreviewService.java` |
| C130 | 逆向建模页与来源登记弹窗已存在，来源登记不依赖先建模型 | `source/dts-platform-webapp/src/pages/data-modeling/prototype/ReverseModelingPage.tsx`；`ModelSourceInventoryDialog.tsx`；`ReverseImportSourceRegistration.tsx` |
| C131 | 语义虚拟数据集写入契约：body `{name,description?,workspace_id?,state:SemanticQueryBody}`；`SemanticQueryBody` 用字符串 id 表达 measures/dimensions，含 `base`/`joins`/`indicatorRefs`/`timeRange`/`derived_metrics`/`order_by`/`filters.value_to` | `source/dts-platform-webapp/src/analytics/api/analyticsApi.ts:2104`、`:181`–`:194` |
| C132 | 卡片创建强制 `dataset_query.database`（Long，>0），显示字段为 `display`（缺省 `table`）与 `visualization_settings`；卡片绑定数据库 id 而非数据集 id | `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/CardResource.java:59`、`:167`、`:184`–`:186`、`:197`、`:200` |
| C133 | 大屏 ScreenSpec 为根级扁平 `schemaVersion`/`width`(200–7680)/`height`(120–4320)/`components`/`globalVariables`；组件字段扁平 `id`/`type`/`x`/`y`/`width`/`height`/`dataSource`/`config`/`interaction`/`drillDown`/`actions`，无 `canvas` 与 `frame` 包装 | `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ScreenSpecValidator.java:97`–`:101`、`:160`–`:182` |
| C134 | 大屏组件类型白名单 48 项（`line-chart`…`image`/`iframe`/`video`/`table`/`title`/`richtext` 等），不含 `CARD`/`TEXT`；卡片引用走 `dataSource.sourceType ∈ {static,api,card,sql,dataset,metric,database}`；`config` 内 plugin marker 可放行白名单外类型 | 同文件 `:17`–`:70`、`:71`、`:165`、`:170`、`:208` |
| C135 | dts-platform → dts-analytics 已有带超时的出站通道，F12 须复用而非新建 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/serving/AnalyticsSemanticPublishClient.java:23`、`:36`–`:37`、`:79`；`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/integration/ScreenReportLinkSyncService.java:36`、`:45` |
| C136 | 仓库已有 `CatalogExternalAssetIdentityRegistry`（数据目录外部资产身份），F12 的导入身份注册表须另名 `ImportIdentityRegistry`，不得混用 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/CatalogExternalAssetIdentityRegistry.java` |

开放问题（F12，勘察未决）：QUERY_DATASET 的 `parameters` 在目标语义层的落点；QUERY_DATASET → 底层 `database` 的解析接口不存在；ScreenSpec 是否接受 `theme`/`backgroundResource`。三项未决前 F12-T04/T05 保持 DRAFT。

F2 账本 C11–C18 见 [原交付契约](assests/delivery-workflow-contract.md)；2026-09-07 新增 [C19–C26](assests/F3-modeling-data-boundary.md#5-context-ledger-c19c26)，记录 ODS 禁建、接入建表、真实来源校验、发布耦合和数据目录落点。F3-T01–T06 复用账本，仅对受影响变化补充证据。

## Gate Registry

| Gate | 项目 | 状态 | 证据/待交付位置 | 未过对应 Task |
|---|---|---|---|---|
| G0 | 交付与登录/迁移基线 | GAP | [baseline](it/baseline.md)，尚未探测当前目标 | F1-T01 |
| G0 | 领域与真实数据画像 | GAP | [domain-profile](assests/domain-profile.md)，数量和样本待实测 | F1-T01 |
| G0 | 领域不变量 | PASS | 本文 ADR，计划不新增 owner/菜单/业务表；不代表代码验收 | — |
| G1 | 契约与 DoR | GAP | Feature K1–K6；阶段矩阵、草稿 CAS/存储细节待固定 | F1-T01、F1-T02 |
| G1 | 非功能预算 | GAP | [nfr-budget](assests/nfr-budget.md)，检查目标已列、未执行 | F1-T01、F1-T08 |
| G2 | F1 实现及单次聚焦 review | PASS（源码） | F1-T02–T07 源码与专项测试归档；见 [source-test-summary](it/evidence/source-test-summary-20260906.md)。仅代表源码实现/review，不替代页面或运行验收 | F1-T08 页面、部署与真实竖切片 |
| G3 | F1 发布安全 | PENDING | 历史交付已部署；本轮含 platform/analytics/ingestion/webapp 四镜像，版本与阶段以整改验证为准 | F1-T08 |
| G4 | F1 可运维性与 DoD | PENDING | [IT](it/README.md)；F1-T08 产出 runbook | F1-T08 |
| G0 | F3 空目标/无来源/接入与数据接手基线 | PASS | 登录、无来源四层设计、连续空表和文件写数已实际验证；独立离线仍由F3-T06跟踪 | F3-T01 |
| G1 | F3 边界、精确契约与兼容方案 | PASS | [F3 K31–K36、四项技术缺口与非功能检查](assests/F3-modeling-data-boundary.md)；已实施 DTO 见冻结章节；2026-09-08 候选用途/占用/迁移增量已冻结并通过隔离验证 | F3-T01 |
| G2 | F3 实现与聚焦 review | PENDING | F3-T02–T05 已实施，整改源码与178项专项测试通过；真实主线待部署复验 | F3-T02–T05 |
| G3 | F3 正式交付与安全恢复 | PENDING | 复用 F1-T08、F2-T06 机制，只补本次差异 | F3-T06 |
| G4 | F3 模块边界与全层真实验收 | PENDING | IT-19/21/23 有分项证据，IT-20/22 完整主线及 IT-24 独立离线待完成 | F3-T06 |

## F12 增量 Gate（2026-09-22）

| Gate | 状态 | 证据与关联 |
|---|---|---|
| G0 交付基线 | GAP | 建模切片已在真实环境跑通（[交付记录](features/F12-业务应用ZIP全链路导入/建模切片交付记录-20260921.md)，IT-01 PASS）；分析/大屏侧目标环境未探测。关联 F12-T07 |
| G0 领域与数据画像 | GAP | PJM 真实画像有（43 业务模型、10 ODS、8 内嵌维度定义）；卡片/大屏侧样本缺。关联 F12-T03 夹具 |
| G0 领域不变量 | PASS | 复用既有导入向导、来源确认、密级与部门链路；不新增菜单与 owner 模型；密级只升不降 |
| G1 契约链贯通 | **GAP** | 2026-09-22 发现的载荷差异已回写设计；机器 Schema、真实契约与样本尚未验证，见[实测对照](features/F12-业务应用ZIP全链路导入/领域载荷与接口契约.md)。关联 F12-T03/T04/T05 |
| G1 UI/UX 规格 | PASS | [F12 README §UI/UX 规格](features/F12-业务应用ZIP全链路导入/README.md)，含线框、四态、happy path 走查（即 IT-01/IT-16 验收脚本） |
| G1 非功能预算 | GAP | 预算已收口为 [F12-nfr-budget](assests/F12-nfr-budget.md)（延迟/解压/可靠性/保留/告警/日志六类），**尚未实测**；由 F12-IT-14 作门禁。关联 F12-T07 |
| G2 实现与 review | GAP | 仅 T02 建模切片有源码与镜像证据；T03–T08 未开工。跨服务传输须复用 `AnalyticsSemanticPublishClient`（Q7），review 时核对 |
| G3 发布安全 | PENDING | 迁移仅扩展、开关默认关闭、回退策略见[运行恢复](features/F12-业务应用ZIP全链路导入/运行恢复与就绪衔接.md)；独立 release-plan 待 T07 产出于 `assests/F12-release-plan.md`，实现前不预置占位文档 |
| G4 可运维性 | PENDING | 日志字段、告警阈值、排障顺序见[运行恢复](features/F12-业务应用ZIP全链路导入/运行恢复与就绪衔接.md)与 [nfr-budget](assests/F12-nfr-budget.md)；独立 runbook 待 T07 产出于 `assests/F12-runbook.md`，实现前不预置占位文档 |
| G4 DoD 验收 | PENDING | F12-IT-01 PASS；IT-02–04 部分组件覆盖；IT-05–29 NOT_RUN。见 [it/README](it/README.md) F12 矩阵 |

## F7 增量 Gate（2026-09-10）

| Gate | 状态 | 证据与关联 |
|---|---|---|
| G1 首次保存/菜单授权契约 | PASS | [F7 契约](assests/F7-first-model-initialization.md)，F7-T01–T03 |
| G2 源码与专项 | PASS（源码） | [最新编码验证](assests/F7-implementation-20260910.md)，后端主回归 58/58、补充专项 29/29、前端 87/87、前端源码构建通过；不代表运行验收 |
| G3 正式交付/部署 | PENDING | F7-T04，尚未构建本轮镜像或部署 |
| G4 真实空库页面与 Chrome95 | PENDING | F7-T04/IT-44–47，本轮尚未执行真实页面验收 |

## 当前源码测试证据（2026-09-06）

- 提交 `bd0670acc89e7dd1be82e0d12961ba7744f63ca2` 的部署目录 Docker Maven 专项为 99/99；工作台 Vitest 日志为 92/92。正式命令、首轮测试夹具修复原因和边界见 [源码专项归档](it/evidence/source-test-summary-20260906.md)。
- IT-04 ephemeral dbt 样例已通过，但其余 UI、Chrome 95、正式交付/部署、真实物化和质量未完成；不以源码证据替代 G0、G3 或 G4。

## Feature 与执行顺序

| Feature | Task 数 | 优先级 | 状态 |
|---|---:|---|---|
| [F1-通用建模契约与物化一致性](features/F1-通用建模契约与物化一致性/README.md) | 8 | P1（F1-T07 为 P2） | IN_PROGRESS |
| [F2-模型交付与资产治理贯通](features/F2-模型交付与资产治理贯通/README.md) | 6 | P1 | IN_PROGRESS（F2-T02–T05 源码集成，正式验证待执行） |
| [F3-全层建模与数据模块边界简化](features/F3-全层建模与数据模块边界简化/README.md) | 6 | P1 | IN_PROGRESS（F3-T01–T06，实施及分项验收） |
| [F12-业务应用ZIP全链路导入](features/F12-业务应用ZIP全链路导入/README.md) | 8 | P1 | IN_PROGRESS（T01 DONE、T02 IN_PROGRESS、T03–T08 DRAFT） |
| [F15-构建与发布分离及交付状态解耦](features/F15-构建与发布分离及交付状态解耦/README.md) | 6 | P0 | DRAFT（T01 设计核对 READY，T02–T06 DRAFT；仅文档重建） |

说明：本表历史上只登记 F1–F3，F4–F11 的状态分散在下文各自章节；F12/F15 增量在此登记，不回溯重算下文历史统计。F12 独立统计为 DRAFT=6、IN_PROGRESS=1、DONE=1；F15 独立统计为 READY=1、DRAFT=5，均不计入下文 `IN_PROGRESS=19, DONE=1` 的历史数字。

建议顺序：F1-T01 归因 → F1-T07 能力契约冻结 → F1-T02 → F1-T05/F1-T06/F1-T03/F1-T04 → F1-T07 一致性验证 → F1-T08。任务依赖优先于排序；不默认启用多代理。
统计：DRAFT=0，READY=0，IN_PROGRESS=19，DONE=1，BLOCKED=0。
F2 顺序：F2-T01 基线/契约冻结 → F2-T02 四步向导与统一交付视图 → F2-T03 质量闭环、F2-T04 资产维护、F2-T05 分析恢复 → F2-T06 集成验收；F2-T02–T05 的源码现已集成并正式测试，F2-T06 正在补齐镜像、迁移、离线及 Chrome 验收证据。F1-T08 与 F2-T06 共享环境/交付证据，不重复计算通过范围。

F3 顺序：F3-T01 冻结新边界/副作用 → F3-T02 ODS 与逻辑引用 → F3-T03 结构物化/完成证据 → F3-T04 三步页面 → F3-T05 接入绑定与数据接手 → F3-T06 验收。F2 以上顺序是已有切片记录，冲突页面不再按旧标准扩建；F3 的收敛实施不重复开发 F2 已有 owner。

F1-T01–T08 均在推进中：源码专项、IT-04真实dbt、历史草稿主要页面分支已留证；正式构建及测试环境部署完成。无时间明细物化、其余场景、Chrome 95和离线安装尚未全部通过，禁止登记Sprint DONE。

## 追溯矩阵

| 需求 | Task | 验收 |
|---|---|---|
| 环境、13 项失败归因 | F1-T01 | baseline；IT-01 |
| 合法字段/阶段统一 | F1-T02 | IT-01、IT-07 |
| 标准覆盖范围一致 | F1-T03 | IT-05 |
| 复合键唯一性 | F1-T04 | IT-04 |
| 草稿历史/层级完整 | F1-T05 | IT-02 |
| FULL/[] 不回退 | F1-T06 | IT-03 |
| 能力边界真实 | F1-T07 | IT-06 |
| 实际物化与离线交付准备 | F1-T08 | IT-01–IT-07 |
| 业务状态、接口、页面动作同一契约 | F2-T01、F2-T02 | IT-08、IT-12、IT-15–IT-17；共享 S/N/I fixtures |
| 正确目标的规则配置与检查 | F2-T03 | IT-09、IT-10 |
| 资产治理同一 owner，模型导航保留身份 | F2-T04 | IT-11、IT-12 |
| 分析自动准备及独立失败恢复 | F2-T05 | IT-08、IT-13 |
| F2 页面/运行/离线交付一致 | F2-T06 | IT-08–IT-18 |
| 零接入任务完成 ODS 到 ADS 的逻辑设计 | F3-T01、F3-T02 | IT-19 |
| 无数据结构物化及当前版本建模完成 | F3-T03 | IT-20 |
| 三步页面、约束按阶段出现、物化后可结束 | F3-T04 | IT-21 |
| 接入绑定同一 ODS、数据模块形成并维护资产 | F3-T05 | IT-22 |
| 治理失败独立恢复及旧流程兼容 | F3-T03–T05 | IT-23 |
| F3 正式包、部署、Chrome95 与离线差异验收 | F3-T06 | IT-24 |
| 整包配置、来源登记、43 草稿导入（PJM 主路径） | F12-T02 | F12-IT-01–04 |
| 统一 ZIP 生产、载荷校验与恶意包拒绝 | F12-T03 | F12-IT-05/11/23 |
| 查询数据集、卡片、大屏引用转换与就绪语义 | F12-T04、F12-T05 | F12-IT-06/07/24/25/26 |
| 同包重导、升级、改名、并发的身份与幂等 | F12-T06、F12-T08 | F12-IT-08/09/19/20/22 |
| 清理本次新建对象后同规划手工重导 | F12-T08 | F12-IT-16–18/21 |
| 上游物化后安全续建与恢复 | F12-T06 | F12-IT-07/26–29 |
| F12 兼容、安全、性能与在线离线交付一致 | F12-T07 | F12-IT-12–15 |

## 完成标准与非目标

- [ ] 五项 P1 修复均有 RED→GREEN 和当前版本证据；完整契约测试失败逐项闭环。
- [ ] UI、API、编译、dbt 检测及当前发布门禁结论一致；Chrome 95 验证未完成不得标 DONE。
- [ ] 重复物化按既有幂等/并发规则验收；现场证据与源码/构建证据分开。
- [ ] 旧模型可读取、不静默覆写；必要资源可离线交付，无开发目录依赖。
- [ ] 前后端同一业务状态/允许操作一致；F2 的质量/资产/分析能力按 F3 边界落到数据模块，资产登记不与分析准备混用。
- [ ] 质量/资产保存与分析重试有版本保护，旧证据不冒充当前成功，跨页返回保留身份与编辑上下文。
- [ ] 无接入可保存四层设计及版本引用；结构物化核对真实目标，零行不阻断建模完成，空表不冒充数据就绪。
- [ ] 三步建模不要求资产运营信息；数据模块写数/治理失败不改物化结果；IT-19–IT-24 留存实际证据。
- 非目标：不实现新输入方式、通用历史引擎、全目录/菜单重做、平行控制面、生产数据修改、删除/归档整改或未经授权部署。F2 只收敛既有页面交互与交付流程。


## F2 扩展决策与开工门槛（2026-09-06 历史基线）

本节记录既有实现来由。2026-09-07 后与模块分工/建模结束条件冲突的规则由 [F3 契约](assests/F3-modeling-data-boundary.md) 替代；原四步及模型页治理要求不再作为 F3 验收标准。版本、安全、单主动作、帮助、身份和原服务复用约束继续有效。

唯一详细规范为 [模型交付与资产治理统一契约](assests/delivery-workflow-contract.md)，包含 Context Ledger C11–C18 与 S01–S10 状态/操作矩阵；下游只查账本中与变更相关的差异。

| 决策 | 业务与界面必须同时遵循的约束 |
|---|---|
| 连续交付入口 | W1 设计→W2 实现→W3 构建检查→W4 发布交付，每步独立页面、一个主操作；后端提供事实/导航权限/允许动作 |
| 结果分离 | 物化、质量、发布、目录登记、分析准备分别取证；分析失败不回滚发布、不妨碍已有资产维护 |
| 上下文不丢失 | 常见规则绑定/治理维护在面板完成；复杂编辑进入已有页面并返回原模型/候选/目标 |
| 写入 owner 不变 | 规则、资产、模型分别由现有服务写入；复用表单不代表复制数据或覆盖未编辑字段 |
| 分析准备无隐藏前置页 | 复用平台源接入能力，在既有服务交付边界恢复；不要求先访问大屏选源 |
| 授权边界 | 延续当前菜单授权约定及独立认证/租户/来源/密级/版本保护，不增加细粒度发布角色门槛 |
| 存储 | F1 的无新增表结论不自动覆盖 F2；唯一性/并发若需迁移，F2-T01/F2-T05 先冻结前向兼容方案 |

| Gate | F2 状态 | 交付物/缺口 | Task |
|---|---|---|---|
| G0 | GAP | 复用 baseline，追加 F2 分析源/质量/登录当前基线；历史观察不当本轮实测 | F2-T01 |
| G1 | GAP | 共享契约已定业务矩阵；精确质量命令、多输出 DTO、字段 owner/CAS 与 NFR 参数待冻结 | F2-T01；F2-T05 接入唯一性 |
| G2 | PENDING | F2-T01 契约、F2-T02 四步向导/统一交付聚合、F2-T03 质量闭环、F2-T04 资产 CAS、F2-T05 精确分析注册主要代码已落地；目录 `canMaintain` 与分析失败映射已在 `3c8`/`c275` 专项通过但尚未部署。`32b7e1309` 分步编辑/权限/转换回归 44/44、帮助主题 6/6 通过；前端正式构建与后续发布验收分别记录，不复用 F1 的源码 PASS | F2-T02–T05 |
| G3 | PENDING | F2 预计包含分析端，正式包/必要迁移与离线验证未执行 | F2-T06 |
| G4 | PENDING | IT-08–IT-18 与运行手册结果未执行 | F2-T06 |

### 向导与生命周期补充（已落地的源码规则，现场验收待执行）

- 四步页面是工作阶段，ModelStatus/候选状态仍由各自现有控制面维护；进入某页不推进生命周期。
- 已具备证据可以跨步查看；回到上游编辑要按字段/实现/规则依赖重算下游资格；URL/浏览器返回不能绕过后端门禁。
- 每页一个主动作，移除重复流程卡片及并列全流程按钮；第三步完成后仍须在第四步确认发布。
- 截图中 CANCELLED 候选与历史目标表构建成功分开呈现，取消或失效候选不能被显示为当前可发布。
- 实施归属：F2-T01 冻结 W/N/I 契约，F2-T02 重构四步页面，F2-T03 落 W3，F2-T04/F2-T05 落 W4，F2-T06 验收 IT-15–IT-17。任务数保持 14，不另加重复任务。

### 页面简洁与帮助收敛

右上角既有“?”承载 W1–W4 规则解释和用法说明，按步骤定位；页面保留操作、状态和必要就地错误/阻断反馈。时间字段长说明作为明确迁移样例，细节见共享 H01–H04。F2-T01/F2-T02/F2-T06 落实契约、界面/帮助和 IT-18 验收；Task 总数不变。

以下 F2-T02-A/F2-T02-B2/F2-T05-A 段落为 2026-09-06 历史切片记录。其后 F2-T02 四步向导与统一交付聚合、F2-T03 质量闭环、F2-T04 资产治理 CAS 和 F2-T05 精确分析注册已继续实现；该历史记录当时的运行容器为 `296605b39f98`，尚未包含 `3c8`、`c275` 或 `375ac377f` 的变更。

F2-T02-B2 已接入首次“保存设计并继续”：业务定义与实现配置分开提交，后续进入既有实现编辑入口。完整四阶段向导已在后续源码切片实现；正式测试、构建、新包部署与浏览器验收仍待完成，进展和历史验证边界见 [F2-T02-B2 验证](it/evidence/F2-T02-B2-definition/verification.md)。

## 2026-09-08 整改执行基线

当前整改按 [审查整改契约](assests/review-remediation-20260908.md) 执行。F3 三步建模是现行规则，历史四步记录不作为模型完成前置。20 项任务均在实施/验收中，未达到 DONE；当前运行受测版本及未通过分支以 IT 记录为准。

## F4 性能稳定性扩展（2026-09-08）

| Feature | Task数 | 优先级 | 状态 |
|---|---:|---|---|
| [F4-模型工作台性能与交付状态稳定性](features/F4-模型工作台性能与交付状态稳定性/README.md) | 4 | P0 | IN_PROGRESS |

### 现状勘察账本 F4 增量
| # | 事实 | 证据 |
|---|---|---|
| F4-1 | 页面10行并发状态请求且任一失败清空整页 | ModelWorkbenchCatalogList.tsx:272 |
| F4-2 | 聚合持有只读事务、质量上下文挂起再申请新事务 | ModelDeliveryStatusQueryService.java:43；CandidateQualityRuleContextService.java:55；DefaultDestinationSyncService.java:164 |
| F4-3 | 首屏等待9组列表/编辑辅助数据 | modelWorkbenchService.ts:568 |
| F4-4 | 现场10连接占满/30s超时、8个闲置事务、浏览器17条首屏全部状态失败 | it/evidence/F4-performance-20260908.md |

| 需求 | Task | 证据 |
|---|---|---|
| 消除连接饥饿 | F4-T01 | IT-25 |
| 单行隔离/限制并发 | F4-T02 | IT-26 |
| 首屏不等辅助数据 | F4-T03 | IT-27 |
| 真实运行与交付验证 | F4-T04 | IT-28 |

F4 Gate：G0=PASS_WITH_GAPS（既有正式环境和登录已实证，新制品性能待测）；G1=PASS（F4契约和非功能预算）；G2/G3/G4=PENDING，由F4-T01–T04跟踪。顺序：F4-T01/F4-T02 → F4-T03 → F4-T04，单代理执行。

F4当前进度：73697f73a源码与83项前端/30项后端专项通过；已按用户要求直接替换原platform/webapp镜像，不使用补丁包。真实浏览器36个交付请求全部200、P95约0.522s，刷新/分页/详情成功；固定并发与Chrome95完整验收仍由F4-T04跟踪。

## F5 建模状态语义与上游引用准入收敛（2026-09-09 修订）

| Feature | Task数 | 优先级 | 状态 |
|---|---:|---|---|
| [F5-建模状态语义与上游引用准入收敛](features/F5-建模状态语义与上游引用准入收敛/README.md) | 7 | P0 | IN_PROGRESS |

保留同规划 DWD/DWS 联合设计能力，分别表达设计状态、已提交实现与物化结果。整改覆盖统一准入、完整版本 pin、实现单独漂移、页面刷新及字段语义校验；不能只改善错误提示而让无效字段继续进入执行。

### 现状勘察账本 F5 增量

| # | 事实 | 证据 |
|---|---|---|
| C27 | ModelStatus 六值中 DESIGNING/VALIDATING/READY_TO_PUBLISH 在 dts-platform/src/main 内零写入点，DRAFT 独自承载全部中间语义 | ModelSpecContract.java:373 |
| C28 | 实现状态是裸 String，全仓 27 处 "ACTIVE".equals 硬编码比较。**其中仅 8 处作用于实现状态**，另 19 处属数据库连接、部署状态、词根、仓库层等无关对象，不在实现状态枚举化范围内；据 27 估算工作量会高估三倍多 | ModelLifecycleContract.java:771；构成核对 2026-09-09 |
| C29 | validateUpstream 的 12 个失败分支共用一个错误码；ValidationResult 只有 boolean+String，结构上装不下上游标识 | ModelImplementationInputPolicy.java:132、:249 |
| C30 | 同语义错误码分散三处，仅物化计划期携带 modelSpecId 明细 | ModelImplementationDependencyService.java:366；ModelReleaseCandidatePreflightService.java:326 |
| C31 | 上游候选来自 listModelSpecs，仅过滤 ARCHIVED；实现步骤过滤只判兼容模式与分层，无实现信息 | modelWorkbenchService.ts:578、:583；ModelImplementationBindingFields.tsx:116 |
| C32 | 前端上游输入回退分支只发三字段，库层 CHECK 要求六字段，靠后端保存期补齐 | modelWorkbenchService.ts:1120；20260724_08_upstream_model_pin_contract.xml |
| C33 | ModelSpecView 被 111 个文件引用，ImplementationView 31 个，DependencyNode 仅 4 处消费 | 引用计数，2026-09-09 |
| C34 | 发布预检只在修订漂移分支检查上游状态，被钉修订为 ARCHIVED 且未漂移时不拦 | ModelReleaseCandidatePreflightService.java:305、:323 |
| C35 | 发布预检的 upstream.status 取自被钉修订快照而非模型当前状态，该语义正确 | ModelSpecApplicationService.java:1521 |
| C36 | 依赖图端点已返回 pinnedRevision/currentRevision/state/status，是可扩展的既有 seam | ModelSpecResource.java:154；ModelSpecApplicationService.java:2216 |

以上 C27–C36 保留为首次源码勘察记录；以下补充纠正其设计推论，静态分支发现不等同于真实路径已复现。

| # | 补充事实与约束 | 证据/归属 |
|---|---|---|
| C37 | 首次选择由服务端补齐实现 pin 是既有有效协议；不能以数据库六字段 CHECK 推导三字段请求一律错误 | pinCurrentUpstreamImplementations；F5-T05 |
| C38 | 正常归档入口已有活动引用保护；C34 的可达性须用正常业务路径核实，禁止直接改库造样本 | ModelSpecApplicationService.archive；F5-T01、F5-T05 |
| C39 | 设计版本未变而实现版本变化也会导致 pin 漂移；仅依赖 C36 的设计版本图不足以表达 | ModelImplementationInputPolicy；F5-T02、F5-T04 |
| C40 | cost_amount 事故暴露来源字段语义校验缺口，与状态展示问题分开整改。缺口精确位置：requiredSourceField 仅校验标识符正则形状，sourceExpression 仅校验别名下标未越界，二者均不校验字段名存在性 | assests/dws-cost-field-issue-20260909.md；ModelLifecycleContract.java:726；ModelingDbtCompiler.java:707；F5-T07 |
| C41 | 批量投影不能逐项调用带查询/递归加载的校验；应批量装载上下文并复用纯判定 | F5-T03 → F5-T02 |
| C42 | 引用入口同样拒绝已归档目标，isCanonicalReferenceTarget 显式排除 ARCHIVED，validateReferenceSet 经由它拒绝新建引用。与 C38 的归档入口保护构成两侧对堵，CURRENT+ARCHIVED 在正常串行路径不可达 | ModelSpecContract.java:307；ModelSpecApplicationService.java:1806 |
| C43 | 归档在更新头记录后同步调用 updateV2RevisionLifecycle，被钉修订快照状态会变为 ARCHIVED，故 DependencyNode.status 技术上可取到该值，C34 的分支不是死代码 | ModelSpecApplicationService.java:1338 |
| C44 | hasActiveModelReferences 只检查当前头记录的 depends_on/dimension_refs，不查历史修订；结合 C42/C43，CURRENT+ARCHIVED 的唯一残留路径是归档检查与下游引用保存并发 | ModelSpecRepository.java:61–81 |

### F5 架构决策

| 决策 | 选择与理由 |
|---|---|
| 独立只读投影 | 不扩展 ModelSpecView 或 DependencyNode；候选与已选输入统一使用带 owner 上下文的批量可用性契约 |
| 规则单一来源 | F5-T03 先分离批量上下文装载与纯准入判定，F5-T02 和写入入口复用；ACTIVE 本身不等于 selectable |
| 完整引用快照 | 六元组包含 implementationChecksum；同一快照返回，用户明确更新已有 pin；同时识别设计和实现漂移 |
| 兼容首次引用 | 保留服务端首次 pin 补齐与旧客户端协议；已有完整 pin 不自动降级或重钉 |
| 范围控制 | 不重构持久化状态枚举，不删除历史状态值；投影状态不替代业务生命周期 |
| 归档边界 | 先核实正常入口保护与预检分支可达性，再补已证实缺口；不改库构造测试状态 |
| 字段校验 | F5-T07 从固定版本字段契约提供选择项，并在提交前共用语义校验；保留可编辑草稿，不以物化成功代替字段校验 |
| 刷新与权限 | 返回页面、聚焦、步骤切换和手动刷新均覆盖；过期响应不覆盖新状态，网络故障不清除已确认拒绝 |
| 验收方式 | 新增验收使用外部 Chrome，不扩展代码级测试；源码、正式交付、部署和真实页面分别记录 |

| 需求 | Task | 计划验收 |
|---|---|---|
| 三种草稿样本与分阶段准入 | F5-T01、F5-T03、F5-T04 | IT-29 |
| 可用性、双轴漂移与刷新 | F5-T02、F5-T04 | IT-30 |
| 结构化拒绝与权限隔离 | F5-T03、F5-T04 | IT-31 |
| 六字段选择/保存/回显与归档保护 | F5-T04、F5-T05 | IT-32（归档预检分支未覆盖继续由 F5-T05 跟踪，闭合前不标 F5 DONE） |
| 批量性能、正式交付及浏览器兼容 | F5-T02、F5-T06 | IT-33 |
| 错误来源字段提前拦截与修正闭环 | F5-T07 | IT-34 |
| 多来源歧义、字段目录及草稿兼容 | F5-T07 | IT-35 |

F5 Gate：G0 复用既有基线，运行时仍核对实际环境；G1=PASS，F5-T01 的实施契约与只读基线已冻结；实际查询预算达成仍由 F5-T06 验证。G2/G3/G4=PENDING，由 F5-T06 跟踪。

执行顺序（单代理）：**F5-T01 → F5-T03 → F5-T02 → F5-T04 → F5-T07 → F5-T05 → F5-T06**。F5-T04 提供完整 pin 的选择、保存、回显和来源顺序，F5-T07 消费该固定输入；F5-T05 只负责归档防御。F5-T01 已完成契约冻结，其余六项编码完成、运行分项验收中；最终证据见 assests/F5-acceptance-20260910.md。


## F6 指标计算口径与资产 BI 协作闭环（2026-09-10）

新增 [F6](features/F6-指标计算口径与资产BI协作闭环/README.md)，8 个 Task（F6-T01–T08），全部 DRAFT。本增量承接指标 review；不改变 F1–F5 的状态及验收约定。

| Feature | Task 数 | 优先级 | 状态 |
|---|---:|---|---|
| [F6-指标计算口径与资产BI协作闭环](features/F6-指标计算口径与资产BI协作闭环/README.md) | 8 | P1 | IN_PROGRESS |

**ADR 增量**：公共口径复用平台指标 owner；模型与上游指标分别精确 pin；显式区分公式计算与预计算结果，不默认 MAX；BI 投影在既有 platform serving 链（`CatalogModelSemanticSyncWorker`→命令服务→载荷工厂→只读指标适配器）上扩展，复用其持久化状态与受控重试，不另建控制面；`dts-metrics` 注册链在 platform 侧零调用点，明确排除。

**契约/Context Ledger（实施前基线）**：[K61–K66、C43–C55、开工缺口与预算](assests/F6-metric-asset-bi-contract.md)。C48 已按第二轮只读核验更正（投影链存在），C50–C55 为补录事实：派生/复合零通路、wire 契约无版本位、指标发布不触发同步、既有状态与重试可复用、dts-metrics 排除、依赖按 code 全局取首条。UI→平台定义/版本→资产与模型→查询/分析注册→BI 卡片→看板链路以此为增量；精确协议未冻结，禁止提前标 READY。

| 需求 | Task | 验收 |
|---|---|---|
| 基线与 owner/契约核实 | F6-T01 | IT-36 |
| 实现模型固定版本 | F6-T02 | IT-37 |
| 执行方式与正确粒度 | F6-T03 | IT-38 |
| 上游版本及历史追溯 | F6-T04 | IT-39 |
| 修饰词、时间与多维查询 | F6-T05 | IT-40 |
| 资产发布投影与 BI 引用 | F6-T06 | IT-41 |
| 页面状态/异常反馈 | F6-T07 | IT-42 |
| 正式交付与真实闭环 | F6-T08 | IT-43 |

**Gate 增量**：G0=GAP、G1=GAP（F6-T01）；G2=PENDING（F6-T02–T07）；G3/G4=PENDING（F6-T08）。本次只完成规划文档及静态一致性检查，未启动实现/构建/部署/运行验收。

**排期约束**：F6 不参与本 Sprint 收敛。F3 离线目标待指定、F4 Chrome95 与并发验收、F5 归档预检分支三项 P0/P1 缺口闭合前不启动 F6-T01。F6-T01 的核实跨 platform/analytics 两服务且需关闭三处结构缺口（C50–C52），工作量不视为轻量前置；若 F3–F5 收敛期延长，F6 整体转入 Sprint-105，不拆分穿插。

**最新全 Sprint 统计（覆盖上文历史统计）**：6 个 Feature、39 个 Task；DRAFT=8，READY=0，IN_PROGRESS=29，DONE=2，BLOCKED=0。执行顺序 F6-T01→F6-T02→F6-T03→F6-T04→F6-T05→F6-T06→F6-T07→F6-T08；单代理。


## 文档完整性缺口（2026-09-10 登记）

静态校验发现 11 份被引用的文档在本 Sprint 目录内不存在，且经 `git log --all --diff-filter=A` 确认**从未提交过任何分支**。引用方仍以正常链接呈现，读者会误认为契约已落盘。

| 缺失文档 | 被引用处 | 影响 |
|---|---|---|
| `assests/F3-modeling-data-boundary.md` | F2 README、F3 README、F3-T01–T05、it/README、本文件 | F3-T01 的「建模完成边界与增量契约冻结」无落盘产物 |
| `assests/delivery-workflow-contract.md` | F2 README、F2-T01–T06、it/README、本文件 | F2 交付契约无落盘产物 |
| `assests/F2-T01-contract-freeze.md` | F2 README、F2-T01、F2-T03、it/baseline.md | F2-T01 的「契约冻结完成」结论无落盘产物 |
| `assests/F2-T02-B2-definition-save-contract.md` | F2-T02 | 定义保存契约缺失 |
| `assests/contract-matrix.md` | F1-T02 | F1 三端字段矩阵缺失 |
| `assests/backend-contract-evidence.md` | F1-T07 | F1 后端契约证据缺失 |
| `assests/delivery-contract-fixtures.json` | F2 README、F2-T01 | 交付契约夹具缺失 |
| `assests/browser-test-issues-20260908.md` | F1-T05、F1-T06、it/evidence/…/browser-round1-20260908.md | 浏览器问题清单缺失 |
| `assests/capability-matrix.md` | it/手工验收用例-20260907.md | 能力矩阵缺失 |
| `assests/domain-profile.md`、`assests/nfr-budget.md`、`assests/review-evidence.md`、`assests/review-remediation-20260908.md` | 本文件 | 域画像、非功能预算与 review 证据缺失 |

处理要求：**不得补写内容冒充当时冻结结论**。每项二选一——把当时的真实产物补进来并保留原始时间，或把引用改成「未落盘」并同步下调对应 Task 的冻结声明（当前 sprint-queue 中 F2-T01/F3-T01 标注为「契约冻结完成」，与本表冲突）。该缺口不属于 F6，归 F1–F3 owner 处理，F6 的 C43–C55 与 K61–K66 已按新约定全部落盘在 [F6 契约](assests/F6-metric-asset-bi-contract.md)。

另：本 Sprint 目录名为 `assests`（拼写错误），其余 Sprint 统一使用 `assets`。共 44 个文件引用该路径，重命名需一次性替换，未执行，待决策。

**F6 文档复审修订**：模型级状态与指标版本交付分离；多维公式聚合后按公共键对齐；存量按模式分类；F6-T01 仅冻结契约与基线，不依赖实现完成。C48 源码已确认，运行待证。详见 F6 统一契约，任务状态与排期不变。

**F6 最新实施统计**：按本轮用户授权开始编码并统一后置测试；全 Sprint DRAFT=0、IN_PROGRESS=37、DONE=2，其他状态0；覆盖此前统计。见 [实施记录](assests/F6-implementation-20260910.md)，没有新增验收完成项。

**F6 编码收尾**：编码及专项验证完成（后端88项、前端40项、前端正式构建）；交付包、部署和真实页面验收未完成。F6-T01–T08仍为IN_PROGRESS，不增加DONE计数。


## F8 质量规则运行契约与失败反馈重构（2026-09-10）

新增 [F8](features/F8-质量规则运行契约与失败反馈重构/README.md)，7 个 Task（F8-T01–T07）；用户已授权实现，F8-T01 DONE，其余 IN_PROGRESS。来源为 2026-09-10 两次真实运行失败（`727cbc0c-…` DATASET_SCOPE_BLOCKED、`a06452ba-…` RESULT_ID_REQUIRED）及"编辑时输入普通 SQL 为何不兼容"的追问。不改变 F1–F7 的状态及验收约定。

| Feature | Task 数 | 优先级 | 状态 |
|---|---:|---|---|
| [F8-质量规则运行契约与失败反馈重构](features/F8-质量规则运行契约与失败反馈重构/README.md) | 7 | P0 | IN_PROGRESS |

**ADR 增量**：质量规则 SQL 的约束按性质分三类——真实安全边界（只读、只引用绑定资产）保留并**前移到保存期**；实现过窄（函数白名单按拼写而非能力）修正粒度；实现泄漏（必须返回 `id` 列）直接去除。业务结论与执行故障拆为两个独立维度，平台故障不得压制已得出的违规结论，也不再生成待认领业务工单。试跑复用后端既有 `POST /api/governance/quality/runs/dry-run`，不新建预检服务。存量规则处置以计数为据，不预设策略。

**契约/Context Ledger（实施前基线）**：[C56–C70、K67–K72、开放问题与预算](assests/F8-quality-rule-execution-contract.md)。关键事实：保存只校验非空（C56）；当前两道预检发生在目标连接和总行数查询之后；F8-T05 前移到目标连接之前（C57）；失败行统计硬取 `id` 列且只在已查出违规后执行（C60/C61）；现网两张演示表均无 `id` 列（C62）；基础设施错误压制业务结论（C63）；工单不分故障类型（C64）；文案映射只覆盖 8 类（C66）；编辑页未声明真实契约且占位示例必踩坑（C67）；试跑接口已存在但前端从未接入（C68）。

| 需求 | Task | 验收 |
|---|---|---|
| 基线画像与契约冻结 | F8-T01 | IT-48 |
| 失败统计去 id 依赖 | F8-T02 | IT-49 |
| 业务结论与执行故障分离 | F8-T03 | IT-50 |
| 失败分类文案与原因透出 | F8-T04 | IT-51 |
| 保存前静态校验与契约提示 | F8-T05 | IT-52 |
| 工单准入 | F8-T06 | IT-53 |
| 正式交付与真实闭环 | F8-T07 | IT-54 |

**已落地的先行修复（不计入 F8 完成度）**：提交 `4c58ce207` 已补 `btrim/ltrim/rtrim` 并将作用域校验器的布尔结果改为结构化 `ScopeCheck{allowed,reasonCode,detail}`（C58/C70），源码专项 8/8 与 18/18 通过；正式构建、部署与页面验收未执行。该提交是 F8-T04/F8-T05 的输入，不代表 F8 任何 Task 完成。

**Gate 增量**：G0/G1=PASS（F8-T01 已冻结）；G2 源码与专项验证见 [实施验证记录](assests/F8-verification-20260910.md)；G3/G4=PENDING（正式交付/部署、真实业务闭环与 Chrome95 未完成）。

**排期约束**：F8 的 F8-T02/F8-T03/F8-T05 属 P0 缺陷修复，可与 F6/F7 并行推进；F8-T01 必须先于其余六项完成，其四项开放问题（存量不可运行计数、多语句占比、历史 RESULT_ID_REQUIRED 计数、试跑权限一致性）未关闭前不得开工。

## F9 部门公共层与 ADS 共享权限链收敛（2026-09-12）

[F9 及九个 Task](features/F9-部门公共层与ADS共享权限链收敛/README.md) 已完成本轮编码与统一自动化验证，整体保持 IN_PROGRESS。后端230项、前端102项、TypeScript及Chrome95目标构建通过；正式镜像/包、部署、真实页面和回退尚未执行。见 [F9验收报告](assests/F9-acceptance-20260912.md)。F1–F8 状态及历史证据保留，F7 的菜单授权/租户上下文在 F9 正式交付后按替代映射收敛。

**设计依据**：[D1–D9、C71–C100、K73–K92、S01–S26、Q1–Q10](assests/F9-permission-chain-contract.md)。本轮补齐精确部门、稳定身份、当前属性/旧会话撤权、全部来源引用、实际操作范围与 USER/SYSTEM、双服务发布职责、404 防枚举、公共层生命周期。大屏零改动，模型编辑与数据消费权限独立；相似提示减少重复，不承诺语义绝对唯一。

| 范围 | Task | 验收 |
|---|---|---|
| 分组契约与基线 | F9-T01 | IT-55 |
| 独立目录密级封堵 | F9-T02 | IT-56 |
| 稳定身份、当前属性与角色职责 | F9-T03 | IT-57 |
| 部门公共层唯一/精确范围/生命周期 | F9-T04 | IT-58 |
| 公共层维护、来源准入与后台基础 | F9-T05 | IT-59、IT-60 |
| ADS ACL、实际批量范围与后台接入 | F9-T06 | IT-61 |
| 通用跨部门入口与既有大屏链核对 | F9-T07 | IT-62 |
| 同部门相似模型提示 | F9-T08 | IT-63 |
| 分批正式验证与完整交付验收 | F9-T09 | IT-55–IT-63 |

**排期与冻结**：T01 优先冻结 K78/审计子集，T02 可独立修复、测试和交付，不等 Q5 历史扫描/恢复决定。T03→T04→T05→T06 为完整建模权限切片；T08 在 T04 后独立排期；本轮按用户要求，全部编码后由主代理统一测试并完成失败整改；真实发布/验收按单独阶段执行。

**存量与依赖**：建模为本版本新增、现场无存量，仅测试环境做旧键/创建人解析预检；目录、会话、角色菜单和大屏均为既有功能。新增稳定会话身份与目录当前属性适配归 T03，T06 复用；不全局替换大屏 sub/ACL。Q5 仍是现场历史风险采集；Q8 身份字段/扩展点、Q9 全入口作用集合、Q10 规范部门/来源映射由 T01 冻结，不再写成“仅剩 Q5”。

**Gate**：G0 产品边界 PASS；本机预检 PASS、客户基线 GAP；G1 技术适配 PASS、性能预算实测 GAP；G2 源码/自动化 PASS，G3/G4 PENDING。文档集中复核见 [F9 设计复核](assests/F9-design-review-20260912.md)；[IT-55–IT-63](it/F9-权限链验收.md) 已回填自动化证据，真实分支未执行，T07 登记 GAP 不代表 F9 通过。

**最新全 Sprint 统计（覆盖上文历史统计）**：9 个 Feature、59 个 Task；按任务文件状态统计 DRAFT=0，READY=0，IN_PROGRESS=56，DONE=3，BLOCKED=0。

## F10 菜单信息架构按实施主线收敛（2026-09-16，第 2 版）

新增 [F10](features/F10-菜单信息架构按实施主线收敛/README.md)，4 个 Task（F10-T01–T04）；F10-T01 勘察已开展为 IN_PROGRESS，其余 DRAFT；未授权编码、迁移或部署。来源为用户提出的“除数据集成外，其他功能需要在不同菜单间跳转”问题。第 2 版按用户意见收敛：名称以技术协议为准且现有名称已基本一致，不改名、不移动节点；跨菜单跳转尽量避免，无法避免的最多一次；大屏管理为一级菜单是客户硬性要求，F10 不改动。不改变 F1–F9 的状态及验收约定。

| Feature | Task 数 | 优先级 | 状态 |
|---|---:|---|---|
| [F10-菜单信息架构按实施主线收敛](features/F10-菜单信息架构按实施主线收敛/README.md) | 4 | P1 | DRAFT |

**ADR 增量**：

| 决策 | 选择与约束 |
|---|---|
| 一级顺序 | 工作台、数仓规划、数据集成、数据建模、数据开发与运维、数据治理、数据分析与服务，与技术协议 2.3.2 章节顺序一致；大屏管理保持一级且不改动；不显示序号 |
| 改动范围 | 只改菜单排序、实施引导顺序与跨菜单链接落点；名称、层级、页面地址、可见性不变 |
| 子项顺序 | 数据建模以数据标准开头、关系图留在建模；数据治理中分级分类在质量之前；数据分析与服务中服务在分析之前 |
| 跳转 | 跨菜单链接必须直达目标对象并可返回；模型构建结果的“配置质量规则”直达资产侧质量配置 |
| 质量时机 | 质量要求随数据标准提出；规则配置与执行在资产登记之后、发布之前，入口在数据治理 |
| 迁移方式 | 沿用既有菜单迁移范式：按 titleKey 定位、只改排序、快照可回滚、种子文件与种子哈希同一提交 |

**契约/Context Ledger**：[M1–M5、版本修订、当前与目标菜单树、排序清单、跳转场景、质量时机、C101–C125、K93–K101、Q11–Q15](assests/F10-menu-ia-contract.md)。关键事实：现有菜单中数据集成排在数仓规划之前，与协议和实施顺序相反（C102、C125）；资产登记前不能配质量规则、当时BLOCKING策略下质量通过前不能发布（C120、C121；现行质量两策略以本轮F13设计为准）；质量配置已在资产侧，但模型构建结果的资产按钮落到数据集详情首页（C122、C123）；种子哈希不一致时读菜单会补建节点并软删除种子外根菜单（C111）。

| 需求 | Task | 验收 |
|---|---|---|
| 排序清单、直达参数、基线与现场核对方法冻结 | F10-T01 | IT-64 |
| 菜单排序迁移、种子同步、可见性与大屏零改动 | F10-T02 | IT-65、IT-66 |
| 实施引导顺序与跨菜单直达 | F10-T03 | IT-67 |
| 同 SHA 正式交付与真实走查 | F10-T04 | IT-64–IT-67 |

**Gate 增量**：G0 产品边界 PASS；G0 现场基线 GAP（Q14 客户现场菜单唯一性未核对）；G1 契约 GAP（K93–K101 拟定，直达参数待冻结）；G2/G3/G4 PENDING。

**排期约束**：T01 先行；T02 与 T03 可并行。客户现场升级前必须先跑唯一性核对 SQL。

**实施进展（2026-09-16）**：用户授权编码后，F10-T01–T03 源码完成，见 [F10 实施记录](assests/F10-implementation-20260916.md)；正式交付、部署与真实验收未执行。

**最新全 Sprint 统计（覆盖上文历史统计）**：10 个 Feature、63 个 Task；按任务文件状态统计 DRAFT=1，READY=0，IN_PROGRESS=59，DONE=3，BLOCKED=0。

## F11 权限模型统一与 RBAC 重构（2026-09-20 review 复核，架构完善）

[F11](features/F11-权限模型统一与RBAC重构/README.md) 已按用户要求写入人员/部门/Keycloak 职责划分并完善重构架构，现有 9 个 Task（T01–T09）。当前为 IN_PROGRESS（设计），未授权重构编码、数据库迁移或发布；T01 因原证据错误与现场核验缺口由 DONE 改为 IN_PROGRESS，T02–T09 均 DRAFT。

**设计主文档**：[权限重构架构与实施契约](assests/F11-permission-architecture.md)。**Context Ledger**：[修订现状及 F11-C01–C11](assests/F11-permission-model-survey-20260919.md)。**测试设计**：[IT-68–IT-79](it/F11-身份与权限重构验收.md)，全部 NOT_RUN。

**待合并的同域草案**：[建模版本语义梳理与改造方案](tbd.md)（DRAFT）。建模侧的候选单版本承担并发令牌与构建身份双重语义，与 F11 的执行身份、发起人反查同域；F11 合并该身份契约及必要的迁移、回归，草案中其余版本/前端改造继续保留 DRAFT，实施范围另行确定。

**2026-09-20 review 复核结论**：抽查的现状断言（508 处 `@PreAuthorize`、`kc_id` 非空唯一、`admin_role_assignment` 带 scope/dataset/operations、`PolicyService` 的 OBJECT/FIELD/ROW、F9 的 2 秒预算、IT 编号与任务统计）全部属实。本轮增补：设计基线更新到 `db17adfda`；personCode 大小写与用户名分配按 `db17adfda` 记为已定（Q21 部分关闭）；发起人反查纳入 T01/T02；新增 Q25（目录中断与建模重试预算冲突）、Q26（无降级方案的可用性代价）；建模身份测试夹具归 T02 交付、T09 执行。

**后续文档复核（52bc4ce4c）**：用户名首次分配实际受导入顺序影响；手工运行已按派发 ID 读取封存发起人，绑定版本/范围检查须保留。已修正主契约、任务及 IT-70，补齐菜单一次解析的组合协议，区分失败关闭与待验收的可用性。完整设计见 [F11 架构设计总览](features/F11-权限模型统一与RBAC重构/架构设计总览.md)。本次仅源码阅读与文档静态检查，不改变 G0/G1 GAP 和验收 NOT_RUN 状态。

架构工作基线：MDM 管人员/组织字段，DTS 管有效业务目录与授权，Keycloak 管认证账号；每类字段唯一写入来源。保留 organization_node，演进 admin_keycloak_user 支持待开户和字段分源，person_profile 在全部消费者迁移后只读归档。统一当前身份、不跨请求缓存允许；业务动作权限保留授权范围，菜单服务委托和对象/后台决策形成完整链路，继承 F9 的部门精确隔离、编辑与消费/审核分离。

本轮修正了“没有权限码写入方”“六套策略互不调用”“旧人员表只有两个读取点”等错误；组织删除当前已查新快照/Keycloak，MDM 原始 status 不等于已确认离职状态。历史 realm/账号数量和注解计数未重验，不作为当前实施覆盖证明。

| Feature | Task 数 | 优先级 | 状态 |
|---|---|---|---|
| [F11-权限模型统一与RBAC重构](features/F11-权限模型统一与RBAC重构/README.md) | 9 | P1 | IN_PROGRESS（设计） |

**任务与切片队列**：T01 + T08/T09 设计 → T07/T02 身份目录切片 → T03/T04/T05 首个权限闭环 → 按资源域扩面 → T06 对应历史清理；T08/T09 随批次迁移/验收。实际 owner、容量、时间盒和实施批次待排，不把整个重构默认承诺在本 Sprint 完成。v2.2.3 当前没有 sprint-queue.md，本节承载 F11 队列，不改 v2.2.2 的历史队列。

**Gate 增量**：G0=GAP（当前环境/数据未核验）；G1=GAP（架构和测试设计已落盘，Q17/Q20–Q24 的矩阵/政策/上游协议与特殊账号等输入待按切片冻结）；G2/G3/G4=PENDING。Q16 采用分域方案 B、Q19 采用扩展→回填→对比→切换→收缩的设计选择，不等于现场迁移已获准或完成。

**最新全 Sprint 统计（覆盖上文历史统计，按实际任务文件）**：11 个 Feature、72 个 Task；DRAFT=9，READY=0，IN_PROGRESS=60，DONE=3，BLOCKED=0。F11 单独为 IN_PROGRESS=1、DRAFT=8；本次未改变其他 Feature/Task 状态。

**证据状态**：仅完成文档、源码复核和文档静态检查；重构源码/测试、正式构建/交付包、部署、真实页面与回退演练均未执行。

## F12 业务应用 ZIP 全链路导入（2026-09-21）

新增 [F12 功能与任务](features/F12-业务应用ZIP全链路导入/README.md)，8 个 Task；先完善模型 ZIP 的来源登记、整包配置与新建对象重置，再开展查询数据集、分析卡片、大屏的统一包导入。业务中心在本需求中指业务过程。完整方案见 [架构与包契约](features/F12-业务应用ZIP全链路导入/架构与包契约.md)，用例使用独立编号 F12-IT-01–29。

F12-T01=DONE（设计基线），T02=IN_PROGRESS（PJM 43草稿主路径已通过，反例/兼容待验收），T03–T08=DRAFT；不改变 F1–F11 状态。F12当前共8个任务（新增T08解决保留审计的重置/同包重导），不把全链路设计完成计作 Feature 完成，不沿用上文历史总数推导实时进度。执行队列见 Feature；无新增菜单，复用既有各领域服务与导入向导。

## F13 / F14：发布质量处理与模型构建恢复（修订2026-09-24）

用户确认F13尚未编码，并要求先完善F13文档、新建F14，后续两个Feature联合编码。本节替代原F13草案摘要，不把设计完成写成实施完成。

| Feature | 用户结果 | Task数 | 状态 |
|---|---|---|---|
| [F13 发布质量处理与失败恢复](features/F13-发布质量对账状态机与阻断可见性/README.md) | 构建完成后资产登记、质量检查、原因/责任/操作和安全恢复 | 8 | IN_PROGRESS（首批后端；见实施记录） |
| [F14 模型构建与失败恢复](features/F14-模型构建与失败恢复/README.md) | 构建前检查、阶段结果、首发错误及核验/回写续跑，防止重复SQL | 8 | IN_PROGRESS（首批后端；见实施记录） |

**共享设计**：[F13/F14联合设计与实施顺序](assests/F13-F14-联合设计与实施顺序.md)。两Feature共同确定运行标识、身份、错误表达和共享页面/API；M0联合核对→M1最早真实切片→M2故障恢复→M3界面→M4共同交付。联合编码不意味着无依赖地同时修改共享文件，具体文件由集成人统一修改。

**本轮架构决策**：状态写入职责、完成结果持久交接、恢复operation与原失败任务隔离、质量qualityRoundId及ADVISORY/BLOCKING发布矩阵已补齐，见联合设计A01–A05。保留通过时冻结结果，不修改现场策略；G1仍待实际表/事务适配核对。M2完成F13-T06后端，M3与T05一起验收页面，修正原依赖倒置。

**F13修订**：不再把候选版本变化当已证实登记根因；保留旧refresh使旧单失效语义；新增立即检查命令仅持久化wake；补齐并发领取、唤醒不丢失、等待超时、身份与回写、旧行清理、上下文动作和真实集成验证。主设计：[F13](assests/F13-release-quality-reconcile-contract.md)。

**F14新增**：复用现有派发恢复/超时/放弃能力，补全构建阶段证据与受控终态恢复；没有SQL成功及完整文件/版本证明时禁止自动续跑。治理质量失败不能抹掉已完成构建，SCHEMA_ONLY不声称业务数据已生成。主设计：[F14](assests/F14-模型构建与失败恢复设计.md)。

**范围衔接**：F1/F3/F5模型及来源规则、F8规则运行、F11权限体系保持原职责，F13/F14只补齐对应执行与恢复协作；F12导入不重写。评审流程和跨引擎SQL事务回滚不在范围。旧任务未验收项保留原标识，不因新Feature登记而自动完成。

**测试设计**：[F13-IT-01–21](it/F13-发布质量对账验收.md)、[F14-IT-01–20](it/F14-模型构建与失败恢复验收.md)，全部NOT_RUN。关键路径包括真实数据库与身份回写、Java/Python接口、SQL成功后继续核验不重跑、并发/迟到回调/重启、首次及重复/修改后构建、质量重跑、Chrome95、旧模型与回退。

**新增用例**：[F13单元16项](features/F13-发布质量对账状态机与阻断可见性/单元测试用例.md)、[F14单元17项](features/F14-模型构建与失败恢复/单元测试用例.md)、[联合系统12场景](it/F13-F14-系统测试用例.md)。系统细化41项IT，按层次分别记录；首批自动化测试结果见实施记录，整项IT/ST仍NOT_RUN。

**统计与容量**：共16Task，READY=2、DRAFT=11、IN_PROGRESS=3、DONE=0、BLOCKED=0。F13原11人日估算不再适用，M0后按完整联合范围与可用人员重估；共同制品/部署计一次，不虚构日程承诺。

**Gate增量**：G0=GAP（历史运行基线需复核）；G1=GAP（两个T01共同完成M0）；G2/G3/G4=PENDING。不修改F1–F12历史状态。首批后端和定向测试开始实施；正式构建、部署和页面验收未执行。历史现场数量与Jira修复结果不是本次PASS。

2026-09-24：F13-T03 与 F14-T02 首批后端开始实施，续批进入 F14-T03 准备阶段来源拒绝处理，见[编码与测试记录](assests/F13-F14-首批编码与测试记录.md)。其余设计核对、持久化恢复及验收状态不因本批单元测试改变。

## F15：构建与发布分离及交付状态解耦（重建2026-09-24）

按用户要求删除旧 F15 主文档和任务描述，重建为 [F15 独立操作方案](features/F15-构建与发布分离及交付状态解耦/README.md)。建模按模式确认构建结果后即可结束；登记、质量、治理、发布、上线与运行分别在所属功能办理。下游失败不改变已确认构建，只阻止真正依赖该结果的操作。

F15 不再组织统一阶段页面。F14-T06 通过 F15-T02 提供构建内容；F13-T05/T06 通过 F15-T03/T04 提供发布摘要及资产/质量处理入口。必要工程检查、独立构建与候选关系、状态写入及持久交接由 T01 核对，不能预先承诺只改前端或将治理命令自动并入构建。

6 个 Task：T01 设计核对 READY，T02–T06 DRAFT；人员和容量待安排。[F15-IT-01–12](it/F15-独立流程验收.md) 覆盖独立完成、失败恢复、版本/权限、旧入口及正式交付，均为 NOT_RUN。G0/G1=GAP，G2/G3/G4=PENDING；本次仅文档重建，源码、测试、正式包、部署和真实页面验收均未执行新版改造。
