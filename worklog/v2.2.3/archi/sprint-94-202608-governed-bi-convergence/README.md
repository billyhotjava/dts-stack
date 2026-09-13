# Sprint-94：治理型商业智能主线收敛

**时间盒**：2026-08-17 ～ 2026-09-18（25 个工作日）
**状态**：BLOCKED（实现门禁已恢复；仍缺 Chrome 95、A1～A4 真实职责分离、目标环境灰度/回滚和纵向 E2E 证据。GitNexus vendored Python 刷新仍有噪声，但本轮逐符号 impact 与提交前 detect_changes 必须继续执行。）
**类型**：Architecture Convergence / Full-stack / UI Productization
**目标**：业务分析人员从平台已发布、已治理的 DWS/ADS 数据集出发，在同一条受控查询链中完成自助分析与看板编排；发布后按部门、角色和密级向受众提供服务，避免形成第二套数据模型、权限或发布控制面。

**2026-08-19 历史边界决策（取代 2026-08-17 的长期兼容假设）**：老安装唯一必须无损保留的是大屏及其版本、权限、模板、审计、素材与菜单/角色绑定。旧 Card、Dashboard、Collection、Pulse、Subscription、MBQL/VDS 不是迁移对象；DWD/DWS/ADS 是 ModelSpec/dbt 可重建产物，不作为升级历史包袱。Sprint-94 交付 canonical 治理 BI、精确的大屏引用预检和默认回滚的清理工具；实际 DELETE/路由物理移除仍作为独立 Contract 发布，避免 Expand 与 Contract 同批上线。路线见 `assets/metabase-retirement-roadmap.md`。

## 1. 用户价值与完成旅程

本 Sprint 不以“补齐 Metabase 菜单”为目标，而以一条可验收的业务旅程为完成边界：

```text
平台数据负责人
  → 发布并治理 DWS/ADS 查询数据集
业务分析人员
  → 在「数据分析与服务 > 商业智能应用 > BI 数据集」选择已发布版本
  → 创建分析，配置维度、指标、筛选、时间范围与可视化
  → 预览、保存、校验并发布分析
看板维护者
  → 复用已发布分析编排看板，校验并发布固定版本
平台发布者
  → 登记受众、部门、角色、密级与有效期
授权消费者
  → 只看到有权限且已发布的看板，所有查询走统一网关并可审计
运维人员
  → 建立旧资产与旧路由的全量分母、观测覆盖和起始窗口（不做迁移、不做重定向）
```

业务结果：

1. “BI 数据集”不再同时展示 Analytics 自建数据库和平台数据源，而只消费平台已发布的数据集版本。
2. 对外只呈现“分析”；`/bi/questions` 与详情/编辑入口只消费 governed Analysis，旧 Card/MBQL 不再承担新建或列表主线。
3. 分析与看板共用同一数据集版本、语义契约、权限、RLS/脱敏、查询预算和审计链。
4. 看板只有在依赖校验通过、版本钉定且受众登记完成后才可发布。
5. 退役门禁只围绕可恢复性和大屏完整性：先冻结旧写 cutoff、验证全库备份、证明大屏无 Card 引用，再在独立 Contract 发布中清理旧 BI 数据。

## 2. 范围与非目标

### 2.1 本 Sprint 范围

- 平台 `QueryDatasetAsset/QueryDatasetVersion` 成为 BI 数据集唯一治理 owner，Analytics 只持有钉定引用和运行快照。
- 在既有 `AnalyticsCard` 上建立 `AnalysisQuerySpec dts.analysis/v1` 兼容门面，不新建分析业务表。
- 建立 `AnalysisQueryGateway`，收口语义预览、分析和看板查询；大屏保持历史链，未来如复用 governed Analysis 须另立 Feature。
- 复用 `analytics_revision` 建立分析/看板校验、版本和发布状态机。
- 扩展既有 `bi_report_link`，以稳定资产身份登记发布受众。
- 完成退役 Expand 阶段：32 条 `bi/*` 路由盘点、canonical 分析入口收敛、大屏历史保留清单、旧 BI 清理 dry-run 与 rollback 门禁。
- 完成三角色、Chrome 95、权限负向、故障注入、灰度与回滚验收。

### 2.2 明确非目标

- 本次 Expand 发布不直接执行生产 DELETE、DROP 或不可回滚路由移除；清理工具和 cutoff 契约在本 Sprint 完成，apply 进入独立 Contract 发布。
- 不在本 Sprint 改造大屏组件；只验证其历史、权限、模板、审计、素材和入口不断层。
- 不复制 Metabase 的 collection、model、trash、pulse、subscription 或数据库管理控制面。
- 不新增 BI 数据集、分析或看板第二套业务表；只允许兼容性 Expand 字段。
- 不新增菜单。
- 不让 Analytics 保存或展示平台数据集的底层原始 SQL。
- 不新建权限动作；继续使用 DTS 既有 `read/write/export` 词汇。
- 不把 OpenMetadata、dbt 或 Analytics 变成业务治理 owner。
- 不在首轮发布中 DROP 旧表、旧列或历史数据。
- 不扩展大屏交互节点、Word 导出、电视模式等设计器增强项。
- 不把旧 VDS/MBQL 转换为 governed Analysis；它们随旧 BI Contract 退役，不承担历史迁移义务。
- 不把自然语言问数评测、报表工厂、指标透镜纳入面向普通用户的主线。

## 3. 现状勘察账本（Context Ledger）

本表是本 Sprint 的一次性勘察结果。后续 Task 直接引用 `Lxx`，实施期禁止重复做无目标的全仓扫描。

| ID | 已确认事实 | 证据与影响 |
|---|---|---|
| L01 | `static-routes.tsx` 共 **32 条 `bi/*` 路由**，`portal-menu-seed.json` 仅 4 条菜单入口（`/bi/dashboards`、`/bi/questions`、`/bi/data`、`/bi/screens`），其余 28 条只能由直达 URL/页内跳转/书签到达 | 全量逐行分母见 `assets/route-inventory.md`；不得新增入口；"菜单不可见"≠"无人调用" |
| L02 | Analytics 仍残留 Metabase 命名空间、Card、Dashboard、MBQL 结构 | `source/dts-analytics/README.md`、旧 route/page；这些结构只作为退役输入，不再是产品主线或迁移目标 |
| L03 | BI 数据页同时拉取平台数据源和 Analytics databases；“创建问题”传 `dbId`，新语义编辑器只读取 `vds/base` | `DataPage.tsx`、`SemanticCardEditorPage.tsx`；当前创建链参数断裂 |
| L04 | 平台已有 `QueryDatasetAsset`、版本、发布与归档 API，是查询数据集生命周期 owner | `QueryDatasetAsset.java`、`QueryDatasetService.java`、`SqlWorkbenchResource.java`；不得新建第二本数据集台账 |
| L05 | Analytics 语义查询目前可直接调用 `DatasetQueryService.runNative`；Card/Dashboard/Dataset/Public/Embed 复用 `QueryExecutionFacade` 的只读安全检查 | `SemanticQueryService.java`、`QueryExecutionFacade.java`；需在其上建立唯一网关 |
| L06 | Card/Dashboard 已有 CRUD、查询和 public link；Screen 已有独立版本表 | `CardResource`、`DashboardResource`、`ScreenResource`、Liquibase；可兼容扩展，不重建控制面 |
| L07 | 平台报表登记已包含引擎、报表类型、部门、角色、密级、数据集版本和有效期 | `BiReportLinkDto.java`、`ReportsResource.java`、`BiLinksPage.tsx`；扩展稳定资产键即可复用 |
| L08 | `analytics_revision` 已记录 Card/Dashboard JSON 快照和回退，但缺少显式版本号、发布状态和依赖快照 | `RevisionService.java`、数据库表；适合 Expand 后承载发布版本 |
| L09 | Analytics Spring Security 当前仍有 `.anyRequest().permitAll()`；平台权限过滤器只覆盖部分 API | `SecurityConfiguration.java`、`PlatformPermissionFilter.java`；真实认证、默认拒绝和兼容白名单是 P0 |
| L10 | Catalog 已有 `BI_DATASET`、`SCREEN` 资产类型，没有独立 Analysis/Dashboard 类型 | `CatalogAssetType.java`；兼容期沿用 CARD/DASHBOARD/SCREEN，用户文案把 CARD 解释为 Analysis |
| L11 | 大屏设计器和版本链已较完整，且是老安装唯一必须无损保留的 BI 历史 | `analytics_screen*`、模板/权限/审计与菜单绑定；清理旧 Card 前必须扫描全部 screen/template payload |
| L12 | 已有 Sprint-13 定义“薄语义层 + DWS/ADS 主线”，但前台四个页面仍未形成端到端治理闭环 | Sprint-13、Sprint-45 页面能力矩阵；本 Sprint 收敛 owner 和旅程，不复制旧设计 |
| L13 | `SemanticCardEditorPage.tsx` 1137 行、`ScreenResource.java` 3117 行、`analyticsApi.ts` 2483 行 | 超过 DTS 800 行门槛；涉及这些文件时必须先抽取职责，不得继续增长 |
| L14 | 运行容器和直接健康检查正常，四个 BI SPA 路由返回 200；反向代理下 Analytics API 未登录返回 401 | 2026-08-17 G0 探针；HTTP 可达不等于真实账号验收 |
| L15 | 运行库中 Card=0、有效 Dashboard=0、Analytics Database=0、Semantic Model=0、VDS=0、Query Dataset=0；仅有 1 个 Screen 和 1 个平台报表登记 | `assets/domain-profile.md`；没有可复用的 BI 正向/负向验收样本 |
| L16 | `biadmin` 有 25 个 DWS/ADS 表；代表表 1～50 行，粒度键非空 | `assets/domain-profile.md`；可建立隔离样本，但不能冒充客户生产画像 |
| L17 | 前端 source-contract 测试 4/4 通过；一次 Vitest 调用混入 Node test 导致 runner 不匹配 | `it/baseline.md`；后续须按 runner 分开执行 |
| L18 | Analytics 和 Platform 后端聚焦 Maven 测试均在编译前因 root-owned `target/` 权限失败 | `it/baseline.md`；先恢复可重复构建，不得把未执行写成失败测试 |
| L19 | GitNexus 全量刷新仍会被 vendored OpenMetadata Python 抽取拖慢；精确 impact/detect_changes 可运行 | 本轮精确 impact 已对 Filter/CardsPage/routes 执行；HIGH 风险审计过滤器按专用测试收口 |
| L20 | 当前没有 Chrome 95、A1～A4 四角色职责分离、部门越权和受众可见性证据 | `it/baseline.md` P3/P5；F0/T03 与 F6/T01 保持 BLOCKED |
| L21 | `SemanticCardEditorPage.tsx` 被 **5 条路由**复用；`bi/questions/:id/edit` 的 `CardEditorRoutePage` 不是单纯旧编辑器，而是 semantic/legacy 兼容分流器；`bi/explore` 是独立探索页 | `static-routes.tsx:714-790`、`CardEditorRoutePage.tsx:7-53`；F2/T02 必须保留现有分流并新增 analysis 分支，不能整体替换宿主 |
| L22 | 直调 `datasetQueryService.runNative` 的生产调用点共 **3 处**：`SemanticQueryService`（4 次）、`QueryExecutionFacade:284`、`ScreenWarmupService:152` | F3/T01 收口前者并保留 facade 低层 adapter；ScreenWarmup 是大屏历史预热的精确例外，本 Sprint 不修改 |
| L23 | Sprint-93 在 2026-08-16 证明 `xiezm` 可登录并访问受保护 API | `sprint-93/it/baseline.md` P2/P5；可复用步骤和账号，但 delivery baseline 要求 Sprint-94 在当前实例重新执行后才能把 P2/P5 判 PASS |
| L24 | `bi/semantic-modeling` 与 `bi/metrics` 已由 `LegacyDataModelingRedirect` 承接 | 项目已有退役样板；F6/T03 处理旧路由时复用，不另造 |
| L25 | `analytics_card` 已有 `card_type` 列（`0034_card_type.xml`，default `question`，含索引）与 `dataset_query_json`（`0004_collections_cards.xml:72`） | ADR-94-02 的 `card_type='analysis'` 可直接落地，无需新增列 |
| L26 | `CatalogAssetRegistrationService` 只接收物理 `DATASET` 与 TABLE/VIEW/MATERIALIZED_VIEW；`BI_DATASET` 会被 `PHYSICAL_ASSET_TYPE_REQUIRED` 拒绝 | `CatalogAssetRegistrationService.java:13`、`CatalogAssetSemanticsContract.java:200-223`；QueryDataset 逻辑身份不能复用物理 observation command |
| L27 | `QueryDatasetVersion` 只保存 version/status/sql/result/execution/publishedAt；当前 `QueryDatasetService.toDto` 按最新 canonical model 实时推导 contractVersion | `QueryDatasetVersion.java:29-48`、`QueryDatasetService.java:308-370`；同一 published version 会漂移，F1 必须保存不可变 snapshot/checksum |

**开放问题**（勘察未决，需实施期确认，均已挂到具体 Task）：

| # | 问题 | 关闭责任 | 影响 |
|---|---|---|---|
| Q1 | 32 条旧路由的历史调用量是否阻断退役 | 已关闭（2026-08-19） | 不阻断；旧 BI 非历史迁移对象，Contract apply 只受大屏引用、cutoff、备份恢复与回滚门禁约束 |
| Q2 | 动态菜单（非 seed）是否暴露了 `route-inventory.md` 中标记"菜单 ✗"的路由 | F0/T02 | 影响冻结范围判定 |
| Q3 | 客户/目标环境的资产量、P95、并发分布未知 | F0/T03 | `assets/nfr-budget.md` 中 4 项"待校准"无法判定 |
| Q4 | `ScreenWarmupService` 的归属 | 已关闭：Screen owner | 属历史大屏非交互预热，本 Sprint 精确登记为已知例外；未来改造大屏运行链时再评估 |
| Q5 | VDS 创作线（3 条路由）与 ADR-94-01 的长期冲突如何收敛 | 已关闭（2026-08-19） | 不迁移；在旧 BI Contract 发布中停止入口并随后删除 |

## 4. 架构决策记录（ADR）

| 决策点 | 选择 | 约束与后果 |
|---|---|---|
| ADR-94-01 数据集唯一 owner | 平台 `QueryDatasetAsset + QueryDatasetVersion` 是治理和发布事实源；Analytics 只保存数据集 ID、版本、契约版本和 checksum | 禁止 Analytics 复制数据集 SQL、字段治理和生命周期 |
| ADR-94-02 分析兼容模型 | 对外使用 `Analysis`；底层暂复用 `analytics_card`，以 `card_type='analysis'` 和 `AnalysisQuerySpec v1` 兼容扩展 | 新 UI 只读写 v1；旧 `/api/card` 不再承担产品主线，后续可在独立 Contract 变更中删除 legacy rows/handlers |
| ADR-94-03 统一运行边界 | `AnalysisQueryGateway` 是语义、分析、看板唯一应用层查询边界，底层继续复用 `QueryExecutionFacade` | 历史大屏预热是精确例外；未来改造大屏时另行接入 |
| ADR-94-04 安全边界 | 真实 Spring Authentication + 平台资产授权 + RLS/脱敏 + 查询预算；除显式 public endpoint 外默认拒绝 | `.permitAll()` 不得继续兜底；公共匿名分享默认关闭 |
| ADR-94-05 版本 owner | `analytics_revision` 兼容扩展为分析/看板 revision owner；实体只保存生命周期和已发布 revision 指针 | 发布钉定数据集版本、契约 checksum、依赖 revision 和受众快照 |
| ADR-94-06 发布登记 | 平台 `bi_report_link` 继续作为分析服务登记与受众 owner，以 `assetType + assetKey + assetVersion` 定位发布物 | Analytics 不建立第二套部门/角色/密级登记表 |
| ADR-94-07 大屏历史边界 | 大屏及版本、权限、模板、审计、素材和菜单绑定无损保留；旧 `cardId` 引用是清理硬阻断 | 不近似转换；先列出并解除引用，引用未清零时清理脚本必须 fail-closed |
| ADR-94-08 资产类型 | 兼容期保留 CARD/DASHBOARD/SCREEN 机器类型；面向用户将 CARD 统一呈现为“分析” | 不因命名改造引入大范围资产身份迁移 |
| ADR-94-09 权限词汇 | 继续使用 `read/write/export`，写入/发布由现有平台角色和受众边界约束 | 不创建 manage/publish 等新动作；发布资格由工作流规则表达 |
| ADR-94-10 退役策略 | Expand 与 Contract 分离：Sprint-94 收敛主线并交付 dry-run；下一独立发布关闭旧写、执行有 cutoff 的旧 BI 清理；稳定后再删 handler/schema | 不再以未知调用量作为保留理由；大屏引用、全库备份、恢复演练和精确 cutoff 是硬门禁 |
| ADR-94-11 路由收敛 | 四个主入口不变；`/bi/questions`、`/new`、`/:id`、`/:id/edit` 全部指向 governed Analysis | 其余 Metabase 仿造路由进入 Contract 删除清单，不再承诺永久兼容 |
| ADR-94-12 数仓范围 | 默认只允许已发布 DWS/ADS 数据集进入 BI 主线；DWD 需显式高级授权，ODS/STG 仅诊断 | 延续 DTS 分层消费边界，避免 BI 绕过治理直接查源 |
| ADR-94-13 canonical 编辑器路由 | canonical 入口为 `/bi/questions/new`、`/bi/questions/:id` 与 `/bi/questions/:id/edit`，统一由 `AnalysisEditorPage` 承接 | 列表只调用 `listAnalyses/archiveAnalysis`；创建必须从 `/bi/data` 已发布数据集开始 |
| ADR-94-14 VDS 创作线 | 旧 VDS/MBQL 不迁移到新主线 | DWD/DWS/ADS 通过 ModelSpec/dbt 重建；VDS/MBQL 随旧 BI Contract 清理 |
| ADR-94-15 逻辑资产身份接缝 | `QueryDatasetAsset + QueryDatasetVersion` 是 BI_DATASET 逻辑事实源；统一使用 `CatalogAssetType.BI_DATASET + CatalogAssetKey.biDataset(id)`，复用现有 mapping/access/classification/report-link 解析 | `CatalogAssetRegistrationService` 仅用于物理 DATASET observation；禁止调用 `observe(BI_DATASET)`、扩宽物理 admission、直写 semantic store 或另建逻辑注册表 |
| ADR-94-16 不可变数据集契约 | QueryDataset 发布时在指定 version 保存 `dts.query-dataset-contract/v1` snapshot、上游 revision、classification floor、policyRef IDs 和 SHA-256 checksum | 历史版本只从 snapshot 读取；旧版本无法无损回填则 UNRESOLVED 并禁止新 Analysis；运行时当前策略可收紧但不得改变历史 checksum |

## 5. 端到端契约链（Vertical Slice）

### 5.1 数据与调用链

```text
QueryDatasetAsset(PUBLISHED) + QueryDatasetVersion(immutable semantic snapshot/checksum)
  → AnalysisDatasetProjection（读取指定 version snapshot，无自有表）
  → /api/analysis 写入 AnalysisQuerySpec v1 + pinned dataset contract
  → AnalysisQueryGateway
      → Authentication
      → platform dataset/access contract
      → contract checksum
      → semantic compile
      → RLS / masking
      → QueryExecutionFacade read-only/compliance
      → timeout / concurrency / cache
      → datasource
      → audit / lineage / usage
  → analytics_revision(DRAFT → PUBLISHED → SUPERSEDED)
  → Dashboard pinned analysis revisions
  → bi_report_link audience registration
  → authorized consumption
```

大屏复用 governed Analysis 不在本 Sprint 竖线内；本 Sprint 只保护现有大屏历史链。

### 5.2 核心契约摘要

| 边界 | 契约 | 关键语义 |
|---|---|---|
| 平台公开投影 | `GET /api/sql/query-datasets/published`、`GET /api/sql/query-datasets/{id}/published/{version}` | 只返回已发布治理信息，不返回底层 SQL；分页 0-based |
| 内部运行契约 | `GET /api/internal/analysis-datasets/{id}/versions/{version}` | SERVICE_INTERNAL；只从指定 version 的 immutable snapshot 返回字段/指标/关系/policyRefs/classification/checksum/baseSql；UNRESOLVED 返回 409 |
| 分析门面 | `GET/POST /api/analysis`、`GET/PUT /api/analysis/{id}`、`POST /api/analysis/query`、`POST /api/analysis/{id}/query` | 新 UI 只写 `dts.analysis/v1`；400/403/409/422 有稳定语义 |
| canonical UI 入口 | `/bi/questions/new?datasetId=&version=&checksum=`、`/bi/questions/{id}/edit` | ADR-94-13；IT-03 断言创建链参数以此为准 |
| 查询规范 | `AnalysisQuerySpec` | 钉定 dataset id/version/contract/checksum；维度、指标、派生指标、筛选、时间、排序、limit、visualization 均有白名单和上限 |
| 统一网关 | `AnalysisQueryGateway.preview/execute` | 所有分析类运行调用必须进入；返回 queryId、列、行数、截断、缓存、耗时和 checksum |
| 版本发布 | `/api/analysis|dashboard/{id}/validate|publish`、`/{id}/versions` | validate 不持久化发布；publish 钉定依赖并推进唯一 PUBLISHED revision |
| 发布登记 | `PUT /api/internal/reports/registrations` | SERVICE_INTERNAL；以稳定资产键幂等 upsert 受众、密级、有效期和数据集版本 |

完整字段、错误码和状态机固化在 F1～F4 的 Feature README 与 Task 契约中。大屏“复用治理分析”仍不在本 Sprint；但历史大屏保留边界和旧 BI 清理门禁已纳入 F0/T04、F6/T03，`assets/deferred-scope-handoff.md` 仅保留被取代方案的移交说明。

## 6. Gate Registry

状态取值：`PASS` | `GAP`（有缺口但已记录并关联 task）| `BLOCKED` | `PENDING` | `N/A`。

| Gate | 检查项 | 状态 | 证据 | 关闭责任 |
|---|---|---|---|---|
| G0 | 可运行实例与健康检查 | PASS | `it/baseline.md` P1/P2；容器健康，代理 API 401 属受保护正常 | - |
| G0 | 当前 Sprint 登录/API 路径 | GAP | Sprint-93 仅提供复验输入；Sprint-94 当前 session 与受保护 API 尚未重跑 | F0/T02 |
| G0 | A1～A4 角色矩阵 | GAP | `it/baseline.md` P3；四角色职责分离未建 | F0/T03 |
| G0 | 真实领域数据画像 | GAP | `assets/domain-profile.md`；只有小型本地 DWS/ADS，目标环境规模未知 | F0/T03 |
| G0 | 前端静态路由分母 | PASS | `assets/route-inventory.md` 已逐行记录 32 条 route/host/menu/disposition | - |
| G0 | 大屏历史与旧 BI cutoff | GAP | 当前本地 screen=1 且未发现 Card 引用；目标安装仍须运行相同全量预检并记录 cutoff | F0/T04 |
| G0 | 调用观测覆盖 | N/A | 2026-08-19 决策：旧 BI 调用量不再决定历史保留；只保留运维观测价值 | - |
| G0 | 可重复测试与构建 | PASS | root-owned target 已定向修复；Platform/Analytics/Common 聚焦测试由普通工作用户执行 | F0/T01 |
| G0 | Chrome 95 | GAP | `it/baseline.md` P5 | F6/T01 |
| G0 | DTS 不变量 | PASS | ADR-94-01/03/06/09/12/15/16；物理 observation 与逻辑 BI_DATASET 已分开 | - |
| G1 | 纵向契约链 | GAP | 本文 §5 已补 snapshot/兼容分流，但 F1/T01 迁移与 UNRESOLVED contract tests 尚未形成 | F1/T01 |
| G1 | NFR 可执行预算 | GAP | `assets/nfr-budget.md`；目标规模与观测窗口待校准 | F0/T03、F6/T02 |
| G2 | 变更范围与影响分析 | GAP | 精确 impact 可用；审计过滤器为 HIGH 并已按专项测试收口，全量索引刷新仍有 vendored Python 噪声 | F0/T01；每个编码 Task |
| G3 | Expand/兼容/灰度/回滚 | GAP | `assets/release-plan.md` 已定义旧写兼容、screen 预检、备份与独立 Contract apply；待实操 | F6/T02、F6/T03 |
| G4 | 运行手册、告警和容量 | GAP | `assets/runbook.md` 已存在；待补真实阈值和演练证据 | F6/T02 |
| G4 | 三角色端到端验收 | BLOCKED | `it/README.md` | F6/T01 |

## 7. Feature 与执行顺序

| ID | Feature | 优先级 | Task 数 | 状态 |
|---|---|---:|---:|---|
| F0 | 交付基线与兼容盘点 | P0 | 4 | IN_PROGRESS（新增 T04 固化大屏保留边界与清理预检） |
| F1 | 分析数据集唯一主线 | P0 | 2 | DRAFT |
| F2 | 自助分析设计服务 | P0 | 2 | DRAFT |
| F3 | 统一分析运行网关与权限 | P0 | 2 | DRAFT |
| F4 | 看板版本发布与受众 | P0 | 2 | DRAFT |
| F6 | 集中验证发布与运维 | P0 | 3 | BLOCKED（新增 T03 独立旧 BI Contract 发布门禁） |

**任务统计**：以各 Task 文件状态为准，共 **15 Task**；新增 F0/T04 与 F6/T03 承接 2026-08-19 历史边界决策。
**主顺序**：F0 → F1 → F2 → F3 → F4 → F6。F4 可在 F3 网关契约冻结后并行准备持久化。
**Contract 去向**：旧写关闭、旧路由/数据清理统一进入 F6/T03 独立发布；大屏引用不清零不得 apply。

## 8. 页面与路由边界

**全量 32 条路由的逐行分母见 `assets/route-inventory.md`**。Expand 发布先收敛四个主入口；其余 Metabase 仿造路由纳入 F6/T03 Contract 删除清单。

| 用户入口 | 组件宿主 | 本 Sprint 目标 | 处置 |
|---|---|---|---|
| `/bi/data` | `DataPage` | 已发布治理数据集目录，可按领域/负责人/刷新/密级筛选并创建分析 | 改造：移除 Analytics database 与裸数据源混合展示 |
| `/bi/data/:dbId/**`（3 条） | `DatabaseDetailPage` 等 | - | legacy 路由；列入 F6/T03 Contract 清单 |
| `/bi/questions` | `CardsPage` | “分析”工作区：草稿、已发布、归档；搜索、创建、编辑、版本 | 改造：文案不再出现 Question/MBQL/Metabase |
| **`/bi/questions/new`** | `AnalysisEditorPage` | **canonical 新建入口**（ADR-94-13） | 已收敛 |
| **`/bi/questions/:id`、`/:id/edit`** | `AnalysisEditorPage` | **canonical 查看/编辑入口**（ADR-94-13） | 已收敛；不再打开 `CardDetailPage` |
| `/bi/card/new`、`/bi/card/:id/edit` | legacy editor | - | 仅为 Expand/旧镜像回切暂存；F6/T03 删除 |
| `/bi/explore` | `SemanticExplorePage` | - | 在 R1 不改；F6/T03 决定是否由 governed Analysis 完全替代 |
| `/bi/virtual-datasets*`（3 条） | legacy editor | - | 不迁移；F6/T03 删除 |
| `/bi/dashboards`、`/new`、`/:id`、`/:id/edit` | `DashboardsPage` 等 | 看板列表、设计、校验、发布、版本和受众摘要 | 改造：未发布/依赖失效不可被消费者访问 |
| `/bi/screens` | `ScreensPage` | 历史大屏持续可用 | 永久保留并做升级前后全链对账 |
| `/bi/collections*`、`/bi/models`、`/bi/trash`、pulse/subscription 等 | - | - | 不迁移；F6/T03 删除 |

详见 `assets/page-capability-matrix.md` 与 `assets/button-component-matrix.md`。

## 9. 追溯矩阵（Traceability）

| 业务结果 | Feature / Task | 必须产生的证据 |
|---|---|---|
| 可重复构建、fresh 索引与逐符号 impact | F0/T01 | `it/baseline.md` P7/P8 关闭记录、两个后端聚焦测试 exit code |
| 当前会话、本地数据集/legacy 输入与静态分母 | F0/T02 | session/API 复验、隔离输入清单、动态菜单与后端旧写 surface 清单 |
| 目标角色、容量与调用观测 | F0/T03 | A1～A4 矩阵、目标环境画像、逐 surface source/window/value-or-UNKNOWN |
| BI 只选择已发布治理数据集 | F1/T01、F1/T02 | IT-01、IT-02、接口契约测试、页面四态 |
| 创建链参数不再断裂 | F1/T02、F2/T02 | IT-03、Network 断言 datasetId/version/checksum |
| 新分析不再写 MBQL/raw SQL | F2/T01、F2/T02 | schema validation、存储快照、旧读取兼容测试 |
| 语义、分析、看板查询统一受控 | F3/T01 | 调用图 contract test、禁止直调静态守卫、IT-06；ScreenWarmup 保持精确历史例外 |
| 未登录、越权、密级/RLS 违规 fail-closed | F3/T02 | 401/403/422/429/504 矩阵、审计记录、负向 E2E |
| 看板发布钉定依赖版本 | F4/T01 | validate/publish/revision 集成测试、IT-08 |
| 发布后按受众可见 | F4/T02 | bi_report_link upsert、三角色正负向 IT-09 |
| 旧 BI 可安全退役且大屏历史不断层 | F0/T04、F6/T03 | screen/template 全量引用预检、cutoff、完整备份恢复、默认 ROLLBACK、独立 Contract apply |
| Chrome 95 和灰度可发布 | F6/T01、F6/T02 | IT-12、release/rollback/runbook/alert evidence |

大屏功能不迁移，只保护其现有历史链；旧 BI 清理由 F6/T03 承接，不与 Expand 同批执行。

## 10. Sprint Definition of Done

- [ ] 平台已发布 DWS/ADS 数据集是 BI 数据集唯一可选来源；Analytics 不复制底层 SQL或治理生命周期。
- [ ] 新建分析只写 `dts.analysis/v1`；分析列表、查看、编辑不再调用旧 Card API，创建只能从已发布数据集开始。
- [ ] 语义、分析、看板运行均可证明进入 `AnalysisQueryGateway`；`ScreenWarmupService` 仅保留精确历史大屏例外。
- [ ] 未认证、无 read/write/export 权限、RLS/密级冲突、过期契约、超预算查询分别稳定返回约定错误并记录审计。
- [ ] 分析和看板都有 DRAFT/PUBLISHED/ARCHIVED 生命周期，revision 可列出、校验、发布、回退，已发布版本依赖不可漂移。
- [ ] 看板发布幂等登记到 `bi_report_link`，消费者只能看到已发布且受众匹配的资产。
- [ ] 大屏、版本、权限、模板、审计、素材和菜单/角色绑定均在升级前后逐项对账；任何 `cardId` 变体都会阻断旧 BI 清理。
- [ ] Expand 发布只交付 canonical 主线和 dry-run；实际旧写关闭、DELETE、路由/schema 移除在 F6/T03 独立执行且可从完整数据库备份恢复。
- [ ] 四个主页面具备 loading/empty/error/success 四态、键盘可用、无后端术语泄漏，Chrome 95 通过。
- [ ] 维护者、独立发布者、授权/非授权消费者完成真实账号纵向旅程；HTTP、Network、console、数据库状态和审计相互印证。
- [ ] G1 NFR 适应度函数通过，发布 feature flag、回滚镜像、运行手册、告警和容量假设齐全。
- [ ] 每个编码 Task 开始前完成 fresh GitNexus impact；提交前 `gitnexus_detect_changes()` 证明只影响预期 owner 和执行流。

## 11. 主要风险与止损条件

| 风险 | 止损条件 | 处理 |
|---|---|---|
| 平台与 Analytics 身份无法可靠映射 | dataset version/checksum 无法稳定钉定 | 停止 UI 编码，先关闭 F1 契约；禁止用名称猜测关联 |
| 安全切换导致历史大屏或公开链接中断 | screen/template payload 仍含 Card 引用，或公开分享开关未盘点 | 清理脚本 fail-closed；公开分享默认 false，老安装按 inventory 显式开启 |
| 旧 MBQL 无法等价转换 | 编译或结果集无法证明一致 | 不转换；按旧 BI Contract 清理，DWD/DWS/ADS 从 ModelSpec/dbt 重建 |
| Expand 与 Contract 被压成一次发布 | 同一次部署既改 schema/代码又删除旧数据/handler | G3 阻断；先验证新主线与回切，再独立执行 F6/T03 |
| cutoff 取值包含新 governed 行 | 关闭旧写后仍有新 legacy id，或 cutoff 未归档 | 禁止 apply；重新冻结写入、采集 max id、备份并 dry-run |
| 超大文件继续增长 | 计划修改 >800 行 owner 文件但未抽取 seam | G2 阻断；先提取 gateway/component/client 模块并加契约测试 |
| 客户数据量远超本地样本 | 查询预算无法满足 | 回写 NFR 与索引方案，重新过 G1，不以本地 0/1 行假装通过 |
| Sprint 与 Sprint-93/90/91 owner 冲突 | 发现同一资产/发布/血缘 owner 被重复实现 | 接缝已钉死在 `assets/dependency-boundary.md`；物理 observation 不接收逻辑 BI_DATASET，发现新写需求先补 ADR |
| 15 Task 超出时间盒 | F1～F4 未冻结或真实 E2E 环境未就绪 | R1 只交付“数据集→分析→看板发布”；F6/T03 保持独立 BLOCKED，不允许与 R1 混批 |
