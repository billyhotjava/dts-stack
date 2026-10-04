# T01: KPI 四卡替换缺口面板

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标

以一行 4 张等宽 KPI 卡（资产总量 / 待处置 / 未定密 / 标签覆盖）替换现有「1 张 `MetricTile` + `GovernanceGapPanel` 绿进度条」的不对称布局，其中仅「未定密」可下钻，其余为纯展示。

## 技术设计 (Contract-first)

### 输入契约

来自既有 `getCatalogAssetsOverview(scope)`（`platformApi.ts:125`，账本 #6）：

```ts
type AssetOverview = {
  total?: number;
  unclassified?: number;
  missingDomain?: number;
  stale?: number;
  attention?: number;
  tagged?: number;
  untagged?: number;
  tagCoveragePercent?: number;
  governanceStatusCounts?: Record<string, number>;
  byDomain?: Record<string, { total: number; attention: number }>;  // ← 新补，后端早已返回
  scanned?: number;
  truncated?: boolean;
  // byLayer / matrix / domainCountByLayer 后端仍返回，本页 F3 之后不再消费
};
```

### 输出契约

渲染 4 个卡片节点，testid 固定：

| testid | 数值来源 | 语义标签 | 交互 |
|--------|----------|----------|------|
| `kpi-total` | `total` | 资产总量 | 无（`<div>`） |
| `kpi-attention` | `attention` | 待处置 | 无（`<div>` + Tooltip） |
| `kpi-unclassified` | `unclassified` | 未定密 | `<button>` → `?view=table&unclassified=1` |
| `kpi-tag-coverage` | `tagCoveragePercent` | 标签覆盖 | 无（`<div>`） |

### 新增纯函数（放 `assetPageShared.tsx`，供两个 Task 与测试共用）

```ts
/** truncated 时绝对数加 ≥ 前缀；undefined 显示 —（与 DomainScopeNav 的 countText 口径一致） */
export const overviewCount = (value: number | undefined, truncated?: boolean): string;

/** 占比，分母为 0 时返回 "0%"，不产生 NaN%（现网 total=0 时正是这个分支） */
export const overviewPercent = (part: number | undefined, whole: number | undefined): string;
```

`overviewPercent` 是必需的：截图中现网范围 `total=0`，任何 `part/total` 直算都会得到 `NaN%`。

### 数据流

`AssetOverviewPage` 既有的 `loadOverview()`（`AssetOverviewPage.tsx:145-184`）→ `overview` state → 本 Task 的 KPI 行。**取数逻辑零改动**，仅消费端替换。

### 错误路径

| 情形 | 行为 |
|------|------|
| `overview === null`（首屏未回） | 四卡渲染骨架数字条，卡框保持高度不塌 |
| 请求失败 | 全局拦截器 toast；`overview` 保持上一次值；无上次值则显示 `—` |
| `total === 0` | 四卡显示 `0` / `0%`（**不显示 `—`**，0 是真实结论），`overviewPercent` 返回 `0%` |
| `truncated === true` | 绝对数加 `≥`；百分比加 `*` 与 Tooltip 说明 |
| `unclassified === 0` | 卡仍可点（跳过去是空结果，但筛选条件真实成立，不算欺骗） |

### 复用点（禁止另造）

- `MetricTile`（`assetPageShared.tsx:189-210`，账本 #5）：作为四卡基元，**扩展**其 props 增加可选 `onClick`，不新写卡片组件
- 现有 `drillToLedgerByReason` 的 URL 拼装思路（`AssetOverviewPage.tsx:292-309`），但目标改为 `/catalog/search?view=table`
- `UNASSIGNED_DOMAIN_KEY`

### 实现方案

1. `MetricTile` 扩展：新增可选 `onClick?: () => void` 与 `hint?: string`；有 `onClick` 时渲染为 `<button>` 并加 hover 边框态，否则渲染为 `<div>`（**不加 `cursor-pointer`**）
2. `assetPageShared.tsx` 新增 `overviewCount` / `overviewPercent`
3. `AssetOverviewPage` 中删除 `GovernanceGapPanel` 引用与 `gapReasons` memo（`:276-290`），删除 `drillToLedgerByReason` 中除 `unclassified` 外的分支
4. 用 `grid grid-cols-2 gap-3 md:grid-cols-4` 渲染四卡
5. 删除文件 `src/pages/catalog/assets/GovernanceGapPanel.tsx` 与 `GovernanceGapPanel.test.tsx`（账本 #4：唯一引用方即本页）

> 注：`AssetOverviewPage.tsx` 的整体装配、矩阵删除与契约测试改写由 **F3/T01** 统一收口，本 Task 只做 KPI 行本身与共享工具；两者在同一文件相邻区域改动，实施时按 F2/T01 → F2/T02 → F3/T01 顺序串行，避免冲突。

## UI 交互规格

见 F2 README §UI/UX 规格。补充：

- 卡片样式沿用 `MetricTile` 现有 `rounded-lg border border-slate-200 bg-white px-4 py-3`
- 可点卡 hover：`hover:border-blue-300`，标签行右侧加 `→` 小箭头（`text-slate-300 group-hover:text-blue-500`）
- 「待处置」卡数字用琥珀色 `text-amber-600`（`attention > 0` 时），为 0 时用常规深灰
- 「待处置」Tooltip 文案（逐字）：`待处置 = 未定密 ∨ 未归域 ∨ 已失效 ∨ 治理状态待处理。无单一筛选条件，请按具体原因下钻。`

## 影响范围

| 文件 | 变更 |
|------|------|
| `src/pages/catalog/assets/assetPageShared.tsx` | `MetricTile` 扩展 `onClick`/`hint`；新增 `overviewCount`、`overviewPercent` |
| `src/pages/catalog/AssetOverviewPage.tsx` | 删 `GovernanceGapPanel` 引用与 `gapReasons`；新增 KPI 行；`AssetOverview` 类型补 `byDomain` |
| `src/pages/catalog/assets/GovernanceGapPanel.tsx` | **删除** |
| `src/pages/catalog/assets/GovernanceGapPanel.test.tsx` | **删除** |
| `src/pages/catalog/AssetOverviewPage.source-contract.test.ts` | 由 F3/T01 统一改写（此处仅记录会失效：`:106` `:107`） |

无 SQL、无接口、无配置变更。

## 验证 (RED→GREEN)

### 契约测试

新建 `src/pages/catalog/assets/overviewFormat.test.ts`：

- [ ] `overviewCount(0, false)` → `"0"`；`overviewCount(undefined, false)` → `"—"`
- [ ] `overviewCount(126, true)` → `"≥126"`
- [ ] `overviewPercent(18, 126)` → `"14%"`
- [ ] `overviewPercent(0, 0)` → `"0%"`（**不得为 `NaN%`**）
- [ ] `overviewPercent(5, undefined)` → `"0%"`

新建 `src/pages/catalog/assets/kpiTiles.test.tsx`（组件级）：

- [ ] 四张卡按 `kpi-total` / `kpi-attention` / `kpi-unclassified` / `kpi-tag-coverage` 全部渲染
- [ ] `kpi-total` 与 `kpi-attention` 的 DOM 节点**不是** `button`（`expect(el.tagName).not.toBe('BUTTON')`）
- [ ] `kpi-unclassified` 是 `button`，点击回调收到 `?view=table&unclassified=1`
- [ ] `truncated` → `kpi-total` 文本含 `≥`
- [ ] `total=0` → 四卡分别为 `0`/`0`/`0`/`0%`，页面不含 `NaN`
- [ ] `attention > 0` → `kpi-attention` 数字带琥珀色类名
- [ ] `kpi-attention` 带含「无单一筛选条件」的 Tooltip 文案

### UI 走查

F2 README §走查 步骤 1–3，证据入 `it/` IT-02。

### 边界/错误路径

- [ ] `overview === null` → 骨架态，卡框高度与成功态一致（无跳动）
- [ ] `unclassified = 0` 时卡仍可点

## Definition of Done

- [ ] 架构：上述测试全绿；`pnpm build` 无 TS 错误；`GovernanceGapPanel` 两文件已删除且全仓无残留引用
- [ ] UI：四卡在 `total=0` / 正常 / `truncated` 三态各有截图，入 `it/`
- [ ] 切片：点「未定密」在运行实例上跳转成功，目标页结果数与卡上数字一致
- [ ] 无占位证据
