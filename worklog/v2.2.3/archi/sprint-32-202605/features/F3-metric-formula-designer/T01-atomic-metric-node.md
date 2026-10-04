# T01: 原子指标节点

**优先级**: P0
**状态**: READY
**依赖**: F2

## 目标

支持从字段生成原子指标节点。

## 技术设计

- 支持 count、count_distinct、sum、avg、min、max、count_if、sum_if。
- 节点保存 source field、aggregation、unit、format、term ids、owner。
- ratio 分母保护作为公式生成默认规则。

## 影响范围

- `source/dts-metrics-webapp` metric node editor
- `source/dts-metrics` metric DSL model

## 验证

- [ ] 字段拖入画布可创建指标节点。
- [ ] 缺 glossary term 的指标不能发布。

## 完成标准

- [ ] 原子指标可参与 DWS/ADS 生成。
