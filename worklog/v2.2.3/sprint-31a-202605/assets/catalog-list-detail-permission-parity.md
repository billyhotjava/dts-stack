# Sprint-31A F4/T02 Catalog 列表/详情权限一致性

## 目标

资产列表、详情、schema contract 和 lineage 必须使用同一可见性判断，避免列表暴露无权资产摘要，详情再返回 404/403。

## 当前落地

`CatalogAssetPortalService.listAssets` 在生成摘要前复用详情侧 `canRead(extension, legacy, activeDept)`：

- OpenMetadata cache 资产：先解析 extension / legacy 映射，再做可见性判断。
- DTS legacy Catalog 资产：分页候选进入摘要前同样调用 `canRead(null, dataset, activeDept)`。
- 不可见资产不进入 `content`，也不计入返回 `total`，避免通过总数泄露资产存在性。

## 权限来源

| 资产来源 | 判断 |
|----------|------|
| OpenMetadata + legacy dataset | `AccessChecker.canRead(legacy)` + `departmentAllowed(legacy, activeDept)` |
| OpenMetadata extension only | 使用 extension 组装 synthetic dataset，再复用 `AccessChecker` |
| DTS legacy dataset | 直接复用 `AccessChecker` |

## 验证

新增单测：

- `listAssets_shouldHideOpenMetadataSummaryWhenDetailWouldDeny`
- `listAssets_shouldHideLegacySummaryWhenDetailWouldDeny`

## 注意

当前实现优先保证“不泄露摘要”和“列表详情一致”。由于权限过滤发生在分页候选之后，`total` 表示本次返回的可见数量，不再表示底层未过滤候选总数。后续如需严格分页总数，需要把密级、归属和 asset_grant 条件下推到 repository specification。
