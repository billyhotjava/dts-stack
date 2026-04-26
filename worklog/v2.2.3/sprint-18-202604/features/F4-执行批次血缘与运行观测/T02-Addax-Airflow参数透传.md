# T02: Addax/Airflow 参数透传

**优先级**: P0  
**状态**: READY  
**依赖**: T01

## 目标

把 execution context 透传到 Addax 和 Airflow，避免运行时上下文丢失。

## 范围

- Airflow trigger conf 包含 `batch_id/task_id/execution_id`。
- Addax job 或 postSql 能读取统一上下文写入 ODS。
- 非 Airflow 执行路径需要明确处理：可阻断或实现本地 runner。

## 完成标准

- [ ] Airflow DAGRun 可在日志中看到 batch_id。
- [ ] ODS 技术字段与 execution 记录一致。
- [ ] Airflow disabled 时不会出现永远 running 的假执行。
