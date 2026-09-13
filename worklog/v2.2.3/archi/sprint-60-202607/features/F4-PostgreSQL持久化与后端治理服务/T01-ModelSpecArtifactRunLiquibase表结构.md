# T01: ModelSpec、Artifact、Run Liquibase 表结构

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: F1-T01

## 目标

为新版本建模建立 PostgreSQL 元数据表和可回滚迁移。

## 技术设计

至少新增：`modeling_business_object`、`modeling_warehouse_plan`、`modeling_model_spec`、`modeling_model_spec_revision`、`modeling_dbt_artifact`、`modeling_pipeline_run`、`modeling_standard_binding`、`modeling_lineage_edge`。

表设计必须包含 `tenant_id/owner/status/version/created_date/last_modified_date`，并对 `dbt_unique_id + project_key`、`model_spec_id + revision` 建唯一约束。

## 影响范围

- `source/dts-platform/src/main/resources/config/liquibase/master.xml`
- 新增 changelog XML。
- repository/service integration tests。

## 验证

- [x] Changelog XML、master include、8 张元数据表、唯一约束和 rollback 通过 source-contract 测试。
- [ ] PostgreSQL migration 可正向和回滚（需要测试数据库证据）。
- [ ] 重复 unique id、revision 和幂等键被数据库约束阻断（需要测试数据库证据）。
- [x] 新 changelog 排在既有 Sprint-64 changelog 之后，不改动旧表。

## 完成标准

- [ ] 使用测试数据库跑过完整 migration，并在 `it/evidence/backend/` 留证。
