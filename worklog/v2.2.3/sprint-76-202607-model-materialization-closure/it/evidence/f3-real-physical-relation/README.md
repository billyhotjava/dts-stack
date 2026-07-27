# F3 真实物理关系与强绑定证据

**日期**：2026-07-27  
**结论**：T01～T03 DEV/TEST GREEN；2026-07-28 复审新增 T04 typed-column
强契约，F3 重新置为 IN_PROGRESS；Sprint-76 仍为 PROD NO-GO。

## RED

1. 真实 PostgreSQL repository 首次写入暴露 INSERT 31 列/32 个占位符，无法保存
   RelationObservation。
2. Candidate 转换到 BUILT 失败时，旧流程可能先留下 pipeline
   `RELATION_VERIFIED`，形成半完成真值。
3. 仅有 `run_results=success` 时，relation 缺失、类型不符或列漂移必须阻断，不能从
   dbt artifact 推导 `physicalAssetVerified=true`。

## GREEN

- PostgreSQL inspector 使用运行时 target context 查询 `pg_catalog`，返回
  TABLE/VIEW/MATERIALIZED_VIEW、按 ordinal 排序的列、数据类型、nullable 与 checksum；
  不执行 `count(*)`。
- artifact identity、candidate scope、dbt invocation、bundle、model/implementation
  revision/checksum 和 relation locator 全部匹配后才允许写 observation。
- `markDbtSucceeded + appendAll` 为一个事务；全部 entry verified 后，
  `markRelationsVerified + Candidate BUILT` 为另一个事务。后者失败会整体回滚并进入
  BUILD_FAILED，不留下部分 BUILT 真值。
- observation 按 attempt 追加；真实 IT 连续写入 attempt 1/2，`findCurrent` 返回
  attempt 2。数据库 trigger 对 update/delete 抛出
  `PHYSICAL_RELATION_OBSERVATION_IS_APPEND_ONLY`。
- 双 entry 候选中任一 relation 缺失时，全部 dbt success 仍不能进入 BUILT。
- inspector registry 仅开放 PostgreSQL；未知 adapter fail-closed。原高级
  `DBT_MANAGED` asset sync 兼容测试保持通过。

## 自动化结果

| 命令/报告 | 结果 |
|---|---|
| `ModelMaterializationRunArtifactServiceTest` | 11 tests，0 failures，0 errors |
| `PostgresPhysicalRelationInspectorTest` | 2 tests，0 failures，0 errors |
| `ModelMaterializationStartServiceIT`（真实 PostgreSQL Testcontainer） | 2 tests，0 failures，0 errors |
| `DbtAssetSyncServiceTest` | 13 tests，0 failures，0 errors |

合计 28 tests，0 failures，0 errors。为遵守不重复消耗原则，本轮没有重跑 G0 登录、
Chrome95 或整包构建。

## 复审边界

1. 当前 dbt manifest 为 F3 提供期望列名和顺序，因此可以严格阻断列缺失/改名/顺序
   漂移；实际数据类型与 nullable 已记录并参与物理 metadata checksum。
2. 复审已确认字段类型是发布前强门禁：canonical ModelSpec 已要求 `dataType`，因此
   DESIGNER_GENERATED 必须由 T04 扩展 compiler artifact contract，并由 inspector
   聚合层完成 expected-vs-actual 数据类型比较。
3. 本证据没有替代 F6 的真实 Airflow → dbt → PostgreSQL → 页面全链、升级回滚、
   安全/NFR 和 Chrome95 验收。
