# T03: 候选 artifact、验证和发布 API

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: T02

## 目标

定义从 graph/model 到候选 artifact、platform/dbt validation、review 和 publish 的 API 链路。

## 技术设计

- `POST /api/metrics/models/{modelId}/artifacts` 生成候选 dbt SQL、schema.yml、metric/exposure docs 和 lineage hint。
- `POST /api/metrics/models/{modelId}/validate` 调 platform `POST /api/internal/metrics/model-validation`。
- `POST /api/metrics/models/{modelId}/submit-review` 进入审批。
- `POST /api/metrics/models/{modelId}/publish-dry-run` 调 release gate。
- `POST /api/metrics/models/{modelId}/publish` 调 platform release submit、BI Dataset register 和 lineage register。

## 影响范围

- `source/dts-metrics` artifact service / publish service
- `source/dts-platform` internal validation gateway
- `services/dts-dbt` candidate artifact validation

## 验证

- [x] 未通过 graph preflight 的模型不能生成 artifact。
- [x] 未通过 `DBT_VALIDATED` 的模型不能提交 publish dry-run。
- [x] publish 响应只返回 platform publish reference，不返回候选 artifact、dbt 凭据或文件路径。
- [x] platform 侧 `POST /api/internal/metrics/model-validation` 实体实现已落地。
- [ ] BI Dataset register 和 lineage register 尚未落地。

## 实现记录

- 新增 `MetricModelResource`：
  - `POST /api/metrics/models/{modelId}/artifacts`
  - `POST /api/metrics/models/{modelId}/validate`
  - `POST /api/metrics/models/{modelId}/submit-review`
  - `POST /api/metrics/models/{modelId}/publish-dry-run`
  - `POST /api/metrics/models/{modelId}/publish`
- 新增 `MetricModelLifecycleService`，当前保存内存 lifecycle state，生成候选 dbt SQL、schema.yml、exposure.yml、metric doc 和 lineage hint。
- `PlatformContractClient` 新增 additive 方法：`validateMetricModel` 与 `submitDbtRelease`。
- platform 新增 `MetricModelValidationInternalResource` 与 `MetricModelValidationService`，复用 `DbtReleaseGateService` 做权威 gate，并脱敏 build evidence 路径。
- Focused test：`MetricModelLifecycleResourceTest`、`PlatformContractClientTest` 已覆盖成功/阻断/脱敏路径。

## 完成标准

- [ ] 生成、验证、发布 API 不绕过 platform/dbt gate；platform validation 已完成，release / BI / lineage 全链路待补齐后再标 DONE。
