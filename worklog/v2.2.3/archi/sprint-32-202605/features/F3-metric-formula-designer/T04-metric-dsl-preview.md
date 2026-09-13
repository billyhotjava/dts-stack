# T04: 指标 DSL 生成与预览

**优先级**: P0
**状态**: READY
**依赖**: T01,T02,T03

## 目标

从 React Flow 图生成可审计、可验证的指标 DSL，并展示 SQL 预览。

## 技术设计

- graph -> DSL adapter 输出 subject/object/join/dimension/metric/filter/model。
- DSL -> SQL preview 先由 metrics 本地生成，只做只读预览。
- 权威模型检测仍提交 platform model validation gateway。

## 影响范围

- `source/dts-metrics` graph-to-dsl / sql generator
- `source/dts-metrics-webapp` DSL preview panel

## 验证

- [ ] DSL snapshot 可做 source contract 测试。
- [ ] SQL preview 禁止 DDL/DML。

## 完成标准

- [ ] 每次预览结果可追溯到 graph draft version。
