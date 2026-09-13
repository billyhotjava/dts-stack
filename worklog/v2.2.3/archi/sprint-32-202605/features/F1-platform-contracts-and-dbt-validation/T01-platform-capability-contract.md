# T01: platform 能力契约清单

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: 无

## 目标

列清楚 React Flow 指标语义工作台需要 `dts-platform` 提供哪些事实源和控制面能力。

## 技术设计

- 固化资产、字段、权限、RLS、治理解析、dbt 验证、发布、BI Dataset、血缘、审计、审批和 capability 的职责归属。
- 输出 `dts-metrics` 可调用的 internal API 清单。
- 明确哪些能力已有、哪些需要新增、哪些只需封装为聚合 API。

## 影响范围

- `worklog/v2.2.3/sprint-32-202605/assets/react-flow-metrics-contract.md`
- `source/dts-platform`
- `source/dts-metrics`

## 验证

- [ ] 文档明确回答“platform 提供什么”。
- [ ] 文档明确回答“检测模型调用 platform 还是 dbt”。
- [ ] 后续任务能按契约拆分 API 和 UI。

## 完成标准

- [ ] 契约清单被 Sprint-32 README 和 service design 引用。
