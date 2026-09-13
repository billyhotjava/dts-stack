# F4: 血缘查询一致性与性能收敛

**优先级**: P1
**状态**: DRAFT（T04 的性能目标待 G0 的 Q1 实测定量）

## 目标

消除血缘四个子页之间的行为分裂：同一份数据只请求一次、四页筛选状态都能刷新恢复与分享、大目录下仍能选中目标数据集、影响分析在真实规模下满足响应预算。

## 契约定义 (Contracts)

| 类型 | 契约 | 关键字段/签名 |
|---|---|---|
| 前端 hook | `useLineageImpact(params)` | 入参 `{datasetId, direction, depth, projectName, layers[], changedWithinHours, snapshotAt}`；出参 `{impact, nodes, edges, columnLineages, loading, error, refetch}`。基于 TanStack Query，queryKey 为参数元组，四页共享缓存 |
| 前端 URL 协议 | 四页统一 | `datasetId / direction / depth / project / layers / changed / at / layout / columns / verification`；参数名沿用图谱页现有定义（账本#16），新增 `verification` |
| REST | `GET /api/catalog/datasets`（既有 `listDatasets`） | 新增/确认支持 `keyword` 服务端搜索 + 分页；前端改为 `size=50` 首屏 + 搜索防抖 |
| REST | `GET /api/catalog/lineage/impact` | **契约不变**，仅内部实现改批量查询 |

## UI/UX 规格

本 Feature 不新增页面，只统一既有四页行为。用户可感知的变化：

| 变化 | 用户表现 |
|---|---|
| 共享缓存 | 在 Segmented 上切换「影响分析/图谱/字段血缘」不再重新 loading，瞬时切换 |
| 统一 URL | 在影响分析设好筛选后刷新页面，筛选保持；复制链接给同事可见同一视图 |
| 服务端搜索 | 数据集下拉输入即搜，不再受 300 条上限限制 |
| 性能 | depth=5 查询从"转圈很久"到满足 `assets/nfr-budget.md` 预算 |

**零回归红线**：图谱页既有 URL 契约测试（`lineageF2.source-contract.test.ts:22-33`，账本#16）的全部断言必须继续通过；参数名、`replace: true` 行为、默认值省略规则均不得变。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 抽取 useLineageImpact 共享数据层 | P1 | READY | F2/T03、F3/T02（同文件冲突，排在其后） |
| T02 | 四页统一 URL 状态协议 | P1 | READY | T01 |
| T03 | 数据集选择器服务端搜索分页 | P1 | DRAFT | 需确认 `listDatasets` 是否已支持 keyword |
| T04 | impact/diff 批量化查询消除 N+1 | P1 | DRAFT | G0 的 Q1（无基线无法判断"优化够了没"） |

## Definition of Ready

- [x] 契约已钉死（hook 出入参、URL 参数集、REST 契约不变声明）
- [x] 竖切片已画通（四页 → hook → 既有接口，无新接口）
- [x] UI 落点已命名（四页既有控件，无新增）
- [ ] 依赖已就绪 —— T03 待确认后端 keyword 支持；T04 待 Q1 基线
- [x] 验收可验证

## 完成标准

- [ ] 四个页面中 `loadDatasets`/`loadImpact` 的复制实现（账本#17，现 4 份）归零，源码契约测试断言之
- [ ] 切换子页的额外网络请求数 = 0（契约测试断言）
- [ ] 四页各有 URL 状态契约测试；图谱页既有断言零回归
- [ ] `impact` 单次请求 SQL 条数 ≤ `depth + 3`（`assets/nfr-budget.md`）
- [ ] 数据集下拉在 >300 条目录下可搜索选中（IT-07）
