# T03: Airflow DAG 投递与 dbt 回调

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: T01,T02

## 目标

将已通过门禁的 ModelSpec revision 投递到 Airflow，并回收 dbt parse/run/test 结果。

## 技术设计

- DAG 参数包含 `modelSpecId/revision/dbtSelector/sourceBatchId/target`。
- task 顺序：Addax readiness → dbt parse → dbt run → dbt test → evidence callback。
- callback 使用幂等 run id，保存 `run_results.json` 和日志 URL。

## 影响范围

- Airflow DAG 模板/trigger adapter。
- `source/dts-platform` orchestration service。
- fake Airflow contract fixture。

## 验证

- [x] 编译失败不会投递 DAG。
- [x] 编译成功后才允许携带 dbt selector 和 PostgreSQL 目标表进入投递门禁。
- [ ] dbt run/test 失败后运行状态为 FAILED，且可重试。
- [ ] callback 重复发送保持幂等。

## 完成标准

- [ ] 至少用 PJM fixture 完成一次 fake Airflow 全链路测试。
