# 能力边界与已批准实现契约

## CAPABILITY

平台需要一个全局、唯一的数据架构控制面：架构管理员维护业务和数仓架构字典，建模、资产、指标、质量通过稳定 ID 和只读投影消费。用户能清楚区分“业务归属、技术分层、资产来源、治理状态、指标类型”，并从各业务页面回到权威定义，而不是在多个页面维护同一实体。业务主数据由独立 MDM owner 维护，只通过引用或分析投影接入。

## CONSTRAINTS

- 当前不提供完整多租户能力，产品范围按平台全局。
- 复用现有 `catalog_domain`、分层投影、规划台账、资产台账和指标 owner，禁止平行实现。
- 业务分类/数据域树为单父级；数仓分层与业务树正交。
- 资产身份、来源、分层、业务归属、治理状态分别建模。
- 指标业务分类不是指标类型，也不是自由分组。
- 密级与业务语义严格分离；所有写操作受权限和审计保护。
- 存量字段和路由先兼容迁移，不直接删除。

## IMPLEMENTATION CONTRACT（架构设计已批准；运行实现转 Sprint-87）

### Actors

| Actor | 可维护 | 只读消费 |
|---|---|---|
| 数据架构管理员 | 业务分类、数据域、业务过程、分层、集市、主题域及生命周期 | 资产统计、模型使用量 |
| 数据建模人员 | ModelSpec、字段、关系、实现、发布/物化意图 | 全部数据架构字典 |
| 资产治理人员 | 资产归域、责任、标签、分级、生命周期 | 数据架构字典、物理关系证据 |
| 指标管理员 | 指标定义、版本、派生、发布 | 业务分类、数据域、业务过程、资产 |
| 质量管理员 | 规则、绑定、运行 | 资产身份、域投影、字段元数据 |
| 主数据管理员 | 业务主数据对象、金记录、来源映射、审核和版本 | 数据架构字典；维度模型/资产投影状态 |

> **（RF-86-09）权限分层约束**：上表 6 种业务职责需要不同写边界。产品/前端授权能力仍是
> `read/write/export` 粗粒度；后端另有 `CATALOG_MAINTAINERS/DATA_MAINTAINER_ROLES`、域访问策略和计划部门 guard，
> 但这些是跨能力复用的宽角色，并非“数据架构管理员”专属授权。I07 必须同时通过唯一 application command boundary
> 约束代码 owner，并由现有 actor allowlist/对象 guard 约束人员写入；审计只能作为侦测控制。方案 A 已在
> [`f1-t01-decision-pack.md`](f1-t01-decision-pack.md) 与 IT-07 权限子评审中通过，ADR-86-08/17 已按前置顺序冻结；运行时收口留给下一实施 Sprint。

### Canonical owner 矩阵

| 能力 | 当前事实源 | 目标 owner | 消费方 |
|---|---|---|---|
| 业务分类/数据域 | `catalog_domain` + `/api/catalog/domains*` | 平台数据架构 | 建模、资产、指标、质量、权限 |
| 业务过程 | `sprint64_business_process` | 平台数据架构 | 事实模型、原子指标、规划 |
| 数仓分层 | 内置契约 + `modeling_warehouse_layer` | 平台数据架构 | ModelSpec、资产、命名校验 |
| 数据集市/主题域 | `modeling_data_mart`、`modeling_subject_domain` | 平台数据架构（owner 已冻结；基数与关系由 ADR-86-14 决定） | 应用模型、资产导航 |
| 数仓计划/建设范围 | `modeling_warehouse_plan` 及域/集市关联 | 数据建模规划 | ModelSpec、发布候选 |
| 模型 | ModelSpec/Revision/Lifecycle | 数据建模 | 发布、资产、指标、质量证据 |
| 物理资产 | `catalog_dataset` + CatalogAssetKey | 数据资产 | 搜索、质量、权限、服务 |
| 指标 | `gov_indicator_definition` + version/reference | 数据指标 | 建模、质量、分析 |
| 质量 | rule/version/binding/run | 数据质量 | 资产与发布门禁 |
| 业务主数据 | 现有人员/组织 MDM 网关；通用台账待后续设计 | 独立主数据管理 | 维度建模、资产、指标、业务应用 |

关键实体关系、当前/目标基数、稳定键和状态传播统一引用 [`data-model-relationships.md`](data-model-relationships.md)。本文件只定义能力边界，不另建一套关系语义。

### 状态与字段语义

```text
AssetIdentity       = assetType + assetKey + physicalLocator
ProducerRef         = producerKind + producerStableId?
                      # producerKind: SOURCE_SYSTEM | INGESTION_JOB | DBT_MODEL | MANUAL_BUILD | MODELING
RegistrationEvidence = channel + observedAt + evidenceRef
                      # channel: SCANNER | INGESTION_EVENT | DBT_SYNC | MATERIALIZATION_OBSERVATION | MANUAL
WarehouseLayer      = ODS | STG | DWD | DWS | ADS | custom canonical code | null
                      # SOURCE→ProducerRef+null；DIM→DWD+DIMENSION_TABLE，旧值兼容保留
BusinessContext     = businessCategoryId + dataDomainId? + businessProcessId?
DiscoveryState      = DISCOVERED | VERIFIED | MISSING
GovernanceReadiness = UNASSIGNED | INCOMPLETE | GOVERNED
PublicationState    = UNPUBLISHED | PUBLISHED | WITHDRAWN
ServingHealth       = UNKNOWN | HEALTHY | STALE | FAILED
LifecycleState      = ACTIVE | DEPRECATED | RETIRED
ConsumptionEligibility = policy(DiscoveryState, GovernanceReadiness, PublicationState,
                                ServingHealth, LifecycleState, quality, permission)
```

这些字段名是已批准的架构语义；实施前仍须与现有 DTO、枚举和数据库列逐项对账。`ProducerRef` 与 `RegistrationEvidence` 不得合并：同一来源系统表可能被多个渠道重复发现。消费资格是可解释的策略结果，不得保存成覆盖发现、发布、健康和退役事实的单一“总状态”。详细契约见 [`f2-f3-data-contract-pack.md`](f2-f3-data-contract-pack.md)，ADR-86-18/19 已批准。

### UI surfaces

| 页面类型 | 已批准形态 | 原因 |
|---|---|---|
| 业务分类/数据域 | 树/分组 + Table/详情抽屉，**按单一口径设计** | 层级必须保留。**（RF-86-14）** 不承诺双形态自适应：自适应需实现两套形态加阈值判断，成本高于任选其一，而客户域数至今未决。先按客户口径（10–50、两层）设计，把「实际规模远超此范围则降级」记为具名风险；`DomainScopeNav.tsx:33` 的 `SEARCH_THRESHOLD = 8` 只决定搜索框是否显示，不作为形态切换阈值 |
| 业务过程/分层/集市/主题域 | 搜索 + Table + 抽屉 | 平面台账适合分页、排序、状态和批量治理 |
| 模型清单 | 搜索/筛选 + 可多选 Table + 行级详情/编辑 | 支持跨模型批量物化；树仅作可选分组筛选 |
| 模型详情 | 独立编辑器或右侧详情区 | 字段、标准、SQL、关系和版本不适合挤入表格 |
| 资产台账 | 搜索/组合筛选 + Table | “全部资产/未归域/按域”均可达；批量归域是一等治理动作，域导航不是资产准入前提。目标使用增量统计投影；旧 fallback 受 5000 上限约束，触顶只显示 `≥5000/isApproximate/asOf` |
| 指标/质量 | 各自 Table + 业务上下文筛选 | 只消费数据架构字典，不复制维护 |

## NON-GOALS

- 不在本 Sprint 设计多租户 UI、计费或租户隔离。
- 不重写 ModelSpec、ReleaseCandidate、dbt/Airflow 或质量运行控制面。
- 不把所有页面统一成 Table；Table 只用于平面对象和批量操作。
- 不把逻辑模型草稿伪装成已存在的物理资产。
- 不定义外部系统内部实现。
- 不在 Sprint-86 实现通用 MDM、金记录或匹配合并；只冻结它与架构字典、维度模型和资产的接口边界。

## 已批准设计包

2026-08-09，xiezm 已批准 [`consolidated-approval-pack.md`](consolidated-approval-pack.md) 的全部选择：

- D01～D03：资产范围、SOURCE/DIM、指标上下文；
- D04～D05：一级数据架构入口、模型 Table、兼容迁移；
- D06～D08：双身份、集市/主题域基数、DAG/批量/二次物化；
- D09～D11：统计投影、来源/登记、五轴状态；
- N01～N12：容量、时延、轮询、超时、并发、Chrome 95 与 Contract 窗口。

ADR-86-17 方案 A 及 D01～D11、N01～N12 均已批准；客户画像、GitNexus、登录/备份/Chrome 95 是 Sprint-87 G0 外部输入，不由本文件代填。

## HANDOFF

F1/T01～F5/T01 的语言、owner、MDM 边界、权限、关系、资产、指标、IA、迁移和 NFR 架构设计均已冻结并写回 ADR/IT。Sprint-87 仍须先关闭 G0，再按 F1～F6 进入 TDD、迁移、UI 和集中验收。通用 MDM 另立 Sprint。
