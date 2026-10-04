# Existing Page Cross-Domain Matrix

本矩阵把 v2.2.4 Sprint-2 的横切域思想映射到 v2.2.3 现有页面。后续编码默认只改这些页面或其已有组件/服务，不创建新的 `/v2` 页面壳。

| 横切域 | v2.2.4 思想 | v2.2.3 现有承载页面 | 改造方向 | 后端策略 |
|--------|-------------|--------------------|----------|----------|
| 字典域 | system types / reference codes / glossary / connectors 统一消费 | `/foundation/data-sources`, `/foundation/connectors`, `/governance/standards/reference`, `/governance/standards/glossary`, `/governance/standards/elements` | 统一下拉来源、空态、同步状态和“去维护字典”入口 | 优先复用 `/platform/dict/system-types`, `/governance/reference-codes`, `/modeling/glossary/terms`, `/infra/connectors` |
| 血缘域 | DataSource -> Dataset -> Metric 链路闭合 | `/catalog/assets`, `/catalog/datasets/:id`, `/catalog/lineage/*`, `/explore/etl/transform*`, `/studio/sql-modeling` | 资产详情展示来源、下游指标、血缘失败修复入口；ETL/SQL 建模页给出同步血缘交接点 | 优先复用 `/catalog/assets-v2/*/lineage`, `/catalog/datasets/*/lineage`, `/catalog/lineage/sync-addax` |
| 治理域 | 质量、授权、治理策略跨阶段复用 | `/governance/quality`, `/governance/rules`, `/governance/asset-grants`, `/catalog/assets`, `/catalog/datasets/:id`, `/studio/sql-modeling` | 资产、指标、模型页面统一显示质量/授权/门禁状态和下一步 | 优先复用 `/governance/quality/*`, `/asset-grants`, `/catalog/datasets/*/governance-health` |
| 元数据与标签 | OpenMetadata owner / tags / domain / profile 进入详情 | `/catalog/assets`, `/catalog/datasets/:id`, `/catalog/metadata`, `/catalog/search` | 资产详情和列表增加标签、域、负责人、profile 摘要，避免只在技术元数据页可见 | 优先复用 `/catalog/datasets/*/openmetadata`, `/catalog/datasets/openmetadata/batch`, `/catalog/assets-v2/*` |
| 状态编排 | Store 统一初始化、部门切换联动 | `/workbench`, `/foundation/data-sources`, `/catalog/assets`, `/studio/sql-modeling` | 不引入新 AppShell；在现有页面收敛查询参数、部门/主题筛选、详情抽屉刷新策略 | 只在页面重复请求造成真实问题时抽共享 hook/service |
| 主链路 | 四阶段从孤立模块变成完整体系 | `/workbench`, `/foundation/data-sources`, `/explore/etl/transform`, `/catalog/assets`, `/governance/*`, `/services/apis`, `/bi/*` | 工作台卡片和页面按钮串联“接入 -> 建模 -> 资产 -> 治理 -> 消费” | 优先路由和 query 上下文打通，后端按缺口补 |

## 页面优先级

| 优先级 | 页面 | 原因 |
|--------|------|------|
| P0 | `/catalog/assets` / `/catalog/datasets/:id` | 横切域汇聚点，客户最容易检查资产是否可信 |
| P0 | `/foundation/data-sources` | 接入阶段源头，字典和血缘源信息从这里开始 |
| P0 | `/studio/sql-modeling` | 标准、dbt、发布门禁和指标生产的主工作台 |
| P0 | `/governance/quality` / `/governance/rules` / `/governance/asset-grants` | 治理能力真实落点 |
| P1 | `/workbench` | 作为主链路导航和下一步引导，不承载所有细节 |
| P1 | `/catalog/lineage/*` / `/catalog/metadata` | 作为诊断和深挖页面，服务资产详情跳转 |
