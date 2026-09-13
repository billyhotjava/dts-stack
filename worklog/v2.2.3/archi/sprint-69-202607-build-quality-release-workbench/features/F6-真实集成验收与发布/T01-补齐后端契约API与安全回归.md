# T01：补齐后端契约 API 与安全回归

**优先级**：P0
**状态**：READY
**依赖**：F1-F4

## 目标

对 Sprint-69 后端命令、查询、迁移、权限、租户、并发和错误契约完成分层自动化回归。

## 技术设计

- service 层覆盖状态机、门禁、漂移、幂等、PARTIAL、rollback。
- resource 层覆盖 HTTP 状态、ProblemDetail、ETag、Idempotency-Key 和 authority。
- repository 层使用 PostgreSQL 覆盖唯一键、索引、事务和租户。
- 不用 H2 结论替代 PostgreSQL 特性结论。

## 影响范围

- `src/test/java/.../service/modeling/`
- `src/test/java/.../web/rest/`
- PostgreSQL Testcontainers/IT 配置

## 实施步骤

1. 汇总 F1-F5 已编写但未运行的测试源码并补齐缺失矩阵。
2. 在唯一测试窗口直接运行 `npm run backend:unit:test`，不再预跑重复 focused suite。
3. 保存 Surefire、Liquibase 和安全回归摘要。

## 完成标准

- [ ] P0 正向/负向契约均有自动测试且无 ignored。
- [ ] 已知仓库告警与 Sprint-69 新失败在报告中分开。
- [ ] **UI 真实验收前置**：400、403、404、409、422、500 均有前端分支测试，页面显示稳定错误说明和恢复动作，不以通用 toast 掩盖业务状态。
