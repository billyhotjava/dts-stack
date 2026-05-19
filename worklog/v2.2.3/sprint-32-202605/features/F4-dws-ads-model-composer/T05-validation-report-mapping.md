# T05: validation report 映射

**优先级**: P0
**状态**: READY
**依赖**: T03

## 目标

把 platform/dbt validation gateway 返回的错误映射回 React Flow 节点和边。

## 技术设计

- 统一诊断字段：nodeId、edgeId、fieldId、metricCode、artifactPath、severity、message、remediation。
- 前端节点角标展示 error/warn/pass。
- 诊断列表支持点击定位到画布节点。

## 影响范围

- `source/dts-platform` validation report DTO
- `source/dts-metrics` validation client
- `source/dts-metrics-webapp` diagnostics UI

## 验证

- [ ] dbt compile error 能定位到模型节点。
- [ ] 缺字段 error 能定位到字段节点。

## 完成标准

- [ ] 用户能从验证失败直接回到修复位置。
