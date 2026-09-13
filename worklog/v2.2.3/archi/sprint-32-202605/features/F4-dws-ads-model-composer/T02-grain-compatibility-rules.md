# T02: 同粒度兼容规则

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

确保同一个 DWS/ADS 模型内的指标、维度和时间窗口粒度兼容。

## 技术设计

- 校验维度集合、时间周期、业务主键和来源对象。
- 对跨对象指标要求显式 join path。
- 对 fanout 风险要求审批或阻断。

## 影响范围

- `source/dts-metrics` model validation service
- `source/dts-metrics-webapp` validation panel

## 验证

- [ ] 不同时间窗口指标不能无声明混入同一模型。
- [ ] fanout 风险在模型节点上可见。

## 完成标准

- [ ] 粒度规则进入 graph preflight。
