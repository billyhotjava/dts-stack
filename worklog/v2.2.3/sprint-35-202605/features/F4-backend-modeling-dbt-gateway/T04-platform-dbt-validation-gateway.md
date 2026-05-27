# T04: platform/dbt validation gateway 编排

**优先级**: P0
**状态**: READY
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

- [ ] dbt compile/test 失败返回 `dbt_validation_failed`，不是 500。
- [ ] report 包含 artifact path 但不泄露宿主机绝对路径。
- [ ] platform audit 记录 validation trace。

## 完成标准

- [ ] 模型检测入口不在前端或 metrics 本地执行 dbt。
