# Sprint-31A F1/T02 资产身份规则

**状态**: DONE
**日期**: 2026-05-17

## 目标

把资产身份从各模块自行解释的 UUID、FQN、screen id、semantic model id，收敛为统一的：

```text
asset_type + asset_key + asset_id
```

- `asset_type`: 标准资产类型，面向权限、审计和对外契约。
- `asset_key`: 跨来源可重复生成的稳定业务键。
- `asset_id`: platform 内部 UUID 或存量 ID；当存在时用于 `asset_grant.asset_id`，不存在时暂以 `asset_key` 兜底。

## 第一批资产类型

| asset_type | 用途 | 当前来源 |
|---|---|---|
| DATASET | 表、视图、ODS/DWD/DWS/ADS 数据资产 | CatalogDataset / OpenMetadataAssetCache |
| DBT_MODEL | dbt 模型定义和构建产物 | dbt manifest / dbt release |
| BI_DATASET | BI 可消费数据集 | QueryDatasetAsset |
| SCREEN | 大屏资产 | analytics screen + platform asset_grant |
| METRIC | 指标资产 | Sprint-32 dts-metrics |
| SEMANTIC_MODEL | 旧 platform semantic 模型兼容资产 | `semantic_model` 迁移期 |
| DATA_PRODUCT | 数据产品资产 | CatalogDataProduct |

## key 规则

| 类型 | key 规则 |
|---|---|
| Catalog dataset | `source:{sourceId|unknown}/schema:{schema}/table:{table}` |
| OpenMetadata dataset | `om:{fqn}` |
| dbt model | `dbt:{uniqueId|relationName}` |
| BI dataset | `bi-dataset:{uuid}` |
| Screen | `screen:{id}` |
| Metric | `metric:{packId|local}/{metricCode}` |
| Semantic model | `semantic-model:{idOrCode}` |

所有 key segment 统一小写，空白和非安全字符折叠为 `_`。

## 已落地代码

- `CatalogAssetType`: 标准资产类型枚举。
- `CatalogAssetIdentity`: 统一资产身份 record。
- `CatalogAssetKey`: 稳定 key 工厂。
- `CatalogAssetIdentityResolver.resolveIdentity`: 基于当前 OM/cache/legacy 解析结果输出统一资产身份。
- `CatalogAssetKeyTest`: key 规则回归测试，按执行约束暂不运行。

## 风险和后续

- 当前只是契约落地和 resolver 轻接入，尚未修改所有 asset_grant 写入路径。
- OpenMetadata 和 CatalogDataset 映射冲突仍由 F1/T05 dry-run 报告处理。
- `asset_grant.asset_id` 仍兼容历史 ID，统一迁移在 F4/T01 和 F6/T01 中处理。
