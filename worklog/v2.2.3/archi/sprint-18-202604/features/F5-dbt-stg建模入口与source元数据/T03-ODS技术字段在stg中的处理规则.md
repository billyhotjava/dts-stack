# T03: ODS 技术字段在 stg 中的处理规则

**优先级**: P1  
**状态**: DONE
**依赖**: F1/T02

## 目标

明确 `_dts_*` 技术字段在 stg、dwd、dws、ads 中的保留和使用规则。

## 范围

- stg 默认保留 `_dts_batch_id/_dts_execution_id/_dts_import_time`。
- 业务指标不直接基于技术字段计算，除非做运行质量或数据新鲜度指标。
- dwd 视业务需要保留 `source_system/source_table`。
- ads 默认不暴露技术字段给业务用户。

## 完成标准

- [x] 技术字段处理规则写入建模规范。
- [x] stg 示例包含技术字段注释。
- [x] dbt tests 不把技术字段误判为业务必填。

## 处理规则

| 字段 | stg 默认策略 | DWD/DWS/ADS 策略 |
|---|---|---|
| `_dts_batch_id` | 保留，用于追溯批次 | DWD 可保留；DWS/ADS 默认隐藏 |
| `_dts_execution_id` | 保留，用于追溯执行记录 | DWD 可保留；DWS/ADS 默认隐藏 |
| `_dts_import_time` | 保留，用于 freshness | DWD 可保留；DWS/ADS 只在运维指标中使用 |
| `_dts_source_system` | 保留或映射为 `source_system` | DWD 可保留 |
| `_dts_source_table` | 保留或映射为 `source_table` | DWD 可保留 |
| `_dts_source_file/_dts_source_sheet/_dts_file_hash/_dts_row_number` | 文件源保留 | 仅排障链路保留 |

dbt tests 默认只对业务主键、必填字段、枚举和外键配置，不对 `_dts_*` 技术字段配置业务 `not_null`。`source freshness` 优先使用 `_dts_import_time`，如果源端有可靠更新时间字段，可在 stg YAML 中覆盖。
