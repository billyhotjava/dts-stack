# F1/T02 普通 compiler 与真实 manifest 证据

**日期**：2026-07-27  
**范围**：普通 DIMENSION/FACT/SUMMARY 的 SQL/YAML、dbt identity、alias、
materialization、meta、source/ref graph 和 fail-closed 依赖。

## RED

1. 首次真实 `dbt compile` 发现 SQL 与 tests 分别生成两个 model YAML，dbt 拒绝重复
   patch 同一 node；compiler 随后合并成单一 schema/tests YAML。
2. 真实 PostgreSQL 编译暴露逻辑类型 `string` 被直接输出为
   `cast(... as string)`；随后改为受控 adapter cast（`text/numeric`）。
3. 两个 candidate overlay node 相互 `ref()` 时，原 scoped service 未报错；新增测试
   得到“Expecting code to raise a throwable”，随后增加
   `MATERIALIZATION_DEPENDENCY_CYCLE` 门禁。

## GREEN

- compiler/lifecycle/scoped 合并单元门禁：
  `ModelImplementationExecutionPlannerTest, ModelLifecycleServiceTest,
  CanonicalModelLifecycleCompilerAdapterTest, ModelingDbtCompilerTest,
  DbtScopedProjectServiceTest`
  → 原 26 项全绿；新增 cycle 后 scoped tests `4/4` 全绿。
- 前端 canonical identity/source-contract：
  `node --experimental-strip-types --test
  src/pages/modeling/modelImplementationContract.test.ts
  src/pages/modeling/modelSpecThreeStageDetail.source-contract.test.ts`
  → `8/8` 全绿。
- 真实运行：
  `DbtScopedProjectServiceRealCompileIT`
  → `dbt-core 1.11.3 / dbt-postgres 1.10.0`，`1/1` 全绿。

## 真实断言

- 普通 DIMENSION=`table`、FACT=`incremental`、SUMMARY=`view` 均进入
  `manifest.json`。
- `model.dts.model_<uuid>`、`alias`、`materialized`、
  `meta.modelSpecId/implementationRevision` 与 canonical revision 一致。
- `parent_map/child_map` 证明 physical source → ephemeral STG → FACT，以及
  FACT → SUMMARY 的真实依赖图。
- 编译 SQL 包含 `cast(project_id as text)` 与
  `cast(budget_amount as numeric)`。
- compile 未写 pipeline run、relation observation、CatalogDataset 或
  `physicalAssetRef`。

本证据关闭 F1/T02，不证明 PostgreSQL 目标 relation 已由 `dbt build` 创建。
