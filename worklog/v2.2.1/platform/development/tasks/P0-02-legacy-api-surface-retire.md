# P0-02 历史 API 面清理（Explore CRUD）

- 优先级：P0
- 状态：planned

## 范围

- 清理或封禁绕过服务层策略的旧接口，统一到 SQL Workbench 新接口。

## 子任务

- 评估并下线：
  - `SavedQueryResource.java` (`/api/saved-queries`)
  - `QueryExecutionResource.java` (`/api/query-executions`)
  - `QueryWorkspaceResource.java` (`/api/query-workspaces`)
- 若短期不能删除，增加严格 RBAC + 审计 + 只读策略。
- 更新 API 文档，明确唯一官方接口集为 `/api/sql/**`。

## 验收标准

- 旧接口不可再被前端使用。
- 安全扫描中不再出现“裸 CRUD 直连实体”风险。
- SQL Workbench 全流程回归通过。

## 风险与回滚

- 风险：第三方脚本仍调用旧接口。
- 回滚：旧接口保留兼容期但默认 403，并输出迁移提示。
