# BI P1-05 首轮落地：发布 Warmup + 缓存观测

## 已完成能力
- 发布自动 Warmup：
  - 发布接口 `/api/screens/{id}/publish` 在保存发布版本后，自动扫描屏幕组件中的 `database` 数据源并执行预热。
  - 入口实现：`ScreenWarmupService.warmupForPublishedScreen(...)`。
  - 发布返回增加 `warmup` 摘要（总计/成功/跳过/失败）。

- 缓存治理 API（DatasetResource）：
  - `GET /api/dataset/cache/stats`
  - `GET /api/dataset/cache/policy/{databaseId}`
  - `POST /api/dataset/cache/policy/{databaseId}`
  - `POST /api/dataset/cache/warmup`

- QueryCache 策略生效：
  - `cacheNativeQueries=false` 时，native query 读取与写入缓存都会被绕过。

- 前端缓存观测：
  - 设计器头部新增“缓存观测”入口。
  - 支持查看命中率/命中次数/未命中次数/驱逐次数。
  - 支持按数据库编辑缓存策略（启用、TTL、是否缓存 Native Query）。

## 说明
- 自动 warmup 当前按“数据库 SQL 数据源”执行，模板变量 SQL（含 `{{ }}`）默认跳过。
- warmup 使用发布操作者身份做权限校验，不绕过现有查询权限模型。

## 后续建议
- 增加“发布后异步 warmup 任务日志”与重试策略。
- 支持按大屏版本维度保存 warmup 执行记录，接入审计。
