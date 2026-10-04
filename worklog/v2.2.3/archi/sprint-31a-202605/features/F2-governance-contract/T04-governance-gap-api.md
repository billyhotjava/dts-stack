# T04: 治理缺口和认证状态 API

**优先级**: P1
**状态**: DONE
**依赖**: T01

## 目标

让平台能直接识别资产治理缺口，并给资产门户和最终验收脚本提供治理报告。

## 技术设计

- 新增 `GET /api/catalog/assets-v2/governance-gaps`，复用资产列表筛选参数。
- 计算缺 owner、classification、warehouseLayer、sourceSystem、domain、schema contract、lineage、freshness 的资产。
- 输出 `BLOCKING / WARNING / READY` 缺口等级，并返回 `severityCounts` 与 `gapCounts`。
- 缺口规则封装为 `CatalogGovernanceGapEvaluator`，后续发布门禁和 IT 验收脚本可复用。

## 影响范围

- Catalog governance service
- asset portal summary
- IT evidence
- `worklog/v2.2.3/sprint-31a-202605/assets/catalog-governance-gap-api.md`

## 验证

- [x] 缺口统计和资产列表可互相追踪。
- [x] 治理缺口不会被前端硬编码替代。
- [ ] 统一测试在 Sprint-31A/31/32 完成后执行。

## 完成标准

- [x] Sprint-31 发布门禁可复用该缺口结果。
