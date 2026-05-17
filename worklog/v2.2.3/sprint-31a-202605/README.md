# Sprint-31A: 企业级数据资产事实源重构（202605）

**时间**: 2026-05
**状态**: CONTRACT_DONE / ENFORCEMENT_IN_PROGRESS
**类型**: Architecture / Implementation（dts-platform + dts-platform-webapp）
**目标**: 在 Sprint-31 主链路补齐和 Sprint-32 `dts-metrics` 独立服务之前，先把 `dts-platform` 的数据资产模块收敛为企业级唯一事实源，统一资产身份、生命周期、治理字段、权限校验、血缘入口和对外读取契约。

## 背景

Sprint-31 已经把 DTS 的企业级主链路定义为：

```text
数据源 / 文件 / API
  -> Connector Center
  -> ODS 落地契约
  -> dbt STG / DWD / DWS / ADS
  -> 发布门禁
  -> Catalog / OpenMetadata / OpenLineage
  -> platform asset_grant / 数据密级 / 审计 / 运行观测
  -> platform-webapp 基础版入口
  -> 独立增值服务：dts-metrics / dts-analytics
```

复盘后确认：如果不先把 platform 的数据资产能力升级为唯一事实源，`dts-metrics` 会继续依赖分散的 `semantic_*` 表、OpenMetadata cache、本地 Catalog fallback、dbt 生成物和 analytics 权限表，导致指标与语义中心无法成为稳定的企业级能力。

因此 Sprint-31A 作为前置 Sprint，先处理资产事实源问题，再进入 Sprint-31 和 Sprint-32。

## 核心原则

1. `dts-platform` 是资产、权限、审计、发布门禁和治理状态的唯一事实源。
2. `dts-metrics`、`dts-analytics`、SQL IDE 和 platform-webapp 只能通过 platform 资产契约读取资产，不直接读取内部表。
3. 自动发现或自动创建的资产必须有治理状态，不能默默成为可用资产。
4. 资产列表和详情都必须遵守密级、授权和治理可见性规则。
5. 本 Sprint 不迁出 IAM，不重写 OpenMetadata，不完成完整 `dts-metrics` 运行时迁移。
6. 按用户约束，本批工作在 Sprint-31A、Sprint-31、Sprint-32 全部实现前不做完整编译、镜像构建和容器重建；如评审问题需要代码级闭环，允许执行 focused contract/unit tests，并把证据归档到最终验收目录。

## Feature 列表

| ID | Feature | 优先级 | Task 数 | 状态 | 依赖 |
|----|---------|--------|---------|------|------|
| F1 | 资产身份和生命周期契约 | P0 | 5 | DONE | - |
| F2 | 资产治理字段和读取契约 | P0 | 5 | DONE | F1 |
| F3 | 血缘和来源证明收敛 | P0 | 5 | DONE | F1, F2 |
| F4 | 资产权限和密级一致性 | P0 | 5 | DONE | F1, F2 |
| F5 | 数据资产门户体验收敛 | P1 | 4 | DONE | F2, F4 |
| F6 | 迁移、兼容和验收闭环 | P0 | 5 | DONE | F1-F5 |
| RX | 架构评审追补项 | P0 | 5 | CONTRACT_DONE / ENFORCEMENT_IN_PROGRESS | F1-F6, Sprint-32 |

**统计**: READY=0, IN_PROGRESS=1, CONTRACT_DONE=1, DONE=33, BLOCKED=0

## Sprint-31 / Sprint-32 关系

Sprint-31A 完成后，Sprint-31 不再承担“资产事实源重构”，只负责基于企业级资产事实源补齐黄金链路、Connector Center、发布门禁、运行血缘和消费层权限。

Sprint-32 的 `dts-metrics` 必须只依赖 Sprint-31A 提供的 platform 资产契约：

```text
dts-metrics
  -> GET platform catalog assets / schema / columns / classification
  -> POST platform glossary term resolve
  -> POST platform asset permission check
  -> POST platform asset permission policy
  -> POST platform audit events
  -> POST platform dbt publish request
  -> POST platform BI dataset candidate/register
```

## 非目标

- 不把 IAM / 安全模块从 platform 剥离。
- 不重写 OpenMetadata 或替换 OpenMetadata。
- 不把完整语义指标运行时迁入 `dts-metrics`，该工作仍属于 Sprint-32。
- 不引入 SQLMesh / Dagster / Kestra 作为生产依赖。
- 不改变当前商务授权策略，license 后续独立收口。

## 完成标准

- [ ] 所有核心数据资产都有稳定 `asset_type + asset_key + asset_id` 映射。
- [ ] 自动创建资产必须进入明确生命周期状态，缺治理字段时为 `PENDING_GOVERNANCE`。
- [ ] Catalog / OpenMetadata / dbt / OpenLineage / Addax 写入的资产能落到同一读取契约。
- [ ] 资产列表和详情都按 `asset_grant`、密级和治理状态做一致过滤。
- [ ] dts-metrics 所需的只读 Catalog 契约、权限校验契约、发布网关契约有稳定 API。
- [ ] 数据资产门户能看到治理缺口、来源、血缘、权限和发布状态。
- [ ] 迁移脚本或 dry-run 报告能说明历史资产、语义模型和 BI/大屏资产如何映射。
- [ ] Sprint-31A / Sprint-31 / Sprint-32 的最终统一 review/test 清单明确，中间不执行编译。

## 相关材料

- Sprint-31: `worklog/v2.2.3/sprint-31-202605/README.md`
- Sprint-32: `worklog/v2.2.3/sprint-32-202605/README.md`
- 架构评审追补: `worklog/v2.2.3/sprint-31a-202605/assets/architect-review-integration-response.md`
- 资产权限设计: `docs/superpowers/specs/2026-03-28-unified-asset-permission-design.md`
- 大屏权限收敛设计: `docs/superpowers/specs/2026-03-30-screen-permission-refactor-design.md`
