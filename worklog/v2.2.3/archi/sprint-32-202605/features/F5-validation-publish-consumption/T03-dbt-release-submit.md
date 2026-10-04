# T03: dbt release submit

**优先级**: P0
**状态**: READY
**依赖**: T02

## 目标

通过 platform 提交 dbt 发布，生成正式运行和发布记录。

## 技术设计

- `dts-metrics` 调用 platform `/api/etl/dbt/release/submit`。
- platform 负责 selector、target、vars、activeDept、运行证据和失败记录。
- metrics 只保存 publish reference。

## 影响范围

- `source/dts-platform` DbtReleaseSubmissionService
- `source/dts-metrics` MetricArtifactPublishService

## 验证

- [ ] platform 返回 BLOCKED/WARNING/SUBMITTED 时前端展示不同状态。
- [ ] metrics 不直接执行 dbt CLI。

## 完成标准

- [ ] 发布记录能关联 graph draft、artifact snapshot 和 dbt run。
