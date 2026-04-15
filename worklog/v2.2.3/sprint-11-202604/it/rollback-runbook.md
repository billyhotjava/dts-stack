# Sprint-11 回滚 SOP

## 触发条件

- P0 bug：无法执行 SQL / 用户数据丢失 / 安全漏洞
- P1 bug：核心路径阻断（> 10% 失败率）
- 审计日志丢失 > 1%
- 后端 5xx > 1%
- 数据库连接池打爆

## 快速回滚（预期 ≤ 5 分钟）

1. 运维把 webapp 容器环境变量 `WEBAPP_ENABLE_SQL_IDE_V2=false`
2. 重启 webapp 容器（或 reload runtime-config.js）
3. 前端下次刷新路由到 `QueryWorkbenchPage`
4. 观察 30 分钟确认恢复

## 现场保留

- **不要**删 `sql_ide_tab` 表（用户 Tab 数据）
- **不要**删 `query_execution_chunk`（结果缓存）
- **不要**drop `sqlide_view_*` UNLOGGED 表（二次查询临时视图）
- 保留 app log + audit log 2 周

## 根因分析

- 分析现场日志
- 打 bug ticket → 跟踪到 Sprint-11 某 Task 的 followup
- 修复后重走阶段 0 → 1 → 2

## 后端数据层回滚（如果需要）

所有新增表 + 列可用 Liquibase rollback 移除：
- `sql_ide_tab`（F2/T07）
- `saved_query.folder`（F3/T15）
- `query_execution_chunk`（F4/T16）
- `rows_json` jsonb 迁移（F4/T16 fix）

**仅在业务方同意丢失用户 Tab 状态 + 保存查询的 folder 标签 + 查询结果缓存时执行。**
