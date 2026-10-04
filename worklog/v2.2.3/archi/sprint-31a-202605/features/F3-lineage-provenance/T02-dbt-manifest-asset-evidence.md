# T02: dbt manifest 资产同步证据

**优先级**: P0
**状态**: DONE
**依赖**: F1, F2

## 目标

dbt manifest 同步时写入模型资产、字段、schema contract、owner/classification 和构建证据。

## 技术设计

- 复用现有 dbt asset sync，补充 manifest 资产证据。
- 新建或同步资产时补 `snapshotTime`，缺生命周期时进入 `PENDING_GOVERNANCE`。
- 缺描述时写入 `uniqueId/schema/table/layer/assetKey/originalFilePath` 证据。
- 表级血缘 notes 写入 `uniqueId/project/upstreamDatasetId/downstreamDatasetId`。
- 同步统计和审计 payload 增加 `manifestEvidenceUpdated`。

## 影响范围

- DbtAssetSyncService
- CatalogColumnSyncService
- dbt release gate
- `worklog/v2.2.3/sprint-31a-202605/assets/dbt-manifest-asset-evidence.md`

## 验证

- [x] dbt model 同步后能在资产门户被查询。
- [x] 缺 schema contract 时状态可追踪。
- [ ] 统一测试在 Sprint-31A/31/32 完成后执行。

## 完成标准

- [x] DWD/DWS/ADS 资产成为指标中心可信输入。
