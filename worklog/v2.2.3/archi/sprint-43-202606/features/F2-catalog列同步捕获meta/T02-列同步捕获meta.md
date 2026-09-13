# T02: 列同步捕获 meta

**优先级**: P0
**状态**: READY
**依赖**: T00, T01

## 目标
按 T00 spike 结论，把 dbt 列 meta（semantic_type/standard_code）填进 `CatalogAssetColumnContract`。

## 技术设计
- **路线 A（OM 已抓）**：`CatalogColumnSyncService` / `OpenMetadataAssetSyncService` 从 `om_column_cache`（tags_json/属性）读 semantic_type/standard_code，写入列契约。
- **路线 B（OM 未抓）**：经 `DbtFileService` 读对应 model 的 schema.yml，parse 列 meta，按 model→表名 关联填充。
- 无 meta 的列 → null（绞杀者：现有 catalog 行为字节不变）。

## 影响范围
- `dts-platform`：`CatalogColumnSyncService` / OM 同步 / `CatalogAssetContractMapper`（依路线）。

## 验证
- [ ] 带 meta 的列同步后契约含 semanticType/standardCode。
- [ ] 无 meta 列回退 null；既有 catalog 同步测试不破。

## 完成标准
- [ ] 捕获贯通、回退安全、单测覆盖，`clean test` 通过。
