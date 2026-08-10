# F1: 元数据来源身份契约收敛

**优先级**: P0
**状态**: IN_PROGRESS
**价值**: 消除 CATALOG_TABLE 在来源解析、编译和分类门禁中的 ID 语义冲突，让同一来源在所有消费链上指向同一资产。

## Contract-first

| 名称 | 类型 | 唯一含义 |
|---|---|---|
| `sourceId` / `locator.assetId` | UUID string | `catalog_table_schema.id`，仅作技术 locator |
| `assetType` | enum | `DATASET` |
| `assetKey` | string | 由 table.parent dataset 生成的 canonical `CatalogAssetKey` |
| `sourceVersion` | hash | canonical table schema fingerprint |
| `sourceBindingId` | UUID | `modeling_warehouse_plan_source.id`，ModelSpec 的稳定引用 |

### 错误语义

- table 不存在/harvest stale：`MISSING`，不得回退按 dataset ID 猜测。
- 无目录权限：`FORBIDDEN`，不得泄露 displayName/schema。
- locator 格式错误：`PROVIDER_ERROR` 或既有稳定 invalid-locator code。
- resolvedVersion 不一致：`STALE`，编译/发布 fail closed。

## Task

| Task | 状态 | 依赖 |
|---|---|---|
| T01-锁定来源定位与资产身份契约 | DONE | G0 本地基线 |
| T02-修复CATALOG_TABLE跨层解析 | IN_PROGRESS | T01 RED tests |

## 风险与边界

`CatalogDataset` GitNexus 影响为 CRITICAL，`CatalogTableSchema` 为 HIGH。本 Feature 不改实体主键或表结构；只在 port/query/gate seam 收敛转换。每个生产 symbol 编辑前仍须重新运行 upstream impact，并在 HIGH/CRITICAL 时停下告警。

## DoD

- [ ] 一个 table locator 在 resolver、compiler、classification 三处解析为同一 dataset asset key
- [ ] 不新增身份字段或迁移；旧 locator fixture 兼容
- [ ] 无权限、缺失、错误 ID、跨租户和 version stale 全部 fail closed
- [ ] F1 相关测试在集中验证批次通过
