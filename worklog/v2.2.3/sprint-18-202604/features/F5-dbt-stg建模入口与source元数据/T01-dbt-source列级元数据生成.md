# T01: dbt source 列级元数据生成

**优先级**: P1  
**状态**: READY  
**依赖**: F1/T04

## 目标

增强 `ods_sources.yml`，让 ODS source 不只是表清单，也能承载列级 metadata。

## 范围

- 从 schema snapshot 读取列名、类型、是否技术字段。
- 输出 source table columns。
- 技术字段增加 `meta.dts_technical=true`。

## 完成标准

- [ ] `DbtSourceService` 支持列级输出。
- [ ] dbt parse 能通过。
- [ ] 单测覆盖表级和列级 source 生成。
