# Sprint-5: ③ 资产（目录/血缘/质量/权属）

**时间**: 2026-06
**状态**: READY
**目标**: 把现网 catalog 筒仓 + governance 资产权属收纳进黄金主线阶段③「资产」，纯前端 mock 跑通"消费 S4 画布产出的 ODS 宽表 → 搜得到、看得清血缘、有质量与权属"的资产体验。

## 背景

依据设计文档 [§5 全量 IA 映射](../2026-06-19-dts-platform-unified-elt-redesign-design.md) 与 [sprint-queue.md](../sprint-queue.md)，阶段③「资产」收编现网 `source/dts-platform-webapp/src/pages/catalog` 下的目录/搜索/血缘/元数据/质量页面，以及 governance 的资产权属能力：

- 目录与搜索：`DataSearchPage` · `DatasetsPage` / `DatasetDetailPage` · `DataProductsPage`
- 血缘：`LineageGraphPage`（`@antv/g6` 关系图，现网同款 `@/components/lineage` 的 `LineageGraph`）· `LineageColumnsPage` / `LineageImpactPage` / `LineageDiffPage` / `LineageImportPage`
- 元数据：`MetadataPage`
- 质量：`QualityPage` / `QualityReportPage` / `QualityRulesPage`
- 权属：`AssetOwnershipPage` / `AssetGrantPage` / `MyGrantsPage`

阶段③是黄金主线第三站，其「完成」状态（左轨 `✓`）由项目数据派生：**发布数据集 ≥ 1**。本 sprint 只做前端 + mock，复刻现网 `*Service.ts` 契约形状（返回 `Promise<Result<T>>`，如 `catalogDomainService` / `metadata` / `dataProductsService`），不接真后端。血缘数据走 mock。

**样例连贯性**：本阶段资产必须能消费 Sprint 4（阶段②集成）画布产出的 **ODS 宽表**——样例项目「销售准备项目」的 PLM 订单 + ERP 客户经去重/连接后生成的 `ODS.宽表`，在阶段③呈现为可搜索、可查血缘（上游=PLM/ERP 源表 + 去重/连接任务节点）、可挂质量规则、可标权属的数据集，驱动左轨阶段③状态点为 `✓`。

依赖 **Sprint 1**（项目外壳 + 阶段导航轨 + Swiss 设计系统 + mock 框架）。本 sprint 不重复搭基础设施，只在阶段③ slot 内落地页面。

## Feature 列表

| ID | Feature | Task 数 | 状态 | 优先级 |
|----|---------|---------|------|--------|
| [F1](./features/F1-目录与搜索/README.md) | 目录与搜索 | 3 | READY | P0 |
| [F2](./features/F2-血缘/README.md) | 血缘 | 2 | READY | P0 |
| [F3](./features/F3-质量与权属/README.md) | 质量与权属 | 2 | READY | P1 |

**统计**: Task 总数 7 ｜ READY=7

## 约束基线（全 sprint 适用）

- **Chrome 95**：禁用 oklch / `:has()` / 容器查询 / subgrid；构建必开 `@vitejs/plugin-legacy`（chrome>=95）。CSS 源码层手写 HSL/hex token。**`@antv/g6` 血缘图须在 legacy 构建下可用**（现网同款 v4.x，已验证）。
- **mock 契约**：分域 `*Service.ts` 返回 `Promise<Result<T>>`，`VITE_USE_MOCK` 开关；不接真后端。本 sprint 用到 `catalogDomainService`、`metadata`、`dataProductsService`，以及新增轻量 mock：`lineageService`、`qualityService`、`assetGrantService`。血缘数据 mock。
- **设计系统**：Swiss 网格、HSL token、CompactTable 默认 10 条/页（切换条数刷新、`pageSize` 收敛）。
- **命名对齐**：页面/组件/service 命名对齐现网 `dts-platform-webapp`（`DataSearchPage` / `DatasetsPage` / `LineageGraphPage` 等），降低未来回植成本。

## 完成标准

- [ ] 阶段③ 全部页面（资产搜索 / 数据集列表+详情 / 数据产品 / 血缘图 / 列级血缘 / 影响分析 / diff / 导入 / 元数据 / 质量+报告+规则 / 权属+授权+我的授权）在阶段③ slot 内可路由可访问。
- [ ] 资产搜索（`DataSearchPage`）支持关键词 + 过滤（域/层/类型），结果用 CompactTable（默认 10 条/页）渲染。
- [ ] 数据集列表+详情、数据产品均接 mock；列表用 CompactTable。
- [ ] 血缘图用 `@antv/g6`（`@/components/lineage` 的 `LineageGraph`）渲染：节点=资产/任务，边=数据流；在 legacy 构建下可平移/缩放/选中。
- [ ] 样例 `ODS.宽表` 数据集可搜索；其血缘图上游可见 PLM/ERP 源表 + 去重/连接任务节点（连贯消费 S4 画布产出）。
- [ ] 至少一个数据集挂上质量规则并有质量报告；至少一个数据集有权属与授权记录。
- [ ] 所有页面标注其使用的 mock service；`VITE_USE_MOCK` 开关下数据可注入/重置。
- [ ] 通过 [it/README.md](./it/README.md) 列出的端到端验证项。
