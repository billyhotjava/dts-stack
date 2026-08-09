# 关键数据模型关系与端到端契约

**状态**：CONFIRMED（架构契约；实现转 Sprint-87）

**用途阶段**：F1/T02 OUTPUT / IT-03 INPUT

**用途**：冻结跨模块关系、写 owner、稳定引用和端到端验收逻辑；不是数据库 ERD，也不授权本 Sprint 修改 schema、API 或运行数据。

**事实基线**：2026-08-09 当前源码、Liquibase 与本地只读数据画像。

**评审包**：[`f1-t02-decision-pack.md`](f1-t02-decision-pack.md)；最终 ADR 状态以
[`decision-register.md`](decision-register.md) 为准。xiezm 已于 2026-08-09 批准本目标契约；它仍是待 Sprint-87 实现的目标事实，不得误报为当前 schema/API 已落地。

## 1. 三类对象必须分开

| 对象域 | 回答的问题 | 典型对象 | 唯一写 owner |
|---|---|---|---|
| 数据架构元数据 / 架构字典 | 业务如何分区、按什么过程与技术层建设 | 业务分类、数据域、业务过程、数仓分层、数据集市、主题域 | 平台数据架构 |
| 数据交付聚合 | 在什么建设范围内设计、发布并运行哪个版本 | 数仓计划、ModelSpec、Revision、ReleaseCandidate、实现、物化证据 | 数据建模 / 发布控制面 |
| 业务主数据（MDM） | 人员、组织、项目、物料等业务实体的权威记录是什么 | 主数据对象类型、金记录、来源映射、版本、审核状态 | 独立主数据管理能力 |

业务主数据可以引用数据架构元数据做业务归属，也可以通过维度模型形成分析投影，但不得反向拥有业务分类、数据域、分层等架构字典 CRUD。现有 `dts-admin /api/mdm` 仅证明人员/组织同步入口已存在，不等于平台已经具备通用 MDM 建模、匹配合并、审核和版本能力。

## 2. 目标关系总图

```text
数据架构字典（平台全局）
业务分类 ──1:n──▶ 数据域 ──1:n──▶ 业务过程
    │
    └────────1:n──▶ 数据集市 ──1:n──▶ 主题域

数仓分层 ──正交约束──▶ 模型类型/目标层

交付聚合
数仓计划 ──n:m──▶ 数据域（建设范围）
数仓计划 ──n:m──▶ 数据集市（建设范围）
数仓计划 ──1:n──▶ ModelSpec ──1:n──▶ ModelSpecRevision
                               ▲              │
                               │              └──n:m──▶ ReleaseCandidate（通过 Entry 固定 revision/checksum/implementation）
                               │                                  │
                               │                                  └──1:n──▶ 构建/物化派发 ──1:n──▶ 物理关系观测
                               │                                                                  │
                               └──1:0..1▶ 语义模型资产投影                          physical locator │
                                                                                                  ▼
                                                                                     物理资产 CatalogDataset

消费与治理
指标定义 ──引用──▶ 业务分类 / 数据域 / 业务过程 + 版本化模型或资产
质量规则版本 ──n:m──▶ 物理资产（GovRuleBinding）──▶ 质量运行证据
业务主数据金记录 ──分析投影──▶ DIMENSION ModelSpec ──物化──▶ 维度表资产
```

图中的 `1:n` 是目标业务基数，`n:m` 是明确允许的范围关联。目标关系与当前物理结构不一致时，以第 3 节的状态为准，禁止把目标图误报为已落地事实。

### 2.1 维度、事实、汇总与应用模型的有向依赖

```text
SOURCE_SYSTEM / ODS / STG 物理资产
        │
        ├──────────────▶ 维度定义 Revision ──固定版本──▶ DIMENSION ModelSpec（DWD）──▶ 维度表资产
        │                                                        │
        └──────────────────────────────▶ FACT ModelSpec（DWD）◀──┘ 共享维度键/关系
                                                   │
                                                   ▼
                                         SUMMARY ModelSpec（DWS）
                                                   │
                     DIMENSION / FACT ─────────────┤
                                                   ▼
                                        APPLICATION ModelSpec（ADS）
                                      （数据集市 + 主题域，ADR-86-14 已确认）
```

| 模型关系 | 当前事实 | 目标约束 | 状态 |
|---|---|---|---|
| 维度定义 Revision → DIMENSION ModelSpec | head/revision 已有 `dimension_definition_id + dimension_definition_revision` 外键，且只允许 DIMENSION 使用 | 一个维度定义版本可产生多个受控实现变体；每个模型 revision 固定所用定义版本 | CONFIRMED |
| 业务过程 ↔ 维度定义 | `sprint64_bus_matrix` 以 `(domain_id, process_id, dimension_id)` 记录，但 `dimension_id` 是字符串且无维度定义外键 | 业务矩阵引用稳定 DimensionDefinition ID/revision；过程和维度必须属于同一数据域或满足显式共享策略 | CONFIRMED（target；implementation gap） |
| 来源资产/模型 → DIMENSION/FACT | ModelSpec 支持来源引用，但跨 CatalogAsset/ModelSpec 的稳定身份仍需对账 | DWD 模型只引用已解析的上游资产或模型 revision；来源改名不改历史快照 | CONFIRMED（target；implementation gap） |
| DIMENSION → FACT | 维度关系可在模型 source/relationship 中表达，尚无独立“事实采用维度版本”总账 | 事实模型固定粒度、业务过程和共享维度版本；维度键关系必须可校验 | CONFIRMED（ADR-86-15；implementation gap） |
| FACT → SUMMARY | 模型类型和目标层已区分 FACT(DWD)/SUMMARY(DWS) | 汇总只依赖已发布的 DWD 事实/维度或允许的 DWS 上游，不得反向依赖 ADS | CONFIRMED（ADR-86-15；implementation gap） |
| DIMENSION/FACT/SUMMARY → APPLICATION | APPLICATION 目标层为 ADS，ModelSpec 可选 dataMartId；尚无 subjectDomainId | 应用模型固定数据集市与主题域；不得成为公共层模型上游 | CONFIRMED（ADR-86-14/15；implementation gap） |

模型依赖图必须是 DAG。批量物化不是“同时对多行发请求”，而是：固定选中模型 revision → 解析并校验依赖闭包 → 去重共享依赖 → 拓扑排序 → 创建一个 ReleaseCandidate → 逐项运行并汇总状态。若依赖未发布、跨计划越界、形成环或 revision 漂移，候选创建失败关闭并返回具名模型/关系。

二次物化遵守不可变证据原则：相同 candidate/version 的合法重试产生新的 dispatch `attempt` 和新的 observation，不覆盖旧运行；ModelSpec revision 发生变化时必须创建新的 candidate/version entry，不得复用旧条目。物理资产身份保持幂等，最新 serving 指针可更新，历史 published/observation 证据保留。

## 3. 当前关系、目标关系与缺口

状态含义：`CONFIRMED` 表示目标契约已批准；附注 `implementation gap` 表示当前源码/schema 尚未收敛，须由 Sprint-87 实现。当前物理事实仍以“当前事实”列为准。

> **（RF-86-11 / RF-86-13）** 本表状态列是 `decision-register.md` ADR 状态的**投影**，不是独立事实源。
> 跨文档词汇映射见 `decision-register.md` 的「状态词汇表」；ADR 冻结后须同步更新本表并标注 ADR 编号，
> 两处不一致时以 `decision-register.md` 为准。

| 关系 | 当前事实 | 目标契约 | 状态 / Owner |
|---|---|---|---|
| 架构字典作用域 | `catalog_domain`、业务过程和 canonical 分层是全局；数据集市/主题域表仍带 `tenant_id` | 产品/API/UI 仅呈现一套平台全局架构字典；现有 tenant 列保留，服务端固定平台 scope，真正多租户另立 ADR | CONFIRMED（ADR-86-01/10；implementation gap） / 数据架构 |
| 业务分类 → 数据域 | `catalog_domain.parent_id` 单父级树，无显式类型列 | 根节点为业务分类、子节点为数据域；子域必须且只能有一个业务分类父节点 | CONFIRMED / 数据架构 |
| 数据域 → 业务过程 | `sprint64_business_process.domain_id` 必填，`(domain_id, process_id)` 唯一 | 一个过程属于一个数据域；过程 ID 作为事实模型和原子指标稳定上下文 | CONFIRMED / 数据架构 |
| 业务分类 → 数据集市 | 当前 `modeling_data_mart_domain` 可表达多对多；服务契约使用业务分类集合 | 一个数据集市只属于一个业务分类；兼容期保留关联表并由服务层强制单值，历史 0/>1 归属人工裁决 | CONFIRMED（ADR-86-14；implementation gap） / 数据架构 |
| 数据集市 → 主题域 | `modeling_subject_domain.mart_id` 非空并有外键；状态为 DRAFT/CURRENT/RETIRED | 一个主题域只属于一个数据集市 | CONFIRMED / 数据架构 |
| 数仓分层 → 模型 | canonical 模型层为 ODS/STG/DWD/DWS/ADS；DIMENSION 模型目标层为 DWD | 分层与业务树正交；DIM 兼容值归一为 `DWD + DIMENSION_TABLE`，SOURCE 归一为来源语义且 canonical layer 为空 | CONFIRMED（ADR-86-05；implementation gap） / 数据架构 + 建模 |
| 数仓计划 → 数据域/集市 | `modeling_warehouse_plan_domain`、`modeling_warehouse_plan_data_mart` 为范围关联 | 计划是建设范围聚合，不是架构字典；只允许选择 CURRENT 且父子关系一致的对象并固定基线版本 | CONFIRMED（target；implementation gap） / 数据建模规划 |
| 数仓计划 → ModelSpec | ModelSpec 直接引用 `plan_id`，v2 同时要求 `domain_id` | 模型必须属于一个计划；模型业务上下文必须落在计划已确认范围 | CONFIRMED + 补强校验 / 数据建模 |
| ModelSpec → 数据域/过程/集市/主题域 | v2 有 `domain_id`、自由文本 `business_activity_ref` 和可选 `data_mart_id`；旧 `process_id` 已退出 v2 主契约；没有 `subject_domain_id` | FACT 固定业务过程；APPLICATION 固定 dataMartId + subjectDomainId；所有值进入不可变 revision/candidate snapshot | CONFIRMED（ADR-86-14/15；implementation gap） / 数据建模 |
| ModelSpec → Revision | `modeling_model_spec_revision` 以 `(model_spec_id, revision)` 唯一并保存 checksum/snapshot | 发布、指标引用和审计均引用不可变 revision，不引用可变 head 代替历史版本 | CONFIRMED / 数据建模 |
| Revision → ReleaseCandidate | candidate entry 固定 `model_spec_id + revision + checksum + implementation` | 候选是批量交付边界；同一模型修订可进入不同环境/批次，候选不得漂移到新 head | CONFIRMED / 发布控制面 |
| ReleaseCandidate → 物理关系证据 | dispatch 绑定 candidate；observation 绑定 candidate、run、ModelSpec 与 database/schema/identifier/type | 只有成功且未过期的观测才能证明物理关系存在；失败构建不得伪装为可服务资产 | CONFIRMED / 物化控制面 |
| ModelSpec → 语义模型资产 | `modeling_catalog_model_serving_projection` 以 ModelSpec 为主键，保存 `SEMANTIC_MODEL` asset key、latest-published 与 serving 指针 | 逻辑/语义模型资产与物理表资产是两个身份，不得合并成同一状态 | CONFIRMED / 建模投影到资产 |
| 物理观测 → CatalogDataset | 当前有完整 physical locator 证据，但不是对 `catalog_dataset.id` 的直接外键 | 以 datasource/database/schema/identifier/type 解析既有 CatalogAssetKey 并幂等投影；冲突失败关闭，重命名建新 key + `RENAMED_FROM`，撤销保留历史 | CONFIRMED（ADR-86-13；implementation gap） / 数据资产 |
| 物理资产 → 生产者/登记证据 | 当前 SOURCE/ODS/dbt 同步链把来源类型、生成方式和登记动作混在不同字段/写入路径 | `ProducerRef` 固定生产者或上游来源；`RegistrationEvidence` 逐次记录扫描/集成/dbt/manual 渠道；多渠道观测幂等归并同一 CatalogAssetKey | CONFIRMED（ADR-86-18；implementation gap） / 数据资产 |
| 物理资产 → 正交状态轴 | 当前候选文档曾用一个 `ConsumptionState` 混合 DISCOVERED/PUBLISHED/SERVING/STALE/RETIRED | 发现、治理、发布、服务健康和生命周期分别保存事实；消费资格由策略、质量和权限计算并返回原因 | CONFIRMED（ADR-86-19；implementation gap） / 数据资产 + 权限 + 质量 |
| 指标 → 业务上下文 | `GovIndicatorDefinition.category` 与 `datasetId` 仍为字符串，缺稳定分类/域/过程关系 | 所有指标单值必填 businessCategoryId；ATOMIC 固定域/过程；DERIVED 同分类同域；COMPOSITE 可同分类跨域；v1 禁止跨分类 | CONFIRMED（ADR-86-06/07；implementation gap） / 数据指标 |
| 质量规则版本 → 资产 | `GovRuleBinding.rule_version_id + dataset_id` 已形成版本化绑定 | 质量运行从绑定读取资产，不从页面展示字段临时拼接；字段范围随绑定快照审计 | CONFIRMED / 数据质量 |
| 业务主数据 → 分析维度 | 现有 MDM 仅有人/组织同步，没有通用对象、金记录和分析投影契约 | 主数据金记录保留业务身份；DIMENSION ModelSpec 只承载分析投影，物化后才产生维度表资产 | CONFIRMED（ADR-86-12 边界；实现转独立 Sprint） / 主数据 + 建模 |

## 4. 关系不变量

1. 平台当前只暴露一个全局架构字典视图；底层遗留 `tenant_id` 不得被 UI/consumer 解释为已具备多租户能力。
2. `dataDomain.parentId == businessCategoryId`；业务分类根节点不得作为数据域引用。
3. `businessProcess.domainId == dataDomainId`；业务过程不能跨域挂接。
4. 数据集市只属于一个业务分类；兼容期保留现有关联表，但任何新 UI/API 不得暴露多归属语义。
5. `subjectDomain.martId == dataMartId`；若应用模型要求主题域，则其 dataMartId 必须一致。
6. 计划是建设范围，不是新的架构字典 owner；ModelSpec 的域/集市必须包含于计划已确认基线。
7. 模型类型、技术分层和业务上下文分别校验：维度模型不等于 DIM 分层，业务分类也不决定技术层。
8. ModelSpec head 可变，Revision 不可变；ReleaseCandidate 必须固定 revision、checksum 与 implementation。
9. 模型依赖必须是有向无环图；DWD → DWS → ADS 不得反向依赖，下游批量交付必须包含或验证依赖闭包。
10. 同一 revision 二次物化只新增 attempt/observation；新 revision 必须进入新的 candidate/version entry，任何重试不得覆盖历史证据。
11. “候选已发布”“物化运行成功”“物理关系可观测”“资产可消费”是四个不同事实，状态不得级联猜测。
12. 语义模型资产使用 `SEMANTIC_MODEL` 身份；表/视图资产使用物理 locator 身份，二者通过发布/服务证据关联但不共用主键。
13. 指标引用业务上下文和版本化来源；质量引用资产身份和规则版本；名称仅作展示，不能作为跨模块关联键。
14. 未归域资产仍是资产；归域、定责、分级、质检和发布分别改变治理状态，不改变资产是否存在。
15. 业务主数据金记录、架构字典和分析维度三者各有 owner；任何同步或投影都不得覆盖上游权威身份。
16. 资产生产者/上游来源与登记渠道是两个维度；重复发现只能新增/刷新登记证据，不能改变 CatalogAssetKey 或覆盖历史生产者引用。
17. 发现、治理、发布、服务健康、生命周期不得合并成一个互斥总状态；消费资格必须可解释到输入事实、质量门禁和权限决策。

## 5. 状态传播规则

| 上游状态变化 | 下游允许行为 | 禁止推断 |
|---|---|---|
| 架构对象 DRAFT | 可保存草稿引用，不可进入新发布基线 | 不得显示为 CURRENT |
| 架构对象 RETIRED | 历史 revision 保留引用；新 ModelSpec/指标禁止选择 | 不得级联删除历史模型、资产或质量证据 |
| ModelSpec 保存/提交 | 产生或推进模型修订 | 不代表已有物理表 |
| ReleaseCandidate PUBLISHED | 证明某组不可变修订经过发布流程 | 不代表每项仍在 SERVING |
| 物理关系观测成功 | 可建立/刷新物理资产投影 | 不代表已归域、已质检或可授权消费 |
| 新登记渠道再次发现同一 locator | 幂等解析同一 CatalogAssetKey，并新增/刷新该渠道的观测证据 | 不得生成第二个资产身份或覆盖生产者历史 |
| 服务健康变为 STALE/FAILED | 阻断或降级消费资格，并保留发布/治理历史 | 不得自动把资产改成未发布、未治理或已退役 |
| 质量通过 | 为指定资产/规则版本/运行提供证据 | 不自动改变模型 revision 或资产身份 |
| MDM 金记录 CURRENT | 可供分析投影选择 | 不代表维度模型已构建或维度表已发布 |

## 6. 端到端主路径

### E2E-A：从架构规划到可消费资产

| 步骤 | Actor / 入口 | 写入 owner | 输出与门禁 |
|---:|---|---|---|
| 1 | 架构管理员维护分类、域、过程、分层、集市、主题域 | 数据架构 | 形成稳定 ID、状态和父子关系；冲突/非法父级失败关闭 |
| 2 | 建模负责人创建/确认数仓计划基线 | 建模规划 | 选择数据域与数据集市范围；非 CURRENT 或关系不一致不得确认 |
| 3 | 建模人员创建 ModelSpec | 数据建模 | 固定 planId、domainId、模型类型、目标层及按类型要求的过程/集市上下文 |
| 4 | 保存并提交模型 | 数据建模 | 生成不可变 Revision + checksum；标准、权限分级、关系校验不通过则停留当前阶段 |
| 5 | 多选模型创建 ReleaseCandidate | 发布控制面 | 服务端先返回 root + dependency closure + blockers；任一 blocker 则零候选/零派发，用户显式取消无资格 root 后重新提交；成功时每个 entry 固定 revision/checksum/implementation |
| 6 | 构建与物化 | 物化控制面 | dispatch/run/observation 全链路带 candidate、model、revision 和幂等键；成功/失败逐项收敛 |
| 7 | 质量、评审与发布 | 数据质量 + 发布控制面 | 发布门禁证据绑定候选项、模型修订和物理观测；尚无 CatalogDataset 时不得伪造资产绑定；审核职责分离，只有满足门禁的候选才能 APPROVED/PUBLISHED |
| 8 | 投影资产 | 数据资产 | PUBLISHED revision 投影语义模型 latest-published；健康 physical locator 投影表/视图资产并形成 serving 指针；失败对象不冒充可服务 |
| 9 | 资产治理与授权 | 数据资产 | 未归域清单支持批量归域、定责、分级、发布；部分失败可重试且审计到单项 |
| 10 | 质量持续运行 | 数据质量 | GovRuleBinding 固定 ruleVersion + datasetId，运行结果回投资产健康和下一发布门禁 |
| 11 | 指标/分析消费 | 数据指标/分析 | 引用稳定业务上下文和 serving 版本；无 serving/质量证据时按策略阻断或降级 |

### E2E-B：从外部数据发现到纳入建模

1. 集成或扫描器用 physical locator 幂等登记外部表/视图，记录 `ProducerRef=SOURCE_SYSTEM + sourceSystemId`、本次 `RegistrationEvidence.channel` 和 `warehouseLayer=null`；多个渠道观测同一 locator 不创建第二个资产。
2. 资产先进入“全部资产/未归域”，不得因没有 domainId 而不可见。
3. 治理人员筛选并多选资产，批量归域；服务端逐项校验权限、目标域状态和幂等键，返回成功/失败明细并写审计。
4. 建模人员从资产身份选择来源，ModelSpec revision 固定来源引用；后续 lineage 连接来源资产、目标模型和物理目标。
5. 源资产改名、失联或漂移时更新健康/漂移证据，不改写历史模型 revision。

### E2E-C：从业务定义到指标发布

1. 指标管理员选择业务分类，再选择其子数据域和该域业务过程；指标类型单独选择。
2. ATOMIC/DERIVED/COMPOSITE 按各自必填、继承和跨分类策略校验，禁止用自由文本绕过父子关系。
3. 指标版本固定来源语义模型 revision 或物理资产身份；派生指标固定上游指标版本。
4. 发布前验证来源处于允许的 published/serving/quality 状态；发布结果不反向修改模型或资产事实。

### E2E-D：业务主数据到分析维度（后续 Sprint）

1. 主数据适配器接收人员、组织、项目、物料等来源记录并保留来源身份。
2. MDM 完成标准化、匹配合并、冲突审核和金记录版本；该过程不写架构字典。
3. 维度模型选择主数据对象/版本作为来源，形成 DIMENSION ModelSpec revision；业务主键与分析代理键分别建模。
4. 按 E2E-A 发布和物化为 DWD 维度表资产，质量规则校验唯一性、完整性和参照一致性。
5. 金记录变更通过新批次/版本传播，历史模型、资产和消费证据保持可追溯。

## 7. 端到端评审用例（IT-03）

| 用例 | Given | When | Then / 必备证据 |
|---|---|---|---|
| 关系一致性 | 一个 CURRENT 分类、子域、过程、集市、主题域 | 建立计划并创建各模型类型 | UI 选项、API 校验、持久化 ID 与父子关系一致；非法组合失败关闭 |
| 批量交付 | 多个模型分别 READY/BLOCKED | 多选创建候选并运行 | 候选固定逐项 revision；无资格项逐项可见且不被静默排除；运行部分失败可定位，重试不重复派发 |
| 依赖顺序与二次物化 | 选中 SUMMARY/APPLICATION，且共享上游；已有一次 observation | 再次批量物化 | 依赖闭包去重且按 DWD→DWS→ADS 执行；相同 revision 新增 attempt/observation，旧证据仍可查 |
| 模型到资产 | 候选运行产生成功与失败观测 | 刷新模型/资产页 | 语义资产与物理资产可区分；成功项显示最新物化/服务证据，失败项不冒充可消费 |
| 未归域治理 | 资产台账大部分或全部未归域 | 多选批量归域 | 全部资产始终可达；逐项成功/失败、审计、刷新后导航计数一致 |
| 指标上下文 | 分类、域、过程与来源版本存在 | 创建并发布指标 | 自由文本不再作为关联键；跨域错误被拦截；来源版本可追溯 |
| 主数据投影 | MDM 后续 Sprint 提供金记录版本 | 创建维度投影 | MDM、ModelSpec、物理资产三种身份不混用；质量与历史版本可追溯 |

评审证据必须同时覆盖 UI 选择、API 请求/响应、数据库关联和审计记录。直接拼 URL、只看页面文案或只验证单表记录均不能证明端到端贯通。

> **（RF-86-12）验收边界**：Sprint-86 的 IT-03 验收的是**上述用例设计的完备性**——每个用例是否有明确的
> Given/When/Then、owner、失败路径和证据类型。上述四类证据是**下一实施 Sprint 的执行要求**，
> 本 Sprint 不执行、也不冒充已执行 E2E（见 `it/baseline.md`）。

## 8. 已批准的关系决策与实施移交

以下结论已通过 `f1-t02-decision-pack.md`、`f2-f3-data-contract-pack.md` 和
`consolidated-approval-pack.md` 获 xiezm 批准；精确 schema/API/UI 仍须通过 Sprint-87 G0/DoR 后实施。

1. 遗留 `tenant_id` 保留，服务端固定平台 scope；真正多租户另立 ADR。
2. 数据集市单业务分类；兼容期保留多对多关联表并由服务层强制单值，0/>1 历史归属进入 migration issue/人工裁决。
3. APPLICATION 强制 `dataMartId + subjectDomainId`；业务过程、主题域与依赖引用进入 head/revision/import/export/candidate 快照。
4. 自由文本业务活动和字符串维度引用采用 Expand + 唯一匹配回填；歧义失败关闭，不猜测。
5. revision DAG、跨计划固定发布版本、候选全有或全无、运行逐项失败、二次物化新 attempt/observation。
6. physical locator 解析既有 CatalogAssetKey；冲突阻断，重命名建新 key + 关系，撤销/重放保留历史。
7. 指标按已批准的 ATOMIC/DERIVED/COMPOSITE 矩阵使用稳定业务上下文与来源版本。
8. DIM 是 `DWD + DIMENSION_TABLE` 兼容归一，SOURCE 是 ProducerRef；不扩展 ModelSpec/dbt canonical layer。
9. 资产采用 ProducerRef/RegistrationEvidence、五轴状态与增量统计投影；旧扫描 fallback 仅作具名近似降级。
10. MDM 只冻结边界并转独立 Sprint；方案 A 权限、NFR 与所有运行验收进入 Sprint-87 对应 Task。

## 9. 实施依赖顺序

```text
统一语言与 owner
  → 关系基数/稳定 ID/状态传播
    → consumer read contract
      → schema/API 兼容扩展与回填
        → 建模/资产/指标/质量 UI 切换
          → 浏览器端到端验收与观测
            → 旧字段/旧路由 Contract
```

任何实施 Task 若无法从本文件的一条关系追溯到 owner、写入点、失败路径和 IT 用例，均不得进入 READY。
