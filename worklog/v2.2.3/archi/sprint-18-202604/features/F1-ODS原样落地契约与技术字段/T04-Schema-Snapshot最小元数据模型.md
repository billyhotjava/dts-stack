# T04: Schema Snapshot v1 与 Phase 2 扩展边界

**优先级**: P0  
**状态**: DONE
**依赖**: T01

## 目标

为数据库和文件接入保存 schema 快照，并把它定义为后续 ODS DDL、schema drift 和 dbt source 生成的唯一元数据真源。Phase 1 可先落最小实现，但模型必须预留 Phase 2 所需的完整字段。

## 范围

- 保存 source table/resource、字段名、字段顺序、源类型、目标承载类型、长度、精度、nullable。
- 预留并逐步采集 default value、column comment、primary key、unique index、普通 index。
- 保存 ODS schema/table、ODS 字段名、是否技术字段、来源字段、字段冲突处理结果。
- ODS 建表、schema drift 比对、dbt `ods_sources.yml` 都必须以 snapshot 为输入，不再分别从 reader config 拼装。
- Phase 1 允许先不完整应用 PK/index/comment 到物理 ODS，但必须保存或预留元数据字段，避免 Phase 2 返工。
- 快照与 `IngestionTask`、`IngestionExecution` 建立引用。

## 完成标准

- [x] 自动建表不再只依赖临时 reader config。
- [x] dbt source 可从 snapshot 获取列信息。
- [x] schema drift 后续可基于 snapshot 比对。
- [x] snapshot 模型覆盖字段顺序、nullable、default、comment、PK/index 的存储或预留。
