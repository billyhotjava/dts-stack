# T01: 扁平化域列表与 Top N 折叠

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标

重写 `DomainScopeNav` 的渲染层，使其在任意域数量下输出**单层、每行两要素**的列表，Top 6 之外的域收进可展开区，组件对外 props 契约保持不变。

## 技术设计 (Contract-first)

### 输入契约

```ts
// 不变，来自 buildDomainScopeNodes(domainTree, stats.byDomain)（assetPageShared.tsx:174-187）
type DomainScopeNode = {
  id: string | null;        // null = 该域缺标识，不可作为筛选条件
  name: string;
  code?: string;            // 仍在类型中，但 F1 之后不再渲染
  stats?: { total: number; attention: number };
  children?: DomainScopeNode[];
};

interface DomainScopeNavProps {
  nodes: DomainScopeNode[];
  allStats?: DomainScopeStats;
  unassignedStats?: DomainScopeStats;
  value?: string;                          // undefined = 全部资产
  onChange: (next: string | undefined) => void;
  loading?: boolean;
  truncated?: boolean;                     // true 时数字前缀 "≥"
}
```

### 输出契约

- 渲染 DOM 中，每个可选行带 `data-testid="domain-scope-row-{id|all|unassigned}"`、数量带 `data-testid="domain-scope-total-{...}"`（**沿用现有 testid 命名**，避免既有 e2e 全线失效）
- 待处置不再有独立数字 testid；改为在行上暴露 `data-attention="{n}"` 供断言
- 折叠行 `data-testid="domain-scope-overflow-toggle"`，带 `aria-expanded`
- `onChange` 调用值域：`undefined` | 域 `id` | `"__UNASSIGNED__"`

### 新增纯函数（同文件导出，供单测直接覆盖）

```ts
/** 递归展开为一层；丢弃 children，其余字段原样保留 */
export const flattenScopeNodes = (nodes: DomainScopeNode[]): DomainScopeNode[] =>
  nodes.flatMap((n) => [{ ...n, children: undefined }, ...flattenScopeNodes(n.children ?? [])]);

/**
 * 按 stats.total 倒序排名并切分。
 * - stats 缺省视为 total 0
 * - id === null 的节点恒排末尾，且不占用 Top N 名额（它不可点，不该挤掉可用域）
 * - total 相同时按 name 的 localeCompare 稳定排序，避免每次渲染顺序抖动
 */
export const rankScopeNodes = (
  flat: DomainScopeNode[],
  topN: number,
): { visible: DomainScopeNode[]; overflow: DomainScopeNode[] };
```

### 数据流

`AssetOverviewPage` 已有的 `getDomainTree({withStats:true})`（账本 #6）→ `buildDomainScopeNodes` 注入 `stats`（`assetPageShared.tsx:174-187`）→ 本组件 `flattenScopeNodes` → `rankScopeNodes(flat, 6)` → 渲染。**页面侧取数逻辑零改动**。

### 错误路径

| 情形 | 行为 |
|------|------|
| `nodes` 为空数组 | 渲染「尚未创建主题域」+ `/governance/subjects` 文字链；「全部资产」与「未归域」两行照常渲染 |
| 节点 `stats` 缺省 | 数量显示 `—`，参与排序时按 0 计，不崩 |
| `id === null` | 行 `disabled`，不可点，`title` 说明原因；不计入 Top N |
| 搜索无命中 | 显示「没有匹配的主题域」，Top N 截断在过滤态下停用 |
| `truncated=true` | 所有数字加 `≥` 前缀（沿用现有 `countText`） |

### 复用点（禁止另造）

- `countText`（`DomainScopeNav.tsx:35-38`）：`≥` 前缀与 `—` 缺省逻辑，原样保留
- `filterTree` → 改造为在**扁平数组**上的 `filter`，删除原递归保父逻辑（扁平后无父子）
- `UNASSIGNED_DOMAIN_KEY`（`assetPageShared.tsx:68`）
- 现有 `loading` 骨架分支（`DomainScopeNav.tsx:194-203`），行数 4→4 不变

### 实现方案

1. 删除 `renderNodes` 递归（`:157-192`）、`collapsed` state、`RightOutlined`/`DownOutlined` 折叠箭头 import
2. `ScopeRow` props 精简：删 `code`、`indent`、`leading`（前导槽改为固定 16px 占位或 `⚠` 图标）；新增 `attention?: number`
3. 新增 `flattenScopeNodes` / `rankScopeNodes` 两个导出纯函数
4. 组件体：`flat = flattenScopeNodes(nodes)` → 若 `showSearch && keyword` 走过滤分支（不截断）→ 否则 `rankScopeNodes(flat, 6)`
5. 折叠区：`expanded` state（默认 false）；展开时在折叠行**上方**追加 `overflow` 行，折叠行文案 `⌄ 更多 {n} 个域` / `⌃ 收起`
6. `SEARCH_THRESHOLD` 8 → 12；判定基数从原 `flatten(nodes).length` 改为 `flat.length`（值相同，去掉重复的本地 `flatten`）
7. 常量提取：`const TOP_N = 6;`（带注释说明取值依据 = 一屏可见上限）

## UI 交互规格

见 F1 README §UI/UX 规格（线框、四态、交互表、走查 5 步）。本 Task 不重复。

补充实现细节：

- 待处置圆点：`<span aria-hidden className="h-1.5 w-1.5 rounded-full bg-amber-500" />` + 行 `title` 追加「，含 N 个待处置」
- 选中态沿用现有 `border-l-[3px] border-l-blue-500 bg-slate-100 font-semibold`
- 空域（`total === 0`）沿用现有 `opacity-40` 弱化，不隐藏

## 影响范围

| 文件 | 变更 |
|------|------|
| `src/components/catalog/DomainScopeNav.tsx` | 重写渲染层，新增 2 个导出纯函数；预计 272 → ~200 行 |
| `src/components/catalog/DomainScopeNav.test.tsx` | 按新形态改写（账本 #13：13 处 render 用例，涉层级折叠/双数字/code/阈值的需改写） |
| `src/pages/catalog/AssetOverviewPage.tsx` | **无改动**（props 契约不变）；页面装配由 F3/T01 统一处理 |
| `src/pages/catalog/assets/assetPageShared.tsx` | 无改动（`buildDomainScopeNodes` 继续产出层级结构，由组件内部扁平化） |

无 SQL、无配置、无接口变更。

## 验证 (RED→GREEN)

先写测试（应失败），再实现。

### 契约测试（`DomainScopeNav.test.tsx`）

- [ ] `flattenScopeNodes` 三层嵌套 → 输出扁平数组，长度 = 节点总数，`children` 均为 `undefined`
- [ ] `rankScopeNodes` 按 `total` 倒序；`stats` 缺省节点排在有 stats 之后；`id === null` 恒在末尾且不占 Top N 名额
- [ ] `rankScopeNodes` 在 `total` 相同时按 name 稳定排序（连续调用两次结果一致）
- [ ] 20 个域输入 → 默认渲染 6 个域行 + 1 个 `domain-scope-overflow-toggle`
- [ ] 点折叠行 → 剩余 14 行出现，`aria-expanded` 变 `true`
- [ ] 行内**不含** `code` 文本，且每行只有 1 个数字节点（断言无 `domain-scope-attention-*` testid）
- [ ] `attention > 0` 的行带 `data-attention` 且 `title` 含「待处置」
- [ ] `id === null` 行 `disabled`，点击不触发 `onChange`
- [ ] 域数 12 → 不渲染搜索框；域数 13 → 渲染；输入关键词后不做 Top N 截断
- [ ] `truncated` → 数字带 `≥` 前缀
- [ ] `nodes: []` → 显示「尚未创建主题域」与治理主题域链接
- [ ] `value="d2"` → 该行 `aria-current="true"`

### UI 走查

见 F1 README §操作走查（5 步），证据入 `it/` IT-01。

### 边界

- [ ] 全部域 `total` 均为 0 → 仍按 name 稳定排序，不报错
- [ ] 恰好 6 个域 → **不**渲染折叠行
- [ ] 恰好 7 个域 → 折叠行文案为「更多 1 个域」

## Definition of Done

- [ ] 架构：上述契约测试全绿；`pnpm build` 无 TS 错误；无迁移
- [ ] UI：走查 5 步在运行实例完成，四态截图（加载/空/成功；错误态附全局 toast 截图）入 `it/`
- [ ] 切片：URL `?domain=` 与选中态双向同步，刷新不丢失
- [ ] 规模：`DomainScopeNav.tsx` ≤ 210 行
- [ ] 无占位证据
