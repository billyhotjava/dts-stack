# T03: 候选 artifact、验证和发布 API

**优先级**: P0
**状态**: READY
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

- [ ] 未通过 `GRAPH_PREFLIGHTED` 的模型不能生成 artifact。
- [ ] 未通过 `DBT_VALIDATED` 的模型不能提交发布。
- [ ] publish 响应只返回 platform publish reference，不返回 dbt 凭据或文件路径。

## 完成标准

- [ ] 生成、验证、发布 API 不绕过 platform/dbt gate。
