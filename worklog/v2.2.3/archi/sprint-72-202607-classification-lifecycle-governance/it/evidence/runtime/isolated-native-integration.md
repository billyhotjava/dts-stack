# 隔离原生集成证据

验证日期：2026-07-26

## PostgreSQL / JDBC

- `JdbcClassificationLifecycleIT` 使用 Testcontainers PostgreSQL 创建真实源表和字段备注。
- JDBC 元数据导入得到 1 个 dataset、2 个 column。
- SECRET 字段将 dataset 有效密级提升为 SECRET，且不能被较低声明覆盖。

## 存量迁移

- `CatalogClassificationMigrationServiceIT` 使用临时 PostgreSQL 执行真实 SQL。
- 没有独立字段声明的 column 从所属 table/dataset 继承 SECRET。
- 迁移候选低于已有事实时保持原值并阻断降级。

## 生命周期销毁

- `CatalogLifecycleDestructionIT` 使用临时 PostgreSQL 和独立托管副本。
- 临时销毁、恢复、双人永久销毁、证明留存及外部源不反向 DROP 均通过。

## 适配器与编排

- Excel/CSV 文件下限和字段密级由 `ExcelImportServiceTest` 8/8 覆盖。
- dbt manifest 映射与传播由 `DbtAssetSyncServiceTest` 13/13 覆盖。
- OpenLineage 接收与密级传播由 `OpenLineageReceiverResourceTest` 2/2 覆盖。
- Airflow DAG lineage/classification 参数和 seal 由 `AirflowDagServiceTest`、
  `IngestionClassificationSealGuardTest` 覆盖。

结论：仓库原生适配器与真实 PostgreSQL 隔离集成 `PASS`。由于本轮按约束未重建、发布容器，
生产 dbt/Airflow/OpenLineage/Excel/CSV 端到端运行仍需在下一放行阶段补证。
