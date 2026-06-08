# T04: platform/dbt validation gateway 编排

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: T03

## 目标

让候选 artifact 的权威验证统一进入 `dts-platform`，由 platform 执行 contract precheck、dbt compile/test/build、release gate 和审计记录。

## 技术设计

- `dts-metrics` 调 `POST /api/internal/metrics/model-validation`。
- platform 内部顺序：asset contract -> permission/RLS/masking -> domain/glossary/data standard -> artifact staging -> dbt compile -> dbt test/build -> structured report。
- report 映射回 graph diagnostic。
- platform 记录 audit event 和 validation trace id。

## 影响范围

- `source/dts-platform/src/main/java/**/web/rest/**`
- `source/dts-platform/src/main/java/**/service/**Dbt**`
- `source/dts-metrics/src/main/java/**/service/MetricArtifactPublishService.java`

## 验证

- [x] metrics 侧 platform transport failure 映射为 `platform_contract_unavailable`。
- [x] metrics 侧 platform validation 非 PASS 映射为 `dbt_validation_failed`。
- [x] publish 响应不透出 platform 原始 release payload、候选 artifact、宿主机绝对路径或 secret。
- [x] platform 侧 `POST /api/internal/metrics/model-validation` 已由 service-auth 保护，并复用 dbt release gate。
- [x] platform validation gateway 已校验 `securitySnapshot.policySource` / `predicateHash` 与请求外层字段一致，不一致时不调用 release gate。
- [ ] platform 侧 audit validation trace 尚未实现。

## 实现记录

- `PlatformContractClient.validateMetricModel` 调 `POST /api/internal/metrics/model-validation`。
- `PlatformContractClient.submitDbtRelease` 调 `POST /api/etl/dbt/release/submit`。
- `MetricModelLifecycleService.validateModel` / `publishDryRun` / `publish` 已按状态机调用 platform validation、release gate 和 release submit。
- `MetricModelLifecycleService.rollback` / `versionHistory` 已提供 service-local version history 与 rollback event contract，发布/回滚响应不透出候选 artifact 或 platform 原始 payload。
- `MetricModelValidationInternalResource` / `MetricModelValidationService` 已在 platform 侧落地，返回 `DBT_VALIDATED` / `DBT_VALIDATION_FAILED`、diagnostics、validationTraceId 和脱敏 releaseGate 摘要。
- `MetricModelValidationService` 已阻断缺失 `securitySnapshot`、`security_policy_mismatch`、`security_hash_mismatch`。
- `ServiceDependencyAuthenticationFilter` 与 `PlatformCapabilityResource` 已同步 `POST /api/internal/metrics/model-validation`。
- `MetricModelLifecycleResourceTest` 与 `PlatformContractClientTest` 已覆盖 metrics 侧编排和 service-auth contract。
- `MetricModelValidationInternalResourceTest`、`ServiceDependencyAuthenticationFilterTest`、`PlatformCapabilityResourceTest` 已覆盖 platform 侧 validation、白名单和 capability contract。

## 完成标准

- [ ] 模型检测入口不在前端或 metrics 本地执行 dbt；当前 platform validation gateway 已接入 release gate，audit trace、BI/lineage register 仍待补齐。
