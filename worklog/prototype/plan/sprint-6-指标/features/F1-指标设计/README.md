# F1: 指标设计

**优先级**: P0
**状态**: READY

## 目标

在阶段④「指标」内落地指标设计的完整闭环：指标中心/列表（浏览 + 定义）、指标模板/商店（复用 + 一键起建）、指标看板/我的看板（消费 + 个性化）。把现网 governance 筒仓的 `IndicatorCenter/List/Store/Template/Dashboard/MyDashboard` 六个页面收口进阶段④同一入口。这是阶段④面向普通用户的主操作面——指标在此层定义与消费，**底层 dbt 完全隐藏**。命名对齐现网 `IndicatorCenterPage` / `IndicatorListPage` / `IndicatorStorePage` / `IndicatorTemplatePage` / `IndicatorDashboardPage` / `MyDashboardPage`，service 用 `indicatorsService`（取数走 `olapService`）。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| [T01](./T01-指标中心列表.md) | 指标中心/列表 | P0 | READY | S4 |
| [T02](./T02-指标模板商店.md) | 指标模板/商店 | P0 | READY | T01 |
| [T03](./T03-指标看板我的看板.md) | 指标看板/我的看板 | P0 | READY | T01 |

## 完成标准

- [ ] `IndicatorCenterPage` 作为阶段④指标入口，聚合指标分类导航 + 指标列表（`IndicatorListPage` 用 CompactTable，默认 10 条/页）。
- [ ] 指标列表含口径/负责人/状态（草稿/已发布）/更新时间列；行操作可看详情、加入看板。
- [ ] `IndicatorTemplatePage` 提供可复用指标模板，`IndicatorStorePage` 浏览已发布指标资产，可「基于模板新建」。
- [ ] `IndicatorDashboardPage` 渲染指标卡/图，`MyDashboardPage` 支持个性化订阅当前项目指标。
- [ ] 全部经 `indicatorsService` + `olapService` 取数；样例项目可呈现「销售达成率」指标。
- [ ] 任一指标页面均**不出现 dbt 编辑入口**；底层模型对普通用户透明。
