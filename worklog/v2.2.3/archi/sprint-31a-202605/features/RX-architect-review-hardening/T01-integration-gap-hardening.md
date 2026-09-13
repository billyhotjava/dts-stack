# T01: 整合裂缝评审与低风险契约追补

**优先级**: P0
**状态**: CONTRACT_DONE
**依赖**: Sprint-31A F1-F6, Sprint-32 F3-F4

## 目标

把评审中确认的“已有能力未显式整合”问题转为当前版本可执行的契约修复，避免 `GovIndicator*`、`Modeling*`、`DataStandard`、`Glossary`、`SecurityPolicy`、`SvcApi*` 等能力继续绕开统一资产事实源。

## 技术设计

- 扩展 `CatalogAssetType`，把代码化资产、治理指标、标准、术语、安全策略、API 服务和回填请求纳入统一资产类型集合。
- 扩展 `CatalogAssetKey`，新增 tenant/env/dialect scoped key 和 code asset key。
- 扩展 `MetricPackValidationService`，对 metric-pack 加入契约校验：
  - inline metric 必须绑定 `term_ids`；
  - 引用 platform asset 时必须声明 tenant/owner namespace；
  - 引用 platform asset 时必须 `security.apply_rls=true`；
  - `pack_dependencies[]` 必须使用安全 pack id 和版本约束。
- 输出评审整合响应文档，明确当前版本和 v2.3 的边界。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/CatalogAssetType.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/CatalogAssetKey.java`
- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/MetricPackValidationService.java`
- `worklog/v2.2.3/sprint-31a-202605/assets/architect-review-integration-response.md`

## 验证

- [x] `./mvnw -q -Dtest=CatalogAssetKeyTest test`
- [x] `./mvnw -q -pl dts-metrics -Dtest=MetricPackValidationServiceTest,MetricArtifactGenerationServiceTest test`
- [x] `git diff --check`

## 完成标准

- [x] 当前版本能阻断 inline 无术语、无租户边界、无 RLS 声明的 metric-pack。
- [x] 新资产类型不改变已有 key 输出，不破坏 dbt/OpenLineage/Addax 现有写入链路。
- [x] v2.3 大项没有误标为当前版本已实现。

## 遗留 enforcement

- [ ] `CatalogAssetIdentityResolver` 仍只闭环 `DATASET`，代码化资产和 metric-pack 尚未接入 resolver。
- [ ] Glossary term 目前只校验引用格式，尚未校验 platform 术语存在性和发布状态。
- [ ] `security.apply_rls=true` 仍是 manifest 声明，SQL 生成器和 publish/preview 阶段尚未强制注入 platform RLS。
