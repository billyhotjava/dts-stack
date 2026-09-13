# F2: Airflow 执行级血缘（OpenLineage）

**优先级**: P0
**状态**: READY
**修补断点**: ❶（DAG 没有 inlets/outlets，没接 OpenLineage）+ ❷（OpenMetadata 上报只在建任务时触发）+ ❸（execution 不记录 source/target）

## 目标

让 Airflow 在每次 DAG run 时通过 OpenLineage 把执行级 lineage（带 runId、状态、起止时间）回到 platform，把"我什么时候跑过、跑成功没、影响了哪些表"作为 lineage 的一等公民。

## 背景

- 当前 `AirflowDagService.buildDagSource()` 生成的 Python 代码里没有 `DAG(..., inlets=[...], outlets=[...])`；
- 没引入 `airflow-provider-openlineage`，Marquez/OM 拿不到任何 run-level 事件；
- OpenMetadata Pipeline 已在 task 创建时注册，但 Pipeline ↔ Lineage 关联（TaskLineage）从未建立。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | OpenLineage Provider Spike & 版本矩阵 | P0 | READY | - |
| T02 | DAG Python 模板加 inlets/outlets | P0 | READY | T01 |
| T03 | OpenLineage Listener Receiver 接入 | P0 | READY | T01 |
| T04 | runId 关联与 OM Pipeline TaskLineage 同步 | P1 | READY | T03 |

## 完成标准

- [ ] Airflow 端安装 `apache-airflow-providers-openlineage`，version pinned
- [ ] dts-ingestion 生成的所有 DAG 模板带 `inlets`/`outlets`
- [ ] dts-platform 暴露 `/api/internal/lineage/openlineage` 接收事件
- [ ] 一次 dbt run → IngestionExecution 的 `lineage_event_id` 被回填，`catalog_dataset_lineage` 对应边的 `last_run_at`/`last_run_status` 更新
- [ ] OpenMetadata 上 Pipeline 实体可看到关联 TaskLineage
