# Sprint-32 规划评审

## 结论

Sprint-32 方向成立，但必须按 MVP 收口执行。该 Sprint 的核心不是一次性做完完整低代码指标产品，而是把 `dts-metrics` 从 platform 中拆成可独立部署、可通过 platform 事实源治理的增值服务。当前版本不在配置层限制启停，商务限制后续由 license 模块统一处理。

## 已修正问题

| 严重级别 | 问题 | 修正 |
|---|---|---|
| P0 | Sprint-32 原完成标准过大，容易把服务拆分、BI 注册、dashboard 自动生成和生产迁移混成一个 Sprint | 增加 MVP / 延展目标，必达项收敛为服务独立、契约打通、最小行业包跑通 |
| P0 | 示例 `flower-rental` 包中 ADS 引用了高风险项目指标，但来源模型只声明了合同 DWS | 增加 `dws_flower_project_risk_month_summary`，ADS 显式引用合同和风险两个 DWS |
| P1 | `stat_month` 维度只绑定合同签约日期，无法服务项目风险对象 | 改为按业务对象声明来源字段：合同用 `sign_date`，项目用 `plan_end_date` |
| P1 | BI Dataset/Superset 和大屏自动生成容易被误解为 Sprint-32 必达 | 明确 Superset 远端注册、完整大屏自动生成属于延展目标 |
| P1 | 历史 `semantic_*` 迁移直接承诺生产执行风险过高 | 收口为 dry-run、映射报告和回滚设计，生产级自动迁移后续推进 |

## 必须守住的架构边界

- platform 是唯一事实源：IAM、租户、组织、角色、资产、数据源密钥、权限、审计、审批、事件和 dbt 发布门禁都保留在 platform。
- `dts-metrics` 不能直接读取 platform 用户表、角色表、权限表或数据源密钥。
- 合作方交付 `metric-pack`，不提交平台源码，不提交任意 SQL。
- `dts-metrics` 生成的是候选 artifact，最终发布必须经过 platform/dbt gate。
- 核心数据源、ELT、dbt、资产目录和 SQL/dbt 工作流不能依赖 metrics 内部实现。

## MVP 准入标准

- `dts-metrics` 容器可以独立启动并通过健康检查。
- 默认 compose 服务列表包含 `dts-metrics`，且服务可独立启动和健康检查。
- `dts-metrics` 使用服务鉴权调用 platform API。
- metric-pack v0.1 可以导入、校验、预览差异，并拒绝未登记资产和任意 SQL。
- 示例 `flower-rental` 包可以生成最小 DWS/ADS 候选 artifact。
- 预览、发布、BI 候选注册都必须经过 platform asset_grant 或 platform 发布契约。
- platform-webapp 通过 capability 和权限控制入口；license 接入前不做版本禁用提示。

## 后续实现风险

1. 服务拆分时不要复制 platform 的权限逻辑到 `dts-metrics`，否则会重新形成双事实源。
2. 兼容 `/api/semantic/**` 时要避免服务不可用后静默回退到旧逻辑，否则排障会失真。
3. DSL 第一版要宁可保守，不要开放 `raw_sql`。
4. metric-pack 的 schema 要先稳定，再做合作方配置台。
5. 现有 platform 语义数据迁移必须先 dry-run，确认实体映射和引用完整后再执行生产迁移。
