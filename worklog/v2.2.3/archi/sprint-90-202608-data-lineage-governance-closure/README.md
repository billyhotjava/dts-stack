# Sprint-90: 数据血缘可治理闭环

**时间盒**: 2026-08-11 ～ 2026-08-28
**状态**: DRAFT（G0 未过：无运行实例、无真实血缘规模画像；F1 为纯后端契约修复可先行进入 READY）
**类型**: Governance Closure / Correctness Repair / UI Productization
**目标**: 用户在「数据治理 > 血缘与影响分析」里不仅能看血缘，还能**补录缺失血缘、核验存疑血缘、看懂采集为什么没连上**——即血缘从"只读展示"变成"可治理资产"。

## 背景与价值

血缘模块的后端内核是真实且完整的：多跳 BFS 影响分析、`valid_from/valid_to` 时间旅行、部门级鉴权、审计、字段级血缘、四路采集源（Addax 入湖 / dbt / OpenLineage / 视图 SQL）都已落地并有单测。**缺的不是算法，是闭环与运营面**：

1. **补不了**——自动采集漏掉的链路无法补录。后端 `POST/DELETE /api/catalog/lineage` 已实现，前端 API 层函数存在但零调用点（账本#6），用户只能 curl。
2. **核不了**——`verificationStatus`（VERIFIED / KNOWN_UNVERIFIED / DECLARED）全库只有写入点、没有任何变更入口（账本#10）。UI 上有「验证」列和虚线渲染，是用户看得见却动不了的状态。
3. **查不准**——字段血缘查询漏了有效期与快照过滤（账本#7），会显示已失效的字段关系；且在选了快照时间时，表级是历史态、字段级是当前态，同页两套时间语义。
4. **不知道为什么空**——「血缘导入」整页 55 行两个按钮，导入结果只有一句 toast，`skipped` 混合了 4 类完全不同的原因（账本#9）；后端已有的血缘失败报告接口前端零消费（账本#13）。

不做的代价：血缘数据只要有一处错漏，用户既无法纠正也无法标记，模块在治理评审中只能作为"展示件"而非"证据链"，分类分级传播与影响分析的可信度随之打折。

## 架构决策记录 (ADR)

| 决策 | 选择 | 约束 |
|---|---|---|
| ADR-90-01 血缘事实源 | `catalog_dataset_lineage` + `catalog_column_lineage` 继续为唯一事实源；OpenMetadata 缓存维持 ADR-85-04 的"同步证据"降级定位 | 不新建平行血缘表，不恢复第二张血缘图 |
| ADR-90-02 人工血缘身份 | 人工声明复用既有表，以 `relation_type='MANUAL'` 区分，**不新增表** | 人工边与采集边共用 BFS/时间旅行/鉴权逻辑，禁止另写查询路径 |
| ADR-90-03 血缘失效语义 | 删除人工血缘 = 置 `valid_to = now()`（软失效），物理删除仅保留既有 `force=true` 高权限路径 | 时间旅行必须能查到"曾经存在过"的人工边 |
| ADR-90-04 核验状态流转 | `DECLARED → KNOWN_UNVERIFIED → VERIFIED` 三态可由治理员显式流转；采集器写入不覆盖人工核验结论 | 采集重跑时若边已被人工置 `VERIFIED`，保留人工结论并记录 `lastVerifiedBy/At` |
| ADR-90-05 采集入口收敛 | 「血缘导入」页升级为**血缘采集运营台**，承载：手动同步、导入历史、失败明细、接收端健康只读 | 不做 OpenLineage 接入配置向导（顺延）；不新增菜单项，路由沿用 `/catalog/lineage/import` |
| ADR-90-06 查询数据层 | 四个子页共用一个 `useLineageImpact` hook + TanStack Query 缓存，Segmented 切换不重新请求 | 禁止继续复制 `loadDatasets/loadImpact`（账本#17 已有 4 份） |
| ADR-90-07 URL 状态协议 | 四页统一采用图谱页已有的 URL 回写协议（账本#16），参数名保持不变 | 图谱页现有契约测试断言不得回归 |
| ADR-90-08 字段血缘置信度 | 保留 `confidence` 的 `PARSED/INFERRED` 二值语义，本 Sprint 只做**可筛选**，不升级 SQL 解析器 | AST 级列血缘解析顺延，不在本 Sprint 承诺精度提升 |

## 端到端契约链 (Vertical Slice)

主竖线一（人工血缘登记与核验）：

| 层 | 契约/落点 | 签名要点 |
|---|---|---|
| UI 入口 | `/catalog/lineage/impact` 工具栏「登记血缘」按钮 + 节点/边表行内「核验」操作；`/catalog/lineage/import` 顶部「待核验 N 条」入口 | 抽屉表单（上游/下游数据集、关系类型、备注）；边表行操作菜单 |
| API | `POST /api/catalog/lineage`（已存在，补 `projectName/direction` 校验）<br>`DELETE /api/catalog/lineage/{id}`（已存在，默认软失效）<br>**新增** `PATCH /api/catalog/lineage/{id}/verification` | 请求 `{verificationStatus, note}`；响应回传更新后的 edge DTO（与 `toEdgeDto` 同形） |
| Service | `CatalogLineageResource.create/delete` + 新增 `verify`；审计事件 `CATALOG_LINEAGE_CREATE/DELETE/VERIFY` | 越权 → `RESOURCE_NOT_VISIBLE`；自环/重复边 → 409 |
| 数据 | `catalog_dataset_lineage`：新增 `last_verified_by`(varchar 64)、`verification_note`(varchar 512) | 复用既有 `verification_status`、`valid_from/valid_to` |
| 迁移 | `20260811_01_catalog_lineage_verification_actor.xml` | 仅 addColumn，Expand-only |

主竖线二（采集运营台）：

| 层 | 契约/落点 | 签名要点 |
|---|---|---|
| UI 入口 | `/catalog/lineage/import` 页重构为运营台 | 卡片：采集源健康 / 最近导入 / 未匹配明细 / 待核验队列 |
| API | `POST /catalog/lineage/sync-addax`（已存在，扩展返回体）<br>`POST /catalog/lineage/import-dbt-manifest`（已存在，扩展返回体）<br>`GET /catalog/assets-v2/lineage-failures`（已存在，前端首次消费） | 导入返回体从 `{created,skipped,total}` 扩展为 `{created,skipped,total,skippedReasons:{unmatchedModel,unmatchedParent,malformedNode,notModel},unmatched:[{uniqueId,name,reason}]}` |
| Service | `CatalogDbtLineageService.importManifest` 按原因分桶；`IngestionLineageWriter` 结果透传 | 未匹配明细上限 200 条，超出截断并标记 `truncated:true` |
| 数据 | 无新表；失败明细为查询期计算 | - |
| 迁移 | 无 | - |

## 现状勘察账本 (Context Ledger)

一次勘察的全部事实，下游 task 直接引用条目号，**禁止重复扫描**。

| # | 事实 | 证据(文件:行) |
|---|------|---------------|
| 1 | 菜单落点：数据治理 > 数据地图与资产 > 血缘与影响分析（影响分析/血缘图谱/字段血缘/血缘导入/快照对比 5 子页） | `dts-admin/.../portal-menu-seed.json:508-556`；角色默认 `role-menu-defaults.json:317-347` |
| 2 | 前端路由与懒加载映射 | `dts-platform-webapp/src/routes/sections/dashboard/static-routes.tsx:321-367`；`dynamic-resolver.tsx:73-78` |
| 3 | 页面文件与规模：`LineagePage.tsx`(16, 分发) / `LineageImpactPage.tsx`(166) / `LineageGraphPage.tsx`(358) / `LineageColumnsPage.tsx`(109) / `LineageDiffPage.tsx`(117) / `LineageImportPage.tsx`(55)；共享层 `pages/catalog/lineageShared.tsx`(486)；纯契约 `features/catalog/lineageContracts.ts`(258)；G6 图 `components/lineage/LineageGraph.tsx`(711) | 同上目录 |
| 4 | 影响分析接口：BFS 多跳 + 时间旅行 `at` + 部门鉴权 + `withJobs/withColumns` + `impactStats` | `CatalogLineageResource.java:149-364` |
| 5 | 快照对比接口：只 diff 边，无节点/字段差异 | `CatalogLineageResource.java:365-429`，`edgeMapAt:430-487` |
| 6 | 人工血缘后端已实现、前端零调用：`create`/`delete(force)` 存在；`createCatalogLineage`/`deleteCatalogLineage`/`getCatalogLineage`/`getDatasetLineage` 均为死代码 | `CatalogLineageResource.java:1059`、`:1271`；`api/platformApi.ts:42,1292,1318,1319` |
| 7 | **字段血缘查询漏过滤**：`findByDatasetLineageIdIn` 无 `valid_from/valid_to`、无 snapshot 条件 | `CatalogLineageResource.java:504`；`CatalogColumnLineageRepository.java:13` |
| 8 | 表级血缘查询的正确写法（可直接照抄给字段级） | `CatalogDatasetLineageRepository.java:38-46 findByEitherSideAt` |
| 9 | 字段血缘**唯一写入方**是 ELT 链路的 `DbtAssetSyncService`；导入页走的 `CatalogDbtLineageService` 只写表级 → 只用导入页的环境字段血缘恒空 | 写入 `DbtAssetSyncService.java:534`，过期 `:486`；导入页服务 `CatalogDbtLineageService.java:129-143` |
| 10 | `verificationStatus` 全部写入点（无任何更新入口） | `IngestionLineageWriter.java:541/545/548`、`CatalogAutoLineageService.java:105`、`CatalogDbtLineageService.java:135`、`OpenLineageReceiverResource.java:104`、`CatalogLineageResource.java:1110` |
| 11 | Addax/入湖血缘自动写入链路 | `dts-ingestion/.../PlatformInfraClient.java:253` → `POST /api/catalog/lineage/ingestion-executions`（`CatalogLineageResource.java:1130`）→ `OdsTableMappingSyncService.java:141/279/342` → `IngestionLineageWriter` |
| 12 | OpenLineage 接收端已实现（503 行）但零 UI、无健康状态 | `web/rest/internal/OpenLineageReceiverResource.java` |
| 13 | 血缘失败报告后端存在、前端零消费 | `CatalogAssetPortalResource.java:244`；`CatalogLineageFailureReportBuilder.java`；`api/platformApi.ts:150` |
| 14 | 回填 dry-run 是纯文本桩（全文件不访问任何 Repository） | `web/rest/internal/LineageBackfillResource.java` |
| 15 | 数据集下拉硬编码 300 条、无服务端搜索 | `pages/catalog/lineageShared.tsx:75-81` |
| 16 | URL 状态只有图谱页有，且被契约测试锁定；其余 3 页无 `useSearchParams` | `LineageGraphPage.tsx:37-152`；`components/lineage/lineageF2.source-contract.test.ts:22-33` |
| 17 | `loadDatasets`/`loadImpact` 复制 4 份 | `LineageImpactPage.tsx:40-81`、`LineageGraphPage.tsx:75-116`、`LineageColumnsPage.tsx:34-75`、`LineageDiffPage.tsx:29-41` |
| 18 | impact 存在 N+1：逐节点 `datasetRepo.findById`，逐节点逐层 `findByEitherSideAt`；diff 因跑两次快照成本翻倍 | `CatalogLineageResource.java:216`、`:181`、`:430-487` |
| 19 | dbt manifest 导入按**表名**匹配（忽略 schema），父节点取 `unique_id` 末段；`lineageRepo.findAll()` 全表加载；`skipped` 混合 4 类原因 | `CatalogDbtLineageService.java:61-67, 70, 110, 121`；skip 点 `:88/:97/:105/:112` |
| 20 | 字段血缘推断为启发式：手写顶层 SELECT 切分 + 子串匹配，退化为同名投影标 `INFERRED` | `DbtAssetSyncService.java:590 selectExpressionsByAlias`、`:425-471` |
| 21 | 后端已有测试：`IngestionLineageWriterTest`、`CatalogDbtLineageServiceTest`、`OpenLineageReceiverResourceTest`、`LineageBackfillResourceTest`、`CatalogLineageResourceIngestionExecutionTest`；**impact 与 diff 无任何测试** | `dts-platform/src/test/.../catalog/`、`.../web/rest/` |
| 22 | 前端测试仅 `lineageF2.source-contract.test.ts`、`lineageContracts.test.ts`；5 个页面零页面级测试 | 同 #3 目录 |
| 23 | 血缘相关 changelog 全集 | `20251225_04_catalog_dataset_lineage.xml`、`20260212_04_..._project_scope.xml`、`20260421_01_..._direction_widen.xml`、`20260429_01_..._verification_status.xml`、`20260429_02_..._lineage_job.xml`、`20260429_03_catalog_column_lineage.xml`、`20260501_03_..._relation_key.xml`、`20260501_04_..._time_travel.xml`、`20260517_01_catalog_column_lineage_time_window.xml` |
| 24 | `catalog_column_lineage` 有效期列名为 `valid_from`/`valid_to`（实体 `validFrom/validTo`），与表级同构 | `20260517_01_catalog_column_lineage_time_window.xml`；`domain/catalog/CatalogColumnLineage.java` |

**开放问题**（勘察未决，须在实施期确认）：

- Q1 现网血缘规模未知（边数、字段关系条数、最大扇出）→ 影响 F4/T04 的批量化阈值与 NFR 预算定量，见 `assets/nfr-budget.md`。
- Q2 现网是否已有 Airflow 在向 `/api/internal/lineage/openlineage` 推事件 → 决定 F3/T02 健康卡片是"有数据"还是"未接入"态。
- Q3 现网 `relation_type` 取值分布（`ADDAX/DBT/AIRFLOW/AUTO_VIEW/MANUAL` 占比）→ 决定核验队列的默认筛选。
- Q4 治理员角色是否已具备血缘写权限（`CATALOG_MAINTAINERS`），若无需在 dts-admin 侧补授权。

## Gate Registry

| Gate | 项目 | 状态 | 证据 | 未过则关联 Task |
|------|------|------|------|-----------------|
| G0 | 交付基线 | GAP | `it/baseline.md` B1-B3 | F0/T01 |
| G0 | 领域与数据画像 | GAP | `assets/domain-profile.md`（Q1/Q2/Q3 待实测） | F0/T01 |
| G0 | 领域不变量自检 | PASS | 见 ADR-90-01/02/03（复用既有表与鉴权，无平行实现） | - |
| G1 | 契约链贯通 | PASS | 本文档 §端到端契约链（两条竖线无 TBD 层） | - |
| G1 | 非功能预算 | GAP | `assets/nfr-budget.md`（阈值待 Q1 实测定量） | F0/T01、F4/T04 |
| G2 | 变更范围守卫 | PENDING | - | - |
| G3 | 发布安全 | PENDING | `assets/release-plan.md`（未创建） | F5/T02 |
| G4 | 可运维性 | PENDING | `assets/runbook.md`（未创建） | F3/T02 |
| G4 | DoD 验收 | PENDING | `it/` | F5/T01、F5/T02 |

**纪律**：G0 关闭前，依赖真实数据/浏览器的 F2/T03、F3/T02、F5 全部保持 `DRAFT`/`BLOCKED_INPUT`，不得置 READY。F1 为纯后端契约修复，不依赖 G0，可直接进入 READY。

## Feature 列表

| ID | Feature | Task 数 | 优先级 | Feature 状态 | Task 状态明细 |
|----|---------|---------|--------|------|------|
| F0 | 交付基线与血缘数据画像 | 1 | P0 | BLOCKED_INPUT | T01 BLOCKED_INPUT |
| F1 | 血缘事实正确性修复 | 2 | P0 | READY | T01 READY, T02 READY |
| F2 | 人工血缘登记与核验闭环 | 3 | P0 | DRAFT | T01 READY, T02 READY, T03 DRAFT（待 B1/B5） |
| F3 | 血缘采集运营台 | 2 | P0 | DRAFT | T01 READY, T02 DRAFT（待 B1/Q2） |
| F4 | 血缘查询一致性与性能收敛 | 4 | P1 | DRAFT | T01 READY, T02 READY, T03 DRAFT（待后端 keyword 结论）, T04 DRAFT（待 Q1） |
| F5 | 纵向集成与验收 | 2 | P0 | BLOCKED_INPUT | T01/T02 BLOCKED_INPUT |

**统计**: READY=5, DRAFT=4, BLOCKED_INPUT=5（共 14 task）。Feature 状态取其最弱 Task 状态。

**依赖顺序**: F1 →（F2/T01 → F2/T02 → F2/T03）∥（F3/T01 → F3/T02）→ F4 → F5
F0 并行补外部输入。F4/T01（共享数据层）会大改四个页面文件，必须排在 F2/T03、F3/T02 之后，避免同文件冲突。

## 追溯矩阵 (Traceability)

| 需求点 | Feature | 关键 Task | 验收证据位置 |
|---|---|---|---|
| 字段血缘不得显示已失效关系；快照时间对表级/字段级语义一致 | F1 | F1/T01 | `it/` IT-01 |
| dbt 导入未匹配模型可定位、可复查 | F1、F3 | F1/T02、F3/T01 | `it/` IT-02 |
| 采集漏掉的血缘可由治理员补录并软失效 | F2 | F2/T01、F2/T03 | `it/` IT-03 |
| 存疑血缘可被显式核验，核验结论不被采集覆盖 | F2 | F2/T02、F2/T03 | `it/` IT-04 |
| 用户能判断"血缘为什么是空的" | F3 | F3/T02 | `it/` IT-05 |
| 四个子页筛选状态可刷新恢复、可分享 | F4 | F4/T02 | `it/` IT-06 |
| 大目录下仍能选中目标数据集 | F4 | F4/T03 | `it/` IT-07 |
| 影响分析在真实规模下满足响应预算 | F4 | F4/T04 | `assets/nfr-budget.md` + `it/` IT-08 |

## 完成标准

- [ ] **契约**：`PATCH /api/catalog/lineage/{id}/verification` 与扩展后的导入返回体有契约测试；`impact`/`diff` 首次拥有接口测试（补账本#21 的空白）；迁移在干净库可执行。
- [ ] **UI**：登记抽屉、核验操作、采集运营台三处具名入口真实可用，各自留空/加载/错误/成功四态证据。
- [ ] **切片**：在运行实例上跑通「采集 → 发现缺边 → 补录 → 核验 → 影响分析可见 → 导出」完整路径，证据入 `it/`。
- [ ] **回归**：图谱页既有 URL 契约测试（账本#16）不回归；四页统一状态协议后新增对应契约测试。
- [ ] 无占位证据。

## 非目标

明确不在本 Sprint 做，避免范围蔓延：

- **快照对比补全**（节点差异、字段差异、预设时间区间、结果导出）→ 顺延后续独立 Sprint（编号待定）。
- **`LineageBackfillResource` 真实实现**（账本#14）→ 顺延；本 Sprint 只在文档中标注其为桩，不误当能力宣传。
- **字段血缘 SQL 解析器升级为 AST**（账本#20）→ 顺延；本 Sprint 只做置信度可筛选（ADR-90-08）。
- **OpenLineage 接入配置向导**→ 顺延；本 Sprint 只做接收端健康状态只读展示。
- **OpenMetadata 血缘缓存的二次渲染**→ 维持 ADR-85-04 降级定位不变。
- 不新增菜单项、不新增血缘表、不改动分类分级传播逻辑。
