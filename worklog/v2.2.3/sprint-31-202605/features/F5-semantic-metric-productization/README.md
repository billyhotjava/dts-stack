# F5: 语义指标服务拆分准备

**优先级**: P0
**状态**: READY
**目标**: 在不影响 Sprint-31 主链路交付的前提下，把当前 platform 内的语义指标能力收口为可拆分边界，为 Sprint-32 独立 `dts-metrics` 服务做准备。当前版本不通过配置关闭 metrics，商务限制后续由 license 模块统一承接。

## 任务

| Task | 内容 | 验收 |
|---|---|---|
| T01 | 能力分层和 capability 契约 | 指标语义能力从 platform 主链路解耦；数据源、ELT、资产、SQL/dbt 不依赖 metrics 内部实现 |
| T02 | platform/metrics 契约清单 | 明确 platform 保留 IAM、asset_grant、catalog、data-source、audit、dbt publish gateway |
| T03 | 现有语义表和接口盘点 | 输出哪些实体/API 迁移到 `dts-metrics`，哪些保留在 platform 作为兼容代理 |
| T04 | 指标 DSL v1 边界草案 | 只定义受控公式 DSL 和安全规则，不在 Sprint-31 承诺完整运行时迁移 |
| T05 | metric-pack v0.1 草案 | 定义合作方可交付的 manifest、domains、objects、dimensions、metrics、datasets、dashboards |
| T06 | Sprint-32 迁移计划 | 完成 `dts-metrics` 服务拆分、部署、IT、回滚和兼容策略的 Sprint-32 定义 |

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
