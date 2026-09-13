# T03: Schema/Column 读取契约

**优先级**: P0
**状态**: DONE
**依赖**: T02

## 目标

为 SQL IDE 和 dts-metrics 提供可控字段读取接口，支撑指标公式、维度选择、schema contract 和字段级血缘。

## 技术设计

- 新增 `GET /api/catalog/assets-v2/{id}/schema-contract`，输出资产契约和字段契约。
- OpenMetadata 字段缓存优先；如果 OM 字段为空且存在 DTS 原生资产映射，则 fallback 到本地 Catalog schema/column。
- 字段契约包含字段名、类型、注释、nullable、顺序、普通标签、敏感标签、标准映射、状态、来源和更新时间。
- 读取前复用资产可见性检查，无权访问返回 `404`。

## 影响范围

- CatalogTableSchema
- CatalogColumnSchema
- SQL IDE catalog API
- dts-metrics platform contract
- `worklog/v2.2.3/sprint-31a-202605/assets/catalog-schema-column-read-contract.md`

## 验证

- [x] 无资产权限时不能枚举字段。
- [x] 字段顺序和类型稳定。
- [ ] 统一测试在 Sprint-31A/31/32 完成后执行。

## 完成标准

- [x] dts-metrics 可以只靠该接口建立业务对象字段候选。
- [x] 字段读取契约文档已写入 `assets/catalog-schema-column-read-contract.md`。
