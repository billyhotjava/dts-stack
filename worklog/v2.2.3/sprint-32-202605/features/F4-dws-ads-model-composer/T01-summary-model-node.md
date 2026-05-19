# T01: 汇总模型节点

**优先级**: P0
**状态**: READY
**依赖**: F3

## 目标

在 React Flow 中创建 DWS/ADS 模型节点，承载输出表名、物化方式、刷新周期、维度和指标集合。

## 技术设计

- 支持 DWS 和 ADS 两类模型节点。
- 支持选择维度、指标、排序、输出字段别名。
- 输出表名和 schema 命名遵循 platform/dbt 命名规则。

## 影响范围

- `source/dts-metrics-webapp` model node editor
- `source/dts-metrics` model artifact domain

## 验证

- [ ] 未选择指标或维度时阻断生成。
- [ ] 非法表名给出前端和后端一致诊断。

## 完成标准

- [ ] 模型节点可生成候选 artifact。
