# Sprint-85 现状勘察账本（Recon Ledger）

2026-08 一次性勘察完成（覆盖前端 `source/dts-platform-webapp`、后端 `source/dts-platform`、菜单种子 `source/dts-admin`、血缘生产者 `source/dts-ingestion`）。下游 Task 只引用本账本；新事实只能追加，禁止重复宽扫。

## 入口与页面

| # | 菜单 | 路由 | 页面 | 渲染内容 |
|---|---|---|---|---|
| CL-01 | 资产地图 | `/catalog/assets` | `pages/catalog/AssetOverviewPage.tsx` | **统计仪表盘（非图）**：域树侧栏、指标磁贴、分层×域矩阵、治理缺口、待处置 Top5 |
| CL-02 | 数据搜索 | `/catalog/search` | `pages/catalog/DataSearchPage.tsx` | 表单+分组结果（资产/数据集/表/字段），三路并行搜索后按 key 去重 |
| CL-03 | 元数据管理 | `/catalog/metadata-management` | `pages/catalog/MetadataManagementPage.tsx` | 治理处置工作台（密级/域/负责人/生命周期/解决失败） |
| CL-04 | 资产台账 | `/catalog/assets/ledger` | `pages/catalog/DatasetsPage.tsx` | Tab：资产列表（工具条+磁贴+对账+运维菜单）+ 数据标签 |
| CL-05 | 血缘与影响分析 | `/catalog/lineage/**`（5 子页） | `pages/catalog/Lineage{Impact,Graph,Columns,Import,Diff}Page.tsx` | 影响概览/图谱/字段血缘/导入/快照对比 |
| CL-06 | 权限申请 | `/security/dataset-access-approval` | `pages/security/DatasetAccessApprovalPage.tsx` | Tab：我的申请/待办/已办；批量审批 |
| CL-07 | 数据集详情 | `/catalog/datasets/:id` | `pages/catalog/DatasetDetailPage.tsx` | 7 Tab（概览/密级/字段/治理/质量/**血缘与影响**/**权限申请**） |
| CL-08 | 旧资产明细抽屉 | `/catalog/asset-detail`（无菜单） | `pages/catalog/AssetDetailPage.tsx` | 旧深链抽屉，8 路并行拉取 |
| CL-09 | 授权管理/我的授权/权限审计 | `/governance/asset-grants` `/my/asset-grants` `/governance/permission-audit` | 对应页面 | 直接授权/我的授权/审计 |
| CL-10 | BI 目录浏览器 | `/bi/data` | `analytics/pages/DataPage.tsx` | analytics 库浏览，与治理目录无互链 |

## 后端

| # | 资源 | 职责 |
|---|---|---|
| CL-11 | `CatalogAssetPortalResource` `/api/catalog/assets-v2` | assets-v2 列表/概览/缺口/失败/治理 PATCH/OM 血缘缓存/同步 |
| CL-12 | `CatalogLineageResource` `/api/catalog/lineage` | 影响 BFS（位时）、diff、手工边、addax/dbt 导入 |
| CL-13 | `CatalogDatasetResource` `/api/catalog` | legacy 数据集 CRUD/元数据/质量/发布 |
| CL-14 | `CatalogDatasetAccessApprovalResource` `/api/catalog/access` | 权限申请工作流（申请/任务/审批/批量） |
| CL-15 | `AssetGrant/PermissionAudit/OwnershipResource` | 直接授权/审计/所有权 |
| CL-16 | 实体 | `CatalogDataset`、`CatalogDatasetLineage`（位时边）、`CatalogColumnLineage`、`CatalogLineageJob`、`OpenMetadataLineageCache`（FQN 表对）、`CatalogDatasetAccessRequest/Task/Grant`、`CatalogAssetTag/Tag/Category`、`InfraOdsTableMapping`（ODS 映射→血缘源节点） |

## 资产地图数据流（现状）

CL-17：概览页并行调 `getDomainTree({withStats:true})` + `getCatalogAssetsOverview(scope)` + `listCatalogAssetsV2(50行)`；后端 `CatalogAssetOverviewAggregator` 单遍聚合出总量/未分类/缺域/治理缺口/标签覆盖率/分层/矩阵；矩阵 >8 域合并为 `__OTHERS__` 伪列（**点击落到全量域范围**）；治理缺口 drill-down 丢弃 `UNCLASSIFIED/STALE` 过滤（**用户以为筛过了**）。

## 血缘数据流（现状）

CL-18：`GET /catalog/lineage/impact` 从根数据集 BFS（深度 1-10、位时 `?at=`）；`withJobs` 把每条边展开为 dataset→job→dataset 三段（作业节点为持久化 `CatalogLineageJob` 或合成 `job:{REL}:{downId}`，源节点 `source:{connId}:{ns}:{stream}`）；`withColumns` 附加列级边；前端 G6 构图，列节点用伪 id `{dsid}::{col}`。
CL-19：资产详情血缘 Tab 同时渲染 **OM 血缘缓存表**（`/assets-v2/{id}/lineage`）与 **DTS 本地影响图**（`/lineage/impact`），同一资产两种故事。

## 已确认的 UX 碎片问题（引用）

1. "资产地图"非图（CL-01、CL-17）；真图在二级菜单（CL-05）。
2. 双轨台账：`ASSET_PORTAL_V2_ENABLED` 在台账/搜索切换 assets-v2 与 legacy（`pages/catalog/DatasetsPage.tsx:287-299`、`DataSearchPage.tsx:332-380`），搜索还有第三条 tag 路径。
3. 详情页双身份：`DatasetDetailPage.tsx:110-124` legacy 优先、assets-v2 回退，Tab 渲染分支不同（`__source: "openmetadata"|"dts-catalog"`）。
4. 两套血缘并列（CL-19）。
5. 权限四路由分散（CL-06、CL-09），申请入口"申请权限"按钮仅跳回资产地图（`DatasetAccessApprovalPage.tsx:303`）。
6. 筛选状态三套 localStorage schema 各自为政；血缘 7+ 控件无 URL 持久化。
7. 组件层反向依赖页面层：`components/lineage/LineageGraph.tsx:15` 导入 `pages/catalog/lineageShared.tsx`。
8. 旧 `AssetDetailPage` 仍可达（8 路拉取/行），与 `DatasetDetailPage` 并存。
