# T02: column masking 加入 policy endpoint 与 SQL 生成器

**优先级**: P0
**状态**: DONE
**依赖**: T01

## 目标

`/api/internal/v1/asset-permission/policy` 返回的 `RlsPolicyResult.maskedColumns` 当前已经可从 platform 的 `CatalogMaskingRule` 中读取，但 `MetricArtifactGenerationService` 尚未把被 mask 的列替换为 `mask_func(col)`。本任务继续把 masking 从 policy metadata 推进到候选 SQL / schema.yml。

## 背景

`PlatformContractClient.RlsPolicyResult` record 已经声明了 `maskedColumns` 字段，platform 侧 endpoint 已能返回 CatalogMaskingRule 命中的字段。manifest `security.apply_rls=true` 暗含「敏感字段会被 mask」的承诺，但当前候选 SQL 只有 row filter，没有 column mask —— 这是合规风险点。

## 技术设计

1. platform `AssetPermissionInternalResource.policy(...)` 从 `CatalogMaskingRule` 读取安全字段名，返回 `maskedColumns`。
2. `MetricArtifactGenerationService.dbtModelSql(...)` 接收 `maskedColumns`，在 select 阶段把脱敏维度替换为 `{{ dts_mask('col') }}` 形态，并同步调整 `group by`，避免按原始敏感值分组后产生重复 masked rows。
3. `schema.yml` 输出 `description` 字段说明该列已被 platform policy mask。
4. 如果一个被 mask 的列同时是聚合表达式的 input，必须显式拒绝（preview 报错），避免 silent 输出错误数据。
5. 因 platform 当前 policy contract 只返回列名，候选 `maskingMacroSql` 采用安全的 null-mask；后续如要保留部分脱敏，需要扩展 policy contract 的 mask function/reason。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/internal/AssetPermissionInternalResource.java`
- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/PlatformContractClient.java`（消费 `RlsPolicyResult.maskedColumns`）
- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/MetricArtifactGenerationService.java`

## 验证

- [x] `AssetPermissionInternalResourceTest` 覆盖 policy 返回 `maskedColumns`
- [x] `MetricArtifactGenerationServiceTest.masksPlatformMaskedDimensionsInGeneratedSqlAndSchema`
- [x] `MetricArtifactGenerationServiceTest.maskedMetricInputStopsPreviewBeforeGeneratingSql`

## 完成标准

- [x] policy endpoint 返回 row-filter + column-mask metadata。
- [x] 候选 SQL 与 schema.yml 体现 mask 行为。
- [x] 聚合 input 被 mask 时显式失败。
