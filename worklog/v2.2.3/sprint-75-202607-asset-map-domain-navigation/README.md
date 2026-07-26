# Sprint-75：资产地图主题域导航与统计口径纠偏

**时间**：2026-07
**状态**：DRAFT（设计已确认，待 spec 复审；禁止编码）
**类型**：Navigation IA / Statistics Contract / UI Convergence / Bug Fix
**目标**：让用户在资产地图左侧一眼看出"哪个主题域有资产、哪个域有待处置"，无需逐个点击；右侧不再用五张卡片重复讲同一件事；地图页回归纯统计概览，控件降到最少，一切执行动作留在资产台账。

## 1. 背景与价值

现场截图显示：360 条资产全部落在"未归域"，五个业务主题域（地铁域、租赁域、财务域、项目管理域）实际为空。但左侧主题域树只渲染域名，用户看不出任何一个域是空的，会误判为"域里有内容，只是没点开"。

同时右侧出现三组互相矛盾的数字：`待处置 360`、`未归域 360`、`治理状态分布 待归域 358 + 待认领 2`。五张 KPI 卡里三张是 0、一张与另一张同值，有效信息密度约 1/5。顶部还有一条"资产数量超过扫描上限，统计基于前 360 条"的黄色警告——**这是误报**，扫描上限是 2000 条，360 远未触及。

这三个现象不是文案问题，而是三条契约同时失位：

1. **导航契约**：主题域树只传递"名字"，不传递"体量与健康度"，导航无法承担"决定下一步看哪里"的职责；
2. **统计口径契约**：`total` 把全局 legacy 计数叠加到分页后的 OpenMetadata 计数上，导致 `truncated` 恒真，警告恒亮；
3. **页面职责契约**：`AssetOverviewPage` 的文件注释已声明"纯统计概览、零输入控件、执行动作在台账"，但实际仍带三个操作按钮，其中"进入台账"与全局菜单一级入口重复。

本 Sprint 不新增菜单、不新增页面、不改动可见性规则，只在既有路由内重构导航与统计口径。

## 2. 目标用户旅程

```text
进入资产地图
  → 左侧一眼看出：全部 360、未归域 360 待处置 360、五个业务域均为 0（整行弱化）
  → 判断出"当前问题是全量未归域"，无需逐个点击业务域
  → 点右侧"治理缺口"里的"待归域 358"
  → 直达资产台账，范围已带上 domain=__UNASSIGNED__ 与治理状态筛选
  → 在台账完成认领、归域、定密等执行动作
```

地图回答"哪里有问题、有多严重"；台账回答"具体是哪些资产、怎么处置"。地图不提供第二条执行路径。

## 3. 架构决策记录（ADR）

| ID | 决策点 | 选择 | 理由 | 影响 |
|---|---|---|---|---|
| ADR-75-01 | 主题域统计的可见性口径 | 复用 `listAssets` 的行级 `canRead`，**禁止**另写一套 SQL ABAC 聚合 | `canRead` 依赖 `CatalogAssetExtension`/`CatalogAssetMapping`/`CatalogDataset` 三方解析 + `CatalogDatasetGrant` 显式授权 + JWT `personnel_level` 回退链；SQL 重写必然与台账口径漂移（本项目已在 clientIp 头列表上付过一次"同规则多处实现"的代价） | 新统计接口的实现方式、扫描上限设计 |
| ADR-75-02 | 统计性能路径 | 用既有批量路径 `loadCandidateMetadata` 替换 `overview` 的逐行查询 | 现状每行 `findFirstByOmAsset` + `findFirstByFqnIgnoreCase`，2000 条即 4000+ 次查询，这才是 2000 上限的真实来源；批量路径已在标签筛选分支验证过 | `CatalogAssetPortalService.overview`、扫描上限可提至 5000 档 |
| ADR-75-03 | 统计与域树的传输方式 | 合并为单一接口 `GET /catalog/domains/tree?withStats=true` | 现状树取自 `getDomainTree()`、矩阵列名取自 `listDomains(0,200)`，域超 200 个时列名掉成"未知主题域"。合并即消除双数据源漂移 | 前端 API 层、矩阵列名解析 |
| ADR-75-04 | "未归域"的信息架构位置 | 移出"业务主题域"分区，独立为"待治理"分区 | 未归域是缺失状态，不是分类维度；与真实域并列会使"主题域"这个分类失去意义 | `DomainScopeNav` 分区结构 |
| ADR-75-05 | 树的视觉层级 | "全部资产"从根节点降级为顶部概览行；去掉 `showLine` 虚线；业务域仅在有子域时出现展开箭头 | 现状两层结构被画成文件树，两级缩进白吃掉侧栏约 1/4 横向空间 | `DomainScopeNav` 布局 |
| ADR-75-06 | 范围选中态的持久化 | `domain` 从裸 `useState` 迁到 `?domain=<id>` / `?domain=__UNASSIGNED__` | 现状刷新丢失、不可分享、后退无效；同页 `tab` 已进 searchParams，`domain` 是唯一例外 | 两个页面容器的 searchParams 绑定 |
| ADR-75-07 | 缺少标识的主题域 | 渲染为 disabled + tooltip 说明，**不再**静默降级 | 现状 `key` 落成 `fallback-x`，点击被判为"全部资产"，用户以为筛了实际没筛，零提示 | 删除 `buildTreeNodes` 的 fallback key 与 `startsWith("fallback-")` 分支 |
| ADR-75-08 | 组件归属 | 抽出共享 `DomainScopeNav`，纯展示、不取数、不读写 URL | 现状同一棵树在 `AssetOverviewPage` 与 `DatasetsPage` 各写一份，含同一个 fallback 补丁 | 新组件 + 两处接入 |
| ADR-75-09 | 地图页控件数量 | 删除"进入台账"与"去台账处置"按钮；"刷新"降级为 icon-only；下钻全部改为"点数据" | "进入台账"与全局菜单一级入口（`portal-menu-seed.json`）重复；"去台账处置"是 Top 5 卡片之外的多余第二触发点 | PageHeader actions、Top 5 模块 |
| ADR-75-10 | 台账是否升为 tab | **不升**，保持全局菜单一级入口 | `DataAssetPortalMenu.source-contract.test.ts` 断言 `/catalog/assets/ledger` 在 menu seed 中恰好出现一次，且 `role-menu-defaults.json` 按角色控制其可见性；升 tab 会造成第二条路径并绕过角色门 | 导航结构不变 |
| ADR-75-11 | 0 值的视觉语义 | 0 值原因显示为中性灰，不用绿色对勾 | 现状"未定密 0"配绿色盾牌，在"360 条全部待处置"语境下暗示合规，是误导性正反馈 | 治理缺口面板配色 |
| ADR-75-12 | 矩阵的退化策略 | 可见域 ≤1 时不画矩阵，退化为单行"分层分布"横条；≥2 域才画表；列改为 top 8 + 静态"其他 N 个域"列（tooltip 列出域名） | 现状单域时仍画 8 行 × 1 列、其中 3 行是"-"；`slice(0, 8)` 静默截断无提示 | 矩阵渲染分支 |
| ADR-75-13 | 界面语言与枚举值 | 界面不出现英文枚举原值；不逐处补文案，而是建统一字典 + 漏译降级为"未知（原值）"。数仓分层用**中文主 + 代号弱化**（"明细层 DWD"）；`Hive`/`JDBC` 等产品名与技术标准名保持原形 | 逐处补文案会随新枚举再次漏译；分层保留代号是因为 Addax→dbt→Airflow 链路里 `dwd_xxx` 模型名需要对得上 | F5 全部；F3/F4 的新组件须直接用字典 |
| ADR-75-14 | 失效资产的判定枚举 | `STALE`/`DISABLED` 改为 `DEPRECATED \| ARCHIVED \| BLOCKED` | 前两者不在 `CatalogAssetLifecycleStatus` 中，从未被写入，导致"失效资产"指标结构性恒为 0（L17） | `CatalogAssetOverviewAggregator`、`assetPortalUx.helpers`、`DatasetsPage`；`attention` 数值会上升 |

## 4. 对象边界

| 对象 | 归属 | 本 Sprint 变化 |
|---|---|---|
| `CatalogDomain` | 既有实体 | 不改 schema；`code`/`owner`/`lifecycleStatus` 开始在导航中被使用 |
| `CatalogAssetOverviewAggregator.AssetOverview` | 既有聚合结果 | 新增 `byDomain` rollup（由既有 `matrix` 的 domainId 维度归并，不新增统计口径） |
| `DomainScopeNav` | **新建**共享组件 | 纯展示，props in / callback out |
| `AssetOverviewPage` | 既有页面容器 | 接入 `DomainScopeNav`、收敛右侧 IA、裁剪控件、绑定 searchParams |
| `DatasetsPage`（台账） | 既有页面容器 | 仅替换左侧 Sider 为 `DomainScopeNav` + 绑定 searchParams；台账自身功能不动 |
| `assetPageShared.buildTreeNodes` | 既有工具 | 删除 fallback key 生成，改为保留 `id: null` 由组件判 disabled |
| 可见性规则（`canRead` / `AccessChecker`） | 既有安全边界 | **零改动**，只被复用 |

## 5. 统计口径契约

| 字段 | 定义 | 来源 |
|---|---|---|
| `all.total` / `all.attention` | 当前用户可见范围内的资产总数 / 待处置数 | `listAssets` 过滤后的行 |
| `byDomain[<uuid>]` | 该主题域下可见资产的 total / attention | 同上，按 `domainId` 归并 |
| `unassigned` | `domainId == null` 的可见资产 total / attention | 同上 |
| `scanned` | 实际参与聚合的行数 | 批量扫描累计 |
| `truncated` | `scanned >= SCAN_CAP` 时为真 | **修正判定**：不再用 `rows.size() < total` |

`attention` 定义沿用 `CatalogAssetOverviewAggregator`：未定密 ∨ 未归域 ∨ 生命周期 STALE ∨ 治理状态 `PENDING_*`/`DISABLED`。本 Sprint 不修改此定义。

`truncated` 为真时，导航与缺口面板的数字前缀显示 `≥`，并给出真实原因文案（"可见资产超过 5000 条统计上限，以下为前 5000 条的统计"），而非现状的误报。

## 6. 端到端契约链（Vertical Slice）

```text
CatalogDomainResource.tree(withStats=true)
  → CatalogDomainService.treeWithStats()
      → CatalogAssetPortalService.domainStats(activeDept)
          → loadCandidateMetadata(batch)      # ADR-75-02
          → canRead(...) 逐行过滤            # ADR-75-01，规则零改动
          → CatalogAssetOverviewAggregator.aggregate(...).byDomain()
  → { tree, stats }
      → platformApi.getDomainTree({ withStats: true })
          → AssetOverviewPage / DatasetsPage 容器
              → DomainScopeNav（纯展示）
              → ?domain= searchParams 绑定    # ADR-75-06
```

## 7. 现状勘察账本（Context Ledger）

以下为编码前已核实的事实，实现时不得再凭猜测改写。

| # | 事实 | 位置 |
|---|---|---|
| L01 | 地图页与台账页各自复制了一份主题域 Tree，含同一个 fallback 补丁 | `AssetOverviewPage.tsx:203-271`、`DatasetsPage.tsx:363-576` |
| L02 | `domain` 是裸 `useState`，未进 searchParams；同页 `tab` 已进 | `AssetOverviewPage.tsx:62`、`:59` |
| L03 | 缺 id 的域生成 `fallback-<prefix>-<index>` key，选中后被判为"全部" | `assetPageShared.tsx:buildTreeNodes`、`AssetOverviewPage.tsx:267` |
| L04 | 矩阵列硬编码 `slice(0, 8)`，截断无提示 | `AssetOverviewPage.tsx:180` |
| L05 | 矩阵列名依赖 `listDomains(0, 200)`，与树的 `getDomainTree()` 是两个数据源 | `AssetOverviewPage.tsx:84`、`:98` |
| L06 | `overview` 扫描上限为 2000（`scanPageSize 200 × scanMaxPages 10`） | `CatalogAssetPortalService.java:88-89` |
| L07 | `total = 分页后 OM 计数 + legacyPage.total()`（**全局值**），每页都并入 | `CatalogAssetPortalService.java:155` |
| L08 | `truncated = rows.size() < total`，因 L07 恒真 → 警告恒亮 | `CatalogAssetPortalService.java:96` |
| L09 | `overview` 每行 `findFirstByOmAsset` + `findFirstByFqnIgnoreCase` → N+1 | `CatalogAssetPortalService.java:138-139` |
| L10 | 批量路径 `loadCandidateMetadata` 已存在，目前仅标签筛选分支使用 | `CatalogAssetPortalService.java:248`、`:187` |
| L11 | `canRead` 依赖三方解析 + 显式授权 + JWT 回退链，无法用纯 SQL 复现 | `CatalogAssetPortalService.java:712`、`AccessChecker.java:46,135` |
| L12 | 台账已是全局菜单一级入口，契约测试断言其恰好出现一次 | `DataAssetPortalMenu.source-contract.test.ts:23-30` |
| L13 | `CatalogDomain` 已有 `code`/`owner`/`description`/`parent`/`lifecycleStatus`/`accessPolicy` | `CatalogDomain.java` |
| L14 | 前端无路由级权限 hook，`useModuleManageAccess` 只有模块 manage 粒度 | `hooks/useModuleManageAccess.ts` |
| L15 | service 层测试脚手架已存在（Mockito + 全 repo mock），无需新建 | `CatalogAssetPortalServicePermissionParityTest`、`CatalogAssetPortalTagFilterTest`、`CatalogAssetOverviewAggregatorTest` |
| L16 | `MetricTile` 被 4 处复用且被契约测试断言为导出符号 → 不可修改，缺口面板须另建组件 | `assetPageShared.tsx`、`DatasetsPage.asset-map-visual.source-contract.test.ts:14` |
| L17 | `STALE`/`DISABLED` 不在 `CatalogAssetLifecycleStatus`（9 值）中且从未被写入 → "失效资产"恒为 0，`attention` 的 STALE 分支是死代码 | `CatalogAssetLifecycleStatus.java`、`CatalogAssetOverviewAggregator.java:51`、`assetPortalUx.helpers.ts:37`、`DatasetsPage.tsx:341` |
| L18 | 治理状态后端实有 8 个值，前端 `GOVERNANCE_STATUS_LABELS` 只收 5 个；已被使用的 `PENDING_GOVERNANCE` 未收录 | `AssetOverviewPage.tsx:43-49`、`assetPortalUx.helpers.ts:52` |
| L19 | 所有枚举映射均以 `\|\| 原值` 兜底 → 漏译静默存活，无法被测出 | `assetPageShared.tsx:156`、`AssetOverviewPage.tsx:334`、`AssetLedgerView.tsx:166,182` |

## 8. UI/UX 规格总览

### 8.1 左侧 `DomainScopeNav`

```text
┌ 资产范围 ───────────────────── ┐
│  🔍 搜索主题域…                │   域数 >8 才渲染
├────────────────────────────────┤
│  全部资产              360  ⚠360│   概览行，非树节点
├─ 业务主题域 ─────────────── 5 ─┤   分区标题 + 域计数
│    地铁域      DTMS       0     │   空域整行 40% 不透明
│    租赁域      LEASE      0     │
│    财务域      FIN        0     │
│  ▸ 项目管理域   PJM        0     │   有子域才出现箭头
├─ 待治理 ───────────────────────┤   独立分区（ADR-75-04）
│  ⚠ 未归域             360  ⚠360│
└────────────────────────────────┘
```

- **数字**：总数 `tabular-nums` slate-600；待处置 amber，仅 >0 时出现。`truncated` 时加 `≥` 前缀。
- **选中态**：3px 左侧主色条 + 中性加深底 + 文字加粗（不用 antd 默认淡蓝块）。
- **空域**：整行 40% 不透明，使"五个域全空"一眼可见。
- **缺标识域**：disabled + tooltip「该主题域缺少标识，无法作为筛选条件」。
- **控件**：无折叠按钮（靠 `Sider breakpoint="lg"`）、无行级 hover 按钮、无右键菜单。整行是唯一交互。
- **四态**：加载中 skeleton 保留分区骨架；统计失败时树仍可用但数字位显示 `—`；域列表为空显示"尚未创建主题域"+ 指向治理主题域页的行内链接；`truncated` 在分区标题右侧显示提示图标。

### 8.2 右侧信息架构

```text
资产地图                                    统计更新于 10:24 ⟳
当前范围：未归域                              ← 可点，带 domain 进台账

┌ 资产总量 ┐ ┌ 治理缺口 ────────────────────────────────┐
│          │ │ 待处置 360 / 总量 360              100%  │
│   360    │ │ ████████████████████████████████████     │
│ 未归域   │ │ 待归域 358 · 待认领 2 · 未定密 0 · 失效 0 │
└──────────┘ └──────────────────────────────────────────┘
                       ↑ 每项可点，直达台账对应筛选

┌ 分层分布（当前范围仅未归域，不画矩阵）───────────────┐
│ 来源层 SOURCE 1 │ 贴源层 ODS 31 │ 暂存层 STG — │ …  │
└────────────────────────────────────────────────────┘
                    ↑ 中文主 + 代号弱化（ADR-75-13）

┌ 待处置 Top 5 ────────────────────────────────┐
│ [卡片] [卡片] [卡片] [卡片] [卡片]            │  卡片本身可点
└──────────────────────────────────────────────┘
```

- 五张 KPI 卡合并为「资产总量大数字 + 治理缺口单条堆叠进度条」，四个原因收进一行。
- 0 值原因中性灰（ADR-75-11）；待处置真为 0 时整条变绿并显示"当前范围无待处置资产"。
- 控件净结果：**3 个按钮 → 1 个 icon-only 刷新**。
- 空范围时空态文案内嵌行内链接，保证零资产时也有带范围的台账出口。

## 9. Gate Registry

| Gate | 判定 | 阻断内容 |
|---|---|---|
| G-75-01 | `domainStats` 与 `listAssets` 在同一 `activeDept` 下计数一致 | 若不一致则禁止发布导航数字（口径漂移即回归 ADR-75-01 违规） |
| G-75-02 | `truncated` 仅在 `scanned >= SCAN_CAP` 时为真 | 误报警告未修复则 F4 不可验收 |
| G-75-03 | 地图页 chrome 按钮数 ≤1（icon-only 除外不计） | 契约测试断言，防止后续回加按钮 |
| G-75-04 | `/catalog/assets/ledger` 在 menu seed 中仍恰好出现一次 | 既有契约测试，防止 ADR-75-10 被违反 |
| G-75-05 | `buildTreeNodes` 不再产生 `fallback-` key | 契约测试断言 |
| G-75-06 | `?domain=` 深链可复现范围（刷新/分享/后退） | 契约测试断言 |
| G-75-07 | 正常数据下界面不出现英文枚举原值，也不出现"未知（" | 漏译即阻断 |
| G-75-08 | 前端字典覆盖 `CatalogAssetLifecycleStatus` 全部 9 值 | 后端加枚举而前端未补译即阻断 |
| G-75-09 | 失效判定命中 `DEPRECATED`/`ARCHIVED`/`BLOCKED`，不再比较不可达值 | 死代码未清除则 F1 不可验收 |

## 10. Feature 列表

| ID | Feature | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| F0 | 评审与可验收基线 | P0 | DRAFT | — |
| F1 | 统计口径与可见性单一事实源 | P0 | DRAFT | F0 |
| F2 | 带统计的主题域树契约 | P0 | DRAFT | F1 |
| F3 | 主题域范围导航组件 | P0 | DRAFT | F2、**F5/T01** |
| F4 | 地图页信息架构与控件收敛 | P1 | DRAFT | F2、F3、**F5/T01** |
| F5 | 界面中文化与枚举字典 | P0 | DRAFT | F0 |

> F5 编号靠后但**实施靠前**：其 T01（字典模块）是 F3、F4 的前置，新组件必须直接用字典，不得再写第二套映射。

## 11. 追溯矩阵

| ADR | Feature | Gate |
|---|---|---|
| ADR-75-01、02 | F1 | G-75-01 |
| ADR-75-03 | F2 | G-75-01 |
| ADR-75-04～08 | F3 | G-75-05、G-75-06 |
| ADR-75-09～12 | F4 | G-75-02、G-75-03、G-75-04 |
| ADR-75-13 | F5 | G-75-07、G-75-08 |
| ADR-75-14 | F1/T06 | G-75-09 |

## 12. 完成标准

1. 左侧导航每行显示总数与待处置数，空域整行弱化，缺标识域 disabled 且有说明；
2. `?domain=` 深链在两个页面均可复现范围；
3. 地图页 chrome 按钮 ≤1（icon-only 刷新）；
4. 五张 KPI 卡收敛为总量 + 治理缺口面板，0 值不再用绿色对勾；
5. 单域范围下不再渲染 8 行 × 1 列矩阵；
6. `truncated` 误报消除，警告文案反映真实原因；
7. `domainStats` 与台账计数在同一账号/部门上下文下一致；
8. `overview` 的 N+1 消除，扫描上限提至 5000 档；
9. 界面不出现英文枚举原值；分层以"中文 + 弱化代号"呈现，三处（地图/台账/详情）一致；
10. "失效资产"指标从结构性恒 0 变为真实计数；
11. 契约测试与单元测试覆盖 G-75-01～09；
12. `tsc --noEmit` 与前端 build 通过；Java 侧 `mvn test` 相关用例通过。

## 13. 非目标

- 不修改可见性规则（`canRead` / `AccessChecker` 零改动）；
- `attention` 的**判定维度**不增不减（仍为 未定密 ∨ 未归域 ∨ 失效 ∨ 治理待办），但"失效"一项的判定枚举按 ADR-75-14 修正为可达值。修正后 `attention` 数值上升属预期，验收时不得按"数字变了=改坏了"判定；
- 中文化只约束**界面**。`worklog/` 下的 spec 文档继续沿用 `P0`/`DRAFT`/`READY`，与 sprint-70～74 的既有惯例保持一致，便于跨 sprint 检索；
- 不新增菜单、不新增路由、不把台账升为 tab（ADR-75-10）；
- 不补前端路由级权限门：`drillToLedger` 保持现状的无条件跳转。若某角色有地图权限而无台账权限，点数据会落到无权路由——这是**现存行为**，补它需要接入菜单可见性解析，超出本 Sprint 范围（见 L14）；
- 不改动台账页自身的筛选、导出、诊断功能，只替换其左侧 Sider；
- 不做主题域的增删改（那属于治理主题域页）；
- 不引入图表库：分层分布用 CSS 横条，不为一个横条引入依赖。
