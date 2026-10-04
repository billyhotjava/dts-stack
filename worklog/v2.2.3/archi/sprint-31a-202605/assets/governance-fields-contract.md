# Sprint-31A F2/T01 治理字段完整性补齐

**状态**: DONE
**日期**: 2026-05-17

## 目标

在不立即修改核心实体字段的前提下，先提供统一治理字段检查器，明确哪些资产缺字段、是否可消费。

## 第一批治理字段

| 字段 | 来源 | 用途 |
|---|---|---|
| owner / ownerDept | CatalogDataset / CatalogAssetExtension | 责任主体和部门治理 |
| classification | CatalogDataset / CatalogAssetExtension | 密级和权限判断 |
| warehouseLayer | CatalogDataset / CatalogAssetExtension | ODS/DWD/DWS/ADS 层级 |
| sourceSystem | sourceId / hiveDatabase / OM serviceName | 来源证明 |
| domain | CatalogDomain / extension.domainId | 业务域归属 |
| lifecycleStatus | CatalogDataset / extension.lifecycleStatus | 消费和发布状态 |
| expectedRefreshIntervalMinutes | 资产契约 / dbt manifest meta / 手工治理 | 期望刷新周期 |
| maxStalenessMinutes | 资产契约 / 手工治理 | 最大可接受陈旧时间 |
| lastObservedAt | 运行血缘 / dbt run / OpenLineage | 最近观测时间 |
| terms[] | Glossary / ModelingGlossaryTerm | 业务术语绑定 |

## 已落地代码

- `CatalogAssetGovernanceProfile`: 治理检查结果。
- `CatalogAssetGovernanceInspector`: 基于现有字段输出 missingFields、governanceStatus、lifecycleStatus、consumable。
- `CatalogAssetGovernanceInspectorTest`: 治理缺口回归测试，按执行约束暂不运行。

## 策略

- `ACTIVE` 生命周期本身不足以代表可消费。
- 可消费必须同时满足：
  - enabled=true
  - lifecycleStatus=ACTIVE
  - 无缺失治理字段
- 当前不改 `CatalogDataset` 实体结构，避免 CRITICAL 影响面。

## 架构评审追补

- Freshness/SLA 先作为契约字段进入资产治理检查和对外 contract；实体列扩展放到后续迁移。
- Glossary 不再作为独立孤岛：资产、字段、指标都必须能暴露 `terms[]`，metric-pack 的 inline metric 已先强制 `term_ids`。
- 质量规则、异常检测和成本归因在当前版本先通过 capability/contract 描述，不在 Sprint-31A 内新增完整运行时。
