# Sprint-2: ① 连接（数据源/连接器/调度）

**时间**: 2026-06
**状态**: READY
**目标**: 把现网 foundation 筒仓（数据源/连接器/驱动/接入变更/调度）收纳进黄金主线阶段①「连接」，纯前端 mock 跑通"配好数据源 → 至少一个源已连通"的入口体验。

## 背景

依据设计文档 [§5 全量 IA 映射](../2026-06-19-dts-platform-unified-elt-redesign-design.md) 与 [sprint-queue.md](../sprint-queue.md)，阶段①「连接」收编现网 `source/dts-platform-webapp/src/pages/foundation` 下的五个筒仓页面：

- `DataSourcesPage` / `DataSourceDetailPage` / `DataSourceFormModal`
- `ConnectorRegistryPage`
- `JdbcDriversPage`
- `AccessChangesPage`
- `TaskSchedulingPage`

阶段①是黄金主线的第一站，其「完成」状态（左轨 `✓`）由项目数据派生：**已连通 ≥ 1 个数据源**。本 sprint 只做前端 + mock，复刻现网 `*Service.ts` 契约形状（返回 `Promise<Result<T>>`），不接真后端。样例项目「销售准备项目」需在本阶段呈现 PLM 订单 + ERP 客户两个已连通源，为后续阶段②集成提供数据来源。

依赖 **Sprint 1**（项目外壳 + 阶段导航轨 + Swiss 设计系统 + mock 框架），本 sprint 不重复搭基础设施，只在阶段① slot 内落地页面。

## Feature 列表

| ID | Feature | Task 数 | 状态 | 优先级 |
|----|---------|---------|------|--------|
| [F1](./features/F1-数据源/README.md) | 数据源 | 4 | READY | P0 |
| [F2](./features/F2-连接器与驱动/README.md) | 连接器与驱动 | 2 | READY | P1 |
| [F3](./features/F3-接入与调度/README.md) | 接入与调度 | 2 | READY | P1 |

**统计**: Task 总数 8 ｜ READY=8

## 约束基线（全 sprint 适用）

- **Chrome 95**：禁用 oklch / `:has()` / 容器查询 / subgrid；构建必开 `@vitejs/plugin-legacy`（chrome>=95）。CSS 源码层手写 HSL/hex token。
- **mock 契约**：分域 `*Service.ts` 返回 `Promise<Result<T>>`，`VITE_USE_MOCK` 开关；不接真后端。本 sprint 用到 `dataSourcesService`、`connectorsService`、`jdbcDriversService`（接入变更/调度复用或新增轻量 mock service）。
- **设计系统**：Swiss 网格、HSL token、CompactTable 默认 10 条/页（切换条数刷新、`pageSize` 收敛）。
- **命名对齐**：页面/组件/service 命名对齐现网 `dts-platform-webapp`（`DataSourcesPage` 等）。

## 完成标准

- [ ] 阶段① 5 个页面（数据源列表/详情/表单/连通测试 + 连接器注册 + JDBC 驱动 + 接入变更 + 任务调度）全部在阶段① slot 内可路由可访问。
- [ ] 数据源列表用 CompactTable（默认 10 条/页）渲染，含状态列；详情页、新建/编辑 modal、连通测试均接 mock。
- [ ] 连通测试 mock 可返回成功/失败两态，UI 区分呈现。
- [ ] 样例项目「销售准备项目」在阶段①呈现 ≥ 2 个已连通数据源（PLM 订单、ERP 客户），驱动左轨阶段①状态点为 `✓`。
- [ ] 所有页面标注其使用的 mock service；`VITE_USE_MOCK` 开关下数据可注入/重置。
- [ ] 通过 [it/README.md](./it/README.md) 列出的端到端验证项。
