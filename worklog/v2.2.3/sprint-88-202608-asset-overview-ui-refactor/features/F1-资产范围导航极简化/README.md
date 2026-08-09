# F1: 资产范围导航极简化

**优先级**: P0
**状态**: READY

## 目标

左侧「资产范围」在 240px 宽度内以**单层列表**呈现，任意域数量下纵向不溢出显示框架；用户不展开任何折叠即可在 Top 6 主题域与「未归域」之间切换范围。

## 契约定义 (Contracts)

本 Feature 不触碰网络契约，只重定义组件 props 契约。

| 类型 | 契约 | 关键字段/签名 |
|------|------|---------------|
| 组件 | `DomainScopeNav` | `{ nodes: DomainScopeNode[]; allStats?; unassignedStats?; value?: string; onChange: (next?: string) => void; loading?: boolean; truncated?: boolean }` — **props 签名不变**，仅内部渲染与 `DomainScopeNode.code` 的使用方式变化 |
| 组件 | `DomainScopeNode` | `{ id: string \| null; name: string; code?: string; stats?: {total,attention}; children?: DomainScopeNode[] }` — **类型不变**，`children` 由组件内部扁平化消费，`code` 不再渲染 |
| 纯函数（新增） | `flattenScopeNodes(nodes): DomainScopeNode[]` | 递归展开为一层，丢弃 `children`，保留原 `stats` |
| 纯函数（新增） | `rankScopeNodes(flat, topN): { visible, overflow }` | 按 `stats.total` 倒序；`total` 缺省视为 0；`id === null` 的节点恒排在末尾且不计入 Top N |
| 数据来源 | `getDomainTree({withStats:true})` | 见账本 #6；`stats.byDomain[id]` 为该域自身资产数，不含子域（账本 #7） |

**上游不变量（账本 #7）**：父域 `stats` 不含子域资产，因此扁平化后各行数字之和 = 全部域资产数，不会重复计数。此为 ADR-88-01 成立的前提，实施期若发现后端口径变化，必须回到 ADR 重议而非在前端补减法。

## UI/UX 规格

### 入口与导航

- 页面：`/catalog/assets`（资产概览），左侧 `Layout.Sider`，宽度 240px，`breakpoint="lg"` / `collapsedWidth={0}` 保持不变。
- 选中范围写入 URL `?domain=<uuid> | __UNASSIGNED__`，无 `domain` 参数即「全部资产」。**不引入组件内部选中 state**。

### 布局线框

```
┌ 资产范围 ────────────────┐   ← 标题行：ApartmentOutlined + 文字，无统计
│                          │
│ ● 全部资产          126  │   ← 概览行，选中态左侧 3px 蓝条
│                          │
│ 主题域                   │   ← 分组标签，右侧不再显示计数
│   研发项目治理       48  │
│   财务业务           32  │
│   人力资源       ·   21  │   ← 「·」= 琥珀圆点，表示该域含待处置
│   供应链             14  │
│   客户主数据          8  │
│   风控                3  │
│   ⌄ 更多 4 个域          │   ← 折叠区，默认收起；展开后追加渲染剩余域
│                          │
│ 待治理                   │
│ ⚠ 未归域         ·    6  │   ← 恒定置底，不参与 Top N 排序
└──────────────────────────┘
```

每行结构固定为三段：`[前导槽 16px] [名称 flex-1 truncate] [数量 tabular-nums]`；待处置圆点画在数量左侧 4px 处，`title` 提示「含 N 个待处置资产」。

### 四态

| 态 | 表现 |
|----|------|
| 加载 | 标题骨架 + 4 行 28px 灰条脉冲（沿用现有 `loading` 分支样式，行数由 6 改 4） |
| 空（无任何域） | 「尚未创建主题域」+ 指向 `/governance/subjects` 的文字链（沿用现有文案）；「全部资产」与「未归域」两行仍渲染 |
| 错误 | 由全局拦截器 toast；组件退化为空态，不自绘错误块 |
| 成功 | 如线框 |

### 关键交互

| 动作 | 触发契约 | 用户看到 |
|------|----------|----------|
| 点「全部资产」 | `onChange(undefined)` → URL 删除 `domain` | 该行进入选中态，右侧统计重载 |
| 点某主题域行 | `onChange(id)` → URL `?domain=<uuid>` | 同上 |
| 点「未归域」 | `onChange("__UNASSIGNED__")` | 同上 |
| 点「⌄ 更多 N 个域」 | 组件内部 `expanded` state 翻转 | 剩余域就地追加在折叠行**上方**，折叠行文案变「⌃ 收起」 |
| 点 `id === null` 的域 | 无（`disabled`） | 光标 not-allowed，`title` 说明「该主题域缺少标识，无法作为筛选条件」 |
| 域数 > 12 时 | 渲染搜索框（阈值由 8 提高到 12） | 输入即在**扁平全集**内过滤，过滤态下不做 Top N 截断 |

搜索阈值上调理由：扁平化后行数就是域总数，8 已过低会让常见规模（10 个域左右）无谓多出一个输入框；12 行 ≈ 一屏可见上限。

### 操作走查 (happy path)

1. 进入 `/catalog/assets` → 左侧显示「全部资产」选中态 + Top 6 主题域 + 「未归域」
2. 点「财务业务」→ 该行变选中态，URL 变为 `?domain=<财务 uuid>`
3. 点「⌄ 更多 4 个域」→ 剩余 4 个域就地展开
4. 点其中一个域 → 选中态转移，URL 更新，右侧 KPI 与图表随之重载
5. 刷新浏览器 → 选中态由 URL 恢复，不丢失

### 可访问性/兼容

- 每行为原生 `<button>`，Tab 可依次聚焦，Enter/Space 选中；选中行 `aria-current="true"`
- 「更多」折叠行带 `aria-expanded`
- 待处置圆点为纯装饰（`aria-hidden`），语义信息由 `title` 与随后的 sr-only 文本承担
- Chrome 95：仅使用 flex + `tabular-nums`，无 container query、无 `:has()`

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 扁平化域列表与 Top N 折叠 | P0 | READY | - |

## Definition of Ready

- [x] 契约已钉死（组件 props 签名不变，新增两个纯函数签名已写明）
- [x] 竖切片已画通（URL → `getDomainTree` → `stats.byDomain` → 行渲染，无 TBD 层）
- [x] UI 落点已命名（`/catalog/assets` 左侧 Sider，每行 `data-testid="domain-scope-row-*"`）
- [x] 依赖已就绪（账本 #2 #3 #7 已记录既有实现与唯一调用方）
- [x] 验收可验证（走查 5 步 + 单测断言，见 T01）

## 完成标准

- [ ] 20 个域的 mock 数据下，侧栏渲染行数 ≤ 9（1 全部 + 6 域 + 1 折叠行 + 1 未归域），不出现纵向溢出
- [ ] 行内不再出现 `code` 代号与第二个数字列
- [ ] `DomainScopeNav.test.tsx` 按新形态改写后全绿，含：扁平化不重复计数、Top N 截断与展开、`id===null` 禁用、搜索阈值 12、`truncated` 时数字带 `≥`
- [ ] 走查 5 步在运行实例上通过，证据入 `it/` IT-01
