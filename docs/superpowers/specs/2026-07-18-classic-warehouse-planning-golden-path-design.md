# DTS 经典数仓规划内核与平台黄金主线设计

> **状态**：ACTIVE（Sprint-65 总体设计）
> **取代声明（2026-07-18）**：本设计取代 `2026-07-17-generic-modeling-workbench-ui-design.md` 的顶层骨架决策（四阶段旅程 SCOPE/LOGICAL/IMPLEMENTATION/RELEASE → 经典数仓黄金主线 + planId 上下文）。07-17 已实现的 SemanticWorkspaceFrame 四阶段导航按退役登记表 **R13** 处置：保留期间仅作模型中心内部导航、不自持上下文。07-17 中的通用模型内核概念（ModelSpec/实体/字段/粒度/关系/来源映射）与"无真实 API 不放假按钮"原则继续有效并被本设计吸收。


**日期**：2026-07-18
**状态**：已批准进入 Sprint 拆解，待整体到局部架构评审
**决策方案**：方案 C——双起点、单内核、单主线
**实施 Sprint**：`worklog/v2.2.3/sprint-65-202607/`
**范围**：数仓规划、模型中心、高级 dbt、标准/资产/指标衔接、菜单路由与既有旅程受控退役

## 1. 决策摘要

DTS 的默认建模路径调整为经典数仓规划。系统不再把“维度建模、关系建模、dbt 原生建模”作为三个并列产品模式，也不再把主题域管理或总线矩阵当作建模工作台。

新架构采用：

```text
双起点：
  业务驱动 BUSINESS_FIRST ─┐
                            ├─> 统一规划基线 -> 经典数仓设计 -> 实现与验证 -> 发布成果
  资产驱动 ASSET_FIRST ────┘

单内核：WarehousePlan + 规划基线 + ModelSpec + 引用关系 + 证据投影

单主线：
  数据连接 -> 数据接入/来源盘点 -> 数仓规划 -> 数据标准
  -> 事实/维度模型设计 -> 构建/质量/发布 -> 数据资产
  -> 指标系统 -> 数据服务/运行运维
```

其中：

- 经典维度数仓是默认规划方法，解决主题域、过程、粒度、事实、维度、分层和指标承载问题；
- 关系建模是模型设计阶段的可选视图，用于主外键、基数和实体关系，不是独立主旅程；
- dbt 是高级实现工具，用于 SQL、宏、编译、测试、manifest 和运行，不是建模方法或黄金主线阶段；
- 总线矩阵是事实模型与一致性维度关系的派生分析视图，不是强制入口、独立事实源或通用门禁；
- PJM、制造、能源、科研等仅作为测试夹具和行业示例，不能进入平台核心枚举、字段和流程分支。

## 2. 为什么需要重设架构

### 2.1 根因不是某个页面跳转错误

此前架构把三个不同层级的概念放在同一层：

| 层级 | 应回答的问题 | 被混入的概念 |
|---|---|---|
| 规划方法 | 为什么建设、按什么业务范围建设 | 主题域、业务过程、指标需求 |
| 模型设计 | 数据按什么粒度和结构组织 | 事实、维度、关系、字段、分层 |
| 工程实现 | 由什么工具生成、测试和运行 | dbt 文件、SQL、manifest、任务 |

混用后会产生三个直接后果：

1. 用户选择“建模工作台”却被送入“主题域管理”，因为导航没有稳定的业务聚合根；
2. dbt 被包装成建模模式，普通用户在规划阶段就被迫理解工程工具；
3. 页面尝试同时解释规划、治理、建模和运行，按钮、提示词和状态大量堆叠。

### 2.2 DataWorks 概念不是不能用，而是不能成为行业方言

“业务对象”“业务过程”“主题域”仍有通用价值，但产品必须用中性定义：

| 概念 | DTS 通用定义 | 不应绑定的行业解释 |
|---|---|---|
| 业务范围 | 本规划要解决的业务边界、目标和责任 | 某个 PJM 项目或电商场景 |
| 主题域 | 一组稳定、内聚的数据责任范围 | 固定使用人货场或项目管理域 |
| 业务过程 | 需要被度量、跟踪或形成状态变化的业务活动 | 只允许高频交易事件 |
| 业务对象 | 过程围绕的稳定实体或管理对象 | 只允许商品、订单、客户 |
| 粒度 | 一行事实记录代表的最小业务含义 | 只允许一笔交易 |
| 过程形态 | 事务、周期快照或累积快照 | 把闭环台账硬编码为平台流程 |

过程形态和对象性质可作为建模辅助属性，但只能影响推荐模板，不能制造行业专属工作流。

## 3. 目标与非目标

### 3.1 目标

- 新用户在一个真实工作台中看到当前规划、当前阻塞和唯一下一步；
- 业务先行和存量资产先行的客户最终使用同一套规划与模型内核；
- 一个 `WarehousePlan` 覆盖多个主题域、业务过程、来源和模型；
- 标准、资产、指标和运行仍由各自专业模块持有，规划只保存引用和阶段证据；
- 经典数仓用户不必理解 dbt；高级开发者可以进入 dbt，但结果必须回写模型台账和运行证据；
- 阶段完成状态来自后端事实和门禁证据，不来自访问页面或 sessionStorage；
- 旧路由、菜单权限和存量数据通过受控退役逐步迁移，不进行破坏式删除。

### 3.2 非目标

- 不复制连接、标准、资产、指标或调度模块的数据所有权；
- 不把总线矩阵扩展成全能建模工具；
- 不在 Sprint-65 硬删除旧表、旧 API 或旧路由；
- 不在核心契约中写入 PJM 或其他行业字段；
- 不让 AI 推荐、名称猜测或 dbt manifest 导入直接成为已确认业务事实；
- 不以“页面可以打开”代替业务完成和运行验证。

## 4. 产品分层与数据所有权

### 4.1 四层架构

```text
┌─────────────────────────────────────────────────────────┐
│ 体验层：数据建设工作台、计划详情、模型中心、高级 dbt     │
├─────────────────────────────────────────────────────────┤
│ 应用层：规划命令、基线门禁、模型生命周期、证据投影       │
├─────────────────────────────────────────────────────────┤
│ 领域层：WarehousePlan、PlanningBaseline、ModelSpec       │
├─────────────────────────────────────────────────────────┤
│ 能力层：连接/接入、标准、目录资产、指标、dbt、Airflow    │
└─────────────────────────────────────────────────────────┘
```

质量、血缘、安全、审计和租户隔离是贯穿各层的横切能力，不应再被做成主线上的一次性页面步骤。

### 4.2 所有权矩阵

| 业务事实 | 唯一所有者 | WarehousePlan 保存什么 |
|---|---|---|
| 数据连接 | 数据连接/集成模块 | `connectionRef`、选择状态、可用性证据 |
| 来源表、Excel、ODS | 接入与元数据目录 | `sourceRef`、范围内字段快照引用、确认状态 |
| 数据标准 | 标准管理 | `standardRef`、绑定结果和版本钉住引用 |
| 模型定义 | 模型中心 | `ModelSpec.planId` 与模型生命周期 |
| dbt 工程产物 | 高级 dbt / 产物仓 | `ImplementationArtifact` 引用和校验结果 |
| 数据资产 | 资产目录 | 发布后资产引用和登记证据 |
| 指标 | 指标系统 | 指标需求、已实现指标引用和口径校验证据 |
| 调度与运行 | Airflow/运行中心 | 运行引用、最近状态和诊断入口 |

规划聚合不复制上述对象正文。跨模块数据变化通过稳定 ID、版本、校验时间和证据摘要投影到规划中。

## 5. 核心领域模型

### 5.1 WarehousePlan 是唯一规划聚合根

`WarehousePlan` 表达一个可交付的数仓建设方案，而不是“一个主题域”“一个过程”或“一张模型表”。

```text
WarehousePlan
├─ identity: id, tenantId, code, name
├─ intent: objective, scope, owner, ownerDepartment
├─ onboardingMode: BUSINESS_FIRST | ASSET_FIRST
├─ lifecycle: DRAFT | BASELINE_READY | DESIGNING | VALIDATING
│             | READY_TO_PUBLISH | PUBLISHED | ARCHIVED
├─ policy: layerPolicyRef, namingPolicyRef, historyPolicyRef
├─ bindings
│  ├─ domainBindings[]
│  ├─ processBindings[]
│  ├─ sourceBindings[]
│  ├─ standardBindings[]
│  └─ metricRequirementRefs[]
├─ models: ModelSpec[]
├─ evidence: StageEvidence[]
└─ governance: version, createdBy, updatedBy, timestamps
```

关键不变量：

- 起点只决定首次进入哪个编辑区，不改变最终对象和门禁；
- 一个计划可覆盖多个主题域、业务过程、来源和模型；
- 来源、业务范围和来源到业务的映射均确认后，规划基线才可成立；
- 已发布计划的结构性修改必须产生新版本或变更申请，不能静默覆盖；
- 阶段状态是事实投影，不允许前端直接设置为完成；
- 删除计划默认是归档，只有无引用的草稿才允许物理删除。

### 5.2 规划基线

规划基线由四类事实组成：

| 基线组成 | 最低完成条件 |
|---|---|
| 业务范围 | 目标、责任人、至少一个主题域和一个过程已确认 |
| 来源盘点 | 至少一个可访问来源已确认，剖析失败已明确处理 |
| 来源业务映射 | 范围内来源至少映射到一个主题域/过程或明确排除 |
| 规划策略 | 分层、命名、历史与时间策略已选择并可追溯 |

“业务先行”和“资产先行”共用同一个 `PlanningBaseline`。任何推荐、导入或自动推断初始状态均为 `CANDIDATE`，人工确认后才成为 `CONFIRMED`。

### 5.3 模型设计内核

复用已有 `ModelSpec`、粒度、来源引用、标准绑定、依赖、修订和 dbt 产物能力，并补足以下中性语义：

| 属性 | 用途 |
|---|---|
| `modelRole` | FACT、DIMENSION、SUMMARY、APPLICATION |
| `factShape` | TRANSACTION、PERIODIC_SNAPSHOT、ACCUMULATING_SNAPSHOT，仅事实模型适用 |
| `objectNature` | MASTER、MANAGEMENT、REFERENCE，作为业务对象辅助分类 |
| `grain` | 事实行或维度行的业务含义和键集合 |
| `timeSemantics` | EVENT_TIME、SNAPSHOT_DATE、PERIOD、MILESTONE_DATES |
| `sourceMappings` | 来源表/字段到模型字段的可追溯映射 |
| `implementationMode` | DESIGNER_GENERATED 或 DBT_MANAGED |

关系图和总线矩阵均是该内核上的视图：

- ER 关系视图从模型实体、主外键和基数生成；
- 分析关系概览从事实模型、维度引用和一致性维度生成；
- 用户可以在对应设计界面维护关系，但不能维护一份与模型定义相冲突的第二真值。

## 6. 现有持久化的收敛方案

### 6.1 当前存在两套规划表

| 表 | 当前用途 | 问题 |
|---|---|---|
| `modeling_plan` / `modeling_plan_version` / `modeling_plan_review` | 旧规划、版本与评审 | 与 vNext 模型没有稳定外键关系 |
| `modeling_warehouse_plan` | vNext 规划 | 当前粒度接近“过程 + 分层”，不是方案级聚合 |

此外，`modeling_model_spec.plan_id` 已外键指向 `modeling_warehouse_plan`，`modeling_dbt_artifact`、`modeling_pipeline_run` 和 `modeling_lineage_edge` 也已构成可复用实现证据链。

### 6.2 采用的收敛决策

以 `modeling_warehouse_plan` 作为唯一运行态规划聚合表，原因是现有 vNext API 和 `ModelSpec` 已经以它为主线。Sprint-65 扩展其方案级字段，并将原有 `domain_id/process_id/layer` 单值关系迁移为子绑定表。

迁移原则：

1. 不创建第三张主计划表；
2. 不在 Sprint-65 删除 `modeling_plan*`；
3. 旧记录通过 `legacy_source/legacy_ref` 和迁移映射回填到新聚合；
4. 迁移期旧 API 只读或双读，所有新写入只进入 canonical 聚合；
5. 版本与评审能力迁移到 canonical 计划后，旧表进入只读冻结；
6. 只有零活跃消费者、迁移核对通过、回滚窗口结束后，后续 Sprint 才可提出物理删除。

建议新增或扩展：

```text
modeling_warehouse_plan                 # 方案级主表
modeling_warehouse_plan_domain          # 主题域绑定
modeling_warehouse_plan_process         # 业务过程绑定
modeling_warehouse_plan_source          # 来源引用与确认
modeling_warehouse_plan_source_mapping  # 来源到域/过程映射
modeling_warehouse_plan_metric_need     # 指标需求引用/草案
modeling_warehouse_plan_policy          # 分层、命名、历史、时间策略
modeling_warehouse_plan_version         # canonical 版本快照
modeling_warehouse_plan_review          # canonical 评审记录
modeling_warehouse_plan_stage_evidence  # 阶段证据投影
modeling_legacy_plan_mapping             # 旧计划迁移映射
```

具体物理表可以在实现评审中合并，但领域边界和唯一所有权不得改变。

## 7. 双起点、单基线工作流

### 7.1 业务驱动起点

```text
建设目标
 -> 业务范围与负责人
 -> 主题域
 -> 业务过程/过程形态
 -> 业务对象与指标需求
 -> 匹配数据连接、Excel、ODS 或现有资产
 -> 确认来源业务映射
 -> 建立规划基线
```

适用于新系统建设、业务部门提出指标需求或需要先统一业务口径的场景。

### 7.2 资产驱动起点

```text
选择数据连接/上传 Excel/选择 ODS/导入 dbt manifest
 -> 元数据与样例剖析
 -> 识别候选主键、时间字段、枚举和更新方式
 -> 确认业务范围、主题域与过程
 -> 确认来源业务映射
 -> 建立规划基线
```

适用于客户已经有 Excel、ODS、存量表或 dbt 项目的现实场景。系统的推断只能产生候选，不能自动确认业务对象、粒度或指标。

### 7.3 统一门禁

两个起点在以下条件同时满足时收敛：

```text
业务范围已确认
AND 来源盘点已确认
AND 主题域/业务过程已确认
AND 来源到业务范围的映射已确认
AND 规划策略已确认
= PlanningBaseline READY
```

未满足时，工作台显示唯一主阻塞和一个下一步动作；次要问题进入可展开清单，避免按钮并列竞争。

## 8. 平台黄金主线

### 8.1 主线阶段与完成证据

| 阶段 | 用户问题 | 最低真实证据 | 专业入口 |
|---|---|---|---|
| 数据连接 | 数据从哪里来，能否访问 | 有效连接和最近一次连通性结果 | 数据连接 |
| 数据接入/来源盘点 | 哪些表/文件进入范围 | 已确认 source binding、Schema/剖析结果 | 数据集成、元数据 |
| 数仓规划 | 为什么建设，范围和策略是什么 | PlanningBaseline READY | 数仓规划 |
| 数据标准 | 字段和口径遵循什么规则 | 标准绑定及版本校验 | 数据标准 |
| 事实/维度模型设计 | 一行是什么、如何分析 | ModelSpec、grain、字段、关系、来源映射 | 模型中心 |
| 构建/质量/发布 | 是否能生成并安全发布 | 编译、测试、质量、发布门禁证据 | 模型中心/高级 dbt |
| 数据资产 | 产物是否可发现和负责 | 资产登记、责任人、血缘 | 数据资产 |
| 指标系统 | 如何统一计算和消费 | 指标引用、口径校验、模型绑定 | 指标系统 |
| 数据服务/运行运维 | 是否稳定提供和可定位问题 | 服务/数据集引用、最近运行和诊断链接 | 数据服务/运行记录 |

### 8.2 工作台是证据投影，不是流程复制器

`数据建设工作台` 读取上述模块的证据，按计划呈现：

- 当前计划和负责人；
- 当前阶段、完成证据和可信度；
- 唯一主阻塞与“继续处理”动作；
- 最近失败、影响对象和修复入口；
- 已发布成果、资产、指标和服务引用。

工作台不复制专业模块的表单和业务逻辑，也不通过浏览记录伪造进度。

## 9. 后台应用与 API 边界

### 9.1 建议资源

```http
GET    /api/modeling/warehouse-plans
POST   /api/modeling/warehouse-plans
GET    /api/modeling/warehouse-plans/{planId}
PATCH  /api/modeling/warehouse-plans/{planId}
POST   /api/modeling/warehouse-plans/{planId}/archive

GET    /api/modeling/warehouse-plans/{planId}/baseline
PUT    /api/modeling/warehouse-plans/{planId}/baseline/business-scope
PUT    /api/modeling/warehouse-plans/{planId}/baseline/sources
PUT    /api/modeling/warehouse-plans/{planId}/baseline/source-mappings
POST   /api/modeling/warehouse-plans/{planId}/baseline/confirm

GET    /api/modeling/warehouse-plans/{planId}/models
GET    /api/modeling/warehouse-plans/{planId}/stage-projection
GET    /api/modeling/warehouse-plans/{planId}/evidence
GET    /api/modeling/warehouse-plans/{planId}/deliverables

POST   /api/modeling/warehouse-plans/{planId}/versions
POST   /api/modeling/warehouse-plans/{planId}/reviews
```

已有 `/api/modeling/vnext/model-specs`、依赖、标准、dbt artifact、release gate、compile、runs 和 lineage 能力优先复用，通过 `planId` 收敛，不复制实现。

### 9.2 命令与查询分离

- 命令 API 修改规划事实并执行租户、版本和门禁校验；
- 查询 API 聚合跨模块证据，但不回写所有者数据；
- 跨模块不可用时返回 `UNKNOWN`/`STALE` 证据，不把未知当成功，也不阻塞查看计划；
- 所有写命令使用乐观锁版本，冲突返回 HTTP 409 和当前版本摘要；
- 删除被模型或成果引用的对象返回 HTTP 409，并提供引用清单。

### 9.3 稳定错误语义

| 错误码 | 含义 |
|---|---|
| `WAREHOUSE_PLAN_VERSION_CONFLICT` | 计划已被他人修改 |
| `PLANNING_BASELINE_INCOMPLETE` | 规划基线缺少必要事实 |
| `SOURCE_BINDING_UNAVAILABLE` | 已选来源不可访问或已失效 |
| `SOURCE_BUSINESS_MAPPING_INCOMPLETE` | 来源尚未全部映射或排除 |
| `MODEL_GRAIN_INCONSISTENT` | 粒度、事实形态和时间语义冲突 |
| `STANDARD_BINDING_STALE` | 标准版本已变化，需要复核 |
| `IMPLEMENTATION_ARTIFACT_CONFLICT` | 设计生成与 dbt 管理权冲突 |
| `PUBLISH_GATE_FAILED` | 发布证据未通过 |
| `LEGACY_PLAN_MIGRATION_REQUIRED` | 旧计划尚未完成迁移确认 |

## 10. 前端信息架构

### 10.1 菜单结构

```text
数据开发与运维
├─ 数据建设工作台
├─ 数仓规划
├─ 模型中心
├─ 高级 dbt
└─ 运行记录
```

数据连接、数据集成、数据标准、数据资产和指标系统保留各自专业菜单。工作台通过 `planId` 和稳定深链连接这些模块，不把它们重新塞进一个巨型页面。

### 10.2 计划页

计划列表只提供一个主要动作“新建规划”。新建时选择：

- 从业务目标开始；
- 从现有数据开始。

选择仅影响计划详情首次打开的 Tab，不创建不同类型的计划。

### 10.3 计划详情六阶段

```text
1. 规划概览
2. 规划基线
3. 数仓架构
4. 事实与维度
5. 实现与验证
6. 发布成果
```

“规划基线”包含“业务范围”和“来源盘点”两个 Tab。顶部只保留计划切换、阶段导航和一个主动作；概念解释首次展示后折叠，错误和次要任务进入侧栏。

### 10.4 模型中心

模型中心负责：

- 事实、维度、汇总和应用模型台账；
- 粒度、过程形态、时间语义、字段和关系；
- 来源映射、标准绑定和物理实现状态；
- 模型版本、发布状态和影响分析；
- 只读 ER 概览与分析关系概览。

主题域管理不再是模型中心入口。治理管理员和建模用户可以是同一个人，因此采用同一控制台内的菜单和 Tab 分工，而不是拆为两套角色产品。

### 10.5 高级 dbt

高级 dbt 负责：

- 项目、文件、SQL、宏和依赖；
- compile、test、run 和 manifest；
- dbt 模型与 `ModelSpec` 的显式绑定；
- 运行诊断与产物回写。

dbt 的写入所有权必须明确：`DESIGNER_GENERATED` 模型默认由模型设计生成，`DBT_MANAGED` 模型由 dbt 工程管理。切换所有权需要显式确认，禁止无提示双向覆盖。

## 11. dbt 回写契约

dbt 产物不是第二套模型台账。每次 import/compile/test/run 后写入或更新：

| 产物 | 回写目标 |
|---|---|
| manifest node | dbt artifact 与 ModelSpec 绑定状态 |
| compiled SQL | 实现修订、校验和、生成时间 |
| tests | 发布门禁证据和失败定位 |
| run result | pipeline run、耗时、状态、错误摘要 |
| lineage | source/model/asset/metric 的血缘边 |
| schema drift | 模型台账差异和待确认变更 |

manifest 反向导入只能创建 `CANDIDATE` 逻辑语义。业务对象、粒度、过程和标准必须由用户确认后才能参与基线或发布门禁。

## 12. 受控退役策略

### 12.1 被替代的设计

- `2026-07-17-domain-modeling-candidate-review-design.md`：保留候选确认和后台不变量作为历史输入，但不再定义主旅程；
- `2026-07-17-generic-modeling-workbench-ui-design.md`：其通用内核研究保留参考，但“关系/维度/dbt 并列模式”和旧四步旅程被本设计替代；
- Sprint-60 至 Sprint-64：保留已实现能力和验收记录，未完成项需映射到 Sprint-65，不回写历史状态。

### 12.2 四阶段退役

| 阶段 | 行为 | 退出条件 |
|---|---|---|
| R0 登记 | 建立旧页面、路由、session 状态、API、表和消费者清单 | 清单有所有者与替代物 |
| R1 兼容 | 新入口上线，旧菜单 ID/权限保留，旧路由重定向并记录使用 | 新双起点主线可用 |
| R2 冻结 | 旧写入口只读，新写入只进 canonical 内核，迁移存量数据 | 迁移核对与回滚验证通过 |
| R3 移除 | 删除无消费者的旧组件/API/表 | 两个版本周期零调用且专项审批 |

任何阶段失败均可通过 Feature Flag 恢复旧入口；已经写入 canonical 聚合的数据不能通过回滚脚本丢弃。

### 12.3 首批退役候选

- 仅重定向到主题域管理的“建模工作台”；
- 把 `DBT_NATIVE` 当作建模方法的前端状态和文案；
- sessionStorage 中作为事实源的 `warehousePlanningContext`；
- 依赖页面访问顺序计算完成度的旅程状态；
- 可编辑但不受模型内核约束的第二份总线矩阵关系；
- 迁移完成后的旧 `modeling_plan*` 写入口。

## 13. 安全、租户与审计

- 所有计划、绑定、模型、版本、证据查询必须带租户边界；
- 计划负责人不自动获得来源数据读取权，页面只展示用户有权查看的字段与样例；
- Excel 剖析和样例数据遵守脱敏、密级和最小暴露策略；
- 规划基线确认、实现所有权切换、发布、归档和迁移均写审计事件；
- 跨模块聚合日志使用 `planId`、`modelSpecId`、`runId` 关联，不记录明文敏感值；
- 迁移工具必须支持 dry-run、行数核对、差异报告和幂等重跑。

## 14. 验收策略

### 14.1 架构验收

- 数据所有权矩阵没有两个可写事实源；
- 业务起点和资产起点最终创建同一种计划和模型；
- dbt 未出现在规划阶段或平台阶段枚举中；
- 行业词只存在于示例、模板和测试夹具；
- 旧表和旧路由均有明确替代物、兼容期和退出条件。

### 14.2 业务旅程验收

至少覆盖：

1. 从业务目标创建计划，完成基线、事实/维度模型、标准绑定、发布与资产/指标引用；
2. 从 Excel/ODS 创建计划，完成剖析、业务确认、来源映射和同一发布流程；
3. 从 dbt manifest 登记候选，人工确认语义后进入模型台账；
4. 发布失败能够从工作台定位到模型、标准、测试或运行证据并返回修复；
5. 旧深链、旧菜单权限和存量规划在兼容期内不丢失。

### 14.3 技术验收

- Liquibase 在空库和存量库升级均通过，迁移可幂等重跑；
- 后端契约测试覆盖门禁、版本冲突、租户隔离和旧记录迁移；
- 前端 source-contract、TypeScript 和构建通过；
- Chrome 95 覆盖双起点、计划恢复、专业页面返回、dbt 高级模式和旧路由；
- GitNexus 变更分析仅命中预期模块和旅程；
- 集成证据保存到 Sprint-65 `it/`，状态不能只由任务文档宣称。

## 15. 实施顺序

```text
F1 架构边界与受控退役
 -> F2 WarehousePlan 持久化与 API
 -> F3 双起点规划基线
 -> F4 经典数仓架构与维度模型
 -> F5 黄金主线与数据建设工作台
 -> F6 模型中心与高级 dbt 分离
 -> F7 菜单路由兼容与旧旅程退役
 -> F8 集成验收与交付证据
```

F3 必须先建立统一规划基线；F3 完成后，F4 模型设计与 F5 工作台证据投影可以并行；F6 等待 F4 的模型契约和 F5 的证据契约稳定。F7 可以先做兼容登记，但最终切换必须等待 F3-F6 验收。

## 16. 评审顺序

后续评审按以下顺序进行，避免从单个按钮倒推架构：

1. 整体：产品主线、领域边界、数据所有权和受控退役；
2. 领域：WarehousePlan、PlanningBaseline、ModelSpec 和证据投影；
3. Feature：八个 Feature 的职责、依赖和完成标准；
4. Task：每个 Task 是否单一、可验证、无隐性跨域写入；
5. UI/API：路由、页面、接口、错误状态和兼容策略；
6. 验收：双起点、迁移、回滚、Chrome 95 和运行证据。
