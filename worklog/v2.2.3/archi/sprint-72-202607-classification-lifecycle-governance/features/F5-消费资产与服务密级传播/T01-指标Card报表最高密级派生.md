# T01: 指标、Card、报表最高密级派生

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: F3-T01

## 目标

从指标表达式、模型字段、Card 查询和报表引用计算全部分析资产的最高密级。

## 技术设计

- 原子指标取绑定 ModelSpec revision/字段密级。
- 派生指标取全部上游指标和字段 max。
- Card/报表取 SQL、dataset、metric、join 全部来源 max。
- 保存来源边和 snapshot version，不允许手工低值覆盖。

## 影响范围

`dts-metrics`、platform indicator、analytics Card/Dashboard、BI report metadata。

## 验证

- [ ] 原子/派生/跨模型指标、Card join、报表组合。
- [ ] 表达式变化触发重算。

## 完成标准

- [ ] 指标与报表详情可显示有效密级来源。
- [ ] 缺来源时阻断发布而非默认为低级。
