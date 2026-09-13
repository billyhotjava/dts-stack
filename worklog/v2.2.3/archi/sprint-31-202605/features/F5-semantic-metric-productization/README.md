# F5: 语义指标服务拆分准备

**优先级**: P0
**状态**: DONE
**目标**: 在不影响 Sprint-31 主链路交付的前提下，把当前 platform 内的语义指标能力收口为可拆分边界，为 Sprint-32 独立 `dts-metrics` 服务做准备。当前版本不通过配置关闭 metrics，商务限制后续由 license 模块统一承接。

**Sprint-31A 依赖**: 本 Feature 只允许定义 `dts-metrics` 拆分边界，所有资产引用、权限校验、审计和发布动作都必须回到 Sprint-31A 的 platform 契约。

## 任务

| Task | 内容 | 验收 |
|---|---|---|
| T01 | 能力分层和 capability 契约 | DONE: `/api/capabilities` 明确 metrics 是可选增值服务，主链路不依赖其内部实现 |
| T02 | platform/metrics 契约清单 | DONE: capability 暴露 platformOwned / metricsOwned / requiredPlatformContracts |
| T03 | 现有语义表和接口盘点 | DONE: 迁移边界文档定义 platform 兼容代理与 dts-metrics 目标归属 |
| T04 | 指标 DSL v1 边界草案 | DONE: 指标 DSL v1 限定受控聚合、条件和 ratio，不接受任意 SQL |
| T05 | metric-pack v0.1 草案 | DONE: 合作方交付包定义 manifest、domains、objects、dimensions、metrics、models、datasets、dashboards |
| T06 | Sprint-32 迁移计划 | DONE: Sprint-32 已拆成服务骨架、平台契约、DSL、metric-pack、前端入口、迁移 IT |

## 代码关注点

- `SemanticModelingService`
- `SemanticModelingResource`
- `SemanticMetricDesignerPage`
- `SemanticDatasetsPage`
- `SemanticPublishPage`

## 非目标

- 不在 Sprint-31 完成完整 `dts-metrics` 运行时迁移；容器落地进入 Sprint-32。
- 不把语义指标权限迁出 platform；platform 仍是权限事实源。
- 不让合作方接触平台源码；合作方交付物先收敛为 `metric-pack` 配置包。

## 交付记录

- platform capability 明确 `metrics.serviceBoundary=optional-value-added-service`，基础版黄金链路可只依赖数据源、ELT、dbt、Catalog 和 asset_grant。
- `platformOwned` 固化为 IAM、asset_grant、catalog、data-source、audit、dbt publish gateway。
- `metricsOwned` 固化为 semantic-domain、business-object、metric-dsl、DWS/ADS 候选 artifact 和 metric-pack validation。
- Sprint-32 继续承接独立 `dts-metrics` 服务实现；Sprint-31 只固定边界和迁移策略。
- 交付契约见 `../../assets/semantic-metric-productization-boundary.md`。
