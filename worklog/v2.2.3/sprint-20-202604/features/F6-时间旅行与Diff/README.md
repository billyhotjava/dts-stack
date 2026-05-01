# F6: 时间旅行与 Diff

**优先级**: P2
**状态**: DONE
**修补断点**: ❽（API 无时间旅行）

## 目标

让 lineage 边带 SCD2 的 `valid_from / valid_to`，永不物理删除自动产出的边；支持 `?at=<timestamp>` 历史查询和两个时间点的 diff 视图。

## 背景

- 当前 lineage 表无时间维度，dbt 项目改名后老边直接消失，无法回溯
- 影响分析在变更评估场景需要"3 个月前 vs 现在"的对比
- F3.T01 已经在列级表预留了 `valid_from / valid_to`，本 Feature 把 dataset 级也对齐

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | SCD2 字段迁移与写入逻辑 | P2 | DONE | F1.T02, F2.T03, F4.T01 |
| T02 | 时间旅行 API (`?at=` 参数) | P2 | DONE | T01 |
| T03 | 前端 Diff 视图 | P2 | DONE | T02, F5.T05 |

## 完成标准

- [x] `catalog_dataset_lineage` 加 `valid_from / valid_to`，AUTO/DBT/ADDAX/AIRFLOW 边关闭时写 `valid_to`
- [x] `/api/catalog/lineage/impact?at=2026-04-01T00:00:00Z` 返回该时刻快照
- [x] LineagePage 提供“时间旅行 Diff”页签，选择两个时间点展示新增/删除/不变
- [ ] 6 个月以上的边走归档分区，保证主表性能（保留为后续大规模数据治理项）
