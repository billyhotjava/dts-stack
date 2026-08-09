# Sprint-86：平台级数据架构控制面与跨域语义收敛

**时间**：2026-08
**状态**：DRAFT / ARCHITECTURE_DISCUSSION
**类型**：Architecture / Domain Modeling / Information Architecture / Compatibility Planning
**目标**：在不进入代码实施的前提下，冻结一套平台全局的数据架构元数据/架构字典、关键数据模型关系、资产纳管、指标业务归属和跨模块消费契约，使数据建模、数据资产、数据指标与数据质量不再各自解释业务分类、数据域、数仓分层、来源和版本状态。

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

> 下图是本 Sprint 的核心架构假设。完整关系、当前物理事实、目标基数、状态传播和端到端用例见 [`assets/data-model-relationships.md`](assets/data-model-relationships.md)，须经 F1～F5 讨论后才能转为正式 ADR。

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

## 4. 已确认方向、建议与未决项

完整登记见 [`assets/decision-register.md`](assets/decision-register.md)。

| 主题 | 当前状态 | 当前结论 |
|---|---|---|
| 租户范围 | 用户已确认 | 当前阶段按平台全局设计；不提供租户选择器，不新增租户级架构字典副本 |
| 业务树 | 既有决策 | 业务分类 1:n 数据域，数据域单父级；数仓分层与业务树正交 |
| 资产范围 | 方向已确认，边界待冻结 | 所有稳定、可寻址、可发现的真实表/视图都进入资产台账；是否可消费由独立状态决定 |
| `SOURCE` | 建议 | 从“数仓分层”迁为“资产来源/来源区”语义；外部源表的 warehouseLayer 应为空 |
| 指标分类 | 用户已确认方向 | `category` 应表达业务分类；指标类型、指标分组必须另设语义 |
| 关键关系链 | 候选，待 F1/T02 评审 | 架构字典 → 计划范围 → ModelSpec/Revision → 候选 → 物化证据 → 语义/物理资产 → 指标/质量 |
| 业务主数据边界 | 方向已确认，精确能力待后续 Sprint | 人员/组织/项目/物料等属于独立 MDM；Sprint-86 只冻结其与架构字典、维度模型和资产的关系 |
| 数据架构一级入口 | 建议，待批准 | 移动并收敛既有规划入口，不新建第二套页面、API 或数据表 |
| UI 形态 | 建议，待蓝图评审 | 平面台账用搜索 + Table；层级架构字典用树/分组 + Table，按客户口径单一形态设计（不承诺自适应，RF-86-14）；复杂模型用列表 + 编辑器；批量物化在模型列表完成 |
| 资产统计规模 | 新增 OPEN，须与资产范围同批冻结 | 现有 5000 扫描上限与两轮全量扫描和纳管范围扩大冲突；口径（实时/缓存/物化）待定（ADR-86-16、RF-86-08） |
| 写权限强制 | 新增 OPEN，阻塞控制面归属 | read/write/export 三档无法按实体区分写权限，I07 运行时不可强制（ADR-86-17、RF-86-09） |

## 5. 候选端到端契约链

当前仍为候选链，G1 在 F1/T02 的关系基数、稳定引用、状态传播和精确 API/DTO/迁移契约冻结前保持 `GAP`。

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
| L26 | **权限粒度仅 read/write/export**，无法按实体类型区分写权限，使 I07「架构字典单一写 owner」运行时不可强制，只能靠 UI 约定；这正是当前两套 `catalog_domain` CRUD 的成因类型 | `assets/domain-profile.md` §6；见 RF-86-09 |

> **评审标记**：本账本及以下各节存在 14 项独立评审发现（1 BLOCKER / 6 MAJOR / 7 MINOR），
> 登记于 [`assets/review-findings.md`](assets/review-findings.md)。
> 第一轮 RF-86-01～07，第二轮（全量复核 20 文件 / 1254 行）新增 RF-86-08～14。
> 仍为 OPEN 的三项：**RF-86-01**（DIM 定性，阻塞 ADR-86-05）、**RF-86-08**（统计规模冲突，阻塞 ADR-86-04/16）、
> **RF-86-09**（写权限强制，阻塞 ADR-86-08）。

## 7. Gate Registry

| Gate | 项目 | 状态 | 证据 | 未过则关联 Task |
|---|---|---|---|---|
| G0 | 交付基线 | N/A | `it/baseline.md`；本 Sprint 只做架构讨论，不交付运行功能 | - |
| G0 | 领域与数据画像 | GAP | `assets/domain-profile.md`；当前环境已实测，客户/生产规模未知 | F1/T01、F5/T01 |
| G0 | DTS 领域不变量 | PASS | 本文 §3、§4；禁止平行 owner、密级与业务语义分离 | - |
| G1 | 契约链贯通 | GAP | `assets/data-model-relationships.md` 已形成候选关系与 E2E，但基数/映射未全部冻结 | F1/T02、F2～F4 |
| G1 | 非功能预算 | GAP | **部分 NFR 是架构决策输入而非实施细节**：资产统计 5000 扫描上限与纳管范围扩大直接冲突（L25、RF-86-08），必须在 ADR-86-04/16 冻结时一并评估；其余预算仍在实施 Sprint 立项时补充 | F2/T01、F5/T01 |
| G2 | 变更范围与影响分析 | GAP | `assets/impact-baseline.md`；当前无源码修改，但前端影响面和新增关系链逐 owner impact 尚未完成（RF-86-04） | F4/T01、F5/T01 |
| G3 | 发布安全 | N/A | 本 Sprint 无代码、schema 或部署 | - |
| G4 | 可运维性 | N/A | 本 Sprint 无运行能力 | - |
| G4 | DoD 验收 | PENDING | `it/README.md`；等待 ADR 评审结论 | F5/T01 |

## 8. Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 |
|---|---|---:|---|---|
| F1 | 数据架构元数据与控制面 | 2 | P0 | DRAFT |
| F2 | 资产范围、来源与分层语义 | 1 | P0 | DRAFT |
| F3 | 指标业务上下文 | 1 | P0 | DRAFT |
| F4 | 信息架构与页面形态 | 1 | P0 | DRAFT |
| F5 | 兼容迁移与实施准入 | 1 | P0 | DRAFT |

**讨论顺序**：F1/T01 → F1/T02 → F2/F3 → F4 → F5。F2 与 F3 可在关系契约确定后并行讨论；任何代码实施必须等待 F5 输出正式 ADR 和下一实施 Sprint。

## 9. 追溯矩阵

| 需求点 | Feature / Task | 预期证据 |
|---|---|---|
| 公共底层数据从建模模块中独立出来 | F1/T01、F4/T01 | 权威归属矩阵、目标导航蓝图、页面收敛清单 |
| 关键数据模型关系端到端逻辑贯通 | F1/T02、F2～F5 | 关系基数、稳定键、状态传播、E2E-A～D 与 IT-03 |
| 平台暂不支持多租户，按全局设计 | F1/T01 | 全局作用域与兼容字段决策 |
| 所有真实表进入资产，但 SOURCE 不混淆所有分层 | F2/T01 | 资产纳管矩阵、来源/分层/状态三轴契约 |
| 指标 category 对应业务分类 | F3/T01 | 指标业务上下文关系、字段兼容方案 |
| 模型工作台改用更易批量操作的 UI | F4/T01 | Table/树/编辑器适用规则、批量物化交互蓝图 |
| 多表依赖与二次物化可追溯 | F1/T02、F4/T01、F5/T01 | DWD→DWS→ADS 依赖 DAG、候选依赖闭包、attempt/observation 历史与 E2E 用例 |
| 避免破坏现有页面、深链和消费方 | F5/T01 | Expand/Contract 方案、影响清单、实施切片 |
| 主数据模块后续可演进且不与本 Sprint 冲突 | F1/T01、F1/T02 | 架构元数据/业务主数据边界、MDM→维度模型→资产投影链、独立 Sprint 入口条件 |

## 10. 本 Sprint 需要继续讨论的问题

1. 是否批准把既有“数仓规划”能力提升为一级“数据架构”，还是保留一级菜单不变、只做内部权威控制面收敛？
2. 平台级数据架构的首版实体范围是否只包含业务分类、数据域、业务过程、数仓分层、数据集市和主题域？建议数仓计划定性为建模建设范围，而不是架构字典。
3. 资产纳管是“发现即登记”还是“验证后登记”？建议发现即登记、状态失败关闭，但需冻结状态机。
4. 外部源表是否在同一资产台账展示？建议是，并以 `origin=SOURCE_SYSTEM`、`warehouseLayer=null` 区分。
5. 指标是否强制单一业务分类？跨业务分类复合指标是首版禁止、审批例外，还是天然支持多归属？
6. `businessCategoryId/dataDomainId/businessProcessId` 的必填规则如何随 ATOMIC/DERIVED/COMPOSITE 变化？
7. `/governance/subjects` 的 4 个独有 Tab 分别迁入哪里，何时允许旧路由进入观测退役？
8. 模型列表的批量物化选择范围按当前页、跨页还是显式候选集？失败重试、幂等和权限如何表现？
9. **（RF-86-01）** DIM 是资产目录兼容值还是新增 canonical layer？建议 DIMENSION 模型继续归 DWD，资产侧 DIM 进入显式兼容归一化；定性前 F2 不得冻结分层轴。
10. **（RF-86-03）** 归域率接近 0 时，如何让“全部资产/未归域/按域导航”均可达，并把批量归域作为首要治理动作？
11. `modeling_data_mart_domain` 当前多对多如何收敛到既有“业务分类 1:n 数据集市”决策？
12. APPLICATION 模型是否强制主题域；若是，`subjectDomainId` 如何进入 head/revision/candidate/导入导出契约？
13. physical observation 如何唯一、幂等地映射 `CatalogDataset`，重命名、冲突和撤销如何处理？
14. 通用业务主数据首版包含哪些对象，如何复用既有人员/组织 MDM 网关？该实现转入后续独立 Sprint。
15. 数据集市/主题域遗留 `tenant_id` 在平台全局模式下采用什么默认 scope、唯一性与未来扩展策略？
16. ModelSpec 自由文本业务活动如何迁为稳定业务过程 ID；业务矩阵维度引用、模型依赖 DAG、跨计划依赖、候选原子性和二次物化权限如何冻结？（**候选原子性已上收 F1/T02，见 RF-86-10**）
17. **（RF-86-08）** 纳管范围扩大后资产/域统计采用什么口径与规模上限？现有 5000 扫描上限、两轮全量扫描与不可靠 `truncated` 是否沿用？触顶后产品如何表达？owner 为 F2/T01 + ADR-86-16，须与 ADR-86-04 同批冻结。
18. **（RF-86-09）** 在 read/write/export 三档粒度下，用什么机制强制「架构字典单一写 owner」？若首版只能做到约定级，是否显式接受并记为具名风险？owner 为 F1/T01 + ADR-86-17，须在 ADR-86-08 冻结前给出。

## 11. 完成标准

- [ ] 统一语言、实体关系和 canonical owner 经架构评审确认。
- [ ] 架构字典、计划、ModelSpec/Revision、候选、物化证据、语义/物理资产、指标和质量的关键关系全部冻结。
- [ ] E2E-A～C 可从 UI 追溯到 API、数据和审计；E2E-D 明确作为后续 MDM Sprint 输入。
- [ ] 平台全局作用域与未来 tenant 兼容策略冻结。
- [ ] 物理资产纳管矩阵、来源/分层/状态三轴契约冻结。
- [ ] 指标业务分类、数据域、业务过程、指标类型/分组关系冻结。
- [ ] 数据架构、建模、资产、指标、质量的目标导航和页面职责冻结。
- [ ] 旧字段、旧路由、重复 CRUD 的 Expand/Contract 迁移顺序和回滚原则冻结。
- [ ] 资产统计口径与规模上限（ADR-86-16）与纳管范围（ADR-86-04）同批冻结。
- [ ] 架构字典写权限强制手段（ADR-86-17）已明确，或约定级已被显式接受并记为具名风险。
- [ ] 三套状态词汇已统一映射，ADR 冻结结果有单一写回位置（RF-86-11／13）。
- [ ] 下一实施 Sprint 可按竖切片拆分，且每个 Task 具备精确 API/DTO/schema/UI 验收契约。

## 12. 非目标

- 本 Sprint 不修改 Java、TypeScript、菜单种子、数据库 schema、运行数据或容器。
- 不在架构结论冻结前重写模型工作台或资产页面。
- 不为了“平台全局”删除现有 `tenant_id` 列；兼容策略必须先评审。
- 不把密级、权限或业务标签并入业务分类/数据域字段。
- 不新建第二套域、分层、资产、指标或发布控制面。
- 不在本 Sprint 实现通用业务主数据、金记录、匹配合并或主数据 UI；只冻结与数据架构、维度模型和资产的边界。
