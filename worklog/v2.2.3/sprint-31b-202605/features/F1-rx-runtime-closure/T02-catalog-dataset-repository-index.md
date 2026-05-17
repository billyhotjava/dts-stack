# T02: CatalogDatasetRepository policy hot-path 索引方法

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标

干掉 `AssetPermissionInternalResource.resolveDataset` 中的 `datasetRepository.findAll().stream().filter(...)` 全表扫描；该方法是 `/api/internal/asset-permission/policy` 的 hot path，dts-metrics 每次 artifact preview 都会调用，必须索引化。

## 背景

`AssetPermissionInternalResource.java:123` 当前实现：

```java
return datasetRepository
    .findAll()
    .stream()
    .filter(dataset -> equalsIgnoreCase(table, dataset.getHiveTable()) || equalsIgnoreCase(table, dataset.getName()))
    .findFirst();
```

dataset 表通常上千到上万行，policy endpoint 在 metric-pack 发布、preview、analytics 大屏权限校验等多个链路被调用，全表扫直接坍塌生产链路 latency。

## 技术设计

1. `CatalogDatasetRepository` 增加：
   ```java
   @Query("select d from CatalogDataset d " +
          "where lower(d.hiveTable) = lower(:identifier) " +
          "   or lower(d.name) = lower(:identifier)")
   Optional<CatalogDataset> findFirstByHiveTableOrNameIgnoreCase(@Param("identifier") String identifier);
   ```
2. `AssetPermissionInternalResource.resolveDataset(AssetRef ref)` 直接调用上面方法；同时支持 `ref.id() != null` 时直接 `datasetRepository.findById(uuid)`，UUID 命中优先。
3. 确认 `catalog_dataset.hive_table` 和 `catalog_dataset.name` 上有 index（或唯一约束）；缺失则补 liquibase changelog。
4. 同样的全表扫如果在 `ScreenPermissionService` / `AnalyticsAssetGrantSyncService` 等其他地方存在，一并替换（grep 验证）。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/catalog/CatalogDatasetRepository.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/internal/AssetPermissionInternalResource.java`
- 任何其他 `datasetRepository.findAll()` 后过滤的调用点
- `source/dts-platform/src/main/resources/config/liquibase/changelog/`

## 验证

- [ ] 新增 `AssetPermissionInternalResourceTest.policy_resolvesDatasetByHiveTable` / `policy_resolvesDatasetByLogicalName`。
- [ ] `grep -rn 'datasetRepository.findAll()' source/dts-platform/src/main/` 返回 0 命中。
- [ ] 性能：10k dataset 数据集下 policy 调用 < 50ms。

## 完成标准

- [ ] `AssetPermissionInternalResource` 与其他 hot path 不再出现 `datasetRepository.findAll()`。
- [ ] 索引存在性已验证（changelog 或现有约束）。
