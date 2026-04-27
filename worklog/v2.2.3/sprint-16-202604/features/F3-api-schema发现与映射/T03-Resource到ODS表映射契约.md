# T03: Resource 到 ODS 原样落地契约

**优先级**: P0
**状态**: DRAFT
**依赖**: T02

## 目标

定义一个 API 数据源下多个 endpoint/resource 如何原样落到 ODS 表，并与现有 tableMapping、dbt source、schema snapshot、stg 生成和目录同步兼容。

## 范围

- 定义 resource id、display name、path、target table、primary key。
- 支持一任务单 resource 和一任务多 resource 的边界决策。
- 定义 ODS 表命名规则：`ods_api_<source>_<resource>` 或租户可配置前缀。
- 定义 ODS 表固定列：`_dts_raw_record`、`_dts_source_system`、`_dts_source_resource`、`_dts_endpoint`、`_dts_import_time`、`_dts_batch_id`、`_dts_execution_id`、`_dts_page_no`、`_dts_record_no`、`_dts_cursor_value`。
- 定义 schema snapshot version 和 stg mapping version 的关联，不在 ODS mapping 中表达字段增删改。

## 完成标准

- [ ] ODS 表名稳定、可读、可避免冲突。
- [ ] tableMapping 能表达 API resource 来源，但不携带业务字段改写规则。
- [ ] 后续 OdsTableMappingSyncService 可复用，且只同步 raw record + 技术字段。
- [ ] 多 resource 的失败隔离策略明确。
