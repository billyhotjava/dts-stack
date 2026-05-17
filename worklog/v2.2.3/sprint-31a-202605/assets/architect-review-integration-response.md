# 架构评审整合响应

**状态**: IN_PROGRESS
**日期**: 2026-05-17
**范围**: Sprint-31A / Sprint-31 / Sprint-32 post-review hardening

## 结论

评审意见的核心判断成立：当前缺口主要不是从零建设，而是已有 `Glossary`、`DataStandard`、`GovIndicator*`、`MetadataStandard`、`CatalogMaskingService`、`SecurityPolicy`、`OpsBackfillRequest`、`DatasetDataAccessApproval` 等能力没有全部纳入新的 platform asset 事实源和 dts-metrics 契约。

本轮不把所有高级能力做成运行时。当前版本只补企业级 GA 的契约裂缝和导入/发布 guardrail；v2.3 再做完整运行时增强。

## 当前版本承接项

| 主线 | 承接方式 | 当前动作 |
|---|---|---|
| X1 旧 Gov / Modeling / Standard 与新事实源裂缝 | 扩展 asset_type 和历史映射报告 | 增加 `MODELING_SQL_MODEL`、`DATA_STANDARD`、`GOV_INDICATOR`、`API_SERVICE` 等资产类型 |
| X2 Freshness/SLA/Cost 缺位 | 先进入资产契约和治理检查字段 | 文档增加 `expectedRefreshIntervalMinutes`、`maxStalenessMinutes`、`lastObservedAt` |
| X3 Glossary 孤岛 | metric-pack 强制绑定 glossary term | inline metric 缺 `term_ids` 阻断校验 |
| X4 版本联动 | 当前记录为 v2.3 运行时任务 | 保留 metric version pin / schema version 设计入口 |
| X5 RLS/密级/隐私未承接 | metric-pack 引用平台资产时必须声明 RLS | `security.apply_rls=true` 成为校验条件 |

## 本轮代码 guardrail

1. `CatalogAssetType` 扩展已有代码化资产类型，避免 Gov/Modeling/Service/Policy 继续绕过 `asset_grant`。
2. `CatalogAssetKey` 增加 tenant/env/dialect scoped key，支持多客户、多环境、国产/开源方言隔离。
3. `MetricPackValidationService` 增加：
   - `tenant_namespace` / `owner_namespace` 隔离检查；
   - `security.apply_rls=true` 检查；
   - inline `metrics[].term_ids` 强制术语绑定；
   - `dependencies.pack_dependencies[]` 安全版本约束。

## v2.3 排期项

| 类别 | 推迟原因 |
|---|---|
| SCD / conformed dimension / hierarchy 运行时 | 需要 metric_* 实体迁移和生成器重构 |
| window / time intelligence / cohort / funnel DSL | 需要方言矩阵和 SQL 生成器重构 |
| schema_version / metric_version_pin / breaking change review | 需要资产版本表和消费者绑定表 |
| cube cache / cost-based routing / GraphQL / OData | 需要消费层和查询路由层独立设计 |
| 差分隐私 / k-anonymity | 需要安全策略和审计策略一起设计 |

## 验收

- platform: `CatalogAssetKeyTest`
- metrics: `MetricPackValidationServiceTest`, `MetricArtifactGenerationServiceTest`
- static: `git diff --check`
