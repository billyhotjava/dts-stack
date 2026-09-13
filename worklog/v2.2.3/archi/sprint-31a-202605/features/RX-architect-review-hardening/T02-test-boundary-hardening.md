# T02: 测试边界与 metric-pack 外部引用收窄

**优先级**: P0
**状态**: DONE
**依赖**: T01

## 目标

补齐评审 R9 / R10 / R11 指出的 guardrail 盲区，确保当前契约至少能被单测和 IT 反例覆盖。

## 技术设计

- `CatalogAssetKey.scopedDataset` 禁止空 `tenant_namespace`，避免新 scoped key 静默落到 `tenant:default`。
- `MetricPackValidationService` 拆分 platform 内部资产类型和 metric-pack 可引用资产类型。
- metric-pack v0.1 只允许引用合作方可见资产：`DATASET`、`DBT_MODEL`、`BI_DATASET`、`SEMANTIC_MODEL`、`METRIC`、`GLOSSARY_TERM`。
- inline `metrics[].term_ids` 必须能在 `dependencies.platform_assets` 中找到对应 `GLOSSARY_TERM` 声明。
- 补充 `raw_sql` 深层递归、unsafe `term_ids`、null asset type、空 tenant 的边界测试。
- 增加 `broken-no-terms-pack.yml` negative IT fixture，并在 live IT 中断言 validate 返回 invalid。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/CatalogAssetKey.java`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/catalog/CatalogAssetKeyTest.java`
- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/MetricPackValidationService.java`
- `source/dts-metrics/src/test/java/com/yuzhi/dts/metrics/service/MetricPackValidationServiceTest.java`
- `worklog/v2.2.3/sprint-32-202605/it/fixtures/broken-no-terms-pack.yml`
- `worklog/v2.2.3/sprint-32-202605/it/scripts/metrics-mvp-admission-check.sh`

## 验证

- [x] `./mvnw -q -Dtest=CatalogAssetKeyTest test`
- [x] `./mvnw -q -pl dts-metrics -Dtest=MetricPackValidationServiceTest test`

## 完成标准

- [x] `scopedDataset(null, ...)` 抛出 `IllegalArgumentException`。
- [x] metric-pack 引用 `SECURITY_POLICY` 等 platform 内部资产类型会被拒绝。
- [x] nested `raw_sql` 和 unsafe glossary term ref 会被拒绝。
- [x] inline metric 引用未声明的 glossary term 会被拒绝。
- [x] IT fixture 中有能触发新校验的 negative manifest。
