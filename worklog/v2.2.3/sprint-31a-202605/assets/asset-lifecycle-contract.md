# Sprint-31A F1/T03 资产生命周期状态契约

**状态**: DONE
**日期**: 2026-05-17

## 生命周期状态

| 状态 | 含义 |
|---|---|
| DISCOVERED | 已发现但尚未纳入治理判断 |
| PENDING_GOVERNANCE | 已入库但缺治理字段，不能默认为可信可用 |
| ACTIVE | 已治理且可作为主链路消费资产 |
| DEPRECATED | 已废弃但仍可查询历史 |
| ARCHIVED | 已归档，不参与默认消费 |
| BLOCKED | 因质量、权限、血缘或治理缺口被阻断 |
| PENDING_REVIEW | 等待治理或发布审核 |

## 已落地代码

- `CatalogAssetLifecycleStatus`: 生命周期枚举和兼容归一方法。
- `CatalogAssetGovernanceStatus`: 治理状态枚举。
- `CatalogAssetGovernancePolicy`: 根据资产字段解析治理状态。
- `OpenLineageReceiverResource.createDataset`: OpenLineage 自动创建资产时设置 `PENDING_GOVERNANCE`。
- `CatalogAssetPortalService.resolveLegacyGovernanceStatus`: 复用统一治理策略。
- `CatalogAssetGovernancePolicyTest`: 生命周期和治理状态回归测试，按执行约束暂不运行。

## 关键策略

- 自动发现资产不能默认 `ACTIVE`。
- 缺 owner、classification、domain 三类关键信息时统一显示 `PENDING_GOVERNANCE`。
- 只缺某一类字段时保留更具体原因，如 `PENDING_CLASSIFICATION`。
- 暂不修改 `CatalogDataset` 实体结构；GitNexus 显示该实体影响面为 CRITICAL，实体字段重构留到有 migration 设计后处理。
