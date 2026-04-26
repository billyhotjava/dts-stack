# T03: ODS 技术字段在 stg 中的处理规则

**优先级**: P1  
**状态**: READY  
**依赖**: F1/T02

## 目标

明确 `_dts_*` 技术字段在 stg、dwd、dws、ads 中的保留和使用规则。

## 范围

- stg 默认保留 `_dts_batch_id/_dts_execution_id/_dts_import_time`。
- 业务指标不直接基于技术字段计算，除非做运行质量或数据新鲜度指标。
- dwd 视业务需要保留 `source_system/source_table`。
- ads 默认不暴露技术字段给业务用户。

## 完成标准

- [ ] 技术字段处理规则写入建模规范。
- [ ] stg 示例包含技术字段注释。
- [ ] dbt tests 不把技术字段误判为业务必填。
