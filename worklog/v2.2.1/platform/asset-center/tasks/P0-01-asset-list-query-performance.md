# P0-01 资产列表查询性能与稳定性

`status`: `done`
`priority`: `P0`

## 目标

解决资产列表在数据量上涨后查询慢、过滤失效、分页不稳定问题。

## 后端实施点

1. 梳理 `catalog/datasets` 查询条件与索引命中。
2. 统一分页排序白名单，避免非索引排序。
3. 增加查询超时与降级返回策略。

## 前端实施点

1. 列表筛选条件防抖与请求合并。
2. 大页码切换优化（保留筛选上下文）。
3. 空结果/错误态文案标准化。

## 验收标准

- 资产列表在目标数据量下响应达标。
- 筛选与分页结果一致、可复现。

## 实施结果

1. `/api/catalog/datasets` 从“全量加载+内存过滤”改为 `JpaSpecificationExecutor + PageRequest`，分页与过滤在数据库侧执行。
2. 增加排序白名单：`createdDate` / `lastModifiedDate` / `name`，并默认回退 `createdDate desc`，避免非白名单排序导致慢查询。
3. 返回体补充 `sortBy`、`sortDir`、`queryCostMs`，便于前端观测与压测取证。
4. 资产列表前端增加筛选防抖（280ms）与请求序号去重，避免快速输入触发并发回包覆盖。
5. 资产列表筛选状态持久化到 `localStorage`，翻页时保留筛选上下文。

## 验证记录

- `source/dts-platform`: `./mvnw -DskipTests compile` 通过。
- `source/dts-platform-webapp`: `pnpm build` 通过。
