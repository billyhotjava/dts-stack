# Sprint-31A F1/T01 资产表和调用面盘点

**状态**: DONE
**日期**: 2026-05-17
**范围**: `source/dts-platform`、`source/dts-platform-webapp`、`source/dts-analytics`、`source/dts-metrics`

## 结论

当前 DTS 已具备多个资产相关能力，但还不是一个企业级唯一资产事实源。核心现状是：

1. `CatalogDataset` 是本地数据资产主表，但只覆盖部分数据集/模型资产。
2. `OpenMetadataAssetCache` 是外部元数据缓存，不应直接成为业务消费事实源。
3. `CatalogAssetExtension` 和 `CatalogAssetMapping` 已经在做 OM cache 与本地 Catalog 的扩展/映射，但 asset identity 还没有统一成 `asset_type + asset_key + asset_id`。
4. `asset_grant` 已经是统一权限事实源的基础，但资产类型、资产 ID、列表/详情过滤和密级语义还需要统一。
5. `OpenLineageReceiverResource` 会自动创建 `CatalogDataset`，但自动创建资产目前缺 owner、classification、sourceSystem、明确 governance status，仍存在“自动发现即入库但治理不完整”的风险。
6. 语义指标仍在 `SemanticModelingService` 内直接读写 `semantic_*` 表，后续必须映射到 dts-metrics，而不是继续扩展 platform 内部语义事实源。

## 资产事实面

| 资产面 | 当前位置 | 事实源定位 | 风险 |
|---|---|---|---|
| 本地数据集资产 | `CatalogDataset` | platform 本地资产主表 | 缺统一 asset_key，治理字段不完整，生命周期语义不稳定 |
| 表结构 | `CatalogTableSchema` | schema 事实的一部分 | 和 OM column cache、dbt schema contract 边界需明确 |
| 字段结构 | `CatalogColumnSchema` | column 事实的一部分 | 写入路径较多，需继续保持按 table 串行写入 |
| OM 资产缓存 | `OpenMetadataAssetCache` | 外部元数据缓存 | 不能直接作为业务事实源；需要通过 mapping/extension 转 platform 契约 |
| OM 字段缓存 | `OpenMetadataColumnCache` | 外部字段缓存 | 与本地字段、dbt schema contract 可能重复 |
| OM 血缘缓存 | `OpenMetadataLineageCache` | 外部血缘缓存 | 和本地 `CatalogDatasetLineage` 双轨 |
| 资产扩展治理 | `CatalogAssetExtension` | platform 治理覆盖层 | 已有 classification、warehouseLayer、ownerDept、governanceStatus，但生命周期和认证状态仍弱 |
| 资产映射 | `CatalogAssetMapping` | OM/cache 与 legacy Catalog 映射 | 已有 fqn、legacyDatasetId、sourceId、matchStatus，但缺统一 asset_key |
| 统一授权 | `asset_grant` | 权限事实源基础 | 当前 asset_id 是字符串，缺资产身份标准；SCREEN 显式授权模式已特殊化 |
| 资产归属 | `asset_ownership` | ownerDept 事实之一 | 和 CatalogDataset.ownerDept / CatalogAssetExtension.ownerDept 需要收敛 |
| BI 数据集 | `QueryDatasetAsset` | BI 数据集资产 | 目前未统一进入 Catalog asset identity |
| 大屏资产 | analytics `AnalyticsScreen` + platform `asset_grant` | 大屏消费资产 | 权限已走 platform 优先，但本地 fallback 仍需最终收口 |
| 语义模型 | platform `semantic_*` | 过渡期语义事实 | Sprint-32 迁入 dts-metrics 前需要 dry-run 映射 |

## 后端调用面

| 调用面 | 关键类/API | 当前职责 | 后续 Sprint-31A 处理 |
|---|---|---|---|
| 资产门户 | `CatalogAssetPortalService` | 合并 OM cache 和 legacy Catalog，提供列表、详情、血缘、治理更新 | 列表层权限、资产身份、治理状态统一 |
| 资产身份解析 | `CatalogAssetIdentityResolver` | 支持 OM UUID、legacy UUID、OM entity id、FQN 解析 | 扩展为 `asset_type + asset_key` 解析 |
| 资产权限 | `AssetPermissionService` / `/api/internal/asset-permission/**` | 基于 asset_grant / ownership / role 做权限判断 | 增加统一动作、密级语义、拒绝原因和消费方契约 |
| 权限审计 | `AssetPermissionAuditService` / `asset_permission_audit` | 授权和操作审计基础 | 补访问拒绝、fallback 命中、密级变更审计 |
| OpenLineage 接收 | `OpenLineageReceiverResource` | 接收 lineage payload，创建 dataset 和 lineage | 自动创建资产进入 `PENDING_GOVERNANCE`，补来源证明 |
| dbt 同步 | `DbtAssetSyncService` / `CatalogColumnSyncService` | 从 manifest 同步资产和字段 | 写入统一 asset identity、schema contract、治理缺口 |
| SQL IDE Catalog | `SqlCatalogLazyService` / `SqlCatalogService` | 为 SQL IDE 返回 schema/tree/search | 改为只读统一资产契约，不绕过权限 |
| 数据安全 | `AccessChecker` / `DatasetSecurityMetadataResolver` | 数据集级读权限、行列安全辅助 | 和 asset_grant 动作语义对齐 |
| 语义指标 | `SemanticModelingService` / `/api/semantic/**` | platform 内语义建模运行时 | Sprint-32 迁移，Sprint-31A 只输出映射策略 |

## 前端调用面

| 前端面 | 当前文件 | 当前职责 | 风险 |
|---|---|---|---|
| 资产地图 | `src/pages/catalog/DatasetsPage.tsx` | 资产地图和层级视图 | 依赖后端治理状态质量 |
| 资产详情 | `src/pages/catalog/DatasetDetailPage.tsx` / `AssetDetailPage.tsx` | 技术详情、治理扩展、血缘 | 需要统一 blocked/fallback/no permission 状态 |
| 数据搜索 | `src/pages/catalog/DataSearchPage.tsx` | 搜索资产 | 需要列表层权限一致性 |
| 血缘页面 | `LineagePage` + subpages | 血缘与影响分析 | 需要本地 lineage 和 OM cache 边界清楚 |
| 权限页面 | `AssetGrantPage` / `AssetOwnershipPage` / `MyGrantsPage` | 授权、归属、我的申请 | 需要和统一 asset identity 对齐 |
| 指标页面 | `src/pages/metrics/**` + `semanticModelingApi.ts` | 旧 platform 语义中心页面 | 后续必须桥接或迁移到 dts-metrics |
| analytics 页面 | `src/analytics/**` | BI/大屏消费和设计器 | 权限继续 platform 优先、本地 fallback 只读过渡 |

## 已有优势

- Catalog 本地模型已经有 `classification`、`ownerDept`、`owner`、`warehouseLayer`、`lifecycleStatus` 等字段。
- `CatalogAssetExtension` 已为 OpenMetadata cache 提供治理覆盖字段和 `governanceStatus`。
- `CatalogAssetMapping` 已有 FQN 到 legacy dataset 的映射基础。
- `asset_grant` / `asset_ownership` / `asset_permission_audit` 已落库。
- `/api/internal/asset-permission/check`、batch-check、accessible-ids、grants 已存在。
- 大屏权限已经走 platform 优先，本地 fallback 已有测试基础。

## 关键缺口

| 缺口 | 严重度 | 说明 | 后续任务 |
|---|---|---|---|
| 缺统一 `asset_type + asset_key + asset_id` | P0 | 现在 UUID、FQN、legacy id、screen id 混用 | F1/T02 |
| 自动创建资产治理状态不硬 | P0 | OpenLineage 自动创建 dataset 未强制 owner/classification/sourceSystem | F1/T03, F3/T01 |
| OM cache 和本地 Catalog 双事实源 | P0 | 资产门户合并展示，但消费侧不知道哪个是事实源 | F1/T04, F2/T02 |
| 列表权限和详情权限不完全等价 | P0 | `CatalogAssetPortalService.listAssets` 先从 OM cache 分页再处理，风险是列表暴露摘要 | F4/T02 |
| asset_grant asset_id 标准不足 | P0 | 字符串 ID 能用，但缺跨模块规范 | F1/T02, F4/T01 |
| semantic 仍在 platform 内部成体系 | P0 | 后续 dts-metrics 会和 platform semantic 双事实源 | F6/T02, Sprint-32 |
| QueryDataset/BI Dataset 未统一资产身份 | P1 | BI 数据集注册和权限需要纳入统一资产 | F1/T02, F2/T02 |
| 治理缺口 API 不完整 | P1 | 页面可展示部分状态，但缺统一报告 | F2/T04 |

## 对后续任务的输入

- F1/T02 应先实现资产身份契约，不急于修改所有消费方。
- F1/T03 应把自动发现和自动创建资产默认状态收紧为 `PENDING_GOVERNANCE`。
- F2/T02 应把资产读取 API 作为 `dts-metrics` 的唯一入口。
- F4/T01 应把权限动作扩展为 READ/PREVIEW/EDIT/PUBLISH/GRANT。
- F6/T02 必须输出 `semantic_*` 到 `metric_*` 的迁移映射，不在 platform 内继续扩展语义事实。
