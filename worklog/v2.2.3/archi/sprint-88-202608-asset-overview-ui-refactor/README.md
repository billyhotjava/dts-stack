# Sprint-88: 资产概览页面 UI 重构

**时间**: 2026-08
**状态**: IMPLEMENTATION_DONE（契约/UI/切片代码齐；E2E 实机四态截图待 it/ 登记，DoD 未置 DONE）
**类型**: UI Productization / Frontend Refactor
**目标**: 用户进入「数据治理 > 数据地图与资产 > 资产概览」后，在**一屏之内**看清当前范围的资产体量、治理缺口与主题域分布，并通过**唯一出口**带参跳进数据查询处置，左侧范围导航在 240px 内不溢出、不需要展开折叠即可选定范围。

## 背景与价值

现网页面（v2.2.3，`/catalog/assets`）存在四个具体问题，均已在真机截图与源码中定位：

1. **左侧导航形态混杂**。`DomainScopeNav` 同时承载折叠箭头、层级缩进、`code` 代号、总量与待处置双数字列，四类元素挤在 240px 宽度内，视觉上既不是标准菜单树也不是列表；域数量增长时纵向溢出显示框架。
2. **主区信息密度极低**。首屏只有 1 张大 KPI 卡 + 1 条绿色进度条，两者宽度不对称；下方矩阵与「待处置 Top 5」在空数据时各占约一屏纯空白，用户滚动三屏拿不到任何结论。
3. **分层维度归属错误**。「分层×主题域矩阵」与分层胶囊把 `warehouseLayer` 当作资产概览的主叙事轴，但分层语义的权威来源是**数据建模模块**的接口；资产概览重复呈现它，既制造双事实源，也把本页复杂度推高。
4. **跳转出口发散**。页面同时存在「范围回显→台账」「查看血缘图谱」「申请权限」「矩阵格子下钻」「缺口原因下钻」「Top5→资产详情」六类出口，指向四个不同模块。而 ADR-86 已把资产台账并入 `/catalog/search?view=table`（见 `LegacyAssetLedgerRedirect.tsx`），出口本可收敛为一个。

不做的代价：资产概览作为「数据地图与资产」的门面页，长期以空白与噪声示人；分层双事实源在 Sprint-87 资产语义投影落地后会正面冲突。

**本 Sprint 不改任何 Java 代码**——所需字段后端 `overview` 端点已全部具备（见账本 #6）。

## 架构决策记录 (ADR)

| 决策点 | 选择 | 理由 | 影响 |
|--------|------|------|------|
| ADR-88-01 左侧导航形态 | 递归**扁平成一层**，按 `total` 倒序取 Top 6，其余进「更多 N 个域」折叠区 | 域的父子关系是**建模期**语义，不是**浏览期**筛选语义；`byDomain` 统计按 `domainId` 精确归属，父域不含子域资产（账本 #7），扁平后不会重复计数 | `DomainScopeNav` 重写；层级折叠能力下线 |
| ADR-88-02 行内元素 | 每行只保留「名称 + 数量」，`code` 代号删除，待处置从独立数字列降级为数量右侧的琥珀圆点 | 240px 内四列必然截断；代号对选择范围无决策价值 | `ScopeRow` props 精简 |
| ADR-88-03 分层维度 | 资产概览**不再呈现 `warehouseLayer`**（矩阵与分层胶囊一并删除） | 分层权威源在数据建模模块，本页复述构成双事实源；用户明确要求降低图表复杂度 | 删除 `matrixHeatTone` / `MATRIX_MAX_COLUMNS` / `MERGED_DOMAIN_KEY` 及矩阵渲染块（约 160 行） |
| ADR-88-04 图表选型 | 只保留 2 个常规图形：治理状态**环形图** + 主题域 Top6 **横向条形图**（条形按「正常/待处置」堆叠） | 环形与条形是认知成本最低的两种图；堆叠使右侧条形与左侧导航语义区分（左=选择器，右=健康度） | 新增 2 个 <120 行图表组件，复用 `@/components/chart` |
| ADR-88-05 出口收敛 | 跳转目标**唯一** = `/catalog/search?view=table&...`；删除血缘图谱、申请权限、资产详情三类出口 | 台账已并入数据查询（ADR-86）；血缘与权限有各自的一级菜单入口，概览页不必重复 | 页面 chrome 从 3 个按钮降为 1 主 CTA + 1 刷新 icon |
| ADR-88-06 「待处置」不可下钻 | 「待处置」KPI 卡为**纯展示**，仅用 Tooltip 说明口径，不做点击 | 后端无单一 `attention` 过滤参数（账本 #9）；做成可点会跳到筛不出该数量的页面，属于欺骗性 affordance | 可下钻项仅 4 个，均已验证参数贯通 |
| ADR-88-07 契约测试重写 | `AssetOverviewPage.source-contract.test.ts` 全量改写，`DomainScopeNav.test.tsx` 按新形态改写 | 现有断言（矩阵存在、MetricTile 恰好 1 个、Button ≤1、GovernanceGapPanel 存在）与新设计直接冲突，属计划内失效 | 见账本 #12、#13 的冲突条款清单 |

## 端到端契约链 (Vertical Slice)

本 Sprint 是纯前端切片，竖线在「浏览器交互 → 既有 API → 既有 Service → 既有表」上打通，**无新增契约、无迁移**。

| 层 | 契约/落点 | 签名要点 |
|----|-----------|----------|
| UI 入口 | `/catalog/assets`（`AssetOverviewPage`），左侧域行 / KPI 卡 / 环形扇区 / 条形条 / 主 CTA | 用户点域行 → 改写 URL `?domain=<uuid>`；点图元 → 跳数据查询 |
| API（读） | `GET /api/catalog/assets/overview?domainId=&domainUnassigned=` | resp: `{total, unclassified, missingDomain, stale, attention, tagged, untagged, tagCoveragePercent, byLayer, governanceStatusCounts, matrix, byDomain, domainCountByLayer, scanned, truncated}` |
| API（读） | `GET /api/catalog/domains/tree?withStats=true` | resp: `{tree: DomainNode[], stats: {all, unassigned, byDomain, truncated}}` |
| Service | `CatalogAssetPortalService.overview(AssetQuery, effDept)` → `CatalogAssetOverviewAggregator.aggregate(rows, scanned, truncated)` | **本 Sprint 不修改**；注意 `AssetQuery` 的 size 硬编码为 200（账本 #10） |
| 数据 | `catalog_asset` / `catalog_domain`（经既有查询链） | 只读 |
| 迁移 | 无 | 本 Sprint 无 changelog 变更 |
| UI 出口 | `/catalog/search?view=table&{domain\|unclassified\|governance}` | 目标页参数消费已验证（账本 #11） |

**出口参数映射表**（每一行都必须在 IT 中逐条走查）：

| 触发元素 | 生成 URL | 目标页消费点 |
|---|---|---|
| 主 CTA「在数据查询中查看」 | `?view=table` + 当前 `domain` | `DataSearchPage.tsx:139` / `:318` |
| KPI「未定密」卡 | `?view=table&unclassified=1` (+ domain) | `DataSearchPage.tsx:132` |
| 环形图扇区 | `?view=table&governance=<STATUS>` (+ domain) | `DataSearchPage.tsx:134` |
| 条形图主题域条 | `?view=table&domain=<uuid>` | `DataSearchPage.tsx:318` |
| 条形图「未归域」条 | `?view=table&domain=__UNASSIGNED__` | 同上（`UNASSIGNED_DOMAIN_KEY`） |
| KPI「资产总量」/「待处置」/「标签覆盖」卡 | 不可点 | — |

## 现状勘察账本 (Context Ledger)

一次勘察的全部事实。**下游 Task 引用条目编号，禁止重复扫描。**

| # | 事实 | 证据(文件:行) |
|---|------|---------------|
| 1 | 概览页主文件 574 行，已超出单文件舒适区；矩阵渲染块占 417–532，缺口面板挂载在 407–414，三类出口按钮在 355–386 | `src/pages/catalog/AssetOverviewPage.tsx:1-574` |
| 2 | `DomainScopeNav` 共 272 行；`ScopeRow` 76–131 渲染「箭头/缩进/名称/code/总量/待处置」六类元素；`renderNodes` 157–192 负责递归层级与折叠；搜索框阈值 `SEARCH_THRESHOLD = 8` | `src/components/catalog/DomainScopeNav.tsx:33,76-131,157-192` |
| 3 | `DomainScopeNav` 全仓**仅** `AssetOverviewPage.tsx:327` 一处使用（其余为自身单测与类型 import），改造无外溢 | `src/pages/catalog/AssetOverviewPage.tsx:327`；`src/pages/catalog/assets/assetPageShared.tsx:3` |
| 4 | `GovernanceGapPanel` 全仓仅 `AssetOverviewPage.tsx:407` 一处使用，可安全删除 | `src/pages/catalog/assets/GovernanceGapPanel.tsx` |
| 5 | `MetricTile` 定义在共享层，样式为「label + icon / 大数字 / footnote」，可直接复用为 KPI 卡基元 | `src/pages/catalog/assets/assetPageShared.tsx:189-210` |
| 6 | 后端 `AssetOverview` record 已含本次所需**全部**字段，含 `byDomain: Map<String, DomainStats>` 与 `governanceStatusCounts: Map<String, Long>`；前端 TS 类型漏声明了 `byDomain`，需补 | `CatalogAssetOverviewAggregator.java:35-50`；`AssetOverviewPage.tsx:42-56` |
| 7 | `byDomain` 由 matrix 按 `domainId` 归并而来，**每条资产只计入自身 domainId**，父域不含子域资产；`domainId=null` 的行不进 `byDomain`，由 `missingDomain` 单独表达 | `CatalogAssetOverviewAggregator.java:104-113` |
| 8 | `tagCoveragePercent` 为整数百分比，由 `tagged/rows.size()` 四舍五入；`tagged`+`untagged` 可直接做 KPI 副文案 | `CatalogAssetOverviewAggregator.java:114` 附近 |
| 9 | 后端**无**单一 `attention` 过滤参数；`attention` 是 `unclassified ∨ missingDomain ∨ stale ∨ governanceStatus∈{PENDING_*, DISABLED}` 的复合判定 | `CatalogAssetOverviewAggregator.java:60-75` |
| 10 | overview 端点内部 `AssetQuery` 的 page/size 硬编码为 `0, 200`，即统计**最多扫描 200 条**，超出即 `truncated=true`，数字须带 `≥` 前缀 | `CatalogAssetPortalResource.java:181` |
| 11 | 台账已并入数据查询：`/catalog/assets/ledger` 经 `LegacyAssetLedgerRedirect` 302 到 `/catalog/search?view=table` 并原样透传参数；目标页确实消费 `unclassified` / `stale` / `governance` / `view` / `layer` / `domain` | `LegacyAssetLedgerRedirect.tsx:6-12`；`DataSearchPage.tsx:132-139,314-321` |
| 12 | 现有页面契约测试的**冲突条款**：`:19` 断言「分层×主题域矩阵」存在、`:79` 断言 `<Button` ≤1 个、`:106` 断言 `GovernanceGapPanel` 存在、`:107` 断言 `MetricTile` 恰好 1 个、`:89-94` 断言矩阵截断逻辑 | `src/pages/catalog/AssetOverviewPage.source-contract.test.ts:19,79,89-94,106-107` |
| 13 | `DomainScopeNav.test.tsx` 有 13 处 render 用例，覆盖层级折叠、双数字列、`code` 展示、搜索阈值，多数随 ADR-88-01/02 失效 | `src/components/catalog/DomainScopeNav.test.tsx:42-130` |
| 14 | 图表基建现成：`@/components/chart` 已封装 `echarts-for-react`，签名 `Chart({option, height, className, loading})`，`opts.renderer = "canvas"`、`notMerge` | `src/components/chart/chart.tsx:1-25` |
| 15 | 分层元数据 `LAYER_META` / `LAYER_ORDER` 除概览页外仍被台账/详情等页使用，**只能停用于概览页，不得删除常量本身** | `src/pages/catalog/assets/assetPageShared.tsx:77-88` |

**开放问题**（勘察未决，实施期确认）：

- `governanceStatusCounts` 的 key 集合在真实数据下是否会出现 `GOVERNANCE_STATUS_DICT` 未覆盖的值（当前 `resolveEnumLabel` 会回落成英文原值）→ 由 F2/T02 在环形图 legend 处兜底为「其他」并计数。
- Chrome 95 下 ECharts 环形图的 `canvas` 渲染表现未实测 → 由 F3/T01 的 IT 走查覆盖，若不兼容则降级为纯 CSS 占比条。

## Gate Registry

| Gate | 项目 | 状态 | 证据 | 未过则关联 Task |
|------|------|------|------|-----------------|
| G0 | 交付基线 | GAP | `it/baseline.md` | F3/T01（IT 走查须在运行实例上补齐） |
| G0 | 领域与数据画像 | GAP | `it/baseline.md` §数据画像 | F3/T01 |
| G0 | 领域不变量自检 | PASS | 见 ADR 表（ADR-88-03 分层归属、ADR-88-05 出口收敛均与 ADR-86 一致） | - |
| G1 | 契约链贯通 | PASS | 本文档 §端到端契约链 | - |
| G1 | 非功能预算 | PASS | 本文档 §非功能预算 | - |
| G3 | 发布安全 | PASS | 本文档 §发布安全 | - |
| G4 | 可运维性 | N/A | 纯前端只读页面，无新增后端调用、无新增日志/告警面 | - |
| G4 | DoD 验收 | PENDING（代码完成） | 契约 6/6 + 单测 14/14；真机四态截图与出口走查待 F3/T01 IT | F3/T01 |

**G0 GAP 说明（诚实标注）**：本 Sprint 规划期未启动运行实例，`it/baseline.md` 中的登录、真实域数据分布、Chrome 95 渲染三项为**待验证**。因本 Sprint 是纯前端只读改造、零后端与零迁移，风险可控，故 Feature 仍置 `READY`；但 **F3/T01 的 DoD 未拿到真机四态证据前，Sprint 不得置 DONE**。

## 非功能预算 (NFR)

| 属性 | 预算 | 验证方式 |
|------|------|----------|
| 首屏完成 | 页面主体（KPI + 两图）在 1 屏内呈现，1440×900 下无需滚动 | IT-03 截图 |
| 请求数 | 页面加载请求数**不增加**：仍为 `overview` + `domains/tree` 共 2 个 | Network 面板计数 |
| 包体 | 不新增第三方依赖；ECharts 已在依赖树中，两个图表组件按现有 `Chart` 封装引用 | `package.json` 无 diff |
| 源文件规模 | `AssetOverviewPage.tsx` 从 574 行降至 ≤ 260 行；新增组件各 ≤ 120 行 | `wc -l` |
| 兼容 | Chrome 95 | IT-05 |
| 可访问性 | 域行、KPI 卡、图表下钻均可键盘触达；不可点的 KPI 卡不得有 `button` 语义 | IT-04 |

## 发布安全

- **兼容性**：`/catalog/assets?domain=` / `?tab=catalog-tags` / `?view=table` 三类既有深链行为**不变**（重定向逻辑原样保留）。删除的仅为页面内部 affordance，非路由。
- **回滚**：纯前端改动，`git revert` 单个 commit 即可回滚，无数据面残留。
- **Expand/Contract**：本 Sprint 只做前端 Contract（删除展示），后端 `byLayer`/`matrix` 字段**保留不动**，其他消费方不受影响。

## Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 |
|----|---------|---------|--------|------|
| F1 | 资产范围导航极简化 | 1 | P0 | READY |
| F2 | 概览统计与图表 | 2 | P0 | READY |
| F3 | 出口收敛与页面瘦身 | 1 | P0 | READY |

**依赖顺序**: F1 ∥ F2/T01 → F2/T02 → F3（F3 做页面装配、出口收敛与契约测试重写，必须最后做）

## 追溯矩阵 (Traceability)

| 需求点（用户原话） | Feature | 关键 Task | 验收证据位置 |
|--------|---------|-----------|--------------|
| 左边菜单显示重点信息，不显示全部，否则超出显示框架 | F1 | F1/T01 | `it/README.md` IT-01 |
| 左边菜单树很别扭，用简单元素表示 | F1 | F1/T01 | IT-01 |
| 需要有统计的 card | F2 | F2/T01 | IT-02 |
| 需要常用的图表；尽量少复杂图表 | F2 | F2/T02 | IT-03 |
| 分层来自数据建模模块的接口（本页不复述） | F3 | F3/T01 | IT-03（断言页面无矩阵/分层） |
| 减少链接与跳转按钮，只需跳数据查询 | F3 | F3/T01 | IT-04 |

## 完成标准

- [ ] **契约**：改写后的 `AssetOverviewPage.source-contract.test.ts` 与 `DomainScopeNav.test.tsx` 全绿；`pnpm build` 无 TS 错误
- [ ] **UI**：1440×900 下概览页一屏呈现，左侧导航 Top6+折叠不溢出，两个图表正常渲染；空/加载/错误/成功四态各有截图
- [ ] **切片**：出口参数映射表 6 行逐条在运行实例上点击验证，目标页筛选结果与概览数字口径一致
- [ ] **规模**：`AssetOverviewPage.tsx` ≤ 260 行，新增组件各 ≤ 120 行
- [ ] `it/` 中无占位证据

## 非目标

- 不改任何 Java 代码；不动 `overview` 端点的 200 条扫描上限（若需真实全量统计，另开 Sprint）
- 不动数据建模模块的分层接口，不在本页重建分层视图
- 不改数据查询页（`DataSearchPage`）自身；只作为跳转目标消费既有参数
- 不重做血缘图谱与权限申请页，仅移除概览页对它们的快捷入口
- 不引入新的图表库或设计系统
