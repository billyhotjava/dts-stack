# Frontend-first Review: Sprint-31A / Sprint-31 / Sprint-31B / Sprint-32

**日期**: 2026-05-18
**结论**: 现有代码已经有大量 platform 资产事实源、权限、审计、质量、血缘和 dts-metrics 契约，但前端闭环不足。后续验收必须以前端页面可操作为准。

## 一、总判断

| Sprint | 原状态 | 前端优先复核 | 结论 |
|---|---|---|---|
| Sprint-31A | CONTRACT_DONE / ENFORCEMENT_IN_PROGRESS | 资产门户有资产地图、明细台账、质量、血缘页面，但 assets-v2 contract/schema 的详情工作台还不完整 | CONTRACT_DONE，UI PARTIAL |
| Sprint-31 | DONE | 主链路和发布门禁页面有入口，但数据资产消费闭环依赖 Sprint-31A 资产详情和治理处置页 | BACKEND_DONE，UI PARTIAL |
| Sprint-31B | IN_PROGRESS | 已补 resolver failure 前端入口；仍需资产详情、治理缺口处置、数据产品 UI | IN_PROGRESS |
| Sprint-32 | DONE | platform 菜单已跳转 dts-metrics，但 dts-metrics 页面必须逐页验收真实操作，不能按 demo 视为 DONE | ROUTING_DONE，PRODUCT_UI_PENDING |

## 二、已补闭环

| 能力 | 后端 | 前端 | 状态 |
|---|---|---|---|
| 资产解析失败报告 | `GET /api/catalog/assets-v2/resolution-failures` | 资产地图“解析失败”弹窗 | DONE |
| capability 暴露 | `catalog.readEndpoints` | dts-metrics 可发现 endpoint | DONE |

## 三、主要缺口

### G1 assets-v2 资产详情仍未成为主工作台

当前资产地图已使用 `/api/catalog/assets-v2`，但详情链路仍分散在旧 `DatasetDetailPage`、`AssetDetailPage` 和旧 dataset API 中。企业级资产详情应以 assets-v2 contract/schema/governance/lineage 为主。

**要求**: `/catalog/datasets/:id` 必须展示资产事实源身份、字段契约、治理状态、血缘、质量 SLA 和权限审批。

### G2 数据产品 UI 仍是轻量 CRUD

`DataProductsPage` 当前主要是名称、代码、负责人、描述和状态；还不能配置成员资产、指标、SLA、消费入口和权限检查。

**要求**: 数据产品必须能打包 dataset + metric + dashboard/API，并展示成员数量、密级和刷新 SLA。

### G3 治理缺口和血缘失败没有处置流

资产地图能显示治理阻断和血缘缺口数量，但用户还不能直接点击某条缺口并执行补 owner、补密级、补分层、同步血缘、创建工单。

**要求**: 缺口列表必须可见、可筛选、可处理。

### G4 dts-metrics 页面必须逐页验收

platform-webapp 现在只保留菜单链接，这是正确边界；但 Sprint-32 不能只按“服务有壳 + 路由能跳转”验收。

**要求**: 指标资产列表、主题域映射、业务对象 Join、公式配置、DWS/ADS 生成、发布运行页必须都有真实 API 和核心动作。

## 四、执行顺序

1. Sprint-31B F6/T02: 先重构 assets-v2 资产详情工作台；
2. Sprint-31B F6/T04: 再补治理缺口和血缘失败处置流；
3. Sprint-31B F6/T03: 补数据产品成员配置 UI；
4. Sprint-31B F6/T05: 最后逐页验收并补齐 dts-metrics 页面。

## 五、验收口径

以后相关 Feature 标 DONE 前必须提供：

- 页面路径；
- 核心 API；
- 核心操作；
- 空态、错误态、无权限态；
- 前端构建或页面级测试证据。
