# T01: 抽取 useLineageImpact 共享数据层

**优先级**: P1
**状态**: READY
**依赖**: F2/T03、F3/T02（三者都改 `pages/catalog/Lineage*.tsx`，本 Task 必须排最后避免冲突）

## 目标

四个子页共用一个数据层 hook，消灭 4 份复制的 `loadDatasets/loadImpact`（账本#17），并让 Segmented 切页不再重发请求。

## 技术设计 (Contract-first)

### 现状（账本#17）

| 文件 | 复制段 |
|---|---|
| `LineageImpactPage.tsx:40-81` | `loadDatasets` + `loadImpact` + 两个 `useEffect` |
| `LineageGraphPage.tsx:75-116` | 同上 |
| `LineageColumnsPage.tsx:34-75` | 同上 |
| `LineageDiffPage.tsx:29-41` | 仅 `loadDatasets` |

四页请求参数完全一致（`withJobs:true, withColumns:true`），切页即重发。

### 输出契约

新建 `features/catalog/useLineageImpact.ts`：

```ts
export interface LineageQueryParams {
  datasetId?: string;
  direction: LineageDirection;
  depth: number;
  projectName?: string;
  layers: string[];
  changedWithinHours: number;
  snapshotAt?: string;      // 已转 ISO Instant
}

export function useLineageImpact(params: LineageQueryParams, keyword: string): {
  impact: ImpactResult | null;
  nodes: ImpactNode[];          // 已过 useLineageData 关键词过滤
  edges: ImpactEdge[];
  columnLineages: ColumnLineage[];
  loading: boolean;
  error: unknown;
  refetch: () => void;
};

export function useLineageDatasetOptions(): {
  options: Array<{ label: string; value: string }>;
  loading: boolean;
  reload: () => void;
};
```

### 缓存契约

- 基于 TanStack Query（项目已在用，NFR 预算「新增依赖 0」）。
- `queryKey = ["lineage-impact", datasetId, direction, depth, projectName, layers.join(","), changedWithinHours, snapshotAt]`。
- `staleTime` 30s：切页命中缓存，**0 额外请求**；改筛选立即重查。
- `keyword` **不进 queryKey**——关键词是纯前端过滤（既有 `useLineageData` 语义，`lineageShared.tsx:245`），进 key 会导致每键一次请求。
- 数据集选项独立 query，`staleTime` 5min。

### 落点归属

hook 放 `features/catalog/`（与 `lineageContracts.ts` 同级）而非 `pages/catalog/lineageShared.tsx`——后者已 486 行（账本#3），且 `lineageF2.source-contract.test.ts` 已断言组件不得反向依赖 `pages/`（账本#16 该测试第一条）。

### 错误路径

- 查询失败 → `error` 非空，各页渲染既有 `EmptyState` + 「重新加载」（复用 `EmptyAction`）。全局拦截器仍负责 toast，页面不重复提示（既有约定）。
- `datasetId` 为空 → query `enabled: false`，返回空数组，不发请求（保持既有"未选数据集不查询"行为）。

### 复用点

- `useLineageData` 关键词过滤逻辑（`lineageShared.tsx:245-284`）原样复用，只是移到 hook 内部调用。
- `loadDatasetOptions`（`lineageShared.tsx:75`）迁入 `useLineageDatasetOptions`，本 Task 不改其 300 条上限（由 T03 处理）。

## UI 交互规格

无控件变化。唯一可感知差异：Segmented 切页从"重新 loading"变为瞬时。需在 IT-06 中用 Network 面板证明请求数为 0。

## 影响范围

| 文件 | 改动 |
|---|---|
| `features/catalog/useLineageImpact.ts` | **新建** |
| `pages/catalog/LineageImpactPage.tsx` | 删除 `loadDatasets`/`loadImpact`/两个 effect，改用 hook |
| `pages/catalog/LineageGraphPage.tsx` | 同上（保留其 URL effect，T02 再统一） |
| `pages/catalog/LineageColumnsPage.tsx` | 同上 |
| `pages/catalog/LineageDiffPage.tsx` | 改用 `useLineageDatasetOptions` |
| `pages/catalog/lineageShared.tsx` | `loadDatasetOptions` 迁出；`useLineageData` 保留（hook 内部调用） |
| `features/catalog/lineageDataLayer.source-contract.test.ts` | **新建** |

## 验证 (RED→GREEN)

- [ ] `no-duplicated-lineage-fetch`：源码契约断言四个页面文件中 `getCatalogLineageImpact` 出现次数为 **0**（全部走 hook）
- [ ] `no-duplicated-load-datasets`：断言 `loadDatasetOptions(` 在 `pages/catalog/Lineage*.tsx` 中出现 0 次
- [ ] `keyword-not-in-query-key`：断言 `queryKey` 定义不含 `keyword`
- [ ] `disabled-when-no-dataset`：`datasetId` 为空时 query 不触发
- [ ] `switch-section-hits-cache`：同参数连续两次调用只产生一次网络请求
- [ ] 既有 `lineageF2.source-contract.test.ts` 全绿（含"组件不得依赖 pages"与"keyword 单一语义"两条）

## Definition of Done

- [ ] 架构：5 条契约测试绿 + 既有测试零回归
- [ ] UI：不适用（无控件变化），但需 IT-06 的 Network 面板证据
- [ ] 切片：四页在运行实例上功能等价，无行为回退
- [ ] 账本#17 的 4 份复制归零
- [ ] 无占位证据
