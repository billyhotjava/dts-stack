# T02: dbt 导入按 schema+表名匹配并返回跳过原因

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标

`POST /api/catalog/lineage/import-dbt-manifest` 不再因跨 schema 同名表而错连血缘，并返回可定位的跳过原因分桶与未匹配模型明细，使「血缘导入」页能回答"为什么只导入了 3 条"。

## 技术设计 (Contract-first)

### 缺陷定位（账本#19）

`CatalogDbtLineageService.java`：

| 行 | 问题 |
|---|---|
| `:61-67` | `tableIndex` 只按 `hiveTable`（单列）建索引，**忽略 schema** |
| `:110` | 下游用 `tableIndex.get(modelName.toLowerCase())` 匹配 |
| `:121` | 上游取 `unique_id` 最后一段（`source.proj.raw.orders` → `orders`）后同样按表名匹配 |
| `:70` | `lineageRepo.findAll()` 把全量血缘拉进内存 |
| `:88/:97/:105/:112` | 四处 `skipped++` 原因完全不同，对外只暴露一个合计数 |

### 输入契约

`MultipartFile manifest`（不变）。新增可选查询参数 `defaultSchema`（string，缺省 null）——当 manifest 节点未提供 `schema` 时的兜底 schema。

### 输出契约

```jsonc
{
  "created": 12,
  "skipped": 7,
  "total": 40,
  "propagationEnqueued": 5,
  "skippedReasons": {           // 四类互斥，合计 == skipped
    "notModel": 3,              // key 不以 model. 开头（原 :83 continue，不计入 skipped，本次改为计入）
    "malformedNode": 1,         // node/depends_on/nodes 结构非法（原 :88/:97/:105）
    "unmatchedModel": 2,        // 下游模型在 catalog 中找不到（原 :112）
    "unmatchedParent": 1        // 父节点找不到（原 :123 continue，此前完全不计数）
  },
  "unmatched": [                // 上限 200 条
    { "uniqueId": "model.proj.dim_user", "name": "dim_user", "schema": "dwd", "reason": "unmatchedModel" }
  ],
  "truncated": false
}
```

> 注意：`notModel` 与 `unmatchedParent` 此前不计入 `skipped`，本次纳入后 `skipped` 数值会变大。这是口径修正，需在 IT-02 中记录并在 UI 文案上说明分桶含义。

### 匹配规则修正

1. 索引改为 `(lower(hive_database), lower(hive_table)) -> dataset_id` 复合键；同时保留 `lower(hive_table) -> Set<dataset_id>` 作为**降级索引**。
2. 下游解析：取 manifest 节点的 `schema` 字段（`CatalogDbtLineageService.java:196` 已在读它用于 detailPayload）+ `name`，优先走复合键。
3. 复合键未命中时走降级索引：
   - 命中**唯一**一条 → 采用，并在 `unmatched` 之外记 `ambiguityResolved` 计数（仅日志，不进响应）；
   - 命中**多条** → 判为 `unmatchedModel`/`unmatchedParent`，写入 `unmatched` 明细并附 `reason: "ambiguousTableName"` 子因，**绝不随机选一条**。
4. 上游解析：`unique_id` 形如 `model.<project>.<name>` / `source.<project>.<source_name>.<table>`，按段数区分后取 name，再走同一套匹配。

### 全表加载修正

`:70` 的 `lineageRepo.findAll()` 改为：先算出本次 manifest 解析得到的候选 `downstreamId` 集合，再用既有 `findCurrentByDownstreamDatasetIdAndRelationTypeIgnoreCase`（`CatalogDatasetLineageRepository.java:56`，账本#8 同文件）批量查，或新增 `findByDownstreamDatasetIdInAndRelationTypeIgnoreCaseAndValidToIsNull`。

### 错误路径

- manifest 非 JSON / `nodes` 非对象 → 既有 `IllegalArgumentException`，保留（400）。
- `unmatched` 超 200 条 → 截断并置 `truncated: true`，`skippedReasons` 计数仍为全量真值。
- 全部模型都未匹配 → `created: 0`，不抛异常，由 UI 呈现为可诊断的空结果。

### 复用点

- 复用既有 `CatalogDatasetLineageRepository` 查询方法，不新增表、不加迁移。
- `upsertDbtJob`（`:176`）逻辑不变。

## UI 交互规格

本 Task 不改 UI。响应体的消费方为 F3/T01→F3/T02（采集运营台的「未匹配明细」表）。在 F3 落地前，`LineageImportPage` 现有 toast 仍只显示 `created/skipped`，不报错。

## 影响范围

| 文件 | 改动 |
|---|---|
| `dts-platform/.../service/catalog/CatalogDbtLineageService.java` | 索引结构、匹配规则、跳过原因分桶、消除 `findAll()` |
| `dts-platform/.../repository/catalog/CatalogDatasetLineageRepository.java` | 可能新增按 downstream 集合的批量查询 |
| `dts-platform/.../web/rest/CatalogLineageResource.java` | `import-dbt-manifest` 端点透传新响应体（约 `:1320` 附近） |
| `dts-platform/src/test/.../service/catalog/CatalogDbtLineageServiceTest.java` | 扩展 |

无迁移、无前端强制改动。

## 验证 (RED→GREEN)

- [ ] `import_matchesBySchemaAndTable`：两个 schema 下同名表 `dwd.orders` / `ods.orders`，manifest 指向 `dwd` → 只连 `dwd.orders`
- [ ] `import_ambiguousTableName_isSkippedNotGuessed`：manifest 节点无 schema 且表名在两个 schema 下都存在 → 判 unmatched，**不随机连边**
- [ ] `import_reportsSkippedReasonBuckets`：构造四类各 ≥1 个节点，断言分桶计数正确且合计 == `skipped`
- [ ] `import_unmatchedDetailIsLocatable`：断言 `unmatched[].uniqueId` 可回指 manifest 节点
- [ ] `import_truncatesUnmatchedOver200`：断言 `truncated == true` 且计数仍为真值
- [ ] `import_doesNotLoadAllLineage`：以 Repository mock 断言未调用 `findAll()`
- [ ] 既有 `CatalogDbtLineageServiceTest` 全绿

## Definition of Done

- [ ] 架构：上述 6 条契约测试绿；无迁移
- [ ] UI：不适用（消费方为 F3/T02）
- [ ] 切片：IT-02 用真实 manifest 导入一次，记录分桶结果与未匹配明细
- [ ] 无占位证据
