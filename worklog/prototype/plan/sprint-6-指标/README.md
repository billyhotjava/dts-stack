# Sprint-6: ④ 指标（指标/语义/dbt 隐藏）

**时间**: 2026-06
**状态**: READY
**目标**: 把现网分裂在 `modeling` 与 `governance` 两个筒仓的指标/语义能力**收口合并**进黄金主线阶段④「指标」，并把 dbt（`DbtFileBrowser`/`ModelPipeline`）从顶层菜单**下沉**为只读抽屉——普通用户在指标/语义层完成全部工作，**全程不接触 dbt**。

## 背景

依据设计文档 [§5 全量 IA 映射](../2026-06-19-dts-platform-unified-elt-redesign-design.md)（阶段④行）与 [§7 dbt 隐藏](../2026-06-19-dts-platform-unified-elt-redesign-design.md)、[sprint-queue.md](../sprint-queue.md)，阶段④「指标」是黄金主线的最后一站，收编现网以下三类筒仓页面：

- **指标筒仓**（governance/indicator）：`IndicatorCenterPage` / `IndicatorListPage` / `IndicatorStorePage` / `IndicatorTemplatePage` / `IndicatorDashboardPage` / `MyDashboardPage`
- **语义筒仓**（modeling/semantic）：`SemanticModelingCenterPage` / `SemanticModelsPage` / `SemanticObjectsPage` / `SemanticMetricsPage` / `SubjectsPage` / `SemanticRunsPage` / `SemanticPublishPage`
- **SQL 建模 + dbt + 字典**（modeling）：`SqlModelingPage` / `ModelTemplatesPage` / `ModelPipelinePage` / `DbtFileBrowserPage` · `SubjectAreasPage` / `GlossaryPage` / `ElementsPage` / `ReferenceCodesPage`

**三处关键架构动作**（本 sprint 兑现点）：

1. **指标能力收口**——`modeling` + `governance` 两个筒仓里分裂的指标/语义能力合并进阶段④同一入口，不再各自为政（F1 指标设计 + F2 语义建模）。
2. **dbt 下沉**——`DbtFileBrowser`/`ModelPipeline` 从顶层菜单降级为阶段④的「查看生成的 dbt」**只读抽屉**，消费 S4 F4 在画布上生成的 dbt 映射，**不暴露文件树式编辑入口**（F4 T01）。
3. **语义/指标即 dbt 隐藏层**——普通流程在指标/语义/SQL 建模层完成；dbt 仅作为隐藏引擎，以只读产物形式出现。

阶段④的「完成」状态（左轨 `✓`）由项目数据派生：**已发布指标 ≥ 1**。样例项目「销售准备项目」需在本阶段呈现「销售达成率」指标的端到端设计→语义建模→发布闭环，且其底层 dbt 产物仅通过只读抽屉可见。

依赖 **Sprint 4**（② 集成 · 配置/运行/双视图）——阶段④的 dbt 隐藏抽屉消费 S4 在画布上生成的 dbt 模型映射；语义/指标的事实来源是 S5 发布的数据集（资产）。本 sprint 只做前端 + mock，复刻现网 `*Service.ts` 契约（返回 `Promise<Result<T>>`），不接真后端。

## Feature 列表

| ID | Feature | Task 数 | 状态 | 优先级 |
|----|---------|---------|------|--------|
| [F1](./features/F1-指标设计/README.md) | 指标设计 | 3 | READY | P0 |
| [F2](./features/F2-语义建模/README.md) | 语义建模 | 2 | READY | P0 |
| [F3](./features/F3-SQL建模/README.md) | SQL 建模 | 1 | READY | P1 |
| [F4](./features/F4-dbt隐藏抽屉与字典/README.md) | dbt 隐藏抽屉与字典 | 2 | READY | P1 |

**统计**: Task 总数 8 ｜ READY=8

## 约束基线（全 sprint 适用）

- **Chrome 95**：禁用 oklch / `:has()` / 容器查询 / subgrid；构建必开 `@vitejs/plugin-legacy`（chrome>=95）。CSS 源码层手写 HSL/hex token。
- **mock 契约**：分域 `*Service.ts` 返回 `Promise<Result<T>>`，`VITE_USE_MOCK` 开关；不接真后端。本 sprint 用到 `indicatorsService`、`semanticModelingApi`、`olapService`（OLAP 取数）、`sqlModelingService`、`dbtService`（只读产物）、`glossaryService`（主题域/术语/参考码）。
- **dbt 隐藏（本 sprint 核心）**：普通流程在指标/语义层完成；dbt **仅以「查看生成的 dbt」只读抽屉出现**，消费 S4 F4 的生成映射，**不暴露文件树式编辑入口**。任何阶段④页面都不得把 dbt 当作主操作面。
- **设计系统**：Swiss 网格、HSL token、CompactTable 默认 10 条/页（切换条数刷新、`pageSize` 收敛、切 size 重置第 1 页）。
- **命名对齐**：页面/组件/service 命名对齐现网 `dts-platform-webapp`（`IndicatorCenterPage` / `SemanticModelsPage` / `SemanticPublishPage` 等）。

## 完成标准

- [ ] 指标筒仓（中心/列表/模板/商店/看板/我的看板）与语义筒仓（模型/对象/度量/主题/运行/发布）全部收口进阶段④同一入口，不再分属 modeling/governance 两处。
- [ ] SQL 建模 + 模板 + ModelPipeline 在阶段④可路由可访问，接 mock。
- [ ] dbt 仅以「查看生成的 dbt」**只读抽屉**形式出现，消费 S4 生成映射；**顶层菜单不再有 dbt 入口，无文件树式编辑面**。
- [ ] 主题域/术语/参考码（字典）在阶段④可访问，接 `glossaryService`。
- [ ] 样例项目「销售准备项目」端到端跑通：设计「销售达成率」指标 → 语义建模 → 发布，驱动左轨阶段④状态点为 `✓`；其底层 dbt 产物仅经只读抽屉可见。
- [ ] 所有页面标注其使用的 mock service；`VITE_USE_MOCK` 开关下数据可注入/重置。
- [ ] 通过 [it/README.md](./it/README.md) 列出的端到端验证项（**含「普通用户全程不接触 dbt」验证**）。
