# Sprint-31 / Sprint-32 依赖重排

**Sprint**: Sprint-31A
**Feature**: F6/T04
**状态**: DONE

## 调整后的顺序

```text
Sprint-31A
  -> platform 资产事实源、权限、血缘、读取契约

Sprint-31
  -> 基于 Sprint-31A 契约补齐企业级数据平台主链路

Sprint-32
  -> 基于 Sprint-31A/Sprint-31 契约落地 dts-metrics 独立服务
```

## Sprint-31 依赖

| Feature | 依赖调整 |
|---|---|
| F1 黄金链路契约 | 依赖 Sprint-31A 资产身份/治理/权限契约 |
| F3 运行血缘 | 写入 Sprint-31A 资产事实源，不自建资产身份 |
| F4 dbt 发布门禁 | 使用 Sprint-31A schema contract / governance gaps / lineage failures |
| F5 语义指标拆分准备 | 只定义拆分边界，不绕过 platform asset contract |
| F6 消费层权限 | 使用 Sprint-31A `asset_grant` 和 classification deny 语义 |

## Sprint-32 依赖

| Feature | 依赖调整 |
|---|---|
| F1 服务骨架 | 服务默认部署，但能力 readiness 依赖 platform capabilities |
| F2 platform 契约 | 直接消费 Sprint-31A capabilities/catalog/permission/audit/dbt publish |
| F3 指标领域模型 | 所有来源资产引用必须来自 platform asset contract |
| F4 metric-pack | 包校验必须调用 platform asset contract 和 permission check |
| F5 webapp routing | platform-webapp 保留入口，页面由 dts-metrics 承载 |
| F6 迁移 IT | 先 dry-run，再执行迁移，不默认破坏历史 semantic 数据 |

## 协作边界

- 平台团队负责 platform 资产事实源、权限、审计、发布网关。
- 合作方/业务团队通过 metric-pack、指标口径文档和客户需求讨论交付指标，不接触平台源码。
- dts-metrics 团队只消费 platform 契约，不读取 platform 内部表。
