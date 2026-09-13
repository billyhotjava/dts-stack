# T01: 持久化 schema 与 JPA 实体设计

**优先级**: P0
**状态**: DONE
**依赖**: 无

## 目标

为 dts-metrics 设计领域持久化模型，覆盖现有内存态的全部业务状态。

## 技术设计

先勘察同仓 `source/dts-platform`、`source/dts-admin` 的 datasource、Liquibase changelog、实体基类与命名约定，**严格沿用**（schema 命名、主键策略、审计字段、`@Version` 用法）。

新增实体（字段以现有内存 Map 结构为准，见 `MetricModelLifecycleService` / `MetricGraphDraftService`）：

- `graph_draft`: id(PK), graph(jsonb), diagnostics(jsonb), status, source, created_at, updated_at。
- `metric_model_state`: model_id(PK), model_name, status, artifact_ref, artifacts(jsonb), applied_policy_source, applied_predicate_hash, platform_validation(jsonb), active_version, updated_at。
- `metric_model_version`: id(PK), model_id(FK), version, model_name, status, platform_publish_reference, release_decision, artifact_ref, published_at, `version_lock`(@Version)。
- `metric_rollback_event`: id(PK), model_id(FK), version, rollback_from_version, rollback_to_version, platform_rollback_reference, reason, rolled_back_at。

jsonb 字段承载 graph/artifacts 等结构（与 F5 类型化协同：边界先 jsonb，内层逐步 record 化）。

## 影响范围

- 新增 `src/main/java/.../metrics/domain/` 实体包。
- 新增 Liquibase changelog（T02 落地）。
- 不改对外 API 形态。

## 验证
- [ ] 实体字段与现有内存 Map 的 key 一一对应，无信息丢失。
- [ ] 命名/基类/审计字段约定与 dts-platform 一致。

## 完成标准
- [ ] 设计文档（表 DDL + 实体清单 + 内存→实体字段映射表）产出，作为 T02-T05 输入。
