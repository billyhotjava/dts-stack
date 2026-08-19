# Sprint-95：工业级 BI 分析创作闭环

**时间**：2026-08-20 ～ 2026-09-04  
**状态**：IN_PROGRESS  
**类型**：Full-stack / UI Productization / Governed BI  
**目标**：业务分析人员在既有治理分析与分析看板入口中完成“拖字段 → 出图 → 调样式 → 做计算 → 配联动 → 发布 → 导出”，全过程继续受数据集契约、权限、查询预算、版本和审计约束。

## 背景与价值

Sprint-94 已建立已发布 QueryDataset → `AnalysisQuerySpec` → `AnalysisQueryGateway` → Analysis/Dashboard revision 的治理主线，但分析编辑器仍以勾选字段和表格预览为主，图表样式与参数映射 seam 未接入。Sprint-95 只补齐这条主竖线，不追求 Tableau/FineBI 全功能，不恢复旧 Metabase 新建主线。

## 架构决策记录（ADR）

| 决策 | 选择 | 理由与影响 |
|---|---|---|
| ADR-95-01 页面 owner | 复用 `/bi/questions/:id/edit` 与 `/bi/dashboards/:id/edit` | 不新增菜单、路由或平行编辑器 |
| ADR-95-02 分析定义 owner | 继续使用 `dts.analysis/v1`；货架、计算、筛选和样式写回同一 `AnalysisQuerySpec` | 查询、保存、发布、看板消费共享同一事实 |
| ADR-95-03 拖拽 | 原生 HTML5 drag/drop，并保留键盘可操作的“加入横轴/纵轴”按钮 | Chrome 95 可用；不新增前端依赖 |
| ADR-95-04 图表真实性 | 首批只开放 table/bar/line/area/pie/number | scatter/map/funnel 等未真实渲染前不展示假入口 |
| ADR-95-05 计算边界 | UI 暴露现有派生指标白名单、日期粒度、筛选和排序；不开放 raw SQL/MBQL | 复用后端校验和字段契约 |
| ADR-95-06 联动 | 复用 Dashboard `parameter_mappings` 和既有 cross-filter；配置写入现有 dashcard | 不新建交互表或第二套联动模型 |
| ADR-95-07 导出 | Analysis 新增 `/{id}/query/{csv|xlsx}`，复用 `AnalysisQueryGateway`、`QueryExportService`、CARD `export` 权限和导出密级封印 | 导出不绕过查询安全、预算、密级和审计 |
| ADR-95-08 发布 | 保存、校验、发布继续分离；发布钉定现有 Analysis/Dashboard revision | 不把“保存成功”伪装成已发布 |

## 端到端契约链（Vertical Slice）

| 层 | 契约/落点 | 签名要点 |
|---|---|---|
| UI 入口 | `/bi/data` → `/bi/questions/new?...` → `/bi/questions/{id}/edit` | 拖字段到横/纵轴，实时受治理预览，样式/计算写回 spec |
| Analysis API | `POST /bi/api/analysis/preview`、`PUT /bi/api/analysis/{id}` | 输入 `AnalysisQuerySpec`；返回 columns/rows/rowCount/duration/cache/truncated |
| 发布 API | `POST /bi/api/analysis/{id}/validate|publish` | 受众含 deptCodes/roleCodes/classification/expiresAt；返回 immutable revision |
| Dashboard API | `PUT /bi/api/dashboard/save`、`POST /{id}/validate|publish` | dashcards 保存 `parameter_mappings` 与 interaction settings，发布钉定 analysis revision |
| 导出 API | `POST /bi/api/analysis/{id}/query/csv|xlsx` | PlatformPermissionFilter 解析 CARD + EXPORT；流式返回文件与密级响应头 |
| Service | `AnalysisQueryGateway.preview`、`QueryExportService` | 契约解析 → 权限/RLS → 编译 → 预算/缓存 → 执行 → 导出 |
| 数据 | `analytics_card`、`analytics_dashboard(_card)`、`analytics_revision` | 复用既有列；本 Sprint 无 schema 迁移 |

## 现状勘察账本（Context Ledger）

| # | 事实 | 证据 |
|---|---|---|
| L1 | canonical 分析编辑器已绑定 question new/edit 路由 | `static-routes.tsx:713-744` |
| L2 | 当前字段通过 Checkbox 选入，查询结果固定渲染 Ant Table | `AnalysisEditorPage.tsx:491-580` |
| L3 | `AnalysisQuerySpec` 已含 dimensions/metrics/derivedMetrics/filters/timeRange/orderBy/visualization | `analysisApi.ts:5-63` |
| L4 | editor 当前声明 7 种展示方式，但未将选择接到图表预览 | `AnalysisEditorPage.tsx:602-609` |
| L5 | `ChartRenderer` 已真实支持 table/line/bar/area/pie/number；combo/waterfall/funnel/scatter/map 回退表格 | `ChartRenderer.tsx:11-26,347-356` |
| L6 | `ChartSettings` 仅被自身与 barrel export 引用，主编辑器未消费 | 2026-08-20 定向 `rg ChartSettings` |
| L7 | `ParameterMappingPopover` 存在但主看板未消费 | `dashboard/ParameterMappingPopover.tsx` + 定向引用检查 |
| L8 | 看板已保存 `parameter_mappings`，并只允许 PUBLISHED analysis revision | `DashboardEditorPage.tsx:368-391,699-703` |
| L9 | 看板当前逐卡串行查询 | `DashboardEditorPage.tsx:176-220` |
| L10 | `AnalysisQueryGateway` 已统一契约、权限策略、编译、预算、缓存、执行和审计 | `AnalysisQueryGateway.java:29-105` |
| L11 | Analytics 已有 `QueryExportService` 流式 CSV/XLSX/JSON 与 Card export 密级封印 | `QueryExportService.java:24-127`、`CardResource.java:557-687` |
| L12 | 当前运行库：25 个 QueryDataset PUBLISHED version、2 个 analysis、2 个非归档 dashboard | 2026-08-20 G0 只读 SQL |
| L13 | 目标三容器运行；UI 200；未登录受保护 health 返回 401（符合 fail-closed） | `it/baseline.md` |
| L14 | 前端 9 个现有契约测试、Analytics 5 个聚焦单测通过 | `it/baseline.md` |
| L15 | 当前 shell 无 E2E 凭据；Chrome 150 可用，Chrome 95 executable 缺失 | `it/baseline.md` |
| L16 | 工作区存在未提交的数据建模改动，本 Sprint 不触碰、不暂存、不回滚 | 2026-08-20 `git status --short` |

## Gate Registry

| Gate | 项目 | 状态 | 证据 | 未过则关联 Task |
|---|---|---|---|---|
| G0 | 交付基线 | PASS_WITH_GAPS | `it/baseline.md` | F3/T02 |
| G0 | 领域与真实数据画像 | PASS_WITH_GAPS | `assets/domain-profile.md` | F3/T02 |
| G0 | DTS 不变量 | PASS | ADR-95-01～08 | - |
| G1 | 契约链与 UI 规格 | PASS | 本文与 Feature README | - |
| G1 | 非功能预算 | PASS | `assets/nfr-budget.md` | - |
| G2 | 变更范围与测试先行 | IN_PROGRESS | RED/GREEN commit + detect_changes | F1～F3 |
| G3 | 发布安全 | PENDING | `assets/release-plan.md`（实施后补） | F3/T02 |
| G4 | 可运维与 DoD | PENDING | `it/README.md` | F3/T02 |

## Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 |
|---|---|---:|---|---|
| F0 | 交付基线与契约冻结 | 1 | P0 | DONE |
| F1 | 可视化分析工作台 | 2 | P0 | IN_PROGRESS |
| F2 | 看板联动与发布消费 | 1 | P0 | READY |
| F3 | 受控导出与集中验收 | 2 | P0 | READY |

**执行顺序**：F0 → F1/T01 → F1/T02 → F2/T01 → F3/T01 → F3/T02。

## 追溯矩阵

| 需求点 | Feature / Task | 验收证据 |
|---|---|---|
| 拖字段 → 出图 | F1/T01 | unit/source-contract + Playwright 分析旅程 |
| 调样式 → 做计算 | F1/T02 | spec round-trip test + 图表截图 |
| 配联动 | F2/T01 | parameter mapping/cross-filter test + Dashboard Playwright |
| 发布 | F2/T01 | 现有 publication contract + E2E |
| 导出 | F3/T01 | Java resource test + CSV/XLSX header/content test + E2E 下载 |
| 工业验收 | F3/T02 | build、Chrome95/现代浏览器、API/DB/console/network 证据 |

## 完成标准

- [ ] 同一 `AnalysisQuerySpec` 从拖拽到保存、重载、发布保持无损。
- [ ] 真实图表随查询结果渲染，未实现图表不再出现在 canonical 创建入口。
- [ ] 派生指标/日期粒度/样式都有合法、错误和只读态。
- [ ] 看板编辑时可配置参数映射与联动目标，发布快照包含这些配置。
- [ ] CSV/XLSX 导出要求已保存 Analysis、EXPORT 权限，并包含审计/密级封印。
- [ ] 聚焦测试、webapp legacy build、Analytics test 通过；页面无 console/page error。
- [ ] Chrome 95 若环境仍缺失，Sprint 保持 PASS_WITH_GAPS，不伪称兼容门禁关闭。

## 非目标

- 不复制 Tableau/FineBI 全部能力；不做 LOD、预测、AI 问数、地图、插件市场。
- 不新增菜单、页面、数据库表或第三方拖拽/图表依赖。
- 不修改旧 `bi/card/*`、VDS 或数据大屏设计器。
- 不扩大权限词汇，继续使用既有 `read/write/export`。
