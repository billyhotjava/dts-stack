# F4: 验证、review 与 IT 证据

**优先级**: P0
**状态**: DONE

## 目标

用 focused tests、diff review 和现场 smoke checklist 验证审计目录 DB 化重构。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | focused 自动化验证 | P0 | DONE | F1-F3 |
| T02 | 审计设计与代码 review | P0 | DONE | T01 |
| T03 | IT 证据与状态收口 | P0 | DONE | T01, T02 |

## 完成标准

- [x] 后端 focused tests 通过。
- [x] review 发现已处理或记录为后续任务。
- [x] IT 目录包含命令、结果、残余风险和现场 smoke checklist。
