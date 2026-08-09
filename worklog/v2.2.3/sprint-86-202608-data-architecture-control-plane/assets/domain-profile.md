# 领域画像（Gate G0）

**勘察日期**：2026-08-09
**数据来源**：当前 `/opt/prod/s10/v2.2.3` 运行环境的 `dts_platform` PostgreSQL；该快照不是客户生产容量证明
**结论**：可用于架构讨论；客户数据量、增长率和脏数据分布未取得，Sprint-87 F0 及相关实施 Task 继续保持 BLOCKED_INPUT

## 1. 统一语言

| 术语 | 本 Sprint 定义 | 禁用/需区分说法 | 出处 |
|---|---|---|---|
| 业务分类 | 平台全局业务组织树的一级节点，承载资产与指标的业务归属 | 不称“主题域” | 用户确认方向；`catalog_domain.parent_id` 根节点 |
| 数据域 | 业务分类下的单父级二级节点，承载业务过程、模型和资产归域 | 不与业务分类混成一个下拉平级选项 | `planning-redesign-dataworks-20260806/design.md` |
| 业务过程 | 业务在数据域中发生的可度量过程，是原子指标和事实建模的重要上下文 | 不等同数据处理任务 | 既有 Sprint-64/规划契约 |
| 数据集市 | 面向消费的业务分类细化空间 | 不再按数据域自由挂接解释 | 既有规划设计 |
| 主题域 | 数据集市下的应用层主题组织 | 不再用“业务主题域”指代业务分类/数据域树 | 既有规划设计与资产侧术语漂移证据 |
| 数仓分层 | ODS/STG/DWD/DWS/ADS 等 canonical 技术加工链位置，与业务树正交 | `SOURCE` 不作为所有资产的总分层；DIMENSION 模型不等于 DIM 分层 | `ModelSpecContract`、`WarehouseLayerApplicationService`、RF-86-01 |
| 资产来源 | 资产的生产者引用；平台如何得知由独立登记渠道表达 | 不与 warehouseLayer 或 RegistrationEvidence 混为一个字段 | ADR-86-18 |
| 物理数据资产 | 稳定、可寻址、可发现的真实表、视图或物化视图 | 不包括 CTE、ephemeral、临时表和仅存在于草稿中的 ModelSpec | 用户确认方向；dbt 物理实现判定现状 |
| 治理完备度 | 资产是否已归域、定责、分级、质检并可消费 | “是资产”不等于“可发布/可消费” | 本 Sprint 核心不变量 |
| 指标业务分类 | 指标由哪类业务产生和负责 | 不等同 ATOMIC/DERIVED/COMPOSITE，也不等同自定义指标分组 | 用户确认方向 |
| 数据架构元数据 / 架构字典 | 用于组织数仓建设与跨模块引用的分类、域、过程、分层、集市、主题域 | 不称“业务主数据” | Sprint-86 边界评审 |
| 数仓计划 | 某次/某项目的数据建设范围与已确认基线 | 不是架构字典，不拥有域、分层或集市 CRUD | ModelSpec 计划关系 |
| 业务主数据（MDM） | 人员、组织、项目、物料等业务实体的权威金记录及来源映射 | 不与业务分类、数据域、数仓分层混称 | 既有人员/组织 MDM + 后续能力边界 |

## 2. 业务不变量

| # | 不变量 | 强制级别 | 违反后果 | 出处 |
|---|---|---|---|---|
| I01 | 当前产品范围按平台全局设计，不提供租户级架构字典选择 | 用户已确认 | 产生无真实使用场景的隔离与 UI 复杂度 | 2026-08-09 讨论 |
| I02 | 业务分类 1:n 数据域，数据域为单父级 | 既有业务规则 | 资产、模型和指标归属出现多套层级解释 | 规划设计 |
| I03 | 数仓分层与业务分类/数据域正交 | 架构规则 | SOURCE、业务域和加工层混成不可校验字符串 | 规划设计 |
| I04 | 所有稳定真实关系可进入资产台账；资产身份与治理/可消费状态分离 | 用户确认方向 | 未治理表不可发现，或错误地把“已登记”当“可消费” | 2026-08-09 讨论 |
| I05 | 跨模块引用稳定 ID；中文名称仅为展示投影 | 架构规则 | 改名后关联断裂、同名冲突、迁移不可审计 | 当前自由文本缺口 |
| I06 | 指标业务分类、指标类型、指标分组是三个不同维度 | 用户确认方向 | `category` 同时表达多义，派生校验不可确定 | 2026-08-09 讨论 |
| I07 | 架构字典只有一个写 owner，消费者不得复制 CRUD | DTS 领域硬约束（**ADR-86-17 方案 A 已冻结；当前运行时仍缺统一 command boundary 与收口后的 actor allowlist，见 §6 与 RF-86-09**） | 再次形成规划页与治理页双实现 | domain-dts A4 |
| I08 | 密级/分类分级与业务分类、业务标签严格分离 | 合规硬约束 | 权限和审计语义被业务字段绕过 | domain-dts D1 |
| I09 | ModelSpec、语义模型资产、物理表资产和业务主数据使用不同身份与状态 | 架构规则 | 用“已提交/已发布”误判物理表存在，或维度投影覆盖主数据身份 | `assets/data-model-relationships.md` |

## 3. 真实数据画像

查询在 `v223-dts-pg-1` 的 `dts_platform` 数据库只读执行。

| 指标 | 2026-08-09 实测值 | 查询口径 |
|---|---:|---|
| `catalog_domain` | 4（根 2、子 2）｜**用户口径：客户环境 10–50、两层（RF-86-02）** | `count(*)` + `parent_id is null/not null` |
| `catalog_dataset` | 83 | `count(*)` |
| 已归域数据集 | 0 / 83 | `domain_id is not null` |
| 带 warehouse layer 数据集 | 60 / 83 | 非空 `warehouse_layer` |
| warehouse layer 分布 | ODS 57、SOURCE 3、NULL 23 | `group by warehouse_layer` |
| `catalog_asset_extension` | 0 | `count(*)` |
| `gov_indicator_definition` | 0 | `count(*)` |
| `modeling_warehouse_layer` | 25，ACTIVE 0，DELETED 25 | 按 status 聚合 |
| 数仓计划 / 计划域 | 1 / 2 | `modeling_warehouse_plan`、`modeling_warehouse_plan_domain` |
| 业务过程 / 数据集市 / 主题域 | 1 / 1 / 1 | 对应规划表计数 |
| 活动模型分层 | DWD 1 | 非 ARCHIVED ModelSpec 按 layer 聚合 |

### 3.1 资产统计的既有规模约束（RF-86-08）

这些不是数据量快照，而是长期有效的架构约束，直接决定纳管范围扩大后的可行性：

| 约束 | 值 | 证据 |
|---|---|---|
| 资产统计扫描上限 | 5000 条（分页 200 × 最多 25 页） | `CatalogAssetPortalService.java:60`、`:132-133` |
| 每次资产地图加载 | 已跑两轮全量扫描 | `CatalogAssetPortalService.java:500-509` 注释 |
| `truncated` 判定 | 依赖不可靠的 legacy total 估算，波动“可达数千”，非纯展示字段 | 同上 |
| `withStats` 域树统计 | 复用同一 `overview()` 路径，继承全部上述约束 | `CatalogDomainResource.java:166` |
| 触顶后前端表达 | 计数降级为 `≥N` | `DomainScopeNav.tsx` `countText` |

代码注释已自陈：「若要修，应连同该成本问题一并设计（例如缓存 domainStats）」。

实测命令摘要：

```sql
select count(*), count(*) filter (where parent_id is null) from catalog_domain;
select count(*), count(*) filter (where domain_id is not null) from catalog_dataset;
select coalesce(warehouse_layer, 'NULL'), count(*) from catalog_dataset group by warehouse_layer;
select count(*) from gov_indicator_definition;
```

**对设计的直接影响**：

- 当前 83 个数据集全部未归域，证明“资产是否存在”和“治理是否完整”必须分开，不能用 domainId 非空作为资产准入条件。
- 现有 3 个 SOURCE 值是真实兼容数据，若拆分来源与分层必须双读、回填和保留回滚映射，不能直接改枚举。
- 指标表当前为空，只说明本环境回填成本低，不代表客户环境无数据；`GovIndicatorDefinition` 的代码消费者仍是 HIGH 风险。
- 规划数据只是少量演示/验收样本，不能据此确定分页上限、索引或批量物化规模。
- **（RF-86-03，信息架构结论）** 归域率为 0 不否定“全部资产/未归域/按域”三种导航；它说明当前 onboarding 的首要治理动作是批量归域，而不是要求用户先选一个空域。F4 必须保留全部资产与未归域入口，并让批量归域、逐项失败和审计成为一等能力。
- **（RF-86-05）** `modeling_warehouse_layer` 25 条全部 DELETED、ACTIVE 为 0，当前 canonical 分层投影实际由内置代码承担。该事实不改变“分层与业务树正交”的 ADR-86-03，而是要求 F2/F5 明确首版是否开放自定义分层，以及 DELETED 记录是保留回滚锚点还是在后续迁移中清理。
- 当前数据集市/主题域各 1 条不足以证明基数正确；现行 `modeling_data_mart_domain` 可表达多对多，必须与目标“业务分类 1:n 数据集市”分开评审。
- **（RF-86-08，架构结论）** ADR-86-04 把纳管范围扩大到「所有稳定可寻址的真实关系」（外部源表、持久化 STG、失败构建残留），资产总量将显著上升，而 §3.1 的 5000 扫描上限只会更早触顶。ADR-86-04/16 已同批批准服务端增量统计投影 + 24h 对账，治理状态须可索引；旧扫描只作具名近似降级。Gate G1 的设计已通过，运行容量验证转 Sprint-87。

## 4. 已批准资产纳管矩阵

| 对象 | 是否资产 | ProducerRef | 登记渠道 | warehouseLayer | 状态/消费规则 |
|---|---|---|---|---|---|
| 外部数据库稳定表/视图 | 是 | SOURCE_SYSTEM + sourceSystemId | SCANNER / MANUAL | 空 | DISCOVERED；治理与授权满足后才可消费 |
| 已落地 ODS 表 | 是 | INGESTION_JOB + job/batch ref | INGESTION_EVENT | ODS | 按发现、治理、发布和健康轴共同决定 |
| 持久化 STG 表/视图 | 是 | DBT_MODEL 或 MANUAL_BUILD + implementation ref | DBT_SYNC / MANUAL | STG | 默认内部，是否开放待策略确认 |
| DWD/DWS/ADS 表、视图、物化视图 | 是 | DBT_MODEL、MANUAL_BUILD 或 MODELING + implementation ref | DBT_SYNC / MATERIALIZATION_OBSERVATION / MANUAL | 对应层 | 按发布、健康、质量与权限策略决定 |
| 资产目录中标记为 DIM 的表 | 是（若物理关系稳定存在） | 对应稳定生产者 | 对应登记渠道 | 兼容归一为 DWD | `assetRole=DIMENSION_TABLE`；旧 DIM 保留到另批 Contract |
| 失败构建留下的稳定关系 | 发现后登记 | 对应稳定生产者 | 对应登记渠道 | 对应层或空 | 健康为 FAILED/STALE，禁止消费；不覆盖其他状态轴 |
| dbt ephemeral、CTE、临时表 | 否 | - | - | - | 不进入物理资产台账 |
| ModelSpec 草稿 | 不是物理资产 | - | - | 设计目标层 | 留在建模控制面 |

`ProducerRef` 回答“由谁/什么生成”，登记渠道回答“平台如何得知”。同一 physical locator 可被多个渠道观测，但必须归并到同一 `CatalogAssetKey`，并保留每次登记证据；不得因为后一次扫描而改写生产者或历史来源。

## 5. 外部边界

| 系统/模块 | 当前契约 | 责任边界 | 失败时策略 |
|---|---|---|---|
| 数据集成 | 写 SOURCE/ODS 资产与血缘 | 提供来源身份和落地证据，不定义业务域 | 无稳定身份则失败关闭或登记待核验 |
| dbt/物化 | 依据 manifest/run_results 识别真实关系 | 提供实现、运行和关系健康证据 | ephemeral 不登记；证据过期标记 stale |
| 数据资产 | `CatalogDataset`/资产身份 | 资产发现、目录、治理状态与消费入口 | 未归域仍可发现，治理动作受限 |
| 数据指标 | `GovIndicatorDefinition` | 指标定义、版本、派生和发布 | 业务上下文缺失时阻断相应阶段 |
| 数据质量 | 从 CatalogDataset 投影可读数据集及域 | 规则、绑定、运行证据 | 无资产身份不得伪造数据集 |
| 业务主数据 | 当前仅人员/组织 MDM 同步入口；通用能力待建 | 维护业务实体金记录，通过维度模型形成分析投影 | 不得写架构字典或直接冒充物理资产 |

## 6. 合规要求

| 要求 | 是否硬门槛 | 处理原则 |
|---|---|---|
| 业务分类/标签不得替代密级 | 是 | classification 保持独立控制面 |
| 架构字典与主数据写操作可授权、可审计 | 是 | 即使平台全局，也不等于所有人可写；两个 owner 分别授权 |
| 兼容迁移不得静默丢关联 | 是 | dry-run、批次、回滚、漂移校验 |
| 产品/前端授权为 read/write/export 粗粒度；后端另有宽角色与对象/部门 guard | 已知缺口 | 不虚构数据架构专属角色，也不抹去现有预防控制。**（RF-86-09）** ADR-86-17 已冻结两层实现：唯一 application command boundary 防止平行代码 owner；方案 A actor allowlist/对象 guard 限制人员写入。当前 `CatalogDomainResource` 仍直写 Repository，`CATALOG_MAINTAINERS` 又跨多种职责，故运行时职责分离尚不完整；审计只能补偿。批准方案见 `f1-t01-decision-pack.md` |

## 7. 外部画像缺口与审批边界

- 客户环境真实域、资产、指标数量、增长率、SOURCE/DIM 存量、典型批量和脏数据比例仍未知；Sprint-87 F0 必须补齐。
- 失败构建残留关系的发现频率、保留期和自动清理责任需要运行画像，不在 Sprint-86 中假设。
- 平台全局架构字典的遗留 tenant 物理字段继续保留；客户端不暴露 tenant，未来真正启动多租户时另立 ADR。
- 外部源统一进入资产台账并以 ProducerRef/筛选区分；跨分类指标首版拒绝；集市单分类、APPLICATION 主题域必填等架构结论已由 xiezm 批准，见 `consolidated-approval-pack.md`。客户规模与脏数据画像仍由 Sprint-87 F0 补齐。
