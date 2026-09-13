# T02：收敛 Catalog 资产身份端口

**优先级**：P0
**状态**：PLANNED
**依赖**：T01

## 目标

让 integration、quality、modeling、materialization 只通过 `CatalogAssetType + CatalogAssetKey` 识别资产，删除字符串拼接和专用资产键路径。

## 技术设计（Contract-first）

- **输入契约**：`CatalogAssetRef {assetType,assetKey,tenantId,externalIdentity?,checksum?}`；tenant 服务端解析。
- **输出契约**：resolve/register 返回唯一 CatalogAssetKey；同 tenant/type/externalIdentity 重放幂等。
- **数据流**：integration identity hint → Catalog port → canonical asset key → quality binding/modeling evidence/materialization result。
- **错误路径**：未知 type、跨租户、ambiguous identity 返回 4xx/typed failure；不降级为原始 string。
- **复用点**：既有 `CatalogAssetKey`、`CatalogAssetType`、identity resolution/audit seam。
- **禁止**：新增 model-specific asset relation table；将 `datasetId` 当跨域通用主键。

## 影响范围

Catalog application port 与各域 adapter；修改 symbol 前逐个跑 GitNexus impact。

## 验证

- [ ] 20 类既有 asset type round-trip（按当前 enum 实际值）。
- [ ] 幂等、ambiguous、unknown、cross-tenant tests。
- [ ] quality/modeling/materialization DTO 只携带 CatalogAssetRef。

## Definition of Done

- [ ] 所有跨域资产引用可解析回唯一 Catalog owner。
- [ ] 无新的平行资产键、表或解析器。
- [ ] identity 失败有分类审计且不泄露跨租户候选。
