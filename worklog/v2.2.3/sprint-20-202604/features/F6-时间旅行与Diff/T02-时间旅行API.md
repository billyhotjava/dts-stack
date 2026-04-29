# T02: 时间旅行 API

**优先级**: P2
**状态**: READY
**依赖**: T01

## 目标

让 `/api/catalog/lineage/impact` 支持 `?at=<ISO8601>` 参数，返回该时刻有效的 lineage 快照。

## 技术设计

### API

```
GET /api/catalog/lineage/impact
  ?datasetId=...
  &direction=BOTH
  &depth=3
  &at=2026-04-01T00:00:00Z      # 新增；不传 = 当前
  &withJobs=true
  &includeColumns=false
```

### 服务实现

`CatalogLineageService.computeImpact()`：

```java
Instant atTimestamp = req.at() != null ? req.at() : Instant.now();

// 查询所有在 at 时刻有效的边：
//   valid_from <= at AND (valid_to IS NULL OR valid_to > at)
List<CatalogDatasetLineage> edges = repo.findActiveEdgesAt(datasetId, direction, atTimestamp);
```

Repository 查询：

```sql
SELECT * FROM catalog_dataset_lineage
WHERE (upstream_dataset_id = :id OR downstream_dataset_id = :id)
  AND valid_from <= :at
  AND (valid_to IS NULL OR valid_to > :at)
```

如查询超过 180 天，自动从归档表 union（`catalog_dataset_lineage_archive`）。

### 性能保护

- `at` 在归档表查询时，要求必须有 `datasetId` 限制（不允许全表扫归档）
- 加缓存 key 包含 `at`，旧时间戳的查询缓存可设大 TTL（24h）
- 响应 header 加 `X-Lineage-Snapshot-At: <at>`，便于前端调试

### 边界

- `at` 在最早 lineage 之前 → 返回空图 + warning
- `at` 在未来 → 视作 now()
- 时区：所有时间统一 UTC，前端转本地

## 影响范围

- 修改 `CatalogLineageResource.java`
- 修改 `CatalogLineageService.java`
- 修改 `CatalogDatasetLineageRepository.java`

## 验证

- [ ] 单测：`at` 落在边的 `valid_from < at < valid_to` 区间命中
- [ ] 单测：`at` 在 `valid_to` 之后不命中
- [ ] 单测：`at` 触发归档表查询
- [ ] API 集成测试：构造 3 个时刻的边，分别用 `at` 查询返回对应快照
- [ ] 响应头 `X-Lineage-Snapshot-At` 正确
- [ ] 不传 `at` 行为完全等同于查 `valid_to IS NULL`，与现有响应一致

## 完成标准

- [ ] API 支持 `at` 参数
- [ ] 归档表自动 union
- [ ] 性能达标
- [ ] 单测覆盖时间边界
