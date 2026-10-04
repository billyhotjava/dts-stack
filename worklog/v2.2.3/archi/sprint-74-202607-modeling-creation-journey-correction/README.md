# Sprint-74：建模创建旅程与阶段边界纠偏

**时间**：2026-07  
**状态**：DONE（2026-07-27 实现、部署、验收与影响审计完成）
**类型**：Product Journey / Modeling Contract / UI Convergence / Safe Compatibility  
**目标**：让建模人员先根据业务目的选择正确模型类型，独立完成逻辑设计，再按需选择普通配置或高级 dbt 形成实现，并且只在真实发布后查看物理结果；页面在每个阶段只提示当前必须处理的事项。

## 1. 背景与价值

用户在“财务规划1”中新建“财务项目模型”后，系统默认创建为 `FACT / DWD`，随后同时展示逻辑、实现、治理、质量、构建、发布和密级传播缺口。结果是用户无法判断：

1. 当前模型类型是否选对；
2. 完成逻辑建模到底必须填写什么；
3. 为什么尚未完成逻辑设计就必须配置“数据实现”；
4. “物理资产”是否等于高级 dbt；
5. 九项发布缺口中哪些属于当前必修，哪些属于以后阶段。

该问题不是增加帮助文案即可解决，而是模型类型默认值、逻辑/实现对象所有权、阶段门禁和页面主动作未形成同一条契约链。

本 Sprint 参考：

- [DataWorks 数据规划与数仓分层](https://help.aliyun.com/zh/dataworks/user-guide/data-planning-overview)
- [DataWorks 数据建模概述](https://help.aliyun.com/zh/dataworks/user-guide/data-modeling-overview/)
- [DataWorks 维度建模](https://help.aliyun.com/zh/dataworks/user-guide/dimensional-modeling//)
- [DataWorks 创建维度表](https://help.aliyun.com/zh/dataworks/user-guide/create-a-dimension-table)
- [DataWorks 创建事实表](https://help.aliyun.com/zh/dataworks/user-guide/create-a-fact-table)
- [DataWorks 发布与物化](https://help.aliyun.com/zh/dataworks/user-guide/publish-and-materialize-a-table)
- [Kimball 维度建模四步法参考材料](https://www.cnblogs.com/itlz/p/14262577.html)

参考资料用于校正产品顺序，不替换 DTS 已有 canonical 对象与发布控制面。

2026-07-26 二次复审明确：参考 DataWorks 是为了校正“规划/逻辑设计/实现/发布结果”的顺序，不代表 v2.2.3 必须复制其独立 DIM 层。本 Sprint 保持 Sprint-73 的 `DIMENSION→DWD` 兼容决定，不新增 `DIM` 枚举或五层策略；模型类型与目标层只在概念、契约和 UI 呈现上明确分离。

## 2. 目标用户旅程

```text
选择业务目的
  → 明确每行代表什么
  → 选择模型类型（无默认值）
  → 保存最小草稿
  → 完成类型专属逻辑设计
  → DESIGNED（可停留、评审和共享，不要求实现）
  → 需要发布时选择实现方式
       ├─ 普通配置（DESIGNER_GENERATED）
       └─ 高级 dbt（DBT_MANAGED）
  → 验证实现
  → 进入 Sprint-69 发布控制面
  → PUBLISHED 后查看发布结果、物理资产和血缘
```

对“财务项目模型”的建议路径是：若它描述的是“可用于分类、筛选和汇总的项目主数据”，应通过受控纠错从 `FACT` 调整为 `DIMENSION`；若它描述的是“每一次项目立项、付款或变更事件”，才保持 `FACT`。系统提供判断和预检，但不替用户静默改型。

## 3. 架构决策记录（ADR）

| ID | 决策点 | 选择 | 理由 | 影响 |
|---|---|---|---|---|
| ADR-74-01 | 创建时默认模型类型 | 不设置默认值；用户必须先选业务目的和模型类型 | 默认 `FACT` 是本次误建的直接原因 | 创建抽屉、深链参数、契约测试 |
| ADR-74-02 | 逻辑设计的完成边界 | 新增 `DESIGNED` 门禁；逻辑模型可停留在 DESIGNED，不要求来源、dbt、物理表或发布证据 | 逻辑建模与数据实现是两个可独立完成的工作成果 | 后端门禁、主动作、页面阶段 |
| ADR-74-03 | canonical 对象所有权 | 延续 `DimensionDefinition → ModelSpecRevision → ModelImplementation → Release/Physical evidence`；禁止平行台账 | 复用 Sprint-67/69 已有 seam | ModelSpec、lifecycle、release candidate |
| ADR-74-04 | 物理名称、装载、分区、保留期 | canonical 新写归 `ModelImplementation.settings`，不得作为 `ModelSpecRevision` 的逻辑字段或 DESIGNED 门禁；Sprint-73 已提交字段走兼容迁移 | 这些内容决定如何生成，不定义业务语义；历史 snapshot 又必须保持可读 | F3/T01、F4/T03 |
| ADR-74-05 | dbt 与物理资产关系 | dbt 是实现引擎之一；入口位于“数据实现”。“发布结果”只读展示真实 table/view、DDL、构建、测试、发布和血缘 | 物理资产是结果，不是 dbt 的同义词 | 三阶段 UI、深链、copy |
| ADR-74-06 | 模型类型与数仓层 | 两个概念分离展示，但 v2.2.3 继续使用经典映射：`DIMENSION/FACT→DWD`、`SUMMARY→DWS`、`APPLICATION→ADS`；不新增 DIM/五层策略 | 解决用户认知混淆，同时避免把创建旅程纠偏扩大为全链路分层迁移 | ModelSpec 创建/更新/导入解析、UI copy |
| ADR-74-07 | 字段标识 | `ModelField.name` 继续作为 ASCII 技术编码，新增 `displayName` 作为业务名称；中文旧 `name` 只读兼容并走预检纠错 | 解决“项目名称”既像业务名又被当物理字段编码的问题 | JSONB 快照、校验、字段 UI |
| ADR-74-08 | 阶段提示 | 默认只显示当前阶段的下一道门禁；全部门禁放入可展开区。可选建议不得进入 blocker 数量 | 让用户明确“现在必须修什么” | blocker panel、stage projection |
| ADR-74-09 | 标准、质量和密级 | 标准覆盖由计划策略控制且只在发布阶段执行；质量证据归发布控制面；密级继续复用 Sprint-72 传播门禁，可继承时不要求逐字段手填 | 避免把治理项前置为逻辑草稿必填，同时不降低合规 | plan policy、stage gate、release candidate |
| ADR-74-10 | 存量误建纠错 | 仅 DRAFT 且无实现、无生命周期/发布候选时允许预检后创建新 revision 改型；其余情况必须复制为新模型 | 不覆盖有运行证据的语义和血缘 | reclassify preview/apply、审计 |
| ADR-74-11 | 菜单与页面 | 不新增一级菜单；复用模型中心、模型详情和建设计划策略页 | 遵循 DTS 信息架构不变量 | 现有路由内重构 |
| ADR-74-12 | 技术标识和错误 | projectKey、dbtUniqueId、implementation revision 默认隐藏并由系统生成/投影；客户主界面只显示业务名称和中文修复动作 | 技术细节不应成为业务用户的必填上下文 | 实现页、错误映射、技术详情折叠区 |
| ADR-74-13 | 可引用模型状态 | `ARCHIVED` revision 不得成为新的上游或维度引用候选；前后端使用同一 canonical predicate | 避免新模型继续依赖已归档语义，同时保持既有历史 revision 可追溯 | ModelSpec reference validator、候选列表、契约测试 |

## 3.1 2026-07-27 页面与逻辑变更复审

本轮基于当前未提交建模 diff、前后端契约和 focused source-contract 实测更新，不把质量治理、`AGENTS.md`、`CLAUDE.md` 等并行修改纳入 Sprint-74 所有权。

| 变更 | 复审结论 | Sprint 处理 |
|---|---|---|
| DRAFT 模型中心提供“编辑”并深链到逻辑设计 | 接受；补齐了草稿继续编辑的可发现入口 | 纳入 F1/T03，保留用户实现并补真实导航验收 |
| 逻辑页允许在计划基线内调整数据集市 | 接受；服务端 `validateDataMartContext` 已按 plan/domain/current baseline fail-closed | 纳入 F2/T01；保留当前绑定失败态，补交互和契约测试 |
| 仅日期维度默认/允许系统生成 | 接受；修复普通业务维度被错误引导到生成器 | 纳入 F3/T01，补服务端输入模式一致性测试 |
| 数据实现按“来源→字段映射→产出方式”重排并隐藏技术标识 | 接受；符合业务用户上下文和 ADR-74-12 | 纳入 F3/T01/T02，继续复用同一 ModelImplementation |
| 目标表摘要读取 `ModelSpec.implementationPolicy`，并提示返回逻辑页修改 | **必须修复**；违反 ADR-74-04，继续制造逻辑/实现双 owner | 摘要和编辑统一改读写 `ModelImplementation.settings`；逻辑页停止 canonical 新写 |
| 逻辑页仍编辑物理名、装载、分区和保留期 | **必须修复**；DESIGNED 仍被实现字段污染 | F3/T01 + F4/T03 负责新写切换、旧 snapshot 兼容和迁移 |
| “物理资产”页仍提供高级 dbt 工作台入口 | **必须修复**；物理结果和实现引擎仍混在一起 | F3/T02/T03 把入口移到数据实现，结果页只读 |
| 前端拒绝 `ARCHIVED` 引用，但后端 canonical predicate 仍允许 | **必须修复**；客户端限制可被 API 绕过 | ADR-74-13；以后端 validator 为准并补前后端同码测试 |
| focused source-contract：72 项中 53 通过、19 失败 | **必须修复**；主要是最小创建契约切换后旧测试仍拿完整更新 payload 调 create validator，另有计划切换断言漂移 | F1/T03、F5/T01 先恢复红绿边界，再作为回归门禁 |

## 4. 对象边界

| 对象 | 必须拥有 | 明确不得拥有 |
|---|---|---|
| `DimensionDefinition` | 项目、客户、组织等业务维度定义、属性语义、复用范围 | 来源表、物理表名、dbt 节点 |
| `ModelSpecRevision` | 模型类型、粒度、技术字段编码、业务字段名称、字段作用、业务键、时间语义、维度引用、逻辑 SCD 要求 | 输入资产、字段来源映射、物化、装载、分区、保留期、dbt 文件 |
| `ModelImplementationRevision` | 实现方式、输入 revision、字段映射、转换、目标物理名、物化、装载、分区、保留期、实现校验 | 业务定义和逻辑模型正文 |
| `ReleaseCandidate` / lifecycle evidence | 构建、质量、审核、发布、回滚、外部登记 | 重新定义逻辑模型 |
| `MetadataObjectRevision` / `PhysicalAssetRevision` 投影 | 真实 catalog/schema/table/view、字段快照、DDL、运行和血缘证据 | 高级 dbt 编辑入口、逻辑粒度 |

## 5. 分阶段门禁契约

| 门禁 | 用户含义 | 阻断项来源 | 明确不检查 |
|---|---|---|---|
| `DRAFT_SAVE` | 能保存最小草稿 | plan、domain、modelType、name、幂等键 | 粒度、字段、来源、标准、质量、实现 |
| `DESIGNED` | 逻辑设计已完成，可评审或进入实现 | 类型专属逻辑规则、字段编码、键/时间引用闭合 | 来源资产、物理名、装载、dbt、质量、发布 |
| `IMPLEMENTATION_READY` | 当前逻辑 revision 已有有效且验证通过的实现 revision | 输入 revision、映射、实现设置、编译预检 | 发布审批和外部登记 |
| `RELEASE_READY` | 可交给发布控制面执行发布 | 当前 revision 的构建/测试/质量/审核/密级/标准策略证据 | 未来版本和历史 revision 的缺口 |

门禁继承前置阶段结果，但 UI 默认只展示用户当前要跨越的一道门禁。一个根因只能生成一个 blocker；兼容字段和 canonical 实现不得各报一次同义错误。

## 6. 端到端契约链（Vertical Slice）

| 层 | 契约/落点 | 签名要点 |
|---|---|---|
| UI 入口 | `/modeling/models` → “新建模型” | 第一步选择业务目的/模型类型，无默认值；第二步保存 plan/domain/name |
| 创建 API | `POST /api/modeling/model-specs` | body 保持 v2：`planId, domainId, modelType, name, description?, dimensionDefinitionRef?, idempotencyKey`；`layer` 由服务端按 v2.2.3 经典映射投影，客户端不可覆盖 |
| 目标层解析 | `ModelSpecContract.targetLayer(ModelType)` | 提升为公开唯一 resolver，ModelSpec create/update/import 与 WarehousePlan 命名校验共用；映射保持 `DIMENSION/FACT→DWD`、`SUMMARY→DWS`、`APPLICATION→ADS` |
| 逻辑保存 API | `PUT /api/modeling/model-specs/{id}` | CAS；`fields[].name`=技术编码，`fields[].displayName`=业务名；不得接收新的实现设置写入 |
| 门禁 API | `GET /api/modeling/model-specs/{id}/stage-gates` | 原数组兼容扩展 `DESIGNED`；顺序固定 `DRAFT_SAVE, DESIGNED, IMPLEMENTATION_READY, RELEASE_READY` |
| 纠错预检 | `POST /api/modeling/model-specs/{id}/reclassify-preview` | body `{targetType, dimensionDefinitionRef?}`；返回保留/清理字段、目标层、eligibility 和 reasonCodes；零写入 |
| 纠错执行 | `POST /api/modeling/model-specs/{id}/reclassify` | `If-Match` + `{targetType, dimensionDefinitionRef?, acceptedClearFields[], idempotencyKey}`；追加 revision，不原地覆盖历史 |
| 实现 API | 复用 `PUT /api/modeling/model-specs/{id}/implementation/inputs` | `ownership, inputMode, inputs, fieldMappings, settings, materialization, idempotencyKey`；物理名/装载/分区/保留写入 `settings` |
| 实现校验 | 复用 `POST /api/modeling/model-specs/{id}/implementation/inputs/validate` | 校验当前 model revision、input revision、映射和实现策略；只读 |
| 发布 | 复用 Sprint-69 `ReleaseCandidate` 控制面 | 构建、质量、审核、发布和回滚仍由唯一发布 owner 管理 |
| 发布结果 UI | 模型详情 `activeStage=physical`，对外标签改为“发布结果” | 只读消费 lifecycle/artifacts/registration；无真实结果时只显示解释和返回实现入口 |
| 数据 | 复用 `modeling_model_spec(_revision)`、`modeling_model_implementation(_revision)`、release candidate/lifecycle 表 | 不新建第二套模型、实现或物理资产台账 |
| 迁移 | `20260727_05_model_spec_reclassification_command.xml`、`20260727_06_warehouse_plan_governance_policy.xml`、`20260727_07_model_implementation_settings_contract.xml` | append-only 改型账本 + 计划治理策略 expand + implementation settings forward-only guard；不改 Layer 枚举 |

## 7. 精确兼容策略

### 7.1 模型类型与目标层兼容

| 当前 v2.2.3 规则 | DIMENSION | FACT | SUMMARY | APPLICATION |
|---|---|---|---|---|
| `CLASSIC_ODS_DWD_DWS_ADS` | DWD | DWD | DWS | ADS |

- 本 Sprint 不新增 `DIM` Layer，不新增 `DATAWORKS_5_LAYER_V1`，不增加建设计划分层切换 UI；
- `modelType` 描述业务语义，`layer` 描述当前版本的物理分层，两者在页面和 DTO 中分别展示；
- 创建、更新、导入统一使用服务端经典映射，客户端不得自行推断或覆盖；
- 历史 revision 的 `layer` 永不批量覆写。
- 若未来客户明确要求独立 DIM 层，必须另立 ADR/Sprint，单独评估数据库约束、dbt/import/release/存量迁移，不在本 Sprint 预埋半套能力。

### 7.2 字段名称

- 新增 `displayName?: string` 到 JSONB 快照；不新增独立字段表；
- 新写 `name` 必须匹配 `^[a-z][a-z0-9_]{0,62}$`；
- 中文/空格旧 `name` 标记 `LEGACY_FIELD_CODE`，读取不失败；
- 旧字段未修改时允许继续缺少 `displayName` 或保留旧 `name`；新增字段或修改该字段的编码/业务名时才执行新规则；
- 旧客户端提交同名字段但省略 `displayName` 时，服务端保留已存业务名称，不把它覆盖为 null；
- 纠错 preview 提示用户确认技术编码，不用机器翻译静默生成；
- 标准绑定、粒度键、时间字段和映射仍以技术 `name` 引用。

### 7.3 Sprint-73 已提交 implementationPolicy

Sprint-73 提交 `645ea2800` 已把 `implementationPolicy.physicalName/loadStrategy/partitionFields/retentionDays` 加入 `ModelSpec` 契约和逻辑页面。它是已经进入源码的兼容事实，但不是 Sprint-74 的 canonical 新写 owner：

1. 编码前先更新 GitNexus 到当前 HEAD，并对 owning symbols 做专项 impact；
2. 新 UI 停止在逻辑表单写实现设置，canonical 新写进入 `ModelImplementation.settings`；
3. 旧 ModelSpec snapshot 保持可读、可原样 round-trip；旧值发生修改时返回明确的“已迁移到数据实现”修复入口；
4. 无 current ModelImplementation 时，可通过 dry-run/apply 投影为新的 implementation revision；已有 current implementation 时以后者为真值，冲突进入人工清单；
5. 迁移完成、零旧写和零消费者证据齐备前不删字段，不通过改写历史 revision 修正。

## 8. 现状勘察账本（Context Ledger）

| # | 事实 | 证据 |
|---|---|---|
| L01 | 模型中心在没有深链类型时默认 `FACT` | `source/dts-platform-webapp/src/pages/modeling/ModelCenterPage.tsx:43-52` |
| L02 | 创建抽屉已有四类下拉，但没有业务目的判断卡 | `source/dts-platform-webapp/src/pages/modeling/components/ModelSpecCreateDrawer.tsx:42-47,511-517` |
| L03 | 当前前后端 Layer 均无 `DIM`，DIMENSION/FACT 都映射 DWD；本 Sprint 决定保持该结果并只纠正概念/UI 混淆 | `source/dts-platform-webapp/src/pages/modeling/modelSpecV2Contract.ts:67-74` |
| L04 | `ModelSpecContract.targetLayer` 已定义经典映射，`WarehousePlanResource.expectedLayer` 又复制了一份；本 Sprint 复用前者并删除重复映射，不扩展 Layer/policy | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecContract.java:218-230`; `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/WarehousePlanResource.java:371-378` |
| L05 | 门禁 API 和 UI 只有 `DRAFT_SAVE/IMPLEMENTATION_READY/RELEASE_READY` | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecStageGateService.java:795-799`; `source/dts-platform-webapp/src/api/modelSpecApi.ts:33-47` |
| L06 | `IMPLEMENTATION_READY` 同时执行旧 ModelSpec 输入检查和新 ModelImplementation 输入检查 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecStageGateService.java:190-224,563-588` |
| L07 | 详情页主动作未传入服务端 blocker，逻辑页可直接显示“配置数据实现” | `source/dts-platform-webapp/src/pages/modeling/ModelSpecDetailPage.tsx:664-674` |
| L08 | 页面在三个阶段上方一次性渲染全部 gate sections | `source/dts-platform-webapp/src/pages/modeling/ModelSpecDetailPage.tsx:792-815`; `source/dts-platform-webapp/src/pages/modeling/components/ModelSpecBlockerPanel.tsx:21-99` |
| L09 | FACT 时间字段仍是自由文本，而服务端要求命中角色为 TIME 的真实字段 | `source/dts-platform-webapp/src/pages/modeling/components/ModelSpecLogicalDesignStage.tsx:334-371`; `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecStageGateService.java:590-609` |
| L10 | 字段只有一个 `name`，可输入中文，同时作为粒度、标准、时间和实现映射标识 | `source/dts-platform-webapp/src/pages/modeling/components/ModelSpecFieldsTab.tsx:74-111`; `source/dts-platform-webapp/src/pages/modeling/modelSpecV2Contract.ts:108-120` |
| L11 | Sprint-73 已提交代码把物理表名、装载、保留和分区放在逻辑设计与实现门禁中，需按 §7.3 做兼容迁移 | `source/dts-platform-webapp/src/pages/modeling/components/ModelSpecLogicalDesignStage.tsx:420-485`; `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecStageGateService.java:486-528` |
| L12 | 高级 dbt 入口位于“物理资产”，造成对象与实现引擎混淆 | `source/dts-platform-webapp/src/pages/modeling/components/ModelSpecPhysicalAssetStage.tsx:74-84` |
| L13 | `ModelImplementation` 已有唯一输入/映射/settings/materialization 契约，可承接实现设置 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelLifecycleContract.java:348-382`; `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/ModelLifecycleResource.java:86-110` |
| L14 | 标准和字段安全等级当前按所有字段判定，且只在 RELEASE_READY 使用 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecStageGateService.java:621-636,719-747` |
| L15 | 现网仅 3 个 v2 模型：2 FACT、1 DIMENSION，全部 DWD/DRAFT；0 个实现、0 个生命周期事件 | `assets/domain-profile.md` 实测 |
| L16 | “财务项目模型”是 FACT/DWD/DRAFT r4，无实现；时间引用 `TIME` 但字段列表无 TIME 角色字段 | `assets/domain-profile.md` 实测 |
| L17 | 当前计划策略均为经典四层；“财务规划1”允许概念设计 | `assets/domain-profile.md` 实测 |
| L18 | Sprint-73 已提交为 `645ea2800`，当前无 Sprint-73 未提交冲突；工作树另有本 Sprint 范围外的 Liquibase 用户修改，必须排除并保留 | 2026-07-26 `git log`、`git status --short` |
| L19 | 当前 platform/webapp 镜像创建时间早于 Sprint-73 提交，数据库最新 changeset 停在 20260726 且无 `modeling_data_mart` | 2026-07-26 G0 实测，见 `it/baseline.md` |
| L20 | GitNexus `s10-stack` 已对齐当前 HEAD `c7e085de3` | 2026-07-27 `npx gitnexus analyze`、`gitnexus list_repos` |
| L21 | 用户已为 canonical DRAFT 增加“编辑”入口，并深链到逻辑设计 | `ModelCenterPage.tsx` 当前 diff |
| L22 | 数据集市可在当前计划 baseline 与业务分类交集内调整；后端 update 已重新校验 plan/domain/current | `ModelSpecLogicalDesignStage.tsx` 当前 diff；`ModelSpecApplicationService.validateDataMartContext` |
| L23 | 日期维度生成器已收敛为 `generationStrategy.type=DATE_DIMENSION`；普通业务维度默认物理来源 | `ModelSpecImplementationStage.tsx` 当前 diff |
| L24 | 实现页展示层已拆分并改为业务语言，但目标摘要仍从 `implementationPolicy` 读取 | `ModelSpecImplementationPresentation.tsx` 当前未跟踪文件 |
| L25 | `implementationPolicy` 仍在逻辑表单和 IMPLEMENTATION_READY blocker 中，ModelImplementation command 尚未写入物理名/装载/分区/保留 settings | `ModelSpecLogicalDesignStage.tsx:475-540`；`ModelSpecImplementationStage.commandFromForm`；`ModelSpecStageGateService.implementationBlockers` |
| L26 | 前端 canonical reference predicate 已拒绝 ARCHIVED，后端 `ModelSpecContract.isCanonicalReferenceTarget` 尚未判断 status | `modelSpecV2Contract.ts` 当前 diff；`ModelSpecContract.java:273-275` |
| L27 | 当前 focused 建模 source-contract 72 项通过 53、失败 19；基线不可作为完成证据 | 2026-07-27 `node --experimental-strip-types --test ...` |

勘察到此停止。下游 Task 必须引用账本编号，不得重复全仓扫描。

## 9. Gate Registry

| Gate | 项目 | 状态 | 证据 | 未过则关联 Task |
|---|---|---|---|---|
| G0 | 交付基线 | PASS | `it/baseline.md`、`it/evidence/acceptance-summary.md` | - |
| G0 | 领域与数据画像 | PASS | `assets/domain-profile.md`、隔离验收计划 | - |
| G0 | DTS 领域不变量自检 | PASS | ADR-74-03/05/09/11 | - |
| G1 | 契约链贯通 | PASS | 本文 §6 | - |
| G1 | 必填/可选边界 | PASS | `assets/requiredness-matrix.md` | - |
| G1 | 非功能预算 | PASS | `assets/nfr-budget.md`、`it/evidence/performance.md` | - |
| G1 | 架构复审 | PASS | `assets/architecture-review.md`、§3.1 | - |
| G2 | 变更范围与代码 Review | PASS | `git diff --check`；GitNexus 80 changed files、risk=LOW、0 affected process | - |
| G3 | 发布安全 | PASS | `assets/release-plan.md` | - |
| G4 | 可运维性 | PASS | `assets/runbook.md` | - |
| G4 | DoD 验收 | PASS | `it/evidence/acceptance-summary.md` | - |

## 10. Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 |
|---|---|---:|---|---|
| F0 | 评审与可验收基线 | 2 | P0 | DONE |
| F1 | 先选对模型再保存草稿 | 3 | P0 | DONE |
| F2 | 独立完成逻辑模型 | 3 | P0 | DONE |
| F3 | 实现方式与发布结果解耦 | 3 | P0 | DONE |
| F4 | 存量纠错、治理策略与兼容 | 3 | P0 | DONE |
| F5 | 集成验收与安全交付 | 2 | P0 | DONE |

**执行顺序**：F0 → F1 → F2 → F3 → F4 → F5。F1/T01 的类型/目标层边界和 F2/T01 的逻辑字段契约冻结后，F1/T02 与 F2/T02 可并行；F3 必须等待 `DESIGNED` 契约冻结；F5 是唯一 Go/No-Go 出口。

## 11. 追溯矩阵

| 需求点 | Feature | 关键 Task | 测试/证据 |
|---|---|---|---|
| 新建时不再误选 FACT | F1 | F1/T01、F1/T02 | IT-01、IT-02 |
| 逻辑模型可独立完成 | F2 | F2/T01、F2/T02 | IT-03、IT-04 |
| 只显示当前必须修复项 | F2 | F2/T03 | IT-05 |
| 数据实现含义清楚且按需进入 | F3 | F3/T01、F3/T02 | IT-06、IT-07 |
| 物理资产不等于 dbt | F3 | F3/T02、F3/T03 | IT-08 |
| 财务项目模型可安全纠错 | F4 | F4/T01 | IT-09 |
| 标准/质量/密级不污染草稿门禁 | F4 | F4/T02 | IT-10 |
| 旧模型与已提交 implementationPolicy 不丢数据 | F4 | F4/T03 | IT-11 |
| Chrome95 真实闭环 | F5 | F5/T01 | IT-01～IT-12 |
| 可迁移、可回滚、可运营 | F5 | F5/T02 | `assets/release-plan.md`、`assets/runbook.md` |

## 12. 完成标准

- [x] 用户不选择模型类型时无法保存，系统不再默认 FACT。
- [x] 用户可仅完成逻辑设计并达到 DESIGNED，不填写来源、物理名、装载、dbt 或发布证据。
- [x] 页面默认只显示当前阶段必须项；可选建议和未来发布要求不计入当前 blocker。
- [x] 普通配置和高级 dbt 均写入同一 ModelImplementation 控制面。
- [x] “发布结果”只消费真实实现/发布证据；无 `physicalAssetRef` 不展示资产，且不提供 dbt 编辑入口。
- [x] 隔离 FACT 样本通过 preview/apply 追加 DIMENSION revision 并保留历史；用户“财务项目模型”未被自动修改。
- [x] 模型类型与目标层分别呈现，四类模型继续使用 v2.2.3 经典映射且历史 layer 不被改写。
- [x] 旧中文字段编码、旧 sourceRefs/dependsOn 和已提交 implementationPolicy 均有兼容/迁移证据。
- [x] 后端契约、前端 source-contract、生产构建、真实认证 API、PostgreSQL、dbt 和 Chrome95 端到端全部有证据。

## 13. 非目标

- 不复制 DataWorks 的全部产品形态或术语。
- 不在本 Sprint 新增 `DIM` 层、DataWorks 五层策略或建设计划分层切换；如有真实客户需求，另立 ADR/Sprint。
- 不新增第二套模型、实现、发布或物理资产台账。
- 不把 dbt 变成所有客户的必选实现。
- 不在本 Sprint 重写 Sprint-69 发布工作台或 Sprint-72 密级传播内核。
- 不静默推断“财务项目模型”一定是维度或事实。
- 不原地改写历史 revision/checksum。
- 不绕过本 Sprint 的 impact、测试、迁移和真实验收门禁直接修改现网数据。
