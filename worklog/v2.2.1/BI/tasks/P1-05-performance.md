# P1-05 性能优化（缓存/预热/渲染）

`status`: `doing`

## 目标
- 控制首屏时延和刷新时延，满足演示与交付要求。

## 范围
- BE：查询缓存 key/ttl、发布后预热任务。
- FE：分批渲染、骨架屏、同源请求合并与短 TTL 缓存。
- QA：50/100 组件压测。

## 交付物
- 缓存策略
- 预热任务
- 性能报告

## 验收
- 首屏 < 3s，缓存命中刷新 < 1s（目标）。

## 本轮进展
- 已在 `useCardDataSource` 增加：
  - 同源并发请求合并（inflight dedupe）
  - 5 秒短 TTL 结果缓存（card/api/database）
- 新增后端缓存治理接口（`DatasetResource`）：
  - `GET /api/dataset/cache/policy/{databaseId}`
  - `POST /api/dataset/cache/policy/{databaseId}`
  - `POST /api/dataset/cache/warmup`
- 预览页改为分批挂载组件（降低大屏首屏卡顿）。
- 目标：减少同屏多组件共享数据源时的重复查询与后端压力。

## 依赖
- P1-04 数据源一致化已完成。
