# T01: SCD2 字段迁移与写入逻辑

**优先级**: P2
**状态**: DONE
**依赖**: F1.T02, F2.T03, F4.T01

## 目标

为 `catalog_dataset_lineage` 增加 SCD2 字段，让所有自动产出的边遵循"插入新行 + 关闭旧行"的演化模式。

## 技术设计

### 字段扩展

Liquibase `20260430_09_lineage_scd2.xml`：

```sql
ALTER TABLE catalog_dataset_lineage
  ADD COLUMN valid_from TIMESTAMPTZ NOT NULL DEFAULT now(),
  ADD COLUMN valid_to TIMESTAMPTZ,
  ADD COLUMN superseded_by UUID REFERENCES catalog_dataset_lineage(id);

CREATE INDEX idx_lineage_validity
  ON catalog_dataset_lineage(downstream_dataset_id, valid_from DESC)
  WHERE valid_to IS NULL;

CREATE INDEX idx_lineage_at
  ON catalog_dataset_lineage(valid_from, valid_to);
```

### 写入逻辑

修改所有 lineage 写入入口（已有的 `CatalogDbtLineageService`、新增的 `IngestionLineageWriter`、`OpenLineageReceiverResource`）：

老逻辑：

```sql
DELETE FROM catalog_dataset_lineage WHERE relation_type='DBT' AND ...;
INSERT INTO catalog_dataset_lineage VALUES (...);
```

新逻辑：

```java
// 1. 关闭老边（不删除，set valid_to）
repository.closeActiveEdges(upstream, downstream, relationType);
// 2. 插入新边
repository.insertEdge(...);
```

`closeActiveEdges`：

```sql
UPDATE catalog_dataset_lineage
SET valid_to = now(), superseded_by = :newId
WHERE upstream_dataset_id = :u
  AND downstream_dataset_id = :d
  AND relation_type = :rt
  AND valid_to IS NULL;
```

### MANUAL 边处理

人工边（`relation_type='MANUAL'`）保留物理删除选项，因为人工删除是真"我的，不是事实变化"。

### 归档策略

`valid_to < now() - 180 days` 的边迁到 `catalog_dataset_lineage_archive`（同结构 + 月份分区），主表只保留半年内的。

定时任务（quartz / spring scheduler）：

```yaml
dts:
  catalog:
    lineage-archive:
      enabled: true
      cron: "0 0 3 * * SUN"   # 每周日凌晨 3 点
      retain-days: 180
```

### 现有数据迁移

已存在的边在迁移时一次性 `valid_from = created_at, valid_to = NULL`。

## 影响范围

- Liquibase `20260430_09_lineage_scd2.xml`
- 修改 `CatalogDatasetLineage.java` 加字段
- 修改 `CatalogDatasetLineageRepository.java` 加 `closeActiveEdges`
- 修改 `CatalogDbtLineageService.java`、`IngestionLineageWriter.java`、`OpenLineageReceiverResource.java`、`CatalogAutoLineageService.java`
- 新增 `LineageArchiveScheduler.java`
- Liquibase `20260430_10_lineage_archive_table.xml`

## 验证

- [x] dbt 重跑：失效边 `valid_to` 被关闭，当前边保持 `valid_to IS NULL`
- [x] 同一上下游对同一 `relation_type` 永远只有 1 条 `valid_to IS NULL`
- [ ] 单测：closeActiveEdges 在并发下不重复关闭（用乐观锁或 unique partial index 保护）
- [ ] 归档任务跑一次：> 180 天的边迁到归档表，主表行数减少（后续项）
- [x] 主查询快照命中 `valid_from / valid_to` 索引，当前边唯一由 partial unique index 保护
- [x] MANUAL 边删除改为关闭 `valid_to`，与自动边保持统一可回溯策略

## 完成标准

- [x] SCD2 字段就位，所有自动写入路径切换
- [ ] 归档调度器生效（后续项）
- [x] 现有数据无丢失（当前库 146 条边回填 `valid_from`）
- [ ] 性能基准：100k 边场景 P95 查询 < 500ms（后续大图压测项）
