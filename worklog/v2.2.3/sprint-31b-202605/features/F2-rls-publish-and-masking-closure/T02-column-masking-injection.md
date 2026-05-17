# T02: column masking 加入 policy endpoint 与 SQL 生成器

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

`/api/internal/v1/asset-permission/policy` 返回的 `RlsPolicyResult.maskedColumns` 当前是空 list，但 platform 已经有 `CatalogColumnPolicy` / `CatalogMaskingService` 能力。本任务把已有 column masking 字段也写入 policy 响应，并让 `MetricArtifactGenerationService` 在生成 SQL 时把被 mask 的列替换为 `mask_func(col)`。

## 背景

`PlatformContractClient.RlsPolicyResult` record 已经声明了 `maskedColumns` 字段，但 platform 侧 endpoint 永远返回空。manifest `security.apply_rls=true` 暗含「敏感字段会被 mask」的承诺，但当前只有 row filter，没有 column mask —— 这是合规风险点。

## 技术设计

1. platform `AssetPermissionInternalResource.policy(...)` 调用 `CatalogMaskingService.computeMaskingForDataset(dataset, userRoles, userClassification)`，返回 `List<MaskedColumn { columnName, maskFunction, reason }>`。
2. `RlsPolicyResult` 扩展 `maskedColumns` 字段：
   ```java
   public record MaskedColumn(String column, String maskFunction, String reason) {}
   ```
3. `MetricArtifactGenerationService.dbtModelSql(...)` 接收 `maskedColumns`，在 select 阶段把列替换为 `{{ mask_function('col') }}` 形态（dbt macro，platform 提供）。
4. `schema.yml` 输出 `description` 字段说明该列已被 mask。
5. 如果一个被 mask 的列同时是聚合表达式的 input，必须显式拒绝（preview 报错），避免 silent 输出错误数据。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/internal/AssetPermissionInternalResource.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/permission/CatalogMaskingService.java`（如未暴露 method，新增 read-only API）
- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/PlatformContractClient.java`（`RlsPolicyResult` 字段扩展 + `MaskedColumn` record）
- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/MetricSqlGenerator.java`
- `source/dts-platform/src/main/resources/dbt/macros/`（mask 宏，如不存在）

## 验证

- [ ] `AssetPermissionInternalResourceTest.policy_returnsMaskedColumnsForSensitiveDataset`
- [ ] `MetricArtifactGenerationServiceTest.applyMask_replacesColumnInSelect`
- [ ] `MetricArtifactGenerationServiceTest.maskedColumnInAggregation_rejectsPreview`

## 完成标准

- [ ] policy endpoint 返回 row-filter + column-mask 完整策略。
- [ ] 候选 SQL 与 schema.yml 体现 mask 行为。
- [ ] 聚合 input 被 mask 时显式失败。
