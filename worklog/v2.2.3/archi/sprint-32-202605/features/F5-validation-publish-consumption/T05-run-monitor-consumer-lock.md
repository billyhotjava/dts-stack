# T05: 运行监控和消费关系

**优先级**: P1
**状态**: READY
**依赖**: T04

## 目标

展示模型运行状态、失败原因、重试入口和下游消费关系。

## 技术设计

- 运行记录来自 platform/dbt run evidence。
- 下游消费关系来自 BI Dataset、大屏、API 查询引用。
- 修改已消费指标时提示 consumer lock。

## 影响范围

- `source/dts-platform` run evidence / consumer relation
- `source/dts-metrics-webapp` run monitor page

## 验证

- [ ] 失败运行能看到 dbt error 摘要。
- [ ] 已消费指标改口径需要新版本或审批。

## 完成标准

- [ ] 运维能定位模型失败属于 graph、contract、dbt 还是 publish。
