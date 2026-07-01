# Sprint-55: 黄金线菜单架构重排

**时间**: 2026-06  
**状态**: DONE  
**目标**: 以 dts-admin 菜单种子为事实源，将 portal 侧栏重排为“数据基础 -> 数据集成 -> 数据开发 -> 指标建模 -> 数据资产 -> 数据消费 -> 治理运营 -> 运维与监控”的黄金线信息架构。

## 背景

Sprint-54 已完成指标建模页面和主题域引用收敛，但侧边栏仍把指标建模压在数据开发中心子层，把主题域/标准与质量治理混在数据治理中心，并把 API 服务、BI 和大屏分散在不同一级入口下。用户在黄金线“数据源 -> 接入 -> 资产 -> 指标 -> 消费端”上寻找能力时，菜单顺序与真实依赖关系不一致。

本 sprint 只调整菜单信息架构，不改现有 canonical 页面路由。dts-admin 的 `portal-menu-seed.json` 是菜单种子事实源；运行态 DB 通过 Liquibase reparent 保留原 menu id 和角色可见性绑定。

## Feature 列表

| ID | Feature | Task 数 | 状态 |
|----|---------|---------|------|
| F1 | 黄金线菜单架构收敛 | 3 | DONE |

## 完成标准

- [x] 数据基础独立承载主题域、标准管理和标准模板。
- [x] 指标建模从数据开发中心提升为一级分区，且不恢复主题域/运行监控独立菜单。
- [x] 数据消费统一承载数据服务中心、商业智能应用和数据大屏。
- [x] 治理运营只承载质量、安全、分类等横切运营治理入口。
- [x] Liquibase reparent 迁移保留原 menu id，不删除 `portal_menu_visibility`。
- [x] source-contract、JSON/XML 校验、运行态 DB smoke 通过。

## 决策

- 主题域和数据标准是前置定义层，放入“数据基础”；指标建模只引用 `/governance/subjects`。
- “数据集成”继续复用现有 `resource` section key，保留连接器、数据源、驱动、元数据采集、入湖配置。
- “指标建模”复用原 `metric-modeling` 节点并 reparent 到根分区，避免新建重复菜单。
- “数据消费”新建根分区，reparent 原 `services`、`bi-apps`、`screens` 节点，保留 API/BI/大屏原页面路由。
- “治理运营”复用原 `governance` section key，保留质量管控、质量报告、分级分类。
- 历史遗留 `semantic-runs` 菜单在本 sprint 退役；运行监控能力后续统一归入“运维与监控/任务运维中心”，保留页面路由兼容但不在指标建模菜单暴露。
