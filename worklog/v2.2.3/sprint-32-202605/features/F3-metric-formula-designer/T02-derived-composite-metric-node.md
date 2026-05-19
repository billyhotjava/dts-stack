# T02: 衍生/复合指标节点

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

支持基于已有指标组合衍生指标和复合指标。

## 技术设计

- 衍生指标 = 原子指标 + 维度 + 时间周期 + 过滤条件。
- 复合指标 = 同粒度指标之间的 ratio、差值、占比、排名。
- 校验同粒度、同时间窗、无循环依赖。

## 影响范围

- `source/dts-metrics` metric dependency graph
- `source/dts-metrics-webapp` metric formula panel

## 验证

- [ ] 循环依赖被阻断。
- [ ] 不同粒度复合指标给出可读诊断。

## 完成标准

- [ ] 复合指标生成 SQL 时有除零保护。
