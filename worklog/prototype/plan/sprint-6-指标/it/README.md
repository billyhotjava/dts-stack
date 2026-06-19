# Sprint-6 集成测试（IT）· 阶段④ 指标

**状态**: READY
**范围**: 阶段④「指标」端到端验证——指标设计(F1) / 语义建模(F2) / SQL 建模(F3) / dbt 隐藏抽屉与字典(F4) 收口后的整体可走通性，**重点验证「普通用户全程不接触 dbt」**。
**前置**: Sprint 4（② 集成 · 画布/dbt 生成映射）已完成；样例项目「销售准备项目」已带 S5 发布的 ODS 宽表数据集。
**环境**: `VITE_USE_MOCK=1`，分域 `*Service.ts` 返回 `Promise<Result<T>>`；Chrome 95（legacy 构建产物）。

---

## IT-1 指标设计闭环（F1）

- [ ] `IndicatorCenterPage` 可达，分类导航 + `IndicatorListPage` CompactTable（默认 10 条/页）渲染正常。
- [ ] 列表切换每页条数后刷新并回到第 1 页；状态列（草稿/已发布）正确。
- [ ] 从 `IndicatorTemplatePage` 用模板新建指标，经 `indicatorsService.createIndicator` 落 mock 并回流列表。
- [ ] `IndicatorStorePage` 商店浏览 + 引用到当前项目可用。
- [ ] `IndicatorDashboardPage` 渲染「销售达成率」KPI 卡（经 `olapService.queryIndicator`）；`MyDashboardPage` 订阅 scoped 到当前项目。
- [ ] 加载/空/错误三态均显式呈现。

## IT-2 语义建模闭环（F2）

- [ ] `SemanticModelingCenterPage` 四视图（模型/对象/度量/主题）可达，列表 CompactTable 行为正确。
- [ ] 语义模型展示其绑定的 S5 数据集来源；度量可被 F1 指标引用。
- [ ] `SemanticPublishPage` 发布语义模型，`publishModel` mock 成功/失败两态均正确处理。
- [ ] 发布成功后 `SemanticRunsPage` 出现运行记录，状态列三态正确。
- [ ] 发布成功驱动左轨阶段④状态点 → `✓`（已发布指标 ≥ 1）。

## IT-3 SQL 建模闭环（F3）

- [ ] `SqlModelingPage` 编辑并「保存为模型」经 `sqlModelingService.saveModel` 落 mock。
- [ ] `ModelTemplatesPage` 用模板预填 SQL 骨架；`ModelPipelinePage` 列表 CompactTable 行为正确。

## IT-4 字典支撑（F4 T02）

- [ ] `SubjectAreasPage`/`GlossaryPage`/`ElementsPage`/`ReferenceCodesPage` 均可达，经 `glossaryService` 取数。
- [ ] 主题域 ↔ F2 语义主题、术语 ↔ F1 指标口径的引用链路可点可达。
- [ ] 参考码以 code（稳定）+ label（展示）成对呈现。

## IT-5 黄金主线端到端（贯穿 F1/F2/F4）

- [ ] 「销售准备项目」端到端走通：基于 S5 ODS 宽表 → F2 建语义模型/度量 → F2 发布 → F1 定义/发布「销售达成率」指标 → F1 看板消费。
- [ ] 全程 scoped 到当前项目；阶段④左轨状态点最终为 `✓`。
- [ ] 「下一步」引导卡（来自 S1 门户）在阶段④完成后正确反映黄金主线收官。

## IT-6 dbt 隐藏验证（本 sprint 核心 · 重点）

> 目标：证明**普通用户全程不接触 dbt**——dbt 仅以「查看生成的 dbt」只读抽屉作为被动、只读的唯一接触点。

- [ ] **顶层无 dbt 菜单**：阶段④导航与平台旁路区均无 `DbtFileBrowser`/`ModelPipeline` 独立菜单项（现网顶层入口已下沉移除）。
- [ ] **走完 IT-5 全主线，未主动触达任何 dbt 界面**：从指标设计→语义建模→发布→看板消费，普通用户可全程不打开 dbt 抽屉即完成工作。
- [ ] **抽屉仅被动唤起**：「查看生成的 dbt ▸」按钮只出现在 F2 语义发布 / F3 ModelPipeline / F1 指标详情等次级位置，需用户主动点击才出现。
- [ ] **抽屉完全只读**：抽屉内 model 列表与详情无新建/编辑/删除/重命名/运行/保存按钮，无文件树编辑、无 inline 编辑、无右键写操作。
- [ ] **抽屉消费 S4 F4 生成映射**：抽屉内可看到生成 dbt model 与画布节点（S4）/语义模型（F2）/SQL 模型（F3）的来源对应关系。
- [ ] **契约层只读**：`dbtService` 仅暴露 `listGeneratedModels`/`getGeneratedModel` 等只读方法，**无** create/update/delete/run。
- [ ] **高级路径也守约束**：F3 `ModelPipelinePage`（现网原为裸 dbt 流水线入口）仅呈现业务语义流水线，不暴露 dbt 文件树/文件编辑/直接运行。
- [ ] **字典域无 dbt**：F4 T02 主题域/术语/参考码各页不出现任何 dbt 入口。

## IT-7 约束基线（全 sprint 横切）

- [ ] **Chrome 95**：阶段④全部页面无 oklch/`:has()`/容器查询/subgrid；`@vitejs/plugin-legacy`（chrome>=95）产物可加载运行。
- [ ] **mock 契约**：`indicatorsService`/`semanticModelingApi`/`olapService`/`sqlModelingService`/`dbtService`/`glossaryService` 均返回 `Promise<Result<T>>`；`VITE_USE_MOCK` 开关下数据可注入/重置。
- [ ] **设计系统**：Swiss 网格、HSL/hex token、CompactTable 默认 10 条/页（切条数刷新 + 回第 1 页）；数据列 `tabular-nums` 对齐；动效仅 `transform/opacity`。
- [ ] **命名对齐**：页面/组件/service 命名对齐现网 `dts-platform-webapp`。
- [ ] **收口验证**：现网 modeling + governance 两筒仓的指标/语义页面确实统一收口进阶段④同一入口，不再分属两处。
