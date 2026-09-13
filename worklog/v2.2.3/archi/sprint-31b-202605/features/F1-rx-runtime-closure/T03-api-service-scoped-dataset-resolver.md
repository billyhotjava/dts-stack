# T03: IdentityResolver 接入 API_SERVICE / scopedDataset 反向解析

**优先级**: P0
**状态**: DONE
**依赖**: T01

## 目标

把 `CatalogAssetIdentityResolver` 从当前 5 种 code asset 扩展为覆盖 `API_SERVICE` 与 `scopedDataset(...)` 形态的反向解析，使数据服务资产与 tenant/env/dialect scoped 数据集都能回到同一 `asset_type + asset_key + asset_id` 契约。

## 背景

Sprint-31A RX/T04 已闭环 `GLOSSARY_TERM / DATA_STANDARD / GOV_INDICATOR / MODELING_SQL_MODEL / METRIC_PACK`。但：

- `SvcApi*` 暴露的数据服务资产在事实源里仍为空白，metric-pack / analytics 想要引用 API_SERVICE 作为来源时拿不到 asset identity。
- `CatalogAssetKey.scopedDataset(tenant, env, dialect, naturalKey)` 是契约里声明的 scoped key，但 resolver 没有反向解析能力，同名不同 tenant/env/dialect 的资产无法区分。

## 技术设计

1. resolver 注入 `SvcApiDefinitionRepository`（或等价 repository），新增 `resolveApiService(String code)`：
   ```java
   private Optional<CatalogAssetIdentity> resolveApiService(String code) {
       return apiServiceRepository.findFirstByCodeIgnoreCase(code)
           .map(api -> codeAssetIdentity(
               CatalogAssetType.API_SERVICE,
               firstText(api.getCode(), idText(api.getId())),
               idText(api.getId()),
               "api-service:" + api.getCode()
           ));
   }
   ```
2. `typeHint` 增加分支：`"api_service", "svc_api" -> resolveApiService(naturalKey)`。
3. 增加 `resolveScopedDataset(typeHint, naturalKey)` 处理 `tenant:env:dialect:naturalKey` 格式 ref；解析失败返回 `Optional.empty()` 并交 T05 审计。
4. `resolveCodeAssetById(UUID)` 同步追加 `SvcApi*` 查询。
5. `CatalogAssetIdentity` 返回的 `assetKey` 必须经过 `CatalogAssetKey.codeAsset(...)` 或 `scopedDataset(...)`，不要手拼字符串。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/CatalogAssetIdentityResolver.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/CatalogAssetKey.java`（确认 scopedDataset / codeAsset 工厂存在；如缺补齐）
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/services/`（API service repository）
- 任何已经通过 `resolver.resolveIdentity(...)` 引用 API service / scoped dataset 的调用方

## 验证

- [x] `CatalogAssetIdentityResolver` 已接入 `API_SERVICE` / `SvcApiRepository`。
- [x] `CatalogAssetIdentityResolver` 已识别 `CatalogAssetKey.scopedDataset(...)` 形态。
- [x] focused resolver 测试已覆盖当前 scopedDataset 边界。

## 完成标准

- [x] resolver 覆盖 DATASET / GLOSSARY_TERM / DATA_STANDARD / GOV_INDICATOR / MODELING_SQL_MODEL / METRIC_PACK / API_SERVICE。
- [x] scopedDataset 反向解析单测覆盖 tenant/env/dialect 不互相冲突。
