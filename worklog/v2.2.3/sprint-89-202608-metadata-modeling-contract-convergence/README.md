# Sprint-89: 元数据采集与数据建模契约收敛

**时间盒**: 2026-08-10 ～ 2026-08-21
**状态**: IN_PROGRESS（G0 部分通过；后端契约可启动，真实登录链路阻塞）
**类型**: Architecture Repair / Metadata Lifecycle / Modeling Vertical Slice
**目标**: 在不恢复平行“元数据管理”模块的前提下，收敛采集、数据资产、数仓规划和 ModelSpec 的来源身份、版本与失效语义，使元数据更新可被建模安全消费、可判断影响、可重确认并可端到端验收。

## 结论与业务价值

元数据管理不是一个独立页面的同义词，而是平台对“数据从哪里来、结构是什么、当前是否可信、谁可以使用、变化会影响什么”的统一控制能力。其权威管理位置继续保留在「数据治理 > 数据资产」；数据集成只负责采集和上报，不拥有第二套资产台账。

数据建模对元数据的依赖分两类：

- 纯概念/逻辑模型草稿、主题域与分层规划可以不依赖外部采集元数据。
- 反向建模、来源盘点、字段映射、SQL/dbt 编译、发布和物化必须依赖可解析、可版本化、权限可校验的元数据。

因此，采集更新不会要求重构中的数据建模停止，但必须通过本 Sprint 把更新影响变成明确状态，避免来源 ID 漂移、旧版本被静默使用或无差别阻断。

## 架构决策记录 (ADR)

| 决策 | 选择 | 约束 |
|---|---|---|
| ADR-89-01 元数据 owner | 数据资产继续作为权威 owner；数据集成是 producer；数据建模是 consumer | 不新增菜单、页面、资产表或平行元数据服务 |
| ADR-89-02 `CATALOG_TABLE` 身份 | `sourceId/locator.assetId` 保持为 `catalog_table_schema.id`；通过其 `dataset_id` 解析 `CatalogAssetType.DATASET + CatalogAssetKey` | locator ID 与资产身份必须分开命名和使用，禁止把 table ID 当 dataset ID |
| ADR-89-03 建模绑定 | `ModelSpec` 继续引用稳定的 `modeling_warehouse_plan_source.id`；来源结构版本使用 canonical schema fingerprint | 不把浏览器传入的表名、库名复制为新的事实源 |
| ADR-89-04 采集生命周期 | 例行同步只允许 upsert + 标记失效；来源重现必须复用 dataset/table/column ID | 物理 purge 仅能作为显式、高权限、可审计的独立动作，不能由例行采集触发 |
| ADR-89-05 变更分级 | `COMPATIBLE`、`BREAKING`、`REVIEW_REQUIRED`、`MISSING` 四类 | 兼容变化允许既有已引用字段继续运行但显示待确认；破坏性/缺失变化 fail closed |
| ADR-89-06 重确认 | 用户在现有 `/data-modeling/planning/spaces?view=baseline&tab=sources` 完成差异查看与重确认 | 复用现有来源盘点 API/页面入口，不新增元数据建模工作台 |
| ADR-89-07 遗留状态 | `source_id is null` 视为非采集管理；`source_id is not null and harvest_status is null` 视为 `UNKNOWN` | 不做无证据批量回填；先重采集或人工确认，再进入建模来源 |

## 端到端契约链

```text
数据集成采集
  -> catalog_dataset / catalog_table_schema / catalog_column_schema
  -> CatalogAssetKey + schemaFingerprint + harvestStatus
  -> WarehousePlan 来源盘点与确认版本
  -> ModelSpec 来源绑定 / 字段映射
  -> SQL/dbt 编译 -> 发布门禁 -> 物化可用性门禁
```

| 层 | 输入 | 输出/不变量 | 失败语义 |
|---|---|---|---|
| 采集 | 数据源自然键 + 表/字段快照 | 同一自然键复用原 ID；记录同步时间与 drift event | 单表失败不删除历史身份；运行记录失败 |
| 数据资产 | dataset/table/column | dataset 资产键唯一；table locator 可回溯父 dataset | `STALE/UNKNOWN` 不得伪装为 CURRENT |
| 数仓规划 | `CATALOG_TABLE` locator | 绑定 ID、当前版本、确认版本、差异级别 | 无权限/缺失/破坏性变化阻断确认或后续门禁 |
| ModelSpec | `sourceBindingId + resolvedVersion` | 编译时重新解析权威物理位置；不信任浏览器表名 | 版本或字段不兼容时返回稳定错误码并 fail closed |
| 发布/物化 | revision-pinned evidence | 分类、来源与物化 generation 同一版本链 | stale/missing evidence 阻断且可定位到来源 |

### 变更影响矩阵

| 元数据变化 | 级别 | 既有模型 | 反向建模/新导入 | 处置 |
|---|---|---|---|---|
| 新增 nullable 字段、仅注释变化 | COMPATIBLE | 已引用字段校验通过可继续；显示待确认 | 刷新后可选择新字段 | 查看差异后重确认版本 |
| 删除未引用字段 | COMPATIBLE | 可继续；显示待确认 | 不再提供该字段 | 重确认 |
| 删除已引用字段、类型不兼容、nullable 收紧 | BREAKING | 编译/发布/物化阻断 | 禁止生成无效映射 | 修复字段映射后重确认 |
| 疑似改名、精度变化或无法判定的方言类型 | REVIEW_REQUIRED | 默认阻断发布 | 允许预览、不允许应用 | 人工判断为兼容或破坏性 |
| 表消失或采集状态 STALE | MISSING | fail closed | 不可选择 | 恢复采集或显式排除来源 |

## 对其他模块的作用与本 Sprint 边界

| 模块 | 元数据作用 | 本 Sprint 处理 |
|---|---|---|
| 数据集成 | 产生技术元数据、同步状态和结构变化证据 | 统一非破坏性生命周期；不恢复集成模块内的元数据管理页面 |
| 数据资产 | 资产身份、检索、分类分级、权限、血缘的权威入口 | 保持唯一 owner；补足 table locator 到 dataset asset key 的解析 |
| 数据建模 | 来源盘点、反向建模、字段映射、编译、发布和物化门禁 | 本 Sprint 主交付链 |
| 数据质量 | 规则绑定、字段存在性与执行对象解析 | 只做回归守卫，不改质量 owner/台账 |
| 指标与血缘 | 复用模型/资产稳定键构建口径与关系 | 只验证资产键不漂移，不扩展指标或血缘功能 |
| 权限与分类分级 | 基于统一资产键做读权限和密级继承 | 修复分类发布门禁的 locator 解析；不代填业务分类元数据 |

## 现状勘察账本 (Context Ledger)

本账本是本 Sprint 的一次性勘察结果；后续 Task 直接引用编号，避免重复扫描。

| # | 当前事实 | 证据 |
|---|---|---|
| C01 | 来源解析契约按 `catalog_table_schema.id` 查表，并返回父 dataset 的 `CatalogAssetKey` | `JpaCatalogSourceReferenceReadAdapter.java:46-54,114-120` |
| C02 | `findCurrentPhysicalSource` 却把相同 `assetId/sourceId` 直接连接到 `catalog_dataset.id` | `ModelSpecRepository.java:409-439` |
| C03 | 分类发布门禁同样把 `CATALOG_TABLE.sourceId` 直接传给 `findDatasetAssetKey(datasetId)` | `ModelClassificationPublishGate.java:267-272` |
| C04 | PostgreSQL 采集对消失表执行 column/table/dataset 物理删除，重现后会生成新 ID | `PostgresCatalogSyncService.java:325-365` |
| C05 | JDBC 采集默认 MARK，但仍允许配置 PURGE；两类采集生命周期不一致 | `JdbcCatalogSyncService.java:1319-1359` |
| C06 | 当前 fingerprint 包含表名及字段名、类型、nullable、status，并对 API 来源加入执行证据 | `JpaCatalogSourceReferenceReadAdapter.java:129-154` |
| C07 | 一般表解析只检查读权限，没有消费 `harvest_status`；标记为 STALE 后仍可能被解析为 AVAILABLE | `JpaCatalogSourceReferenceReadAdapter.java:106-126` |
| C08 | 现有 API 已有 `GET/PUT /api/modeling/warehouse-plans/{id}/baseline/sources`；反向建模已读取确认且 AVAILABLE 的来源 | `WarehousePlanResource.java:278-291`；`modelingImportContextService.ts:38-51` |
| C09 | 前端已有来源盘点阶段与 `tab=sources` 路由契约，但当前 `/spaces` 页面未呈现该来源管理面 | `warehousePlanViewModel.ts:80-119,276`；`PlanningPage.tsx` |
| C10 | 本地真实库：83 datasets、78 tables、1510 columns、29 drift events、1 plan、33 ModelSpecs；plan source bindings 为 0 | `assets/domain-profile.md`（2026-08-10 只读 SQL） |
| C11 | 57/83 datasets 的 `harvest_status` 为空；其中 7 条有 source_id；另有 1 STALE、25 SYNCED、5 datasets 无 table | `assets/domain-profile.md` |
| C12 | 29 条 drift ticket 全为 OPEN/REVIEW；累计 added=838、removed=2、changed=0 | `assets/domain-profile.md` |
| C13 | GitNexus 上游影响：`CatalogDataset` 为 CRITICAL（211 symbols/102 direct/2 processes/4 modules），`CatalogTableSchema` 为 HIGH（51/27/2/3） | 本轮实施前影响分析；编码前须按目标 symbol 再确认索引新鲜度 |
| C14 | PostgreSQL/JDBC/Inceptor 三条同步链都会先 `deleteByTable` 再创建字段，字段 ID 在正常重采时也会漂移 | `PostgresCatalogSyncService.java:184-213`；`JdbcCatalogSyncService.java:260-292`；`InceptorCatalogSyncService.java:220-249` |

> 风险告警：C02/C03 是源码级确定的不一致，但本地库尚无 `CATALOG_TABLE` 规划绑定，不能表述为已在生产复现。C13 说明任何实体级改动都可能跨目录、建模、质量和权限扩散；优先修 resolver/query seam，不直接改实体身份。

## Gate Registry

| Gate | 状态 | 证据 | 结论 |
|---|---|---|---|
| G0 运行/健康 | PASS | `it/baseline.md` P1 | dts-platform health=UP，门户 HTTP 200 |
| G0 登录/API/UI | BLOCKED_INPUT | `it/baseline.md` P2/P5/P6 | 未取得本轮授权账号；只能证明保护端点返回 401 |
| G0 领域与数据画像 | PASS_LOCAL / GAP_PRODUCTION | `assets/domain-profile.md` | 本地数据已画像；客户/生产规模与脏数据仍待提供 |
| G0 领域不变量 | PASS | ADR-89-01～07 | 唯一资产 owner、单 ModelSpec、权限 fail closed |
| G1 契约 | PASS | 本文 §端到端契约链 | locator、asset key、version、状态与门禁已冻结 |
| G1 NFR | PASS_DESIGN | `assets/nfr-budget.md` | 每条预算均绑定可执行 fitness function，尚未运行 |
| G3 发布安全 | PENDING | F4/T02 | 实施结束后做 diff、回滚与影响复核 |
| G4 DoD | BLOCKED_INPUT | `it/README.md` | 真实登录/Chrome 95/全链路证据未具备 |

## Feature 与任务状态

| Feature | 优先级 | Task 数 | 状态 |
|---|---|---:|---|
| F0-交付基线与真实链路 | P0 | 1 | BLOCKED_INPUT |
| F1-元数据来源身份契约收敛 | P0 | 2 | DONE |
| F2-采集生命周期与变更分级 | P0 | 2 | IN_PROGRESS |
| F3-建模失效处置与来源盘点 | P0 | 2 | BLOCKED |
| F4-纵向集成与发布验收 | P0 | 2 | DRAFT / BLOCKED_INPUT |

**Task 统计**: DONE=4，IN_PROGRESS=1，DRAFT=1，BLOCKED_INPUT=3。
**执行顺序**: F0 输入补齐可并行等待；编码按 F1/T01 → F1/T02 → F2 → F3 → F4/T01 → F4/T02。所有编码完成后只做一次集中构建与 E2E，失败时再针对性重跑。

## 追溯矩阵

| 用户问题/目标 | Feature | 验收证据 |
|---|---|---|
| 元数据管理的意义及对其他模块作用 | Sprint ADR/影响表 | README 审核通过 |
| 采集更新元数据是否影响重构中的数据建模 | F1/F2/F3 | IT-01～IT-04 |
| 数据建模是否需要元数据 | F1/F3 | IT-04、IT-08 |
| 后续优化内容 | F2/F3/F4 | drift 分级、重确认、纵向链路与 NFR 证据 |

## 完成标准

- [ ] `CATALOG_TABLE` 的 table locator、dataset asset key、schema version 在来源解析、编译与分类门禁中语义一致
- [ ] 例行采集不存在物理删除；表消失/重现后 dataset/table/column 稳定 ID 不变
- [ ] 兼容、破坏性、待审与缺失变化均有自动化契约测试和稳定错误码
- [ ] 现有来源盘点入口能展示 current/confirmed version、差异级别和处置动作，未新增菜单/页面
- [ ] 采集 v1 → 建模确认 → ModelSpec → 编译/发布/物化 → 采集 v2 的纵向测试通过
- [ ] 权限、租户、分类分级、质量与血缘回归通过；Chrome 95 真机链路完成
- [ ] 发布、回滚、观测和 IT 证据齐全，`it/` 无占位证据

## 非目标

- 不恢复数据集成模块内已删除的元数据管理页面，不引入 OpenMetadata 作为第二权威源
- 不新建 `CatalogDataset`/`CatalogTableSchema` 的替代实体、平行 ID 或平行 ModelSpec
- 不代用户填写业务分类、密级、owner、标签等业务元数据
- 不在缺少生产画像时执行批量回填、物理 purge 或不可逆迁移
- 不扩展 MDM、指标设计、血缘编辑器或数据质量功能；仅保证本契约不破坏其现有消费
