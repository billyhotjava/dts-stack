# F6: 兼容迁移、IT 与回滚

**优先级**: P0
**状态**: READY

## 目标

保证旧语义页面/API、metric-pack、迁移 dry-run、React Flow 新工作台和发布链路有明确兼容、验收和回滚路径。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 旧入口兼容与弃用提示 | P0 | READY | F2 |
| T02 | metric-pack 到 graph draft 映射 | P0 | READY | F3,F4 |
| T03 | E2E 与 contract 测试 | P0 | READY | F1-F5 |
| T04 | 发布准入和回滚 runbook | P0 | READY | T03 |

## 完成标准

- [ ] 旧 `/api/semantic/**` 有兼容、代理或弃用说明。
- [ ] metric-pack 能生成 graph draft 和候选 artifact。
- [ ] IT 证据覆盖 React Flow、platform contract、dbt validation、发布 dry-run 和回滚。
