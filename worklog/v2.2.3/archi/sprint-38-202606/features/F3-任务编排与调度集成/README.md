# F3: 任务编排与调度集成

**优先级**: P0
**状态**: IN_PROGRESS

## 目标

按 C1 瘦触发架构改造调度链：Airflow 保留调度/监控职责，DAG 只回调 dts-ingestion 执行接口；执行生命周期、失败分类、增量补数、血缘对齐 JDBC 路径的运维口径。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | Airflow瘦触发DAG | P0 | DONE | F2-T01 |
| T02 | 执行生命周期与失败分类 | P0 | IN_PROGRESS | F2-T01 |
| T03 | 增量与补数语义 | P0 | IN_PROGRESS | F2-T04, F2-T05 |
| T04 | 血缘与可观测 | P1 | IN_PROGRESS | T02 |

## 完成标准

- [ ] API 任务在 Airflow 可定时/手动触发，实际抓取在 dts-ingestion 进程内执行（手动触发已由 IT-01 验证；定时触发仍待补 IT）
- [ ] 执行状态/行数/失败分类回填 IngestionExecution，与 JDBC 路径同一套监控
- [ ] backfill 窗口、增量游标推进、重跑幂等有 IT 证据

## 进展

### 2026-06-12

- T01 已进入实现：API DAG 改为 PythonOperator 薄触发，调用 `/internal/api-ingestion/executions` 并用稳定 execution DB id 轮询。
- 新增内部 POST/GET 资源层，复用 `X-DTS-Service` 服务间 principal；`IngestionTaskService.executeInternalApi` 支持 Airflow 传入 `batchId` 与 backfill 窗口。
- T01 已补齐 mock API live IT 的手动 Airflow trigger：Airflow run `s38-api-e2e-airflow-90-1781249278` task state `success`，由 DAG 创建 execution `136`，raw/checkpoint 推进到 `2026-06-12T00:04:00Z`；证据见 `it/evidence/api-end-to-end-20260612.txt`。
- T02 已进入实现：`ExecutionFailureClassifier` 支持结构化 `API_RUNTIME_*` 分类，默认自动重试白名单移除不可恢复的 `GOVERNANCE_LIMIT`。
- T03 已进入实现：常规增量读取 checkpoint 后套用 lookback；backfill window 覆盖 cursor 起点且不读取/推进 checkpoint，语义文档已补。
- T04 已进入实现：API executor 写 lineage snapshot 并发布 Micrometer 指标；platform OpenLineage 直发 payload 带 rowCount facets。
