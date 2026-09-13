# T01: OpenLineage 资产解析增强

**优先级**: P0
**状态**: DONE
**依赖**: F1

## 目标

让 OpenLineage 接收端优先解析已有 Catalog 资产，无法解析时创建 `PENDING_GOVERNANCE` 资产并记录来源证明。

## 技术设计

- OpenLineage 接收端返回 `assetEvidence`，包含 `datasetId / assetKey / rawName / namespace / created / resolvedBy`。
- 不可解析时自动创建 `PENDING_GOVERNANCE` 资产，并在 description 写入 raw namespace/name、job、run id。
- 血缘 notes 写入 upstream/downstream assetKey 和 run id，便于追溯。
- 自动创建资产不默认 `ACTIVE`。

## 影响范围

- OpenLineageReceiverResource
- Catalog lineage service
- `worklog/v2.2.3/sprint-31a-202605/assets/openlineage-asset-resolution-evidence.md`

## 验证

- [x] 已有资产不会重复创建。
- [x] 未治理资产有明确状态。
- [ ] 统一测试在 Sprint-31A/31/32 完成后执行。

## 完成标准

- [x] OpenLineage 成为资产事实链的一部分。
