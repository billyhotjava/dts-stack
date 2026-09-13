# F3: 列级血缘（column-level lineage）

**优先级**: P1
**状态**: READY
**修补断点**: ❺（列级 lineage 缺失）

## 目标

把"改一个字段会影响哪些下游"做成可查询的能力：在 dbt model 层面解析 `compiled_sql`，提取列级映射，落到独立表 `catalog_dataset_lineage_column`，前端可 toggle 显示。

## 背景

- `SqlTableReferenceExtractor` 目前只识别表级引用，列级要单独做；
- dbt manifest 每个 model 有 `compiled_sql`，是天然的列级抽取入口；
- AUTO_VIEW 路径里同样有 SQL 可用；
- Inceptor 原生方言（LATERAL VIEW、MERGE INTO）的列级解析复杂度高，本 Sprint 只做 dbt 编译后 SQL（标准 ANSI/PG/Spark 方言），原生方言推到 v2.4.0。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 列级表设计与迁移 | P1 | READY | - |
| T02 | SqlColumnLineageExtractor 实现 | P1 | READY | T01 |
| T03 | dbt manifest compiled_sql 集成 | P1 | READY | T02 |
| T04 | 列级 API 扩展 (`includeColumns=true`) | P1 | READY | T01 |

## 完成标准

- [ ] 新表 `catalog_dataset_lineage_column` 已建并写入 dbt 项目数据
- [ ] `SqlColumnLineageExtractor` 单测覆盖 SELECT a/SELECT a AS b/SELECT a+b AS c/CASE WHEN/CTE/JOIN
- [ ] 一个真实 dbt 项目跑通同步，列级数据非空
- [ ] `/api/catalog/lineage/impact?includeColumns=true` 返回结构包含 `columnEdges[]`
- [ ] 性能：100 个 model × 平均 30 列的项目，全量解析 < 60s
