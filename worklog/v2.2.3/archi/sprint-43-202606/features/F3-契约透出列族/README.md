# F3: schema-contract + assets-v2 透出列族

**优先级**: P0
**状态**: READY
**依赖**: F2（列契约携 meta）

## 目标
由列 meta 派生并透出列族——`dimensionColumns/metricColumns/timeColumns/grain/standardCodes`——在 `CatalogAssetSchemaContract` 与 `/catalog/assets-v2` 响应中，供建模/可视化消费。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | schema-contract 列族派生（service 统一派生） | P0 | READY | F2-T01 |
| T02 | assets-v2 透出列族（含 grain/standardCodes） | P0 | READY | T01 |

## 完成标准
- [ ] `CatalogAssetSchemaContract`（或新增富化视图）按列 `semanticType` 派生 dimension/metric/time 列族 + standardCodes；grain 取自 model meta/平台 `semantic_model.grain`。
- [ ] `/catalog/assets-v2` 资产项透出列族（替换当前缺失），命名与 dts-metrics `VisualAssetSummary` 对齐。
- [ ] 无 meta 资产回退空列族（不破现有消费）。
