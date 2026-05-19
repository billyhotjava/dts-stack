# T04: metrics model validation gateway

**优先级**: P0
**状态**: READY
**依赖**: T02,T03

## 目标

新增或封装 platform 聚合 API，让 `dts-metrics` 提交候选 dbt artifact 后，由 platform 调 dbt 做权威模型检测。

## 技术设计

- API 建议：`POST /api/internal/metrics/model-validation`。
- 输入：graph id、candidate SQL、schema.yml、model selector、source assets、metric codes、request context。
- platform 先做 contract precheck，再写候选 artifact 到受控工作区。
- platform 调用 dbt compile/test/build 或现有 release gate，并返回结构化诊断。
- 诊断必须包含 nodeId/edgeId/fieldId/metricCode/path/severity/message。

## 影响范围

- `source/dts-platform` dbt gateway / release gate
- `source/dts-metrics` artifact validation client
- `source/dts-metrics-webapp` validation report panel

## 验证

- [ ] dbt compile 失败能定位到生成 SQL 或 schema.yml。
- [ ] dbt test/build 失败能返回阻断项。
- [ ] platform 不可达或 dbt 不可用时返回可重试错误。

## 完成标准

- [ ] `dts-metrics` 不直接调用 dbt，也不持有 dbt 项目目录或运行凭据。
