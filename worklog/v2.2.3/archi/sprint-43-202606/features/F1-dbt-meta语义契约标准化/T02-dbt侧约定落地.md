# T02: dbt 侧约定落地

**优先级**: P1
**状态**: READY
**依赖**: T01

## 目标
按 T01 规范，在 dbt 语义层样例模型补全新 meta，作为 F2/F3 的真实测试夹具。

## 技术设计
- 在 `services/dts-dbt/models/dws/semantic/schema.yml`（及相关 DWS/ADS schema.yml）：
  - 列补 `meta.semantic_type`（含 time 列标 `time`）+ `meta.standard_code`（维度列）。
  - model 补 `meta.grain: [stat_date, ...]`。
- 只补 meta，不改模型 SQL/物化；dbt compile 须仍通过。

## 影响范围
- `services/dts-dbt/models/**/schema.yml`（语义层样例）。

## 验证
- [ ] dbt compile/parse 通过（meta 不破坏）。
- [ ] 至少一个完整样例模型含 dimension/metric/time + grain + standard_code，供 F2/F3 测试。

## 完成标准
- [ ] 样例 meta 落地、dbt 解析通过、可作下游夹具。
