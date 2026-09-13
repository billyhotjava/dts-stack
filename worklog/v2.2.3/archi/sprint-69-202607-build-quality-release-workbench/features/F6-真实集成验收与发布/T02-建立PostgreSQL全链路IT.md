# T02：建立 PostgreSQL canonical lifecycle 全链路 IT

**优先级**：P0
**状态**：READY
**依赖**：T01

## 目标

通过真实 canonical API 和 PostgreSQL 完成候选创建、构建、质量、审核、发布、部分失败重试和回滚。

## 技术设计

- 不直接插入 PUBLISHED 数据；所有状态必须通过生产 service/resource 迁移。
- 测试记录真实 actor、tenant、ETag、幂等键和 event timeline。
- 注入一个 registration 步骤失败，证明 PARTIAL 与精确重试。
- 回滚后验证 StageProjection 降级和历史证据保留。

## 影响范围

- 新增 `ModelLifecycleCanonicalIT.java`
- 新增 PostgreSQL fixtures
- `it/evidence/api-postgres/`

## 实施步骤

1. 先让旧 Stage 6 语义和缺少 candidate API 的 IT 失败。
2. 通过真实 HTTP/API 完成主链及负向链。
3. 导出脱敏请求、响应、数据库断言和事件时间线。

## 完成标准

- [ ] canonical ModelLifecycle 而非旧 ModelingVNext 路径完成全链路。
- [ ] 任何手工补库、预置发布状态或跳过安全过滤器都会使 IT 失败。
- [ ] **UI 真实验收前置**：每次真实 API 状态迁移后刷新工作台并与 PostgreSQL 事件对账；多角色切换、PARTIAL 重试和回滚后的页面状态均一致。
