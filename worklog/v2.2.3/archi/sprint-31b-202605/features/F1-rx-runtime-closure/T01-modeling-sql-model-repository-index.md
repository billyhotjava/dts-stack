# T01: ModelingSqlModelRepository 索引方法替换全表扫描

**优先级**: P0
**状态**: DONE
**依赖**: 无

## 目标

干掉 `CatalogAssetIdentityResolver.resolveModelingSqlModel` 中的 `sqlModelRepository.findAll().stream().filter(...)` 全表扫描路径，改为 repository 索引方法直接命中。

## 背景

`CatalogAssetIdentityResolver.java:217` 当前实现：

```java
return sqlModelRepository
    .findAll()
    .stream()
    .filter(model -> matches(expected, model.getName()) || matches(expected, model.getAlias()))
    .findFirst()
    .map(this::modelingSqlModelIdentity);
```

resolver 是 metric-pack preview / publish / asset_grant check 的 hot path，模型数线性增长后会出现毫秒到秒级退化，且查询触发 N+1 加载关联实体。

## 技术设计

1. `ModelingSqlModelRepository` 增加：
   ```java
   @Query("select m from ModelingSqlModel m " +
          "where lower(m.name) = lower(:identifier) " +
          "   or lower(m.alias) = lower(:identifier) " +
          "order by m.updatedAt desc")
   Optional<ModelingSqlModel> findFirstByNameOrAliasIgnoreCase(@Param("identifier") String identifier);
   ```
2. `CatalogAssetIdentityResolver.resolveModelingSqlModel` 直接调用上面方法，返回 `Optional.map(...)`。
3. 在 `ModelingSqlModel` 的 `name` / `alias` 字段上确认唯一/普通索引存在；若缺失，在 changelog 中追加。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/modeling/ModelingSqlModelRepository.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/CatalogAssetIdentityResolver.java`
- `source/dts-platform/src/main/resources/config/liquibase/changelog/` 新 changelog（如索引缺失）

## 验证

- [x] 新增/更新 `CatalogAssetIdentityResolverTest.resolvesModelingSqlModelByName`，验证 repository 索引方法并断言不调用 `findAll`。
- [x] 增加 alias 命中测试，避免回退全表扫描。
- [ ] `EXPLAIN` 验证索引命中（如执行环境允许）。

## 完成标准

- [x] `CatalogAssetIdentityResolver` 内不再出现 `sqlModelRepository.findAll()`。
- [x] focused 单测已绿；10k model 性能回归留给最终 IT。
