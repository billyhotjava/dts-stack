# 数据资产页域导航对齐建议

**日期**：2026-08-09
**状态**：PROPOSAL（未批准；建模模块重构中，收敛动作待统一调度）
**类型**：IA / 术语对齐 / 跨模块打通
**上文**：Sprint-85 数据资产门户体验收敛（ADR-85-01 已确立"资产概览=治理概览仪表盘"）
**范围**：`/catalog/assets`（资产概览）、`/catalog/assets/ledger`（资产台账）的左侧域导航，及其与数据建模模块的联系

---

## 一、结论摘要

起点问题是「左侧菜单是否有必要，搜索组合+Table 是否更优」。勘察后结论分两层：

1. **布局层**：侧栏本身可以留（域是主导航），但它当前是**唯一被提拔为侧栏的筛选维度**，与工具栏的其余五个维度不对等，已产生可复现的一致性缺陷。
2. **更根本的一层**：域的定义源在数据建模模块，而资产侧**用错了术语、指向了菜单外的页面**。域既然是主导航，这条链路断着，侧栏留不留都导不准。

因此建议不是"删侧栏"，而是**先接通域的定义源，再修筛选一致性**；布局形态本身只做一处收敛（概览页去侧栏）。

---

## 二、现状勘察

### 2.1 域的定义源

`/catalog/domains` 一棵两层树，在建模模块中按层级分成两个概念
（`pages/data-modeling/prototype/PlanningCatalogEditors.tsx:20` 注释）：

```
业务分类 = catalog domain 根节点（parentId = null）
数据域   = catalog domain 子节点
```

- CRUD 实现：`prototype/services/planningCatalogDomainService.ts`
- 菜单入口：`portal-menu-seed.json:221` → `/data-modeling/planning/business-categories`
  `portal-menu-seed.json:244` → `/data-modeling/planning/domains`
- 规模：现网中等量级，10–50 个节点、两层（与上述结构吻合）

### 2.2 资产侧的三处漂移

**漂移一：术语。** `DomainScopeNav.tsx:236` 分组标题写「业务主题域」，渲染的却是上面这棵
「业务分类 / 数据域」两层树。而建模模块另有一个**真正的主题域**
（`navigation.ts:28` `planning/subjects`，"应用层主题域及其服务对象"，`portal-menu-seed.json:277`），
是不同实体。资产页占用了别的实体的名字。

同一实体目前至少四种叫法：

| 位置 | 叫法 |
|---|---|
| `DomainScopeNav.tsx:236` | 业务主题域 |
| `SubjectAreasPage.tsx:510` | 业务分类 |
| `GovernanceCenterPage.tsx:61` | 主题域 |
| 建模模块 `navigation.ts:8/16` | 业务分类（根） / 数据域（子） |

**漂移二：空态引导指向菜单外页面。** `DomainScopeNav.tsx:247-252`：

```tsx
尚未创建主题域，请先在 <a href="/governance/subjects">治理主题域</a> 中创建。
```

`/governance/subjects` → `SubjectAreasPage`，**不在菜单种子中**。新用户按引导点过去，
落在一个从菜单里找不到的页面上。

**漂移三：域的能力分裂在两个页面。**

| | 菜单可达 | 能力 |
|---|---|---|
| `/data-modeling/planning/*` | 是 | 仅 CRUD drawer（`PlanningCatalogEditors.tsx`） |
| `/governance/subjects` | 否 | 树 CRUD（`SubjectAreasPage.tsx:518-613`）+ 4 tab（`SubjectWorkspaceTabs.tsx:26-29`：建模范围 / 数据集市 / 主题域信息 / 治理概览） |

两者操作同一个 `/catalog/domains`。看似是一次未完成的迁移：CRUD 搬到了建模模块，
4 个 tab 的能力没搬，老页面从菜单摘除后靠深链续命。

`/governance/subjects` 现有 5 处活引用，**不可直接删除**：

| 引用处 | 用途 |
|---|---|
| `warehousePlanningContext.ts:320/332/346` | 数仓规划上下文缺失时的 repairRoute（带 `?active=<domainId>`） |
| `GovernanceCenterPage.tsx:61` | 治理中心卡片 |
| `DataManagementWorkbenchPage.tsx:161` | 数据管理工作台入口 |
| `helpTopics.ts:200` | 帮助中心指引 |
| `DomainScopeNav.tsx:248` | 资产侧栏空态 |

外加 e2e `sprint71-data-tags-real.spec.ts:379/427/440` 走 `?active=<id>&tab=governance`。

> 注：`gitnexus impact` 对 `SubjectAreasPage` 与 `DomainScopeNav` 均返回 `impactedCount: 0`，
> 与事实不符（`DomainScopeNav` 明确被 `AssetOverviewPage.tsx:7`、`DatasetsPage.tsx:18` 导入）。
> 该索引未覆盖此类 React 组件引用，本文引用关系以全量检索为准。

### 2.3 筛选一致性缺陷（与布局无关，独立成立）

**缺陷 A：重置静默清域。** `DatasetsPage.tsx:505`

```ts
const activeFilterCount =
  [assetType, classification, governanceStatus, matchStatus].filter(...).length +
  (effectiveSelectedTagIds.length > 0 ? 1 : 0);
```

`domain` 与 `layer` 未计入，但 `resetFilters`（`DatasetsPage.tsx:437-439`）会
`params.delete("domain")` / `delete("layer")`。后果：

1. 侧栏选中「财务域」→ 筛选徽标仍为 0，"重置"按钮不出现
   （条件 `activeFilterCount > 0 || keyword.trim()`）；
2. 再选一个密级 → "重置"出现 → 点击 → 侧栏选中的域被静默清掉，用户无预期。

`layer` 来自概览页矩阵下钻，同样漏计。

**缺陷 B：窄屏域不可达。** `AssetOverviewPage.tsx:322` / `DatasetsPage.tsx:601` 均为
`breakpoint="lg" collapsedWidth={0}`，lg 以下侧栏整体消失，此时 `domain` 只能靠手改 URL。
域既然是主导航，这是功能性缺口。

### 2.4 概览页侧栏与主体信息重复

`AssetOverviewPage.tsx:462-525` 的「分层×主题域矩阵」，列即主题域、格子即 total/attention，
与侧栏 `ScopeRow`（`DomainScopeNav.tsx:112-127`）的 total/attention 是同一份信息的两个副本。

---

## 三、已定决策

| ID | 决策 | 依据 |
|---|---|---|
| D-01 | 域是主导航（先选域再看资产），台账页**保留**侧栏 | 用户确认的使用方式 |
| D-02 | 术语以建模模块为准，**资产侧改名跟随**：一级「业务分类」、二级「数据域」 | 建模模块是定义源；改动局限在资产侧文案，风险最低 |
| D-03 | 概览页**去掉**侧栏，域切换与下钻由矩阵承担 | 与矩阵信息重复；概览页不缺横向空间但缺乏职责区分 |
| D-04 | `SubjectAreasPage` **本轮不删** | 5 处活引用 + 独有 4 tab 能力；删除会断数仓规划 repairRoute 与三条 e2e |

> D-04 修正了本次讨论早期"该页已下线、可删"的判断——该判断仅核对了菜单种子，未核对代码引用，不成立。

---

## 四、建议方案

### 波次 1：可独立推进（不依赖建模重构）

**1.1 术语对齐**
- `DomainScopeNav.tsx:236` 分组标题「业务主题域」→ 按层级拆为「业务分类」（一级）/「数据域」（二级）
- 台账表格域列、`AssetOverviewPage` 矩阵表头（`AssetOverviewPage.tsx:467`「分层 \ 主题域」）同步改名
- `assetPortalUx.helpers.ts:42` 的 `"缺少主题域"` 等文案一并对齐
- 保留 URL 参数名 `?domain=`（对外协议不动，避免打断 Sprint-85 已铺的 URL filter protocol 与既有深链）

**1.2 修筛选一致性**
- `DatasetsPage.tsx:505` `activeFilterCount` 补入 `domain` 与 `layer`
- 侧栏选中态与工具栏筛选徽标、重置行为归一
- 窄屏（lg 以下）在工具栏补一个域 TreeSelect 兜底，消除域不可达

**1.3 概览页去侧栏**
- 移除 `AssetOverviewPage.tsx:320-336` 的 `Layout.Sider`
- 域切换收进已有的 `scope-echo` 行（`AssetOverviewPage.tsx:358`，已有"当前范围：xxx"回显位）改为 TreeSelect
- 矩阵列头点击 = 切域，与格子点击（下钻台账）区分开
- 侧栏的 total/attention 信息在概览页由矩阵完整承载，无信息损失

**1.4 反向打通（低成本，先做一半）**
- 建模模块数据域行 → `/catalog/assets/ledger?domain=<id>` 查看该域下资产
- `listPlanningCatalogDomains`（`planningCatalogDomainService.ts:59`）当前调 `getDomainTree()` 未带
  `withStats`；接上 `withStats: true` 后建模侧可直接显示各域资产量/待处置数，与资产侧同源同数

### 波次 2：待建模重构落定后统一调度

以下动作的落点取决于建模模块重构结果，**本轮不做**，列出以便统一调度时一并决策：

| 待调度项 | 依赖 | 说明 |
|---|---|---|
| 侧栏空态与「管理域」入口的最终指向 | 两个域页面如何收敛 | 当前空态指 `/governance/subjects`（菜单外）。可先临时改指 `/data-modeling/planning/business-categories` 止血，但最终指向随收敛结果定 |
| `SubjectAreasPage` 4 tab 能力的去留 | 建模模块是否吸收建模范围/数据集市/主题域信息/治理概览 | 吸收后方可删老页并重定向 5 处深链 |
| 域 CRUD 双实现收口 | 同上 | `PlanningCatalogEditors` 与 `SubjectAreasPage` 目前各写一套 `/catalog/domains` 读写 |
| `GovernanceCenterPage.tsx:61`「主题域」卡片改名与改指 | 术语最终口径 | 第四种叫法，随 D-02 一并清理，但目标路由待定 |

---

## 五、不做的事

- 不改 `?domain=` URL 参数名与 `/catalog/domains` 后端契约
- 不动 `ASSET_PORTAL_V2_ENABLED` 双轨开关（属 Sprint-85 F1 范围）
- 不删 `SubjectAreasPage`、不动其 4 个 tab
- 不新增菜单项（沿用 Sprint-85 ADR-85-10）
- 不把台账侧栏改成"搜索组合+Table"的纯工具栏形态——域是主导航，形态保留

---

## 六、验证要点

- 术语：全仓检索确认「业务主题域」在资产侧零残留；建模模块 `planning/subjects`（应用层主题域）未被误改
- 缺陷 A：侧栏选域后筛选徽标计数正确；点击"重置"后域的清除行为与用户预期一致（连同 URL）
- 缺陷 B：320 / 768 / 1024 / 1440 四档断点下域筛选均可达
- 概览页：去侧栏后矩阵列头切域、格子下钻台账两个交互不冲突；单域范围退化为分层分布时仍可切回全部
- 回归：`AssetOverviewPage.source-contract.test.ts`、`DatasetsPage.*.source-contract.test.ts`、
  `DomainScopeNav.test.tsx`、`DataAssetPortalMenu.source-contract.test.ts` 全绿
- 深链：概览治理缺口下钻（`?unclassified` / `?stale` / `?governance`）与矩阵下钻（`?layer` / `?domain`）不受改名影响
