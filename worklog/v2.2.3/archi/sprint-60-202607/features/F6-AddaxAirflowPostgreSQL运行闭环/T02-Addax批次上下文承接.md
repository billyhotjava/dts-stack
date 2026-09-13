# T02: Addax 批次上下文承接

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: T01

## 目标

让模型运行知道本次数据来自哪个 Addax 接入批次和源数据版本。

## 技术设计

- 运行请求可选择最近成功批次或由 Airflow 自动传入批次号。
- ODS 入湖元数据统一写入 `source_batch_id`、`source_system`、`imported_at`。
- 缺批次时允许只做编译，不允许执行生产数据任务。

## 影响范围

- Addax task metadata adapter。
- PostgreSQL ODS 元数据映射和运行 API。

## 验证

- [ ] PJM fixture 运行记录能追溯到 source batch。
- [x] 批次不存在时返回可读阻断原因。

## 完成标准

- [ ] Addax 批次和 dbt run 在同一模型运行详情中可见。
