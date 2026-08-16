# F6：发布安全与端到端验收

**优先级**：P0
**状态**：BLOCKED（依赖 F0～F5 和目标环境）

## 目标

使用真实账号、真实菜单和运行数据库证明首次物化、质量、发布、治理、二次物化和回滚完整不断链，并形成可运维交付证据。

## 契约定义

| 类型 | 契约 | 关键输出 |
|---|---|---|
| E2E | `it/README.md` IT-01～13 | commit/image/actor/IDs/步骤/结果/截图/日志 |
| 发布 | `assets/release-plan.md` | Expand/Shadow/Backfill/Read switch/rollback |
| 运维 | `assets/runbook.md` | 指标、日志字段、阈值、故障剧本 |
| 审计 | dts-admin 动作字典 | observation/reconcile/backfill/sync/quality/publish 均有分类 |

## UI/UX 规格

使用 xiezm 从真实菜单完成全链；部门角色只做负向越权。代码全部完成后集中运行一次 E2E，失败只做针对性重跑。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 完成首次与二次物化的治理全链验收 | P0 | BLOCKED | F0～F5、目标环境 |
| T02 | 完成发布回滚观测和运行手册 | P0 | BLOCKED | T01、发布窗口 |

## Definition of Ready

- [x] IT 编号、证据格式和执行顺序已定义。
- [ ] F0～F5 契约和模块验证全绿。
- [ ] 登录、Chrome 95、数据库快照和发布窗口可用。

## 完成标准

- [ ] IT-01～13 全部 PASS，无占位证据。
- [ ] 二次物化不增加模型/资产数量。
- [ ] 回滚和故障注入后可恢复且历史不丢失。
- [ ] runbook、告警、日志和审计可用于真实故障处置。
