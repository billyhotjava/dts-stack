# T01: schema-contract 列族派生

**优先级**: P0
**状态**: READY
**依赖**: F2-T01

## 目标
在平台 service 统一由列 `semanticType` 派生列族，透出到 schema-contract。

## 技术设计
- `CatalogAssetPortalService.getAssetSchemaContract`（或富化扩展）：遍历列 `semanticType` → 分组 `dimensionColumns/metricColumns/timeColumns`；收集 `standardCodes`；`grain` 取 model 级 meta 或平台 `semantic_model.grain`。
- 派生逻辑放 service（单一来源），消费端只读。
- 纯函数派生器可单测（输入列列表 → 列族）。

## 影响范围
- `dts-platform`：`CatalogAssetPortalService` / `CatalogAssetSchemaContract`（或新富化 DTO）。

## 验证
- [ ] dimension/metric/time 列正确分组；standardCodes 收集；grain 取得。
- [ ] 无 meta → 空列族。

## 完成标准
- [ ] 派生器 + service 接通、单测覆盖、`clean test` 通过。
