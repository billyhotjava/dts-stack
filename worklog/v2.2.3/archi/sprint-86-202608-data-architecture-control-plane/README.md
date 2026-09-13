# Sprint-86：平台级数据架构控制面与跨域语义收敛

**时间盒**：2026-08-10 ～ 2026-08-21（10 个工作日）
**状态**：DONE（Architecture；无代码、迁移、构建、部署或真实 E2E 交付）
**类型**：Architecture Enabler / Domain Modeling / Information Architecture / Compatibility Planning
**目标**：在不进入代码实施的前提下，冻结一套平台全局的数据架构元数据/架构字典、关键数据模型关系、资产纳管、指标业务归属和跨模块消费契约，使数据建模、数据资产、数据指标与数据质量不再各自解释业务分类、数据域、数仓分层、来源和版本状态。

## 0. Sprint 治理与时间盒

本 Sprint 是有明确退出条件的架构 Enabler，不是无限期“讨论”。实际人员姓名必须在首次评审前写入对应 IT 记录；仅写角色不能作为已完成证据。

| 责任角色 | 决策责任 | 登记位置 |
|---|---|---|
| 产品决策负责人 | 确认业务目标、范围、优先级和可接受风险 | IT-01、IT-06 |
| 数据架构负责人 | 主持 ADR，保证统一语言、关系、状态和 owner 一致 | IT-01～IT-04、IT-07 |
| 受影响 canonical owner | 对本域契约、兼容路径和验收方式负责 | 各 Feature 对应 IT |
| 安全/权限负责人 | 评审 ADR-86-17 及审计、职责分离边界 | IT-07 |
| Sprint 记录负责人 | 维护状态、决议、异议、行动项和证据链接 | 全部 IT 记录 |

决策规则：每项 ADR 至少由产品决策负责人、数据架构负责人和受影响 canonical owner 共同评审；涉及权限时必须包含安全/权限负责人。未达到该组合或存在未关闭异议时保持 `OPEN/PROPOSED`，不得写成 `ACCEPTED`。

**已登记评审人**：xiezm，兼任本 Sprint 的产品决策、数据架构、安全/权限、交付及各受影响 canonical owner；各 IT 仍按实际评审范围分别记录 PASS/PENDING，不因角色兼任批量通过。

| 里程碑 | 目标日期 | 退出条件 |
|---|---|---|
| Kickoff / IT-01 | 2026-08-10 | 真实参与者、统一语言输入和决策顺序登记完成 |
| 架构关系检查点 | 2026-08-14 | F1/T01、F1/T02 有明确评审结论；未决项有 owner 和截止日期 |
| 领域契约与 IA 评审 | 2026-08-18 | F2/F3/F4 的候选契约与失败路径完成评审 |
| 迁移准入 / IT-06 | 2026-08-20 | 兼容切片、风险、NFR 和下一 Sprint DoR 可判定 |
| Sprint close | 2026-08-21 | IT-01～IT-07 有真实记录；未完成项进入具名后续 Sprint |

WIP 规则：F1 关闭前只允许 F1；随后 F2/F3 最多并行两个 Feature；F4、F5 按依赖顺序进入。时间盒内无法冻结的范围不得静默延期或带入编码，必须由产品决策负责人决定缩减 Sprint 目标或转入 Sprint-86B。

## 1. 背景与价值

本轮讨论最初由维度建模工作台的交互问题触发：目录记录选择、详情编辑、批量物化等能力不断叠加后，继续调整左树或改成 Table 只能处理页面症状，无法解决底层公共数据被放在“数据建模”模块内部的问题。

进一步勘察确认：

1. 业务分类、数据域、业务过程、数仓分层、数据集市和主题域不是业务主数据，而是平台共享的数据架构元数据；它们不仅供建模使用，还会被资产、指标、质量、权限和治理统计消费。
2. `catalog_domain` 已成为资产、质量、权限和建模共享的事实，但维护入口和术语仍分裂在 `/data-modeling/planning/*` 与菜单外的 `/governance/subjects`。
3. 资产侧把“业务分类 / 数据域”称为“业务主题域”，与应用层真正的“主题域”冲突。
4. `SOURCE` 当前被写入 `catalog_dataset.warehouse_layer`，同时又承担“外部来源”的含义；来源与数仓分层被混为一个字段维度。
5. 指标定义仍用自由文本 `category`、`domain`，无法稳定引用业务分类、数据域和业务过程。
6. 当前没有完整租户能力，继续围绕租户设计 UI 与架构字典隔离会增加不存在的产品复杂度。

如果不先收敛这些问题，后续把模型目录改成 Table、增加批量物化或继续重构资产页，只会形成新的平行解释和迁移债务。

## 2. 本 Sprint 的能力陈述

平台数据架构管理员可以在一个权威控制面维护平台全局的业务分类、数据域、业务过程、数仓分层、数据集市和主题域；数据建模只引用这些架构字典完成模型设计与交付，数据资产登记所有稳定可寻址的真实关系并独立标记治理/可消费状态，数据指标引用业务上下文，数据质量引用资产身份。任何消费者不得复制架构字典 CRUD 或用中文名称、自由文本代替稳定 ID。人员、组织、项目、物料等业务主数据由后续独立 MDM 能力维护，并通过引用/分析投影接入本链路。

## 3. 目标边界假设

> 下图是本 Sprint 已批准的目标架构。完整关系、当前物理事实、目标基数、状态传播和端到端用例见 [`assets/data-model-relationships.md`](assets/data-model-relationships.md)；当前源码/schema 与目标之间的实现缺口转入 Sprint-87。

```text
平台级数据架构字典（唯一维护者，建议从建模中独立）
  ├─ 业务分类 ──1:n── 数据域 ──1:n── 业务过程
  ├─ 业务分类 ──1:n── 数据集市 ──1:n── 主题域
  └─ 数仓分层（与业务树正交）
                 │ 稳定 ID / 状态 / 版本
                 ▼
数仓计划（建设范围）──▶ ModelSpec ──▶ Revision ──▶ ReleaseCandidate
                                                    │
                                                    ▼
                                         构建/物化/物理关系观测
                                                    │
                         ┌──────────────────────────┴──────────────────────────┐
                         ▼                                                     ▼
                语义模型资产投影                                      物理表/视图资产
                         │                                                     │
                         └──────────────▶ 指标 / 质量 / 权限 / 分析 ◀──────────┘

业务主数据（后续独立 MDM）──分析投影──▶ DIMENSION ModelSpec ──物化──▶ 维度表资产
```

边界原则：

- 数据架构负责“定义什么、归属哪里、生命周期是什么”，不负责模型字段设计、SQL、构建或物化。
- 数据建模负责“如何形成模型并交付”，不再拥有公共架构字典定义。
- 数据资产负责“哪些稳定物理关系存在、状态如何、能否消费”，不反向维护业务分类或分层字典。
- 指标负责指标定义、派生、版本和发布，业务分类是业务归属，不等于指标类型或自定义分组。
- 数据质量负责规则、绑定和运行证据，数据集和数据域来自资产/架构投影。
- 业务主数据负责业务实体金记录及来源映射，不拥有架构字典；分析维度只是主数据的下游投影。

## 4. 已批准方向与实施边界

完整登记见 [`assets/decision-register.md`](assets/decision-register.md)；F1/T01 的统一语言、owner 与权限结论见 [`assets/f1-t01-decision-pack.md`](assets/f1-t01-decision-pack.md)，D01～D11、N01～N12 的集中批准记录见 [`assets/consolidated-approval-pack.md`](assets/consolidated-approval-pack.md)。所有结论均为架构约束，不代表运行实现已经完成。

| 主题 | 当前状态 | 当前结论 |
|---|---|---|
| 租户范围 | 用户已确认 | 当前阶段按平台全局设计；不提供租户选择器，不新增租户级架构字典副本 |
| 业务树 | 既有决策 | 业务分类 1:n 数据域，数据域单父级；数仓分层与业务树正交 |
| 资产范围 | ACCEPTED | 所有稳定、可寻址、可发现的真实表/视图都进入资产台账；是否可消费由独立状态决定 |
| `SOURCE/DIM` | ACCEPTED | SOURCE 归一为 ProducerRef 且 canonical layer 为空；DIM 归一为 `DWD + DIMENSION_TABLE`，旧值兼容保留 |
| 指标分类 | 用户已确认方向 | `category` 应表达业务分类；指标类型、指标分组必须另设语义 |
| 关键关系链 | ACCEPTED | 双身份、集市单分类、APPLICATION 主题域、revision DAG、候选原子创建与二次物化规则已冻结 |
| 业务主数据边界 | ACCEPTED | 只冻结 MDM 与架构字典、维度模型、资产的边界；人员/组织/项目/物料等具体能力后续实现 |
| 数据架构逻辑控制面 | ACCEPTED | 公共架构字典归属平台数据架构；移动并收敛既有能力，不新建第二套表、API 或 CRUD。是否新增一级菜单由 ADR-86-09 决定 |
| UI 形态 | ACCEPTED | 新增一级“数据架构”例外并只重组既有能力；模型记录树改搜索/筛选 + 多选 Table；层级选择仍用树 |
| 资产统计规模 | ACCEPTED_DESIGN | 服务端增量投影 + 24h 对账；100,000 资产/50 域设计容量；P95 ≤1.5s；5/10 分钟 freshness/stale；运行验证转 Sprint-87 |
| 写权限强制 | ACCEPTED（实施待后续 Sprint） | 唯一 command boundary + 现有权限 guard；仅 `ROLE_ADMIN/ROLE_OP_ADMIN/ROLE_INST_DATA_OWNER` 可写平台全局字典，部门角色只读（ADR-86-17、RF-86-09） |
| 资产来源轴 | ACCEPTED | ProducerRef 与 RegistrationEvidence 分轴，多渠道发现幂等归并同一 CatalogAssetKey（ADR-86-18） |
| 资产状态轴 | ACCEPTED | 发现、治理、发布、服务健康、生命周期五轴；消费资格由策略计算并返回原因（ADR-86-19） |

## 5. 已批准的端到端契约链

关系基数、稳定引用、状态传播、失败规则和迁移顺序已通过架构 G1；精确 schema/API/DTO 及运行证据由 Sprint-87 在 G0/DoR 通过后补齐。

端到端主路径不是“页面 A 跳到页面 B”，而是：架构对象 → 建设范围 → 不可变模型修订 → 发布候选 → 构建/质量/物化证据 → 语义资产与物理资产 → 指标/质量消费。任一环节都必须保留稳定 ID、版本、owner、失败状态和审计。

| 层 | 当前落点 | 本 Sprint 要冻结的目标契约 |
|---|---|---|
| UI | `/data-modeling/planning/*`、`/governance/subjects`、资产台账、指标工作台 | 架构字典的唯一维护入口；各消费者只读选择/筛选；模型列表支持单选编辑与多选交付 |
| API | `/api/catalog/domains`、`/api/catalog/domains/tree`、建模规划资源、指标资源 | 稳定 ID、层级、状态和版本的只读/维护边界；禁止重复 CRUD；兼容旧深链 |
| Service | `CatalogDomainResource`、`WarehouseLayerApplicationService`、规划服务、指标服务 | 明确 canonical owner 与 consumer port；跨模块只依赖契约，不直接复制校验 |
| 数据 | `catalog_domain`、`modeling_*` 规划/模型/发布表、`catalog_dataset`、`gov_indicator_definition`、`gov_rule_binding` | 平台全局架构字典；模型 head/revision、语义资产/物理资产、来源/分层/业务归属/治理状态正交；名称仅作展示投影 |
| 迁移 | 现有字段与路由均在使用 | Expand → 双读/回填 → 切换 → 观测 → Contract；任何删除另行审批 |

## 6. 现状勘察账本（Context Ledger）

本账本是后续 Task 的共享事实源；除非状态发生变化，不再重复扫描相同问题。

| # | 事实 | 证据 |
|---|---|---|
| L01 | `catalog_domain` 是带 `parent_id` 的单父级树，本体无 tenant 字段 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/catalog/CatalogDomain.java:9` |
| L02 | 域的树 CRUD 与资产统计都由 `/api/catalog/domains*` 提供，`withStats` 复用资产台账统计口径 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/catalog/CatalogDomainResource.java:56`、`:147` |
| L03 | 规划中已约定“业务分类→数据域→业务过程”和“业务分类→数据集市→主题域”，数仓分层正交；建模空间保持隐藏单空间占位 | `worklog/v2.2.3/planning-redesign-dataworks-20260806/design.md:10`、`:18`、`:27` |
| L04 | 资产侧与规划侧存在术语漂移、两个域 CRUD 页面和 5 处老深链，不能直接删除 `SubjectAreasPage` | `worklog/v2.2.3/asset-domain-navigation-20260809/README.md:39`、`:64`、`:74` |
| L05 | `CatalogDataset` 直接引用 `CatalogDomain`，同时用字符串保存 `warehouseLayer` | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/catalog/CatalogDataset.java:23`、`:54` |
| L06 | 当前 SOURCE/ODS 写入链把外部源表记为 `SOURCE`、落地表记为 `ODS`，并创建来源到 ODS 的关系 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/lineage/IngestionLineageWriter.java:38`、`:203`、`:414` |
| L07 | dbt 同步只把 `view/table/incremental` 视为物理实现，并只识别 ODS/STG/DWD/DWS/ADS 受控分层 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtAssetSyncService.java:1165`、`:1291`、`:1640` |
| L08 | 数仓分层已存在“内置代码 + 全局自定义表”的唯一投影，存储层明确无 tenant 字段 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/modeling/WarehouseLayerRepository.java:15`、`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/warehouse/WarehouseLayerApplicationService.java:31` |
| L09 | 质量数据集选择直接从 `CatalogDataset` 投影 domainId/domainName | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/QualityDatasetCatalogService.java:30`、`:43` |
| L10 | 指标 `category` 与 `domain` 均为自由文本，前端也是普通文本输入 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/governance/GovIndicatorDefinition.java:23`、`:171`；`source/dts-platform-webapp/src/pages/data-modeling/prototype/MetricsPage.tsx:336` |
| L11 | 2026-08-09 当前环境实测：域 4（根 2、子 2）；数据集 83，归域 0，带分层 60（ODS 57、SOURCE 3）；指标 0 | `assets/domain-profile.md` §3 |
| L12 | 当前规划样本很小：计划 1、计划域 2、业务过程 1、数据集市 1、主题域 1、活动模型分层仅 DWD 1 | `assets/domain-profile.md` §3 |
| L13 | GitNexus 预研：`GovIndicatorDefinition` 为 HIGH（18 个直接消费者）；`CatalogDomain` 为 MEDIUM；分层服务和 SOURCE 写入链为 LOW | `assets/impact-baseline.md` |
| L14 | **DIM 语义跨上下文分裂**：建模 canonical layer 与 dbt 受控层无 DIM，DIMENSION 模型归 DWD；资产目录/前端却允许或展示 DIM，必须决定兼容归一化，而非断言“后端不存在” | `ModelSpecContract.java:218`、`CatalogAssetOverviewAggregator.java:20`、`CatalogDataset.java:54`、`assetPageShared.tsx:77`；见 RF-86-01 |
| L15 | GitNexus 对本次抽查的 React 组件出现 upstream impact 返回 0 的假阴性；前端影响分析必须用 GitNexus + scoped `rg` 交叉验证，不能把结论外推为所有 TS 符号均系统性失效 | `assets/impact-baseline.md`、`assets/review-findings.md` RF-86-04 |
| L16 | 当前 `modeling_data_mart_domain` 可表达数据集市↔域多对多，与既有“业务分类 1:n 数据集市”目标基数存在实现漂移 | `20260727_01_modeling_data_mart.xml:52`；见 ADR-86-14 |
| L17 | 主题域通过非空 `mart_id` 归属数据集市，但 ModelSpec 目前没有 `subject_domain_id` | `20260806_03_modeling_subject_domain.xml:20`、`:40`；`assets/data-model-relationships.md` §3 |
| L18 | ModelSpec revision 不可变快照通过 candidate entry 固定 revision/checksum/implementation，再进入 dispatch 与 physical observation | `20260724_03_model_release_candidate.xml:65`、`20260727_12_model_materialization_dispatch.xml:21`、`20260727_13_physical_relation_observation.xml:22` |
| L19 | ModelSpec 以独立 `SEMANTIC_MODEL` 身份投影 latest-published/serving；该身份不同于表/视图物理资产 | `20260802_03_catalog_model_serving_projection.xml:18`、`:23` |
| L20 | `GovRuleBinding` 绑定规则版本与 UUID `datasetId`；指标当前仍用字符串 category/datasetId，二者契约成熟度不同 | `GovRuleBinding.java:25`、`:30`；`GovIndicatorDefinition.java:29`、`:38` |
| L21 | 现有 `dts-admin /api/mdm` 是人员/组织同步网关，不是通用业务主数据控制面 | `MdmGatewayResource.java:20`；`docs/release/2025-11-17-mdm-sync.md:1` |
| L22 | 关键关系、状态传播、E2E-A～D 和未决基数统一登记于关系契约，F2～F5 必须消费而非复制定义 | `assets/data-model-relationships.md` |
| L23 | 架构字典当前存储作用域不一致：域/过程/canonical 分层全局，数据集市/主题域仍带 tenant_id；产品虽按平台全局，兼容 scope 仍需冻结 | `20260711_01_sprint64_governance.xml:8`、`20260727_01_modeling_data_mart.xml:14`、`20260806_03_modeling_subject_domain.xml:20` |
| L24 | 维度定义 revision 已可被 DIMENSION ModelSpec head/revision 固定；业务矩阵仍以无外键字符串 `dimension_id` 连接过程与维度，模型依赖闭包需单独冻结 | `20260724_01_dimension_definition.xml:162`、`20260711_01_sprint64_governance.xml:34` |
| L25 | **资产统计存在既有规模上限**：`ASSET_STATS_SCAN_CAP=5000`（分页 200×25 页），每次资产地图加载已跑两轮全量扫描，`truncated` 判定依赖波动“可达数千”的 legacy total 估算而非纯展示字段；`withStats` 域树统计复用同一路径。代码注释自陈“若要修，应连同该成本问题一并设计（例如缓存 domainStats）” | `CatalogAssetPortalService.java:60`、`:132-133`、`:500-509`；`CatalogDomainResource.java:166`；见 RF-86-08 |
| L26 | 产品/前端授权能力仍是 `read/write/export` 粗粒度；后端另有 `CATALOG_MAINTAINERS/DATA_MAINTAINER_ROLES`、受限域 `EDIT/MANAGE` 校验和计划部门 guard，但不存在数据架构专属角色，且 `CatalogDomainResource` 仍直写 Repository。I07 可部分预防，尚未形成完整职责分离 | `AuthoritiesConstants.java:8-67`、`CatalogDomainResource.java:87-143,229-264`、`WarehousePlanAuthorizationGuard.java:16-91`；见 `assets/f1-t01-decision-pack.md`、RF-86-09 |
| L27 | 初稿候选 `AssetOrigin` 同时包含生产者与登记方式，初稿 `ConsumptionState` 同时包含发现、发布、健康和退役；现已按 ADR-86-18/19 批准为正交来源/登记与五类状态轴 | `assets/capability-boundary.md`；见 RF-86-18 |
| L28 | 当前前端已声明 8 个规划路由，其中业务分类、分层、数据域、业务过程、数据集市、主题域 6 个属于架构字典；空间和系统设置继续留在建模范围 | `source/dts-platform-webapp/src/pages/data-modeling/navigation.ts:8-36`；`source/dts-admin/src/main/resources/config/data/portal-menu-seed.json:216-287` |
| L29 | `/governance/subjects` 的独有工作区固定为建模范围、数据集市、主题域信息、治理概览 4 个 Tab，必须逐项迁移而非整页删除 | `source/dts-platform-webapp/src/pages/governance/SubjectWorkspaceTabs.tsx:4-29` |
| L30 | 既有发布候选控制面已提供 workspace、materializations、retry、refresh、cancel、rematerialize 和 rollback 等端点，批量/二次物化应兼容扩展该边界而非新增平行 `/bulk-materialize` | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/ModelReleaseCandidateResource.java:37-442` |
| L31 | 资产与指标已有 canonical API seam：`/api/catalog/assets-v2`（含治理 PATCH）和 `/api/governance/indicators*`；下一实施 Sprint 采用兼容字段扩展和逐消费者切换 | `CatalogAssetPortalResource.java:42,368`；`GovernanceIndicatorResource.java:58,106-642` |
| L32 | Sprint-87 已建立 F0～F6 的 BLOCKED 实施骨架；Sprint-86 ADR/IT 已通过，客户画像、GitNexus 刷新、登录、备份和 Chrome 95 仍是 G0 阻塞，不得因架构文档关闭提前编码 | `../sprint-87-202608-data-architecture-implementation/README.md` |

> **评审标记**：本账本及以下各节存在 20 项独立评审发现（1 BLOCKER / 11 MAJOR / 8 MINOR），
> 登记于 [`assets/review-findings.md`](assets/review-findings.md)。
> 第一轮 RF-86-01～07，第二轮（全量复核 20 文件 / 1254 行）新增 RF-86-08～14。
> 第三轮按现代 Sprint/DoR/DoD/追溯要求新增 RF-86-15～20，并已通过本文档结构修订与集中批准关闭架构问题。
> **RF-86-01**（DIM 定性）与 **RF-86-08**（统计规模冲突）均已关闭；运行实现与验证转入 Sprint-87。
> **RF-86-09** 已因 ADR-86-08/17 冻结转为 `MITIGATED`，实现闭环由 F5/T01 转入下一实施 Sprint。

## 7. Gate Registry

| Gate | 项目 | 状态 | 证据 | 未过则关联 Task |
|---|---|---|---|---|
| G0 | 交付基线 | N/A | `it/baseline.md`；本 Sprint 只做架构讨论，不交付运行功能 | - |
| G0 | 领域与数据画像 | PASS（架构基线） | `assets/domain-profile.md`；本地事实与客户口径已分开，客户/生产画像缺口具名转入 Sprint-87 F0 | - |
| G0 | DTS 领域不变量 | PASS | 本文 §3、§4；禁止平行 owner、密级与业务语义分离 | - |
| G1 | 契约链贯通 | PASS（架构设计） | 关系、基数、映射、失败规则与 E2E 用例设计已批准；运行证据转 Sprint-87 | - |
| G1 | 非功能预算 | PASS（架构设计） | NFR-86-01～13 已有批准设计值/方案、owner 和 fitness function；运行验证转 Sprint-87 | - |
| G2 | 变更范围与影响分析 | N/A | 本 Sprint 无源码修改；GitNexus 最新索引与逐 symbol impact 是 Sprint-87 G0/G2 门禁 | - |
| G3 | 发布安全 | N/A | 本 Sprint 无代码、schema 或部署 | - |
| G4 | 可运维性 | N/A | 本 Sprint 无运行能力 | - |
| G4 | DoD 验收 | PASS（Architecture） | `it/README.md`；IT-01～07 均形成真实架构评审结论 | - |

## 8. Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 |
|---|---|---:|---|---|
| F1 | 数据架构元数据与控制面 | 2 | P0 | DONE |
| F2 | 资产范围、来源与分层语义 | 1 | P0 | DONE |
| F3 | 指标业务上下文 | 1 | P1 | DONE |
| F4 | 信息架构与页面形态 | 1 | P1 | DONE |
| F5 | 兼容迁移与实施准入 | 1 | P0 | DONE |

**完成顺序**：F1/T01 → F1/T02 → F2/F3 → F4 → F5。代码实施只允许在 Sprint-87 关闭 F0/G0 后开始。

## 9. 追溯矩阵

| 需求点 | ADR / 契约 | Feature / Task | 评审 | 证据位置与当前状态 |
|---|---|---|---|---|
| 公共底层数据从建模模块中独立出来 | ADR-86-08/17；owner 矩阵 | F1/T01、F4/T01 | IT-01、IT-02、IT-05、IT-07 | PASS；唯一 command boundary、方案 A 与 IA 蓝图已批准 |
| 关键数据模型关系端到端逻辑贯通 | ADR-86-13/14/15；`data-model-relationships.md` | F1/T02、F2～F5 | IT-03、IT-04 | PASS；双身份、DAG、候选、attempt 与 E2E 用例设计已批准 |
| 平台暂不支持多租户，按全局设计 | ADR-86-01/10 | F1/T01、F5/T01 | IT-01、IT-06 | PASS；平台 scope 与兼容迁移路线图已批准 |
| 所有真实表进入资产，但 SOURCE/DIM 不混淆分层 | ADR-86-04/05/16/18/19 | F2/T01 | IT-04、IT-07 | PASS；纳管、来源/登记、五轴、统计与 NFR 设计已批准 |
| 指标 category 对应业务分类 | ADR-86-06/07 | F3/T01 | IT-04 | PASS；指标上下文、兼容字段和逐消费者矩阵已批准 |
| 模型工作台支持列表、多选和详情编辑 | ADR-86-09/15 | F4/T01 | IT-05 | PASS；页面蓝图、操作走查和旧路由映射已批准 |
| 多表依赖与二次物化可追溯 | ADR-86-15；NFR-86-04～08/12 | F1/T02、F4/T01、F5/T01 | IT-03、IT-05、IT-07 | PASS；DAG、闭包、attempt/observation、并发和设计容量已批准 |
| 避免破坏现有页面、深链和消费方 | ADR-86-10 | F5/T01 | IT-06 | PASS；Expand/Contract、回滚、观测和 Sprint-87 骨架已批准 |
| 主数据模块后续可演进且不与本 Sprint 冲突 | ADR-86-12；E2E-D | F1/T01、F1/T02 | IT-02、IT-03 | PASS；只冻结 MDM 边界，E2E-D 转独立后续 Sprint |

## 10. 集中审批已完成，外部输入已移交

2026-08-09，xiezm 以全部法定评审角色批准 [`assets/consolidated-approval-pack.md`](assets/consolidated-approval-pack.md) 的全部选择：

1. D01～D03：资产范围、SOURCE/DIM、指标上下文。
2. D04：一级“数据架构”入口例外、模型多选 Table 与旧路由承接。
3. D05：Expand/Contract、14 天 + 一个发布周期的观测门禁。
4. D06～D08：语义/物理双身份、集市/主题域基数、DAG/批量/二次物化。
5. D09～D11：统计投影、生产者/登记渠道、五轴状态。
6. N01～N12：设计容量、P95、轮询、卡顿/取消、并发、Chrome 95 与兼容窗口。

上述批准只冻结架构语义与设计预算。另有三类外部输入不在 Sprint-86 内伪造：客户/生产画像、可用的 GitNexus 索引、真实登录/备份/Chrome 95 条件。它们已进入 Sprint-87 F0/G0；MDM 具体实现继续留在独立后续 Sprint。

## 11. 完成标准

- [x] 统一语言、实体关系和 canonical owner 经架构评审确认。
- [x] 架构字典、计划、ModelSpec/Revision、候选、物化证据、语义/物理资产、指标和质量的关键关系全部冻结。
- [x] E2E-A～C 的用例设计可追溯到下一实施 Sprint 的 UI、API、数据和审计证据类型；E2E-D 明确作为后续 MDM Sprint 输入。
- [x] 平台全局作用域与未来 tenant 兼容策略冻结。
- [x] 物理资产纳管矩阵、来源/分层/状态三轴契约冻结。
- [x] 指标业务分类、数据域、业务过程、指标类型/分组关系冻结。
- [x] 数据架构、建模、资产、指标、质量的目标导航和页面职责冻结。
- [x] 旧字段、旧路由、重复 CRUD 的 Expand/Contract 迁移顺序和回滚原则冻结。
- [x] 资产统计口径与规模上限（ADR-86-16）与纳管范围（ADR-86-04）同批冻结。
- [x] 架构字典写权限强制手段（ADR-86-17）已明确；运行时实施由 F5/T01 具名承接。
- [x] 资产生产者/来源与登记渠道（ADR-86-18）以及正交状态轴（ADR-86-19）冻结，不再使用混合枚举。
- [x] `assets/nfr-budget.md` 的所有预算均有批准设计值/方案、owner 与可执行 fitness function 规格；运行证据转 Sprint-87。
- [x] 三套状态词汇已统一映射，ADR 冻结结果有单一写回位置（RF-86-11／13）。
- [x] Sprint 时间盒、真实参与者、决策记录和行动项完整登记；实现与外部输入进入具名 Sprint-87。
- [x] 每个 Feature/Task 通过自己的架构 DoR/DoD，且可追溯到对应 IT 记录。
- [x] 下一实施 Sprint 已按竖切片拆分，并以 F0/G0 和各 Task DoR 约束精确 API/DTO/schema/UI 实施契约。

## 12. 非目标

- 本 Sprint 不修改 Java、TypeScript、菜单种子、数据库 schema、运行数据或容器。
- 不在架构结论冻结前重写模型工作台或资产页面。
- 不为了“平台全局”删除现有 `tenant_id` 列；兼容策略必须先评审。
- 不把密级、权限或业务标签并入业务分类/数据域字段。
- 不新建第二套域、分层、资产、指标或发布控制面。
- 不在本 Sprint 实现通用业务主数据、金记录、匹配合并或主数据 UI；只冻结与数据架构、维度模型和资产的边界。
