# T02: Addax Job 技术字段注入重构

**优先级**: P0  
**状态**: READY  
**依赖**: F4/T01

## 目标

把技术字段写入从零散 postSql 收敛为统一 execution context 驱动，确保 batch、task、execution 一致。

## 范围

- `AddaxJobService` 使用统一 `_dts_*` 技术字段定义。
- 技术字段默认值来自执行上下文，不在 job 内随机生成。
- 保留历史 `source_system/import_time` 兼容。
- 确保 writer columns 与 reader columns 不发生错位。

## 完成标准

- [ ] 同一次执行所有表 `_dts_batch_id` 一致。
- [ ] `_dts_execution_id` 可回查 `IngestionExecution`。
- [ ] 技术字段注入有单测覆盖。
