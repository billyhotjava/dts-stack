# F1/T01 Canonical execution plan evidence

**日期**：2026-07-27
**范围**：普通 ModelImplementation 设置校验、保存门禁、编译投影和前端阻断信息。

## RED

1. 真实页面 settings 首次进入旧 compiler 时失败：
   `IMPLEMENTATION_SETTING_NOT_ALLOWED`。
2. compiler 投影未携带规范 KEY 时，新增测试编译失败：
   `cannot find symbol: method keyFields()`。
3. 严格 dbt node identity 校验暴露兼容迁移旧 selector 以数字 UUID 开头，
   `implementationMigrationDryRun... expected 1 but was 0`；迁移随后改用系统管理身份，
   没有放宽 compiler 约束。

## GREEN

- 后端组合：
  `./mvnw -Dtest=ModelImplementationExecutionPlannerTest,ModelLifecycleServiceTest,ModelingDbtCompilerTest,ModelSpecStageGateServiceTest,ModelLifecycleResourceTest test`
  → `55 tests, 0 failures`。
- planner/projection/compiler 收口：
  `./mvnw -Dtest=ModelImplementationExecutionPlannerTest,ModelSpecCompilerProjectionTest,ModelingDbtCompilerTest test`
  → `19 tests, 0 failures`。
- 保存拒绝与兼容迁移：
  `./mvnw -Dtest=ModelLifecycleServiceTest test`
  → `6 tests, 0 failures`。
- 前端增量契约：
  `node --experimental-strip-types --test src/pages/modeling/modelImplementationContract.test.ts`
  → `6 tests, 0 failures`。

## 结论

- FULL table/view 与 INCREMENTAL+KEY 可形成唯一 execution plan。
- SNAPSHOT、PostgreSQL partition、未知 settings、装载/物化冲突和非法 dbt identity 均 fail-closed。
- 无效计划在 repository write 前被拒绝。
- API 保留 `valid/code` 兼容形态，前端按稳定错误码提供中文修复引导。

本证据只关闭 F1/T01，不代表 dbt 已运行、目标 relation 已存在或 Sprint-76 可生产发布。
