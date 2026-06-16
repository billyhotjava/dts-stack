# F2: 接入入湖到建模产品闭环

**优先级**: P0
**状态**: DONE

## 目标

把数据源、入湖任务、ODS、dbt source、DWD/DWS/ADS 模型和发布门禁串成默认产品流程，降低手工导入 dbt 模型的依赖。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 数据源入湖任务归一化 | P0 | DONE | F1-T01 |
| T02 | ODS 到 dbt source 契约自动化 | P0 | DONE | T01 |
| T03 | DWD/DWS/ADS 发布门禁 | P0 | DONE | T02 |
| T04 | 存量手工 dbt 导入迁移清单 | P1 | DONE | T03 |
| T05 | 平台建模闭环 API 接入 | P0 | DONE | T02/T03/T04 |

## 完成标准

- [x] JDBC/API/file 均可进入同一入湖到建模主流程。
- [x] ODS 产物具备 dbt source 注册或生成入口。
- [x] DWD/DWS/ADS 发布前必须通过 compile/test/build 与治理快照。
- [x] 平台侧提供 F2 建模闭环 API，外部页面或 IT 可直接触发 dbt source 候选、模型发布门禁和迁移清单评估。

## 进度记录

- 2026-06-14: T01 已完成。代码落点：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/goldenchain/ingestion/`；验证：`./mvnw -q -Dtest=GoldenChainIngestionTaskNormalizerTest test`。
- 2026-06-14: T02 已完成。代码落点：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/goldenchain/modeling/`；验证：`./mvnw -q -Dtest=GoldenChainOdsDbtSourceContractServiceTest test`。
- 2026-06-14: T03 已完成。新增 `GoldenChainModelReleaseGateService`，覆盖 PROD 硬阻断、DEV/DEMO warning、DWD 主键/标准码、DWS/ADS 粒度和治理快照门禁；验证：`./mvnw -q -Dtest=GoldenChainModelReleaseGateServiceTest test`。
- 2026-06-14: T04 已完成。新增 `GoldenChainDbtMigrationInventoryService` 和 PM 业务包迁移清单 `assets/dbt-migration-inventory-pm.md`；验证：`./mvnw -q -Dtest=GoldenChainDbtMigrationInventoryServiceTest test`。
- 2026-06-15: T05 已完成。新增 `GoldenChainModelingResource`，暴露 `/api/golden-chains/modeling/ods-dbt-source-candidates`、`/api/golden-chains/modeling/model-release-decisions`、`/api/golden-chains/modeling/dbt-migration-inventory`；验证：`./mvnw -q -Dtest=GoldenChainModelingResourceTest test`。
