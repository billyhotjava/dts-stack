# F5：集成验收与安全交付

**优先级**：P0  
**状态**：DONE

## 目标

用真实认证、PostgreSQL、普通/dbt 实现、发布结果和 Chrome95 证明 Sprint-74 是完整产品旅程，并可安全迁移回滚。

## 契约定义

| 类型 | 契约 | 关键内容 |
|---|---|---|
| Fitness | `assets/nfr-budget.md` | 每行可执行红/绿 |
| IT | `it/README.md` | IT-01～IT-12 |
| Release | `assets/release-plan.md` | expand/migrate/contract + rollback |
| Ops | `assets/runbook.md` | 指标、日志、告警、故障处理 |

## UI/UX 规格

不新增 UI；对 F1～F4 的具名入口做完整四态和 Chrome95 验收。mock 只用于开发，不作为 DONE 证据。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 建立契约测试与真实Chrome95端到端验收 | P0 | DONE | F1～F4 |
| T02 | 完成迁移发布回滚与运维证据 | P0 | DONE | T01 |

## Definition of Ready

- [x] IT journey 和 NFR 预算已写明
- [x] 最终集中验证节奏已确定
- [x] F0 PASS
- [x] F1～F4 实现与 focused tests 完成

## 完成标准

- [x] IT-01～IT-12 全绿
- [x] NFR fitness functions 全绿
- [x] clean DB migration、dry-run、rollback rehearsal 通过
- [x] GitNexus detect_changes：risk=LOW、0 affected process；范围外用户修改已保留并在交付说明中分离
