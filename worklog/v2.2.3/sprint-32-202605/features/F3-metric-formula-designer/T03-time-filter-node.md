# T03: 时间周期和过滤条件节点

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

把时间周期、统计窗口和业务过滤条件从表单提升为图节点。

## 技术设计

- time window 节点支持昨日、本周、本月、滚动 N 天、自定义表达式。
- filter 节点支持 =、!=、>、>=、<、<=、in、like、is_null、is_not_null。
- 过滤字段必须来自已授权资产字段或已验证维度。

## 影响范围

- `source/dts-metrics-webapp` filter/time node editor
- `source/dts-metrics` DSL generator

## 验证

- [ ] 过滤条件引用不存在字段时阻断。
- [ ] 时间字段未声明时阻断时间窗口指标。

## 完成标准

- [ ] 时间/过滤节点能进入 SQL/dbt artifact。
