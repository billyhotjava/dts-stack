# T01: 治理字段完整性补齐

**优先级**: P0
**状态**: DONE
**依赖**: F1

## 目标

统一 owner、ownerDept、steward、classification、warehouseLayer、sourceSystem、lifecycleStatus、certificationStatus 等治理字段。

## 技术设计

- 识别 CatalogDataset、CatalogTableSchema、QueryDataset 等实体已有字段。
- 缺失字段通过兼容式 migration 补齐。
- 自动资产缺字段时进入 `PENDING_GOVERNANCE`。

## 影响范围

- Liquibase changelog
- Catalog entity / DTO
- asset portal API

## 验证

- [x] 缺字段历史资产不会被误判为已治理。
- [x] 新资产创建时治理字段可通过 inspector 识别缺口。
- [ ] 最终统一测试阶段运行 `CatalogAssetGovernanceInspectorTest`。

## 完成标准

- [x] 治理字段 profile 已可作为资产 API 的稳定输出；API 接入在 F2/T02-T04。
