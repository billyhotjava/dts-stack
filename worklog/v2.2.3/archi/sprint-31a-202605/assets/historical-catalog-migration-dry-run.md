# 历史 Catalog 迁移 Dry-run

**Sprint**: Sprint-31A
**Feature**: F6/T01
**状态**: DONE

## 目标

在迁移历史 Catalog、OpenMetadata cache、BI Dataset 等资产之前，先通过只读 dry-run 输出候选资产身份、已有映射数量和冲突数量，不执行破坏性写入。

## 已落地入口

```text
GET /api/catalog/assets-v2/migration/dry-run
```

访问要求：

- 仅资产维护角色可访问。
- 只读事务。
- 记录审计动作 `CATALOG_ASSET_MIGRATION_DRY_RUN`。

## 输出内容

| 字段 | 含义 |
|---|---|
| `candidateCount` | 从历史来源生成的候选资产数量 |
| `existingMappingCount` | 当前 `catalog_asset_mapping` 已存在映射数量 |
| `conflictCount` | 相同 `assetType + assetKey` 的冲突数量 |
| `candidates` | 候选资产明细 |
| `conflicts` | 冲突明细 |

## 当前覆盖来源

| 来源 | 目标资产类型 | key 规则 |
|---|---|---|
| `CatalogDataset` | `DATASET` | `CatalogAssetKey.dataset` |
| `OpenMetadataAssetCache` | `DATASET` | `CatalogAssetKey.openMetadataDataset` |
| `QueryDatasetAsset` | `BI_DATASET` | `CatalogAssetKey.biDataset` |

## 迁移原则

1. dry-run 只输出风险，不写入、不删除、不自动合并。
2. 冲突必须人工确认后才能生成最终迁移脚本。
3. 缺治理字段的历史资产不自动变成 `ACTIVE`，只能进入待治理状态。
4. 迁移报告是 Sprint-31/32 的输入，不能替代最终 IT 验证。

## 最终验证

本阶段按用户约束不运行中间编译和接口测试。最终统一测试阶段需要执行：

```text
curl -fsS http://127.0.0.1:{platform-port}/api/catalog/assets-v2/migration/dry-run
```

并归档返回的 `candidateCount/conflictCount`。
