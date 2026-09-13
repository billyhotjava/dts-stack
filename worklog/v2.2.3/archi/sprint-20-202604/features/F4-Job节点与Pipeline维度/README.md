# F4: Job/Pipeline 节点维度引入

**优先级**: P1
**状态**: READY
**修补断点**: ❻（lineage 节点只有 dataset，无 job/pipeline）+ ❽（API 无 job 维度）

## 目标

让 lineage 图从"dataset → dataset"扩展为"dataset → job → dataset"：用户能看到中间是哪个 dbt model / Airflow DAG / Addax 任务在加工，对排障极其关键。

## 背景

当前 `CatalogDatasetLineage` 只记录 dataset 间关系，但实际 ETL 过程中"中间加工者"（dbt model / Airflow DAG / Addax task）都是有名字的实体。前端图上看到 `ods_orders → dwd_orders`，看不到它经过了 `dwd_orders.sql` 这个 dbt model，也看不到这个 model 是哪个 DAG run 跑出来的。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | lineage_job 表与节点类型扩展 | P1 | READY | F1.T02, F2.T03 |
| T02 | impact API 返回结构改造（dataset+job 节点） | P1 | READY | T01 |
| T03 | job 数据源接入（dbt/airflow/addax） | P1 | READY | T01 |

## 完成标准

- [ ] 新表 `lineage_job` 已建并写入 dbt model / Airflow DAG / Addax task 三类记录
- [ ] `/api/catalog/lineage/impact` 返回的 `nodes[]` 区分 `kind: 'dataset' | 'job'`
- [ ] 前端 LineagePage（F5）能渲染 dataset → job → dataset 三段图（本 Feature 仅 API，前端在 F5 实现）
- [ ] dbt model 至少 90% 节点能正确关联其 dataset 上下游
