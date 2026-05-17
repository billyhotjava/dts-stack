# Sprint-31A F1/T05 历史资产映射和冲突报告

**状态**: DONE
**日期**: 2026-05-17

## 目标

在真正迁移前，先以 dry-run 方式扫描历史资产，输出候选资产身份和冲突，不执行破坏性写入。

## 已落地代码

- `CatalogAssetMappingReportService`
  - 扫描 `CatalogDataset`
  - 扫描 `OpenMetadataAssetCache`
  - 扫描 `QueryDatasetAsset`
  - 使用 `CatalogAssetKey` 生成候选 asset key
  - 输出 candidate count、existing mapping count、conflict count、candidates、conflicts

## 当前覆盖

| 资产来源 | asset_type | key 规则 |
|---|---|---|
| CatalogDataset | DATASET | `source:{sourceId|unknown}/schema:{schema}/table:{table}` |
| OpenMetadataAssetCache | DATASET | `om:{fqn}` |
| QueryDatasetAsset | BI_DATASET | `bi-dataset:{uuid}` |

## 输出语义

```text
candidateCount        候选资产身份数量
existingMappingCount  当前 catalog_asset_mapping 数量
conflictCount         生成后存在相同 asset_type + asset_key 的冲突数量
candidates            候选资产身份列表
conflicts             冲突明细
```

## 后续

- F6/T01 会把该 service 暴露为 dry-run API 或运维脚本，并把结果归档到 IT evidence。
- F6/T02 会补充 `semantic_*` 到 `metric_*` 的映射。
- 当前服务只读，不修改历史数据。
