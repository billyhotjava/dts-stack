# BI 路由全量盘点（Gate G0 / F0-T02 静态分母，F0-T03 调用观测）

**采集日期**：2026-08-17
**证据**：`source/dts-platform-webapp/src/routes/sections/dashboard/static-routes.tsx`、`portal-menu-seed.json`
**用途**：本表是旧 BI Contract 的路由分母。任何“旧路由已收敛”结论必须逐行对账。

> **2026-08-19 修订**：原 S1～S4 阶段列已被新的 Expand/Contract 路线取代。R1 先保留回切面，F6/T03 统一处理 legacy 路由；大屏入口永久保留。下表的旧阶段值只作为 2026-08-17 勘察快照，不再是发布门禁。

## 1. 事实：32 条 `bi/*` 静态路由，4 个菜单入口

菜单可见（`portal-menu-seed.json`）仅 4 条：`/bi/dashboards`、`/bi/questions`、`/bi/data`、`/bi/screens`。
其余 28 条只能通过直接 URL、页面内跳转或历史书签到达。调用量未知时仍记 `UNKNOWN`，但它不再决定旧 BI 数据是否迁移；F6/T03 以大屏引用、cutoff 和备份恢复为硬门禁。

## 2. 全量路由表

| # | 路由 | 组件宿主 | 菜单 | 30天调用 | 本 Sprint 处置 | 退役阶段 | 责任 |
|---:|---|---|:--:|:--:|---|:--:|---|
| 1 | `bi/home` | `AnalyticsHomePage` | ✗ | UNKNOWN | 保留不动 | S2 | - |
| 2 | `bi/data` | `DataPage` | ✓ | UNKNOWN | **改造**：只列已发布 Query Dataset | - | F1/T02 |
| 3 | `bi/data/:dbId` | `DatabaseDetailPage` | ✗ | UNKNOWN | **冻结**：F1/T02 移除入口链接后成为孤儿，保留可直达 | S1 | F0/T02 |
| 4 | `bi/data/:dbId/tables/:tableId` | `TableDetailPage` | ✗ | UNKNOWN | **冻结**：同上 | S1 | F0/T02 |
| 5 | `bi/data/:dbId/tables/:tableId/fields/:fieldId` | `FieldDetailPage` | ✗ | UNKNOWN | **冻结**：同上 | S1 | F0/T02 |
| 6 | `bi/questions` | `CardsPage` | ✓ | UNKNOWN | **改造**：分析工作区（草稿/已发布/归档） | - | F2/F4 |
| 7 | `bi/questions/new` | `AnalysisEditorPage` | ✗ | UNKNOWN | **canonical 新建入口**（ADR-94-13） | - | F2/T02 |
| 8 | `bi/questions/:id` | `AnalysisEditorPage` | ✗ | UNKNOWN | **canonical 查看入口** | - | F2/T02 |
| 9 | `bi/questions/:id/edit` | `AnalysisEditorPage` | ✗ | UNKNOWN | **canonical 编辑入口** | - | F2/T02 |
| 10 | `bi/explore` | `SemanticExplorePage` | ✗ | UNKNOWN | 保留不动（**非编辑器**，是探索页） | S2 | - |
| 11 | `bi/card/new` | `SemanticCardEditorPage` | ✗ | UNKNOWN | R1 回切面；F6/T03 删除 | Contract | F6/T03 |
| 12 | `bi/card/:id/edit` | `SemanticCardEditorPage` | ✗ | UNKNOWN | R1 回切面；F6/T03 删除 | Contract | F6/T03 |
| 13 | `bi/virtual-datasets` | `SemanticVirtualDatasetsPage` | ✗ | UNKNOWN | 不迁移；F6/T03 删除 | Contract | F6/T03 |
| 14 | `bi/virtual-datasets/new` | `SemanticCardEditorPage` | ✗ | UNKNOWN | 不迁移；F6/T03 删除 | Contract | F6/T03 |
| 15 | `bi/virtual-datasets/:id` | `SemanticCardEditorPage` | ✗ | UNKNOWN | 不迁移；F6/T03 删除 | Contract | F6/T03 |
| 16 | `bi/dashboards` | `DashboardsPage` | ✓ | UNKNOWN | **改造**：版本/依赖健康/受众 | - | F4 |
| 17 | `bi/dashboards/new` | `DashboardEditorPage` | ✗ | UNKNOWN | **改造**：只可添加已发布 Analysis revision | - | F4/T01 |
| 18 | `bi/dashboards/:id` | `DashboardDetailPage` | ✗ | UNKNOWN | **改造**：消费侧受众鉴权 | - | F4/T02 |
| 19 | `bi/dashboards/:id/edit` | `DashboardEditorPage` | ✗ | UNKNOWN | **改造**：校验/发布状态机 | - | F4/T01 |
| 20 | `bi/screens` | `ScreensPage` | ✓ | UNKNOWN | 保留不动（大屏复用顺延，见 roadmap S3） | S3 | - |
| 21 | `bi/models` | `ModelsPage` | ✗ | UNKNOWN | 保留不动 | S2 | - |
| 22 | `bi/semantic-modeling` | `LegacyDataModelingRedirect` | ✗ | UNKNOWN | **已退役**：现存重定向，作为后续阶段的样板 | 已完成 | - |
| 23 | `bi/metrics` | `LegacyDataModelingRedirect` | ✗ | UNKNOWN | **已退役**：同上 | 已完成 | - |
| 24 | `bi/collections` | `CollectionsPage` | ✗ | UNKNOWN | 保留不动 | S2 | - |
| 25 | `bi/collections/:id` | `CollectionItemsPage` | ✗ | UNKNOWN | 保留不动 | S2 | - |
| 26 | `bi/trash` | `TrashPage` | ✗ | UNKNOWN | 保留不动 | S2 | - |
| 27 | `bi/search` | `SearchPage` | ✗ | UNKNOWN | 保留不动 | S2 | - |
| 28 | `bi/project-cockpit` | `ProjectCockpitPage` | ✗ | UNKNOWN | 保留不动 | S2 | - |
| 29 | `bi/explore-sessions` | `ExploreSessionsPage` | ✗ | UNKNOWN | 保留不动 | S2 | - |
| 30 | `bi/report-factory` | `ReportFactoryPage` | ✗ | UNKNOWN | 保留不动 | S2 | - |
| 31 | `bi/metric-lens` | `MetricLensPage` | ✗ | UNKNOWN | 保留不动 | S2 | - |
| 32 | `bi/nl2sql-eval` | `Nl2SqlEvalPage` | ✗ | UNKNOWN | 保留不动 | S2 | - |

**Sprint-94 处置统计**：改造 9、冻结 8、保留不动 13、已退役 2。**重定向 0、删除 0。**

## 3. 编辑器组件复用关系（关键风险）

`SemanticCardEditorPage.tsx`（1137 行）被 **5 条路由**同时复用：

```text
bi/questions/new         ─┐
bi/card/new              ─┤
bi/card/:id/edit         ─┼─→ SemanticCardEditorPage
bi/virtual-datasets/new  ─┤
bi/virtual-datasets/:id  ─┘
```

另有两个独立编辑器组件，容易被误认为同一条线：

- `bi/questions/:id/edit` → `CardEditorRoutePage`（semantic/legacy **兼容分流器**，不是可整体替换的旧编辑器）
- `bi/explore` → `SemanticExplorePage`（探索页，**不是**编辑器）

**后果**：F2/T02 拆分 `SemanticCardEditorPage` 时，会同时改变 `virtual-datasets` 两条路由的行为。虚拟数据集（VDS）创作线与 ADR-94-01「平台 QueryDataset 是数据集唯一 owner」直接冲突，但本 Sprint **不删除、不重定向**，只做两件事：

1. F2/T02 抽取组件时必须保证 VDS 两条路由行为**零回归**（沿用兼容 props/adapter）。
2. F0/T03 为 VDS 创作入口登记观测来源与起始窗口，作为 roadmap S1 冻结的判据。

## 4. 未决问题

- 全部 32 条的真实调用量在 F0/T03 确认目标环境来源前为 UNKNOWN；没有历史数据时从观测起点重新累计。任何 S1/S2 关闭动作不得早于规定窗口。
- 动态菜单（非 seed）是否可能暴露表中"菜单 ✗"的路由，需 F0/T02 用真实账号菜单走查确认。
- 本表只覆盖前端静态路由，**不含**后端 `/api/card`、`/api/dataset`、public/embed 端点的旧写面。后者由 F0/T02 建静态清单、F0/T03 补观测状态。
