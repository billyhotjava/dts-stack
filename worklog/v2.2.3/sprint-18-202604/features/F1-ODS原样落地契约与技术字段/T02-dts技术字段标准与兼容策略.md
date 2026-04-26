# T02: `_dts_*` 技术字段标准与兼容策略

**优先级**: P0  
**状态**: READY  
**依赖**: T01

## 目标

统一 ODS 技术字段，支持血缘排查、批次追踪和文件溯源，同时兼容历史 `source_system/import_time`。

## 范围

- 定义 `_dts_source_system`、`_dts_source_table`、`_dts_import_time`、`_dts_batch_id`、`_dts_execution_id`、`_dts_task_id`。
- 文件源扩展 `_dts_source_file`、`_dts_source_sheet`、`_dts_file_hash`、`_dts_row_number`。
- 定义字段类型、是否可空、默认值来源和写入时机。
- 定义与源字段重名时的 fail-fast 策略。

## 完成标准

- [ ] 新建任务默认使用 `_dts_*` 字段。
- [ ] 旧任务的 `source_system/import_time` 不被破坏。
- [ ] 单测覆盖技术字段生成和重名冲突。
