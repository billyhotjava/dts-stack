# T03: dbt manifest compiled_sql 集成

**优先级**: P1
**状态**: READY
**依赖**: T02

## 目标

把 T02 的列级解析器接到现有的 `DbtAssetSyncService`：每次 dbt artifact 同步时，对 model 的 `compiled_sql` 跑一次列级解析，写入 `catalog_dataset_lineage_column`。

## 技术设计

### 入口

`DbtAssetSyncService.syncFromManifest()` 现有的 model 循环里，新增一段：

```java
for (DbtNode model : manifest.nodes()) {
    if (!isModel(model)) continue;
    syncModelDatasetLineage(model);   // 现有逻辑
    if (settings.columnLineageEnabled()) {
        syncModelColumnLineage(model);  // 新增
    }
}

private void syncModelColumnLineage(DbtNode model) {
    if (model.compiledSql() == null) return;
    Map<String, List<String>> upstreamSchemas = buildUpstreamSchemas(model.dependsOn());
    ColumnLineageGraph graph = extractor.extract(model.compiledSql(), inferDialect(model), upstreamSchemas);
    persist(graph, model);
}
```

### 上游 schema 构建

调用 `CatalogDatasetColumnRepository.findByDatasetId(...)` 拿上游表的列清单，喂给 extractor。

### 持久化

- 找到对应 `catalog_dataset_lineage` 父边（DBT 类型）
- 删除该父边下所有 `relation_type=DBT, valid_to IS NULL` 的列边（视为本次重建）
- 插入新解析的列边

### 配置

```yaml
dts:
  catalog:
    column-lineage:
      enabled: ${DTS_COLUMN_LINEAGE_ENABLED:true}
      max-columns-per-model: ${DTS_COLUMN_LINEAGE_MAX_COLS:200}   # 超出跳过避免风险
      sql-parse-timeout-ms: 3000
```

### 失败兜底

- model 解析失败：保留 dataset 级边，跳过列级；写日志
- `compiled_sql` 缺失：dbt 未跑 compile，跳过且记 sync state

### `DbtArtifactSyncState` 扩展

加 2 个字段：

| 列 | 说明 |
|---|---|
| `column_lineage_synced_at` | 最近一次列级同步时间 |
| `column_lineage_failures` | 失败 model 数 |

## 影响范围

- 修改 `dts-platform/.../service/etl/DbtAssetSyncService.java`
- 修改 `dts-platform/.../domain/etl/DbtArtifactSyncState.java`
- 修改 `dts-platform/.../service/etl/CatalogDbtLineageService.java`
- Liquibase `20260430_06_dbt_artifact_state_columns.xml`
- `application.yml` 增配置

## 验证

- [ ] 单测：`DbtAssetSyncServiceTest` 新增 column lineage 同步用例（mock manifest）
- [ ] 集成测试：用真实 dbt 项目 fixture（`source/dts-platform/src/test/resources/dbt-fixtures/`）跑一次同步
- [ ] 验证：100 model 项目同步 < 60s
- [ ] 重复跑两次：列边数量稳定（重建逻辑无副作用）
- [ ] 关闭 `dts.catalog.column-lineage.enabled=false` 时跳过列级，dataset 级仍工作

## 完成标准

- [ ] dbt 同步集成完成
- [ ] sync state 字段已扩展
- [ ] 单测+集成测试通过
- [ ] 配置开关有效
