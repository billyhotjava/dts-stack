# T05: 灰度发布、迁移与回滚 runbook

**优先级**: P0
**状态**: DRAFT
**依赖**: F1-F6

## 目标

定义 API 接入从隐藏能力到正式开放的发布路径，以及失败时的关闭和回滚策略。

## 范围

- Feature flag：隐藏入口、只读数据源、允许 preview、允许调度。
- 数据库迁移：props 扩容、secret 表、checkpoint 表、execution plan 表。
- 回滚：关闭 API 入口、停止调度、保留历史任务、禁用 runner。
- 运维 runbook：常见故障、排查命令、数据修复和客户侧协同。

## 完成标准

- [ ] 可以按租户或环境灰度开放。
- [ ] 回滚不会删除已落 ODS 数据。
- [ ] 停用 API runner 后现有 DB/File 任务不受影响。
- [ ] runbook 覆盖上线、验证、降级和回滚。

