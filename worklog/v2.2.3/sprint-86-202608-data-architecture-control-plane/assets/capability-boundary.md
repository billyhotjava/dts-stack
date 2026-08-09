# 能力边界与候选实现契约

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

## IMPLEMENTATION CONTRACT（候选，尚未冻结）

### Actors

| Actor | 可维护 | 只读消费 |
|---|---|---|
| 数据架构管理员 | 业务分类、数据域、业务过程、分层、集市、主题域及生命周期 | 资产统计、模型使用量 |
| 数据建模人员 | ModelSpec、字段、关系、实现、发布/物化意图 | 全部数据架构字典 |
| 资产治理人员 | 资产归域、责任、标签、分级、生命周期 | 数据架构字典、物理关系证据 |
| 指标管理员 | 指标定义、版本、派生、发布 | 业务分类、数据域、业务过程、资产 |
| 质量管理员 | 规则、绑定、运行 | 资产身份、域投影、字段元数据 |
| 主数据管理员 | 业务主数据对象、金记录、来源映射、审核和版本 | 数据架构字典；维度模型/资产投影状态 |

> **（RF-86-09）权限粒度约束**：上表 6 种角色需要 6 套不同的写边界，而现有权限模型只有
> `read/write/export` 三档（见 `domain-profile.md` §6），无法按实体类型区分写权限。
> 这使不变量 I07「架构字典单一写 owner」在运行时不可强制，只能靠 UI 约定与代码自觉。
> ADR-86-17 须在 ADR-86-08 冻结前给出强制手段，或显式接受约定级并记为具名风险。

### Canonical owner 候选矩阵

| 能力 | 当前事实源 | 目标 owner | 消费方 |
|---|---|---|---|
| 业务分类/数据域 | `catalog_domain` + `/api/catalog/domains*` | 平台数据架构 | 建模、资产、指标、质量、权限 |
| 业务过程 | `sprint64_business_process` | 平台数据架构 | 事实模型、原子指标、规划 |
| 数仓分层 | 内置契约 + `modeling_warehouse_layer` | 平台数据架构 | ModelSpec、资产、命名校验 |
| 数据集市/主题域 | `modeling_data_mart`、`modeling_subject_domain` | 平台数据架构（PROPOSED；待 F1/T01 与 ADR-86-14） | 应用模型、资产导航 |
| 数仓计划/建设范围 | `modeling_warehouse_plan` 及域/集市关联 | 数据建模规划 | ModelSpec、发布候选 |
| 模型 | ModelSpec/Revision/Lifecycle | 数据建模 | 发布、资产、指标、质量证据 |
| 物理资产 | `catalog_dataset` + CatalogAssetKey | 数据资产 | 搜索、质量、权限、服务 |
| 指标 | `gov_indicator_definition` + version/reference | 数据指标 | 建模、质量、分析 |
| 质量 | rule/version/binding/run | 数据质量 | 资产与发布门禁 |
| 业务主数据 | 现有人员/组织 MDM 网关；通用台账待后续设计 | 独立主数据管理 | 维度建模、资产、指标、业务应用 |

关键实体关系、当前/目标基数、稳定键和状态传播统一引用 [`data-model-relationships.md`](data-model-relationships.md)。本文件只定义能力边界，不另建一套关系语义。

### 状态与字段语义候选

```text
AssetIdentity       = assetType + assetKey + physicalLocator
AssetOrigin         = SOURCE_SYSTEM | INGESTION | DBT | MANUAL | DISCOVERY
WarehouseLayer      = ODS | STG | DWD | DWS | ADS | custom canonical code | null
                      # DIMENSION 模型当前归 DWD；资产目录兼容 DIM 的归一化策略待 RF-86-01 冻结
BusinessContext     = businessCategoryId + dataDomainId? + businessProcessId?
GovernanceReadiness = UNASSIGNED | INCOMPLETE | GOVERNED
ConsumptionState    = DISCOVERED | PUBLISHED | SERVING | STALE | RETIRED
```

这些字段名只用于架构讨论；实施前必须与现有 DTO、枚举和数据库列逐项对账。

### UI surfaces 候选

| 页面类型 | 推荐形态 | 原因 |
|---|---|---|
| 业务分类/数据域 | 树/分组 + Table/详情抽屉，**按单一口径设计** | 层级必须保留。**（RF-86-14）** 不承诺双形态自适应：自适应需实现两套形态加阈值判断，成本高于任选其一，而客户域数至今未决。先按客户口径（10–50、两层）设计，把「实际规模远超此范围则降级」记为具名风险；若确需阈值，复用 `DomainScopeNav.tsx:33` 既有的 `SEARCH_THRESHOLD = 8`，不另立第二个常量 |
| 业务过程/分层/集市/主题域 | 搜索 + Table + 抽屉 | 平面台账适合分页、排序、状态和批量治理 |
| 模型清单 | 搜索/筛选 + 可多选 Table + 行级详情/编辑 | 支持跨模型批量物化；树仅作可选分组筛选 |
| 模型详情 | 独立编辑器或右侧详情区 | 字段、标准、SQL、关系和版本不适合挤入表格 |
| 资产台账 | 搜索/组合筛选 + Table | “全部资产/未归域/按域”均可达；批量归域是一等治理动作，域导航不是资产准入前提。**（RF-86-08）** 域导航计数受 5000 扫描上限约束，触顶后为 `≥N` 近似值，蓝图须明确该降级的产品表达 |
| 指标/质量 | 各自 Table + 业务上下文筛选 | 只消费数据架构字典，不复制维护 |

## NON-GOALS

- 不在本 Sprint 设计多租户 UI、计费或租户隔离。
- 不重写 ModelSpec、ReleaseCandidate、dbt/Airflow 或质量运行控制面。
- 不把所有页面统一成 Table；Table 只用于平面对象和批量操作。
- 不把逻辑模型草稿伪装成已存在的物理资产。
- 不定义外部系统内部实现。
- 不在 Sprint-86 实现通用 MDM、金记录或匹配合并；只冻结它与架构字典、维度模型和资产的接口边界。

## OPEN QUESTIONS

- 数据架构是新一级菜单，还是现有一级菜单下的权威工作区？
- 数据集市/主题域与数仓计划的 owner 边界。
- 数据集市当前多对多存储与目标单业务分类基数的收敛方式。
- APPLICATION 模型是否必须固定主题域，以及 ModelSpec 到物理资产的 locator 契约。
- 维度/事实/汇总/应用模型依赖 DAG、批量候选闭包与二次物化 attempt 规则。
- 资产发现与发布的状态机最小集合；治理状态若参与统计聚合，是否必须可索引（受 ADR-86-16 约束）。
- 纳管范围扩大后的资产统计口径与规模上限（ADR-86-16）；现有 5000 扫描上限与两轮全量扫描是否继续沿用。
- 在 read/write/export 三档粒度下强制架构字典单一写 owner 的手段（ADR-86-17）。
- 指标跨业务分类规则与必填矩阵。
- 旧页面、字段和深链的观测周期。

## HANDOFF

当前结论为“需要架构评审”，尚未达到直接实施条件。F1/T01 冻结语言/owner，F1/T02 冻结关系/E2E，F2～F4 再消费同一契约，最后由 F5 输出正式 ADR、精确接口/schema 契约和下一实施 Sprint；下一 Sprint 再按竖切片进入 TDD、迁移和 UI 验收。通用 MDM 另立 Sprint。
