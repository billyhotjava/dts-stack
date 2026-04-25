# T03: Resource 到 ODS 表映射契约

**优先级**: P0
**状态**: DRAFT
**依赖**: T02

## 目标

定义一个 API 数据源下多个 endpoint/resource 如何映射到 ODS 表，并与现有 tableMapping、dbt source、目录同步兼容。

## 范围

- 定义 resource id、display name、path、target table、primary key。
- 支持一任务单 resource 和一任务多 resource 的边界决策。
- 定义 ODS 表命名规则：`ods_api_<source>_<resource>` 或租户可配置前缀。
- 定义字段增删改的 mapping version。

## 完成标准

- [ ] ODS 表名稳定、可读、可避免冲突。
- [ ] tableMapping 能表达 API resource 来源。
- [ ] 后续 OdsTableMappingSyncService 可复用。
- [ ] 多 resource 的失败隔离策略明确。

