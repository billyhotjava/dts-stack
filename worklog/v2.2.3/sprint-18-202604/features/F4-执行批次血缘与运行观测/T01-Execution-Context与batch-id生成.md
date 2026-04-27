# T01: Execution Context 与 batch_id 生成

**优先级**: P0  
**状态**: DONE
**依赖**: F1/T02

## 目标

定义一次接入执行的统一上下文，保证所有下游组件使用同一批次信息。

## 范围

- 在执行开始前生成 `_dts_batch_id`。
- 绑定 `task_id`、`execution_id`、operator、trigger type。
- 上下文持久化到 `IngestionExecution` 或扩展字段。
- 提供给 Addax job 生成和 Airflow trigger conf。

## 完成标准

- [x] 同一次执行所有目标表 batch 一致。
- [x] 重试执行生成新的 batch，但保留 retry 关系。
- [x] 单测覆盖 batch 生成和持久化。
