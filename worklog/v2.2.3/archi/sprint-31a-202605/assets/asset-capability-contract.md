# 数据资产事实源能力契约

## CAPABILITY

Sprint-31A 交付后，`dts-platform` 对内成为数据资产事实源，对外为 `dts-metrics`、`dts-analytics`、SQL IDE、platform-webapp 提供稳定资产读取、权限校验、血缘查询、治理状态和发布门禁契约。业务增值服务不再直接拼接内部 Catalog 表、OpenMetadata cache 或本地权限表。

## CONSTRAINTS

- platform 继续持有 IAM、组织、角色、asset_grant、审计、审批、事件和 dbt 发布网关。
- 自动发现资产可以被记录，但不能默认成为可用资产；缺 owner、classification、warehouseLayer、sourceSystem 任一关键治理字段时进入 `PENDING_GOVERNANCE`。
- 资产列表与详情的可见性必须一致，不能列表暴露、详情拦截。
- 资产密级不是前端展示字段，而是访问控制和发布治理字段。
- OpenMetadata 是外部元数据来源之一，本地 Catalog 是 platform 的服务契约，不应把二者暴露成两个并列事实源。
- dts-metrics 只能引用 platform 已登记并且授权可见的资产。

## IMPLEMENTATION CONTRACT

### Actors

- 平台管理员：维护数据源、资产治理规则、权限和发布门禁。
- 数据工程师：通过 dbt / Connector / SQL IDE 生成或发布资产。
- 数据治理人员：补齐 owner、classification、warehouseLayer、生命周期和业务域。
- 指标建模人员：在 dts-metrics 中引用可信资产创建指标和 DWS/ADS。
- BI 使用者：在 analytics / platform-webapp 中消费有权限的资产和数据集。

### Surfaces

- `dts-platform` 后端：资产身份、生命周期、治理字段、血缘、权限校验、发布门禁 API。
- `dts-platform-webapp`：资产地图、资产详情、治理缺口、权限申请、血缘影响分析。
- `dts-metrics`：只读调用资产契约，不直接访问 platform 表。
- `dts-analytics`：只读调用 asset_grant 和 asset metadata，不再维护最终权限事实源。

### States

```text
DISCOVERED
  -> PENDING_GOVERNANCE
  -> ACTIVE
  -> DEPRECATED
  -> ARCHIVED
```

补充状态：

- `BLOCKED`: 资产因质量门禁、权限、血缘或治理缺口被阻断。
- `PENDING_REVIEW`: 发布或治理变更等待审核。

### Interfaces

```text
GET  /api/catalog/assets-v2
GET  /api/catalog/assets-v2/{assetId}/contract
GET  /api/catalog/assets-v2/{assetId}/schema-contract
GET  /api/catalog/assets-v2/{assetId}/lineage
GET  /api/catalog/assets-v2/governance-gaps
GET  /api/catalog/assets-v2/lineage-failures
GET  /api/catalog/assets-v2/migration/dry-run
POST /api/internal/asset-permission/check
POST /api/internal/audit-events
POST /api/internal/dbt/publish-requests
POST /api/internal/bi/datasets/register
GET  /api/internal/capabilities
```

### Data Implications

- 建立稳定资产身份规则：`asset_type + asset_key` 唯一，`asset_id` 为内部 UUID。
- 补齐资产治理字段：owner、ownerDept、steward、classification、warehouseLayer、sourceSystem、lifecycleStatus、certificationStatus。
- 为 OpenMetadata、dbt manifest、OpenLineage、Addax writeback 保留来源证明和 fallback reason。
- 历史 `semantic_*`、BI dataset、screen 资产保留映射表或 dry-run 报告，不在 Sprint-31A 强制物理删除。

## NON-GOALS

- 不迁出 IAM。
- 不替换 OpenMetadata。
- 不实现完整指标 DSL。
- 不生成完整 DWS/ADS 运行时。
- 不重做 BI 大屏设计器。

## OPEN QUESTIONS

- 历史资产缺 owner/classification 时，是否允许批量默认标记为 `INTERNAL`，还是必须人工治理后 `ACTIVE`。
- `asset_type` 是否一次性扩展到 `METRIC`，还是先只保留 `DATASET/MODEL/BI_DATASET/SCREEN` 并为 Sprint-32 预留。
- OpenMetadata 和本地 Catalog 同名冲突时，以哪个字段作为最终 display name。

## HANDOFF

Sprint-31A 先实现 platform 资产事实源和 API 契约；Sprint-31 基于该契约补黄金链路；Sprint-32 的 dts-metrics 只能消费该契约，不再直接依赖 platform 内部表。
