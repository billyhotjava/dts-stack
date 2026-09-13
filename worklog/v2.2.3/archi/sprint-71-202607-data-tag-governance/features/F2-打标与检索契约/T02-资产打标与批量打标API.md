# T02: 资产打标与批量打标 API

**优先级**: P1
**状态**: READY
**依赖**: T01

## 目标

提供对任意 `CatalogAssetType` 资产的打标、取消打标与批量打标能力，这是协议「对数据资源打标」的核心落地。

## 技术设计

### 打标锚点

复用既有资产标识体系，**不为 dataset 单独建关系表**：

- `CatalogAssetType`（`service/catalog/CatalogAssetType.java`，20 个枚举值）标识资产类型
- `CatalogAssetKey`（`service/catalog/CatalogAssetKey.java`）生成资产唯一标识字符串
- 二者组合写入 `catalog_asset_tag.asset_type` + `asset_key`

这样 dataset、dbt_model、metric、data_product、screen 等全部资产类型天然可打标，无需逐类扩展。

### 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/catalog/asset-tags?assetType=&assetKey=` | 查询单个资产的全部标签 |
| POST | `/api/catalog/asset-tags` | 打标（单资产多标签） |
| DELETE | `/api/catalog/asset-tags` | 取消打标 |
| POST | `/api/catalog/asset-tags/batch` | 批量打标（多资产 × 多标签） |

### 服务实现

扩展 `CatalogTagService` 或新建 `CatalogAssetTagService`：

- `tagAsset(assetType, assetKey, tagIds)` — 幂等：已存在的关联跳过，不抛错
- `untagAsset(assetType, assetKey, tagIds)`
- `batchTag(List<AssetRef>, tagIds)` — 单事务，部分失败整体回滚，返回逐项结果
- 校验：标签必须存在且 `enabled=true`；停用标签不可用于新增打标
- 校验：`assetType` 必须是合法枚举值，非法值返回 4xx 而非 500

### 批量打标规模约束

批量接口须设上限（建议单次 ≤ 500 个资产），超限返回明确错误，避免无界写入。

## 影响范围

- 新增 `.../service/catalog/CatalogAssetTagService.java`
- 修改或新增 `.../web/rest/catalog/CatalogTagResource.java` 打标端点
- 依赖 `CatalogAssetType`、`CatalogAssetKey`（只读复用，不修改）

## 验证

- [ ] RED：先写 `CatalogAssetTagServiceIT` + Resource IT，断言下列场景后运行应失败
- [ ] 单资产打多标签成功，关联表记录正确
- [ ] 重复打标幂等，不产生重复行、不抛错
- [ ] 取消打标后关联行被删除
- [ ] 批量打标单事务：其中一项非法时整体回滚
- [ ] 批量超过 500 条返回 4xx
- [ ] 停用标签打标被拒绝
- [ ] 非法 `assetType` 返回 4xx 而非 500
- [ ] 对非 dataset 资产（如 METRIC、DATA_PRODUCT）打标同样成功

## 完成标准

- [ ] 4 个端点可用，幂等与事务语义正确
- [ ] 打标覆盖全部 `CatalogAssetType`，有非 dataset 类型的 IT 证据
- [ ] 批量规模有上限保护
