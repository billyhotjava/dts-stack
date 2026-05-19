# F4: DWS/ADS 模型编排与 artifact 生成

**优先级**: P0
**状态**: READY

## 目标

把已验证指标组合成同粒度 DWS/ADS 候选模型，并生成 dbt SQL、schema.yml、exposure/metric 文档和 lineage hint。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 汇总模型节点 | P0 | READY | F3 |
| T02 | 同粒度兼容规则 | P0 | READY | T01 |
| T03 | dbt artifact generator | P0 | READY | T02 |
| T04 | artifact diff 与版本 | P1 | READY | T03 |
| T05 | validation report 映射 | P0 | READY | T03 |

## 完成标准

- [ ] DWS/ADS 只能由已验证指标和维度组成。
- [ ] 生成物必须先进入 platform/dbt validation gateway。
- [ ] 验证失败能定位到模型节点、指标节点或字段节点。
