# F1: Addax 入湖血缘自动回写

**优先级**: P0
**状态**: READY
**修补断点**: ❹（Addax tableMapping 没回写到 CatalogDatasetLineage）+ ❸（IngestionExecution 不记录 source/target 表）

## 目标

让任何一次 Addax 入湖任务在 execution 成功后，**自动**在 `catalog_dataset_lineage` 写一条 `relationType=ADDAX` 的边，覆盖前端 LineagePage 上"业务库 → ODS"这段当前完全空白的链路。

## 背景

- `IngestionTaskDTO.tableMapping` 已经清楚地表达了 source 表与 target 表的映射关系；
- `OpenMetadataAdapter.registerLineage()` 只在任务**创建/更新**时被调用一次，execution 成功与否完全不影响 lineage；
- `IngestionExecution` 实体目前只记录 status / startTime / endTime，没有 source/target 表字段；
- 结果：前端 LineagePage 显示的图永远从 `ods_*` 开头，看不到上游的 MySQL/Oracle/Excel 来源。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | IngestionExecution 扩展 source/target 表字段 | P0 | READY | - |
| T02 | LineageWriter 服务（写 catalog_dataset_lineage） | P0 | READY | T01 |
| T03 | Execution 完成事件回调挂接 | P0 | READY | T02 |
| T04 | 历史 execution 数据回填脚本 | P1 | READY | T02 |

## 完成标准

- [ ] 新建 Addax 任务跑一次 → `catalog_dataset_lineage` 出现 `relationType=ADDAX` 边
- [ ] 执行失败时不写正式 lineage 边，但 IngestionExecution 记录 source/target 列表（待人工确认）
- [ ] 前端 LineagePage 可以看到 "MySQL.orders → ods_orders" 这条边
- [ ] 同一任务重跑不重复落库（按 task_id + execution batch upsert）
- [ ] 历史数据回填脚本对最近 30 天的成功 execution 都生成了 lineage
