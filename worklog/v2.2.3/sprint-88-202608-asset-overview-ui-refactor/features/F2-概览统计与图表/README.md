# F2: 概览统计与图表

**优先级**: P0
**状态**: READY

## 目标

主区首屏用 4 张对称 KPI 卡 + 2 个常规图表（治理状态环形、主题域 Top6 堆叠条形）替代现有「1 大卡 + 1 绿进度条 + 矩阵 + Top5」，让用户不滚动即可回答：有多少资产、多少要治、治理进度如何、货集中在哪个域。

## 契约定义 (Contracts)

无新增网络契约。全部字段取自既有 `GET /api/catalog/assets/overview`（账本 #6）。

| 类型 | 契约 | 关键字段/签名 |
|------|------|---------------|
| REST（既有） | `GET /api/catalog/assets/overview?domainId=&domainUnassigned=` | 本 Feature 消费：`total`、`attention`、`unclassified`、`tagged`、`untagged`、`tagCoveragePercent`、`governanceStatusCounts`、`byDomain`、`missingDomain`、`truncated`、`scanned` |
| 前端类型（需补齐） | `AssetOverview` | 现有 TS 声明（`AssetOverviewPage.tsx:42-56`）**漏了 `byDomain`**，须补 `byDomain?: Record<string, {total:number; attention:number}>` |
| 组件（新增） | `AssetGovernanceDonut` | `{ counts?: Record<string, number>; total: number; truncated?: boolean; loading?: boolean; onSliceClick: (status: string) => void }` |
| 组件（新增） | `AssetDomainBars` | `{ byDomain?: Record<string,{total,attention}>; domainNames: Map<string,string>; unassigned?: {total,attention}; truncated?: boolean; loading?: boolean; onBarClick: (domainKey: string) => void }` |
| 图表基建（既有） | `Chart` from `@/components/chart` | `{option: EChartsOption; height?; className?; loading?}`（账本 #14） |

### 字段口径（必须在 UI 上如实表达）

| KPI | 取值 | 副文案 | 可点 |
|-----|------|--------|------|
| 资产总量 | `total` | `{域数} 个主题域` | 否 |
| 待处置 | `attention` | `占比 {attention/total}%` | **否**（ADR-88-06） |
| 未定密 | `unclassified` | `需补定密` | 是 → `?unclassified=1` |
| 标签覆盖 | `tagCoveragePercent`% | `{tagged}/{total}` | 否 |

`truncated=true` 时（账本 #10：扫描上限 200 条），**所有绝对数字加 `≥` 前缀**，百分比后加 `*` 并由 Tooltip 说明「基于前 {scanned} 条可见资产」。页面顶部仍保留现有 `Alert` 提示。

## UI/UX 规格

### 入口与导航

- 页面：`/catalog/assets` 主区，`Layout.Content` 内。无新增路由、无新增菜单。

### 布局线框

```
┌────────────┬────────────┬────────────┬────────────┐
│ 资产总量    │ 待处置      │ 未定密   →  │ 标签覆盖    │
│   126      │    18      │     7      │   62%      │
│ 6 个主题域  │ 占比 14%    │ 需补定密    │  78/126    │
└────────────┴────────────┴────────────┴────────────┘
┌─ 治理状态 ─────────────┐┌─ 主题域分布 Top 6 ────────┐
│        ╭─────╮         ││ 研发项目治理 ████████░ 48 │
│       │ 82%  │ ■ 已治理 ││ 财务业务     █████     32 │
│       │已治理 │ ■ 待定密 ││ 人力资源     ███░      21 │
│        ╰─────╯ ■ 待审核 ││ 供应链       ██        14 │
│                ■ 已停用 ││ 客户主数据   █          8 │
│                         ││ ⚠ 未归域     █░         6 │
└─────────────────────────┘└──────────────────────────┘
       ░ = 待处置部分（堆叠段）
```

栅格：KPI 行 `grid-cols-2 md:grid-cols-4`；图表行 `grid-cols-1 lg:grid-cols-2`。图表高度统一 260px，保证 1440×900 下整页一屏。

### 四态

| 态 | KPI 卡 | 环形图 | 条形图 |
|----|--------|--------|--------|
| 加载 | 数字位骨架条，卡框不塌 | `Chart` 的 `loading` 遮罩 | 同左 |
| 空（`total === 0`） | 四卡显示 `0` / `0%`，**不置灰**（0 是真实结论） | 不画图；显示「当前范围内暂无资产」+ 「切到全部资产」文字链 | 同左 |
| 错误 | 全局拦截器 toast；卡显示上一次成功值或 `—` | 图区显示「统计加载失败」+ 复用页头刷新按钮的提示 | 同左 |
| 成功 | 如线框 | 环形 + 中心完成率 + 右侧 legend | 6 条堆叠横条 |

**空态刻意不画空图**：ECharts 空 series 会渲染出一个无意义的灰圈，比一句话更差。

### 关键交互

| 动作 | 触发契约 | 用户看到 |
|------|----------|----------|
| 点「未定密」卡 | `router.push('/catalog/search?view=table&unclassified=1' + domain)` | 跳数据查询表格视图，筛出未定密资产 |
| Hover「待处置」卡 | 无 | Tooltip：「待处置 = 未定密 ∨ 未归域 ∨ 已失效 ∨ 治理状态待处理；无单一筛选条件，请按具体原因下钻」 |
| 点环形扇区 / legend 项 | `?view=table&governance=<STATUS>` | 跳数据查询，筛出该治理状态 |
| 点条形图某域条 | `?view=table&domain=<uuid>` | 跳数据查询，筛出该域 |
| 点条形图「未归域」条 | `?view=table&domain=__UNASSIGNED__` | 同上 |
| Hover 条形堆叠段 | 无 | ECharts tooltip：`总计 48 · 待处置 5` |

**「待处置」卡与「资产总量」卡不可点**：不渲染为 `<button>`，无 hover 抬升、无箭头图标 —— affordance 与能力必须一致。

### 操作走查 (happy path)

1. 进入 `/catalog/assets` → 首屏见 4 张 KPI 卡 + 2 个图表，无需滚动
2. Hover「待处置」卡 → 看到口径说明 Tooltip，光标为默认箭头（非手型）
3. 点「未定密」卡 → 跳到 `/catalog/search?view=table&unclassified=1`，表格结果数与卡上数字一致
4. 返回，点环形图「待定密」扇区 → 跳数据查询，`governance` 筛选已勾选
5. 返回，点条形图「财务业务」条 → 跳数据查询，域筛选为财务业务
6. 左侧切到「财务业务」范围 → KPI 与两图同步重载，条形图退化为单条

### 可访问性/兼容

- 可点 KPI 卡为 `<button>`，Tab 可达，Enter 触发；不可点卡为 `<div>` 且无 `tabindex`
- 图表为 canvas，**必须**在图表卡内提供 sr-only 文本摘要（如「治理状态：已治理 103，待定密 12，待审核 8，已停用 3」），否则读屏用户拿不到数据
- Chrome 95：ECharts canvas 渲染；若 IT-05 实测异常，降级方案 = 环形改 CSS `conic-gradient` 占比环、条形改纯 div 宽度百分比（降级方案不需要新依赖）

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | KPI 四卡替换缺口面板 | P0 | READY | - |
| T02 | 治理状态环形图与主题域条形图 | P0 | READY | T01（共用 `AssetOverview` 类型补齐与 `truncated` 数字格式化工具） |

## Definition of Ready

- [x] 契约已钉死（消费字段、两个新组件 props 签名、`byDomain` 类型补齐均已写明）
- [x] 竖切片已画通（既有 API → 既有 Service → 组件 → 出口 URL，无 TBD）
- [x] UI 落点已命名（`/catalog/assets` 主区；四卡与两图的可点/不可点逐个指定）
- [x] 依赖已就绪（账本 #5 #6 #8 #9 #14）
- [x] 验收可验证（走查 6 步 + 各 Task 的契约测试）

## 完成标准

- [ ] 四卡在 `total=0`、正常、`truncated` 三种数据下均正确渲染（含 `≥` 前缀）
- [ ] 两图在空数据下显示文案而非空图
- [ ] 三个可下钻元素生成的 URL 与 Sprint README §出口参数映射表逐字一致
- [ ] 不可点的两张卡不是 `<button>`，无手型光标
- [ ] 每个图表卡含 sr-only 数据摘要
- [ ] 走查 6 步通过，证据入 `it/` IT-02、IT-03
