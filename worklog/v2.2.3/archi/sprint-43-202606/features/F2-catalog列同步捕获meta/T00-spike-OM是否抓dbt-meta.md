# T00: spike — OM 是否已抓 dbt meta（前置）

**优先级**: P0
**状态**: READY
**依赖**: 无（全 sprint 前置）

## 目标
确定 dbt 列 `meta.semantic_type` 等是否已被 OM 的 dbt ingestion 抓进 OpenMetadata（作 tag / custom property），以决定 F2-T02 的实现路线。

## 技术设计
- 查 `dts-ingestion` / `dts-platform` 的 OM 集成（`OpenMetadataClient`、`om_column_cache` 的 tags_json/属性字段）。
- 查 `dts-openmetadata-ingestion` 的 dbt ingestion 配置（services/dts-airflow* + compose），确认是否启用 dbt meta → OM tag/property。
- 实测：对一个带 `meta.semantic_type` 的列，查 OM API（或 om_column_cache）看是否有该属性。

## 路线分支（产出结论）
- **A. OM 已抓** → F2-T02 从 `om_column_cache`（OM 镜像）读 semantic_type/standard_code，最省、与现有同步一致。
- **B. OM 未抓** → F2-T02 直 parse dbt schema.yml（经 `DbtFileService` 读 services/dts-dbt/models/**/schema.yml），或扩 OM ingestion（重，尽量避免）。

## 影响范围
- 仅调研，无代码改动。

## 验证
- [ ] 给出明确结论（A/B）+ 证据（OM 字段截图/查询结果）。

## 完成标准
- [ ] F2-T02 实现路线确定并记录在本任务。
