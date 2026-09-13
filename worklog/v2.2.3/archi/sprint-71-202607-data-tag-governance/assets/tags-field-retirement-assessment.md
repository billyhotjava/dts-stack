# `CatalogDataset.tags` 字段下线评估

## 结论

`CatalogDataset.tags` 在 Sprint-71 **不能下线，也不能由结构化打标反向覆盖**。

除旧标签展示与模糊检索外，当前代码还把该字段用作 dbt/API 落地资产的
机器证据载体。直接删除、清空或把结构化标签回写为逗号文本，会破坏资产
时效判定、API 执行顺序校验和现有 API/UI 契约。

当前运行库有 `503` 条 dataset，非空白 `tags` 为 `0`；这只说明当前环境没有
待迁移样本，不代表字段已无代码依赖。统计证据见
`tags-field-live-probe-20260725.md`。

## 当前消费点

### 1. 数据模型与写入口

| 消费点 | 当前用途 | 下线前迁移方案 |
| --- | --- | --- |
| `domain/catalog/CatalogDataset.java:48` | `varchar(1024)` 持久字段 | 保留到所有读写方迁移完成后再单独做 schema 变更 |
| `web/rest/catalog/CatalogDatasetResource.java:610` | 资产画像更新直接写入旧字段 | 前端停止编辑旧字段后移除该赋值；结构化打标只写 `catalog_asset_tag` |
| `pages/catalog/AssetDetailPage.tsx:392,447,462,474,715` | 加载、比较、提交并展示“标签（逗号分隔）” | 改为结构化 `AssetTagPanel`；旧文本仅以只读“历史标签文本”展示 |

### 2. API 响应、搜索与快照

| 消费点 | 当前用途 | 下线前迁移方案 |
| --- | --- | --- |
| `service/catalog/CatalogMetadataService.java:191,238,253` | OpenMetadata 本地响应、列表摘要与关键词匹配 | 对外契约完成版本化通知；关键词路径迁到结构化标签，旧字段在兼容窗口保留 |
| `service/catalog/CatalogAssetPortalService.java:502` | legacy dataset 关键词 `LIKE` | Sprint-71 保留，与精确 `tagIds` AND 查询并存；稳定一个发布周期后再移除 |
| `web/rest/CatalogSearchResource.java:115,376` | 搜索匹配及 dataset 响应中的 `tags` 字符串 | 新增 `assetTags`，保留旧 `tags`；调用方迁移后才可删除 |
| `web/rest/catalog/CatalogResourceHelper.java:219,322,334,435` | dataset DTO、筛选、关键词与审计快照 | 结构化标签不能复用 `tags` 字段名；旧审计快照仍需保留原值 |
| `api/platformApi.ts:61` | 技术元数据摘要的 `tags?: string` 契约 | 新增结构化字段，兼容窗口后另行移除字符串属性 |

### 3. 机器证据载体

这组依赖使字段下线风险高于普通 UI 标签迁移：

| 消费点 | 当前用途 | 下线前迁移方案 |
| --- | --- | --- |
| `service/etl/DbtAssetSyncService.java:825,1265,1303,1356` | 写入、解析并更新 JSON 形式的 dbt materialization/run/stale 证据 | 新建专用 dbt 物化证据列或表，先回填并双读校验；不可迁入数据标签目录 |
| `service/etl/OdsTableMappingSyncService.java:259,263,590` | 写入并解析 `key=value;...` 的 API 执行证据 | 迁入专用 API landing execution evidence 存储；保持执行序列与身份校验 |
| `service/modeling/warehouse/SourceReferenceResolverAdapter.java:229-245` | 从 table/dataset tags 解析 API 指纹证据 | 改读上述专用 evidence 存储并补等价指纹回归 |
| `service/infra/JdbcCatalogSyncService.java:250,283` | 目录同步时保留并恢复旧值 | 专用 evidence 存储完成后删除透传 |
| `service/infra/InceptorCatalogSyncService.java:231,263` | 目录同步时保留并恢复旧值 | 同上 |
| `service/infra/PostgresCatalogSyncService.java:195,227` | 目录同步时保留并恢复旧值 | 同上 |
| `service/catalog/DatasetJobService.java:206` | 同步/任务路径保留旧值 | 明确其业务属性后迁入 evidence 或历史文本专用字段 |

因此存量迁移工具必须把 JSON 对象和已知 `key=value` 证据格式视为非人工标签
文本，列入受保护/待处理清单，不能按逗号、分号拆分后尝试自动建关系。

### 4. 前端与其他模块扫描

- `dts-platform-webapp` 有上述资产详情编辑合同；Sprint-71 需要新增结构化标签
  交互，但不能静默删除旧字段。
- `dts-analytics-webapp/modern` 当前检出目录没有应用源码，扫描未发现直接消费点；
  后续正式下线前仍需对实际交付制品和对外 API 消费方复查。
- 仓库中大量其他实体也名为 `tags`（指标、术语、SQL 模型、表/列、编排等），
  它们不是 `CatalogDataset.tags`，下线时不得用全局替换处理。

## Sprint-71 过渡期规则

1. 新增、取消和批量打标只修改 `catalog_asset_tag`，不回写
   `CatalogDataset.tags`。
2. 响应中结构化标签使用独立的 `assetTags` 字段；旧 `tags` 保持字符串类型。
3. 精确标签筛选使用关系表 AND 语义；旧关键词/`LIKE` 搜索继续可用。
4. 迁移 execute 只新增带批次证据的关系，绝不修改旧字段；rollback 只删除该批次
   创建的关系。
5. 机器证据格式不自动迁移为业务标签。

## 后续下线门禁

只有以下条件全部满足后，才可在后续 Sprint 立项删除字段：

- 现网迁移已执行，未命中、歧义和受保护机器证据清单均已人工处置；
- dbt 与 API landing 机器证据已迁入专用存储，并完成回填、双读比对和回归；
- 平台 API、平台前端、分析前端及外部消费者均已完成契约迁移通知；
- `CatalogMetadataService`、资产门户、数据搜索和同步链不再读取或写入旧字段；
- 结构化标签检索已稳定运行至少一个完整发布周期；
- 再次全仓扫描和真实流量/审计核对均无旧字段依赖。

建议时间点：**不早于 v2.2.3 之后一个完整稳定发布周期**，并以专用机器证据
迁移完成为硬门禁，而不是按固定日期强删。
