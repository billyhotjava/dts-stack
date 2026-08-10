# T02: 修复 CATALOG_TABLE 跨层解析

**优先级**: P0
**状态**: DRAFT
**依赖**: F1/T01 RED 证据

## 实现契约

- 在 `CatalogSourceReferenceReadPort` 提供语义明确的 table locator → dataset asset key 查询；保留现有 dataset-id 方法给合法消费者，不做含糊重命名。
- `ModelSpecRepository.findCurrentPhysicalSource` 先连接 `catalog_table_schema`，再沿 `dataset_id` 连接 `catalog_dataset`，由父 dataset 读取库表位置与 warehouse layer。
- `ModelClassificationPublishGate` 对 CATALOG_TABLE 使用 table→dataset resolver，不再把 sourceId 当 datasetId。
- 任何一段无法解析时沿既有 fail-closed blocker 返回；禁止按名称模糊匹配或 fallback 到浏览器 locator。

## 预期生产文件

- `CatalogSourceReferenceReadPort.java`
- `JpaCatalogSourceReferenceReadAdapter.java`
- `ModelSpecRepository.java`
- `ModelClassificationPublishGate.java`

## 数据/兼容

无数据库迁移、无批量回填。既有 `modeling_warehouse_plan_source.source_id` 与 `locator_json.assetId` 原样保留；旧 binding 能读即兼容。

## 验证

- [ ] T01 全部从 RED 转 GREEN
- [ ] `ModelSpecSourceValidationAdapterTest`、WarehousePlan source inventory tests 无回归
- [ ] 分类 subject key 与 resolver 返回 asset key 完全一致
- [ ] SQL 始终带 tenant/plan/binding/version 条件
- [ ] 实施完成后运行 GitNexus detect changes，影响只落在预期 source resolution/compile/classification flows
