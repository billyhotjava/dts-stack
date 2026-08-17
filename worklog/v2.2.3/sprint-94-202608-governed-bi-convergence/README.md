# Sprint-94：治理型商业智能主线收敛

**时间盒**：2026-08-17 ～ 2026-09-18（25 个工作日）
**状态**：BLOCKED（G0 未通过：Sprint-94 当前登录/API 尚未复验；Chrome 95 未验；A1～A4 角色矩阵和目标环境观测来源未取得；后端聚焦测试被 root 权限的 `target/` 阻断；GitNexus 索引仍 stale。Sprint-93 证据只作为复验输入，见 `assets/dependency-boundary.md`）
**类型**：Architecture Convergence / Full-stack / UI Productization
**目标**：业务分析人员从平台已发布、已治理的 DWS/ADS 数据集出发，在同一条受控查询链中完成自助分析与看板编排；发布后按部门、角色和密级向受众提供服务，避免形成第二套数据模型、权限或发布控制面。

**2026-08-17 范围裁剪**：原 F5「大屏复用与 Metabase 退役」整体顺延并移出活跃 Feature，契约交接保存在 `assets/deferred-scope-handoff.md`。Metabase 仿造功能改为**渐进退役**，拆成 S0～S4 五个阶段跨多 Sprint 执行，本 Sprint 只完成 **S0（盘点、冻结与建立观测口）**。不存在的历史调用量保持 UNKNOWN 并阻断 S1，不要求 Sprint-94 伪造回填。路线与门禁见 `assets/metabase-retirement-roadmap.md`，全量路由分母见 `assets/route-inventory.md`。

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
2. “分析卡片”对外统一为“分析”，新建产物收敛到 canonical 入口；旧 Card/MBQL 保持 Sprint 开始时的兼容行为，本 Sprint 不关闭旧写。
3. 分析与看板共用同一数据集版本、语义契约、权限、RLS/脱敏、查询预算和审计链。
4. 看板只有在依赖校验通过、版本钉定且受众登记完成后才可发布。
5. Metabase 兼容面完成**全量盘点、冻结与观测覆盖登记（S0）**；缺失历史调用保持 UNKNOWN 并阻断 S1；本 Sprint 不重定向、不关闭旧写、不迁移、不删除。

## 2. 范围与非目标

### 2.1 本 Sprint 范围

- 平台 `QueryDatasetAsset/QueryDatasetVersion` 成为 BI 数据集唯一治理 owner，Analytics 只持有钉定引用和运行快照。
- 在既有 `AnalyticsCard` 上建立 `AnalysisQuerySpec dts.analysis/v1` 兼容门面，不新建分析业务表。
- 建立 `AnalysisQueryGateway`，收口语义预览、分析和看板查询（大屏接入属退役 S3）。
- 复用 `analytics_revision` 建立分析/看板校验、版本和发布状态机。
- 扩展既有 `bi_report_link`，以稳定资产身份登记发布受众。
- 完成 Metabase 退役 **S0**：32 条 `bi/*` 路由与后端旧写面的全量盘点、逐 surface 观测来源/窗口/状态，新建产物入口收敛到 canonical。
- 完成三角色、Chrome 95、权限负向、故障注入、灰度与回滚验收。

### 2.2 明确非目标

- **不做任何路由重定向、不关闭任何旧写开关、不执行迁移 apply、不删除任何路由或数据**（分别属于 S1/S2/S3/S4，见 roadmap）。
- 不做大屏复用已发布分析（顺延至 S3；契约交接见 `assets/deferred-scope-handoff.md`）。
- 不复制 Metabase 的 collection、model、trash、pulse、subscription 或数据库管理控制面。
- 不新增 BI 数据集、分析或看板第二套业务表；只允许兼容性 Expand 字段。
- 不新增菜单。
- 不让 Analytics 保存或展示平台数据集的底层原始 SQL。
- 不新建权限动作；继续使用 DTS 既有 `read/write/export` 词汇。
- 不把 OpenMetadata、dbt 或 Analytics 变成业务治理 owner。
- 不在首轮发布中 DROP 旧表、旧列或历史数据。
- 不扩展大屏交互节点、Word 导出、电视模式等设计器增强项。
- 不删除、不重定向、不改变虚拟数据集（VDS）三条路由的行为；F2/T02 拆分共享组件时必须保证其零回归。
- 不把自然语言问数评测、报表工厂、指标透镜纳入面向普通用户的主线。

## 3. 现状勘察账本（Context Ledger）

本表是本 Sprint 的一次性勘察结果。后续 Task 直接引用 `Lxx`，实施期禁止重复做无目标的全仓扫描。

| ID | 已确认事实 | 证据与影响 |
|---|---|---|
| L01 | `static-routes.tsx` 共 **32 条 `bi/*` 路由**，`portal-menu-seed.json` 仅 4 条菜单入口（`/bi/dashboards`、`/bi/questions`、`/bi/data`、`/bi/screens`），其余 28 条只能由直达 URL/页内跳转/书签到达 | 全量逐行分母见 `assets/route-inventory.md`；不得新增入口；"菜单不可见"≠"无人调用" |
| L02 | Analytics 仍以 Metabase 命名空间、Card、Dashboard、MBQL 兼容结构为核心 | `source/dts-analytics/README.md`、`CardEditorRoutePage.tsx`；兼容读保留，新写面改为 DTS Analysis |
| L03 | BI 数据页同时拉取平台数据源和 Analytics databases；“创建问题”传 `dbId`，新语义编辑器只读取 `vds/base` | `DataPage.tsx`、`SemanticCardEditorPage.tsx`；当前创建链参数断裂 |
| L04 | 平台已有 `QueryDatasetAsset`、版本、发布与归档 API，是查询数据集生命周期 owner | `QueryDatasetAsset.java`、`QueryDatasetService.java`、`SqlWorkbenchResource.java`；不得新建第二本数据集台账 |
| L05 | Analytics 语义查询目前可直接调用 `DatasetQueryService.runNative`；Card/Dashboard/Dataset/Public/Embed 复用 `QueryExecutionFacade` 的只读安全检查 | `SemanticQueryService.java`、`QueryExecutionFacade.java`；需在其上建立唯一网关 |
| L06 | Card/Dashboard 已有 CRUD、查询和 public link；Screen 已有独立版本表 | `CardResource`、`DashboardResource`、`ScreenResource`、Liquibase；可兼容扩展，不重建控制面 |
| L07 | 平台报表登记已包含引擎、报表类型、部门、角色、密级、数据集版本和有效期 | `BiReportLinkDto.java`、`ReportsResource.java`、`BiLinksPage.tsx`；扩展稳定资产键即可复用 |
| L08 | `analytics_revision` 已记录 Card/Dashboard JSON 快照和回退，但缺少显式版本号、发布状态和依赖快照 | `RevisionService.java`、数据库表；适合 Expand 后承载发布版本 |
| L09 | Analytics Spring Security 当前仍有 `.anyRequest().permitAll()`；平台权限过滤器只覆盖部分 API | `SecurityConfiguration.java`、`PlatformPermissionFilter.java`；真实认证、默认拒绝和兼容白名单是 P0 |
| L10 | Catalog 已有 `BI_DATASET`、`SCREEN` 资产类型，没有独立 Analysis/Dashboard 类型 | `CatalogAssetType.java`；兼容期沿用 CARD/DASHBOARD/SCREEN，用户文案把 CARD 解释为 Analysis |
| L11 | 大屏设计器和版本链已较完整；核心缺口是复用已发布分析和统一运行链 | Sprint-36 设计审计；**本 Sprint 不动大屏，顺延至退役 S3** |
| L12 | 已有 Sprint-13 定义“薄语义层 + DWS/ADS 主线”，但前台四个页面仍未形成端到端治理闭环 | Sprint-13、Sprint-45 页面能力矩阵；本 Sprint 收敛 owner 和旅程，不复制旧设计 |
| L13 | `SemanticCardEditorPage.tsx` 1137 行、`ScreenResource.java` 3117 行、`analyticsApi.ts` 2483 行 | 超过 DTS 800 行门槛；涉及这些文件时必须先抽取职责，不得继续增长 |
| L14 | 运行容器和直接健康检查正常，四个 BI SPA 路由返回 200；反向代理下 Analytics API 未登录返回 401 | 2026-08-17 G0 探针；HTTP 可达不等于真实账号验收 |
| L15 | 运行库中 Card=0、有效 Dashboard=0、Analytics Database=0、Semantic Model=0、VDS=0、Query Dataset=0；仅有 1 个 Screen 和 1 个平台报表登记 | `assets/domain-profile.md`；没有可复用的 BI 正向/负向验收样本 |
| L16 | `biadmin` 有 25 个 DWS/ADS 表；代表表 1～50 行，粒度键非空 | `assets/domain-profile.md`；可建立隔离样本，但不能冒充客户生产画像 |
| L17 | 前端 source-contract 测试 4/4 通过；一次 Vitest 调用混入 Node test 导致 runner 不匹配 | `it/baseline.md`；后续须按 runner 分开执行 |
| L18 | Analytics 和 Platform 后端聚焦 Maven 测试均在编译前因 root-owned `target/` 权限失败 | `it/baseline.md`；先恢复可重复构建，不得把未执行写成失败测试 |
| L19 | GitNexus 当前 indexed commit=`76d9657`、current commit=`a0a9fc0`，状态 stale；刷新曾因 vendored OpenMetadata Python worker 超时并停滞后被中止 | `it/baseline.md`；任何代码 Task 开始前必须恢复索引并逐符号 impact |
| L20 | 当前没有 Chrome 95、A1～A4 四角色职责分离、部门越权和受众可见性证据 | `it/baseline.md` P3/P5；F0/T03 与 F6/T01 保持 BLOCKED |
| L21 | `SemanticCardEditorPage.tsx` 被 **5 条路由**复用；`bi/questions/:id/edit` 的 `CardEditorRoutePage` 不是单纯旧编辑器，而是 semantic/legacy 兼容分流器；`bi/explore` 是独立探索页 | `static-routes.tsx:714-790`、`CardEditorRoutePage.tsx:7-53`；F2/T02 必须保留现有分流并新增 analysis 分支，不能整体替换宿主 |
| L22 | 直调 `datasetQueryService.runNative` 的生产调用点共 **3 处**：`SemanticQueryService`（4 次）、`QueryExecutionFacade:284`、`ScreenWarmupService:152` | F3/T01 收口前者并保留 facade 低层 adapter；ScreenWarmup 是 S3 owner 的非交互已知例外，Sprint-94 不修改，架构规则精确登记 source location 与移除阶段 |
| L23 | Sprint-93 在 2026-08-16 证明 `xiezm` 可登录并访问受保护 API | `sprint-93/it/baseline.md` P2/P5；可复用步骤和账号，但 delivery baseline 要求 Sprint-94 在当前实例重新执行后才能把 P2/P5 判 PASS |
| L24 | `bi/semantic-modeling` 与 `bi/metrics` 已由 `LegacyDataModelingRedirect` 承接 | 项目已有渐进退役样板组件；S2 阶段做重定向时复用它，不另造 |
| L25 | `analytics_card` 已有 `card_type` 列（`0034_card_type.xml`，default `question`，含索引）与 `dataset_query_json`（`0004_collections_cards.xml:72`） | ADR-94-02 的 `card_type='analysis'` 可直接落地，无需新增列 |
| L26 | `CatalogAssetRegistrationService` 只接收物理 `DATASET` 与 TABLE/VIEW/MATERIALIZED_VIEW；`BI_DATASET` 会被 `PHYSICAL_ASSET_TYPE_REQUIRED` 拒绝 | `CatalogAssetRegistrationService.java:13`、`CatalogAssetSemanticsContract.java:200-223`；QueryDataset 逻辑身份不能复用物理 observation command |
| L27 | `QueryDatasetVersion` 只保存 version/status/sql/result/execution/publishedAt；当前 `QueryDatasetService.toDto` 按最新 canonical model 实时推导 contractVersion | `QueryDatasetVersion.java:29-48`、`QueryDatasetService.java:308-370`；同一 published version 会漂移，F1 必须保存不可变 snapshot/checksum |

**开放问题**（勘察未决，需实施期确认，均已挂到具体 Task）：

| # | 问题 | 关闭责任 | 影响 |
|---|---|---|---|
| Q1 | 32 条路由与后端旧写面的历史调用量缺少已确认来源/保留窗口 | F0/T03 | Sprint-94 记录 UNKNOWN 原因并建立观测起点；连续 14 天可读前不得进入 S1 |
| Q2 | 动态菜单（非 seed）是否暴露了 `route-inventory.md` 中标记"菜单 ✗"的路由 | F0/T02 | 影响冻结范围判定 |
| Q3 | 客户/目标环境的资产量、P95、并发分布未知 | F0/T03 | `assets/nfr-budget.md` 中 4 项"待校准"无法判定 |
| Q4 | `ScreenWarmupService` 的归属 | 已关闭：退役 S3 | 属 Screen 非交互预热，本 Sprint 精确登记为已知例外且不修改；S3 接入网关时移除例外 |
| Q5 | VDS 创作线（3 条路由）与 ADR-94-01 的长期冲突如何收敛 | roadmap S1 | 本 Sprint 只冻结并登记观测口，不做处置 |

## 4. 架构决策记录（ADR）

| 决策点 | 选择 | 约束与后果 |
|---|---|---|
| ADR-94-01 数据集唯一 owner | 平台 `QueryDatasetAsset + QueryDatasetVersion` 是治理和发布事实源；Analytics 只保存数据集 ID、版本、契约版本和 checksum | 禁止 Analytics 复制数据集 SQL、字段治理和生命周期 |
| ADR-94-02 分析兼容模型 | 对外使用 `Analysis`；底层复用 `analytics_card`，以 `card_type='analysis'` 和 `AnalysisQuerySpec v1` 兼容扩展 | 新 UI 只写 v1；旧 `/api/card` 的现有读写行为在 S0 保持不变，只读收敛由 S1/S2 独立门禁推进 |
| ADR-94-03 统一运行边界 | `AnalysisQueryGateway` 是语义、分析、看板唯一应用层查询边界（大屏于 S3 接入，契约不变），底层继续复用 `QueryExecutionFacade` | 禁止资源层或语义服务绕过网关直接执行 SQL |
| ADR-94-04 安全边界 | 真实 Spring Authentication + 平台资产授权 + RLS/脱敏 + 查询预算；除显式 public endpoint 外默认拒绝 | `.permitAll()` 不得继续兜底；公共匿名分享默认关闭 |
| ADR-94-05 版本 owner | `analytics_revision` 兼容扩展为分析/看板 revision owner；实体只保存生命周期和已发布 revision 指针 | 发布钉定数据集版本、契约 checksum、依赖 revision 和受众快照 |
| ADR-94-06 发布登记 | 平台 `bi_report_link` 继续作为分析服务登记与受众 owner，以 `assetType + assetKey + assetVersion` 定位发布物 | Analytics 不建立第二套部门/角色/密级登记表 |
| ADR-94-07 大屏复用 | 新组件只引用已发布 Analysis revision；旧 `cardId` 通过兼容 adapter 读取 | **顺延至退役 S3**；契约交接见 `assets/deferred-scope-handoff.md` |
| ADR-94-08 资产类型 | 兼容期保留 CARD/DASHBOARD/SCREEN 机器类型；面向用户将 CARD 统一呈现为“分析” | 不因命名改造引入大范围资产身份迁移 |
| ADR-94-09 权限词汇 | 继续使用 `read/write/export`，写入/发布由现有平台角色和受众边界约束 | 不创建 manage/publish 等新动作；发布资格由工作流规则表达 |
| ADR-94-10 退役策略 | Metabase 仿造功能**渐进退役**，分 S0 盘点/冻结/观测 → S1 只读兼容准备 → S2 兼容重定向 → S3 迁移 apply → S4 物理退役；阶段不可跳跃 | 本 Sprint 只做 S0；S1 起每阶段需前一阶段的观测窗口证据，见 `assets/metabase-retirement-roadmap.md` |
| ADR-94-11 路由收敛 | 四个主入口不变；本 Sprint 对 28 条非菜单路由只做**盘点与冻结**，不做重定向 | 重定向属 S2，且必须复用既有 `LegacyDataModelingRedirect` 样板（L24），不另造 |
| ADR-94-12 数仓范围 | 默认只允许已发布 DWS/ADS 数据集进入 BI 主线；DWD 需显式高级授权，ODS/STG 仅诊断 | 延续 DTS 分层消费边界，避免 BI 绕过治理直接查源 |
| ADR-94-13 canonical 编辑器路由 | 分析编辑器 canonical 入口定为 `/bi/questions/new` 与 `/bi/questions/:id/edit`；保留 `CardEditorRoutePage` 作为兼容分流器，增加 `card_type='analysis'` 分支 | semantic legacy 继续当前 `/bi/card/:id/edit` 分流，其他 legacy Card 保持现有兼容行为；本 Sprint 不新增 redirect、不关闭旧写；IT-03 以 canonical 路由为准 |
| ADR-94-14 VDS 创作线 | 虚拟数据集三条路由与 ADR-94-01 长期冲突，但本 Sprint 只冻结并登记观测来源/窗口，行为零变更 | F2/T02 拆分共享组件时须保证 VDS 路由零回归；处置方案在 S1 决定（开放问题 Q5） |
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

大屏复用（`Screen pinned analysis revisions`）已顺延至退役 S3，不在本 Sprint 竖线内。

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

完整字段、错误码和状态机固化在 F1～F4 的 Feature README 与 Task 契约中。大屏组件与兼容迁移契约已移交 `assets/deferred-scope-handoff.md`，**不属于 Sprint-94 活跃 Feature/Task**。

## 6. Gate Registry

状态取值：`PASS` | `GAP`（有缺口但已记录并关联 task）| `BLOCKED` | `PENDING` | `N/A`。

| Gate | 检查项 | 状态 | 证据 | 关闭责任 |
|---|---|---|---|---|
| G0 | 可运行实例与健康检查 | PASS | `it/baseline.md` P1/P2；容器健康，代理 API 401 属受保护正常 | - |
| G0 | 当前 Sprint 登录/API 路径 | GAP | Sprint-93 仅提供复验输入；Sprint-94 当前 session 与受保护 API 尚未重跑 | F0/T02 |
| G0 | A1～A4 角色矩阵 | GAP | `it/baseline.md` P3；四角色职责分离未建 | F0/T03 |
| G0 | 真实领域数据画像 | GAP | `assets/domain-profile.md`；只有小型本地 DWS/ADS，目标环境规模未知 | F0/T03 |
| G0 | 前端静态路由分母 | PASS | `assets/route-inventory.md` 已逐行记录 32 条 route/host/menu/disposition | - |
| G0 | 动态菜单与后端旧写面分母 | GAP | 前端 inventory 明确不含 `/api/card` write、MBQL、public/embed；动态菜单未走查 | F0/T02 |
| G0 | 调用观测覆盖 | GAP | 现有 query metrics 不能反推 32 条路由；source/retention 未确认 | F0/T03 |
| G0 | 可重复测试与构建 | GAP | `it/baseline.md` P7；后端 target 权限阻断 | F0/T01 |
| G0 | Chrome 95 | GAP | `it/baseline.md` P5 | F6/T01 |
| G0 | DTS 不变量 | PASS | ADR-94-01/03/06/09/12/15/16；物理 observation 与逻辑 BI_DATASET 已分开 | - |
| G1 | 纵向契约链 | GAP | 本文 §5 已补 snapshot/兼容分流，但 F1/T01 迁移与 UNRESOLVED contract tests 尚未形成 | F1/T01 |
| G1 | NFR 可执行预算 | GAP | `assets/nfr-budget.md`；目标规模与观测窗口待校准 | F0/T03、F6/T02 |
| G2 | 变更范围与影响分析 | BLOCKED | GitNexus 索引落后且刷新失败 | F0/T01；每个编码 Task |
| G3 | Expand/兼容/灰度/回滚 | PENDING | 产物：`assets/release-plan.md`（待建） | F6/T02 |
| G4 | 运行手册、告警和容量 | PENDING | 产物：`assets/runbook.md`（待建） | F6/T02 |
| G4 | 三角色端到端验收 | BLOCKED | `it/README.md` | F6/T01 |

## 7. Feature 与执行顺序

| ID | Feature | 优先级 | Task 数 | 状态 |
|---|---|---:|---:|---|
| F0 | 交付基线与兼容盘点 | P0 | 3 | READY（T01/T02 READY，T03 BLOCKED） |
| F1 | 分析数据集唯一主线 | P0 | 2 | DRAFT |
| F2 | 自助分析设计服务 | P0 | 2 | DRAFT |
| F3 | 统一分析运行网关与权限 | P0 | 2 | DRAFT |
| F4 | 看板版本发布与受众 | P0 | 2 | DRAFT |
| F6 | 集中验证发布与运维 | P0 | 2 | BLOCKED（T01 BLOCKED，T02 DRAFT） |

**任务统计**：READY=2，DRAFT=9，BLOCKED=2，IN_PROGRESS=0，DONE=0（共 **13 Task**；顺延范围不在 `features/`，不参与统计）。
**主顺序**：F0 → F1 → F2 → F3 → F4 → F6。F4 可在 F3 网关契约冻结后并行准备持久化。
**顺延去向**：大屏复用与迁移通道 → S3、路由重定向 → S2、旧写 flag → S1；契约与门禁见 `assets/deferred-scope-handoff.md` 和 `assets/metabase-retirement-roadmap.md`。

## 8. 页面与路由边界

**全量 32 条路由的逐行分母见 `assets/route-inventory.md`**。本节只列本 Sprint 有改造动作的入口；其余 23 条一律"保留不动"或"冻结"，**本 Sprint 不做任何重定向**。

| 用户入口 | 组件宿主 | 本 Sprint 目标 | 处置 |
|---|---|---|---|
| `/bi/data` | `DataPage` | 已发布治理数据集目录，可按领域/负责人/刷新/密级筛选并创建分析 | 改造：移除 Analytics database 与裸数据源混合展示 |
| `/bi/data/:dbId/**`（3 条） | `DatabaseDetailPage` 等 | - | 冻结：上游入口移除后成为孤儿，仍可直达，S1 再议 |
| `/bi/questions` | `CardsPage` | “分析”工作区：草稿、已发布、归档；搜索、创建、编辑、版本 | 改造：文案不再出现 Question/MBQL/Metabase |
| **`/bi/questions/new`** | `SemanticCardEditorPage` | **canonical 新建入口**（ADR-94-13） | 改造 |
| **`/bi/questions/:id/edit`** | `CardEditorRoutePage` 兼容分流器 | **canonical 编辑入口**（ADR-94-13） | 改造：新增 analysis 分支；semantic/legacy 现有行为保持不变 |
| `/bi/card/new`、`/bi/card/:id/edit` | `SemanticCardEditorPage` | - | 冻结：仍可用、**不重定向**（重定向属 S2） |
| `/bi/explore` | `SemanticExplorePage` | - | 保留不动（**是探索页，不是编辑器**） |
| `/bi/virtual-datasets*`（3 条） | 共享 `SemanticCardEditorPage` | - | 冻结：拆分组件时须零回归（ADR-94-14） |
| `/bi/dashboards`、`/new`、`/:id`、`/:id/edit` | `DashboardsPage` 等 | 看板列表、设计、校验、发布、版本和受众摘要 | 改造：未发布/依赖失效不可被消费者访问 |
| `/bi/screens` | `ScreensPage` | - | 保留不动（大屏复用顺延 S3） |
| `/bi/collections*`、`/bi/models`、`/bi/trash`、report-factory/metric-lens/nl2sql-eval 等 | - | - | 保留不动，S2 再议重定向 |

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
| 语义、分析、看板查询统一受控 | F3/T01 | 调用图 contract test、禁止直调静态守卫、IT-06；ScreenWarmup 精确例外移交 S3 |
| 未登录、越权、密级/RLS 违规 fail-closed | F3/T02 | 401/403/422/429/504 矩阵、审计记录、负向 E2E |
| 看板发布钉定依赖版本 | F4/T01 | validate/publish/revision 集成测试、IT-08 |
| 发布后按受众可见 | F4/T02 | bi_report_link upsert、三角色正负向 IT-09 |
| Metabase 退役 S0：分母完整、观测口可追溯、新建入口收敛、无行为变更 | F0/T02、F0/T03、F2/T02 | 32 行静态分母、逐 surface 观测状态、canonical 入口 E2E、零重定向/零删除 diff；UNKNOWN 不伪造为 0 |
| Chrome 95 和灰度可发布 | F6/T01、F6/T02 | IT-12、release/rollback/runbook/alert evidence |

顺延项的大屏复用与兼容迁移追溯已移交 `assets/deferred-scope-handoff.md`，不在本 Sprint 矩阵内。

## 10. Sprint Definition of Done

- [ ] 平台已发布 DWS/ADS 数据集是 BI 数据集唯一可选来源；Analytics 不复制底层 SQL或治理生命周期。
- [ ] 新建分析只写 `dts.analysis/v1`；旧 Card/MBQL 现有读写行为未被 Sprint-94 改变，未来只读收敛不提前执行。
- [ ] 语义、分析、看板运行均可证明进入 `AnalysisQueryGateway`，Sprint-94 范围内无资源层直连执行；`ScreenWarmupService` 仅保留精确 S3 临时例外。
- [ ] 未认证、无 read/write/export 权限、RLS/密级冲突、过期契约、超预算查询分别稳定返回约定错误并记录审计。
- [ ] 分析和看板都有 DRAFT/PUBLISHED/ARCHIVED 生命周期，revision 可列出、校验、发布、回退，已发布版本依赖不可漂移。
- [ ] 看板发布幂等登记到 `bi_report_link`，消费者只能看到已发布且受众匹配的资产。
- [ ] 退役 S0 完成：32 条静态路由、动态菜单和后端旧写面分母完整；每个 surface 有 source/window/value 或 UNKNOWN 原因与观测起点；新建产物只从 canonical 入口产生。
- [ ] **可证明本 Sprint 无任何重定向、无旧写关闭、无迁移 apply、无删除**；VDS 三条路由行为零回归。
- [ ] 四个主页面具备 loading/empty/error/success 四态、键盘可用、无后端术语泄漏，Chrome 95 通过。
- [ ] 维护者、独立发布者、授权/非授权消费者完成真实账号纵向旅程；HTTP、Network、console、数据库状态和审计相互印证。
- [ ] G1 NFR 适应度函数通过，发布 feature flag、回滚镜像、运行手册、告警和容量假设齐全。
- [ ] 每个编码 Task 开始前完成 fresh GitNexus impact；提交前 `gitnexus_detect_changes()` 证明只影响预期 owner 和执行流。

## 11. 主要风险与止损条件

| 风险 | 止损条件 | 处理 |
|---|---|---|
| 平台与 Analytics 身份无法可靠映射 | dataset version/checksum 无法稳定钉定 | 停止 UI 编码，先关闭 F1 契约；禁止用名称猜测关联 |
| 安全切换导致现有嵌入/公开链接中断 | 未清点调用量、无显式白名单和回滚开关 | 保持旧读路径，先 shadow log，再按受众灰度 |
| 旧 MBQL 无法等价转换 | 编译或结果集无法证明一致 | 标记 legacy-read-only，不自动改写、不删除（迁移本身已顺延 S3） |
| 退役阶段被压缩或跳跃 | 出现"顺手做个重定向/顺手关个 flag"的提交 | S0 的 DoD 明确要求可证明零重定向/零关闭；阶段门禁见 roadmap，不可跳跃 |
| 拆分共享编辑器组件误伤 VDS | `virtual-datasets` 两条路由行为变化 | F2/T02 必须先补 VDS 路由回归测试再动刀（ADR-94-14） |
| 超大文件继续增长 | 计划修改 >800 行 owner 文件但未抽取 seam | G2 阻断；先提取 gateway/component/client 模块并加契约测试 |
| 客户数据量远超本地样本 | 查询预算无法满足 | 回写 NFR 与索引方案，重新过 G1，不以本地 0/1 行假装通过 |
| Sprint 与 Sprint-93/90/91 owner 冲突 | 发现同一资产/发布/血缘 owner 被重复实现 | 接缝已钉死在 `assets/dependency-boundary.md`；物理 observation 不接收逻辑 BI_DATASET，发现新写需求先补 ADR |
| 裁剪后 13 Task 仍超出 25 个工作日 | 第 12 个工作日 F1+F2 未全部 DONE | 触发二次裁剪：F4/T02 受众登记顺延，保底交付"数据集→分析→看板发布"主线；**不允许压缩 F6 验收范围** |
