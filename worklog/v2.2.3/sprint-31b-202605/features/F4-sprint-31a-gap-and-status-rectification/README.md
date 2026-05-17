# F4: Sprint-31A 漏项与口径修正

**优先级**: P1
**状态**: READY

## 目标

修正 Sprint-31A 中存在的状态口径不一致、低估优先级、文档空 evidence、字段血缘 backfill 策略缺失等漏项，使后续 Sprint 与外部审计读到的 Sprint-31A 状态与现实一致。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | Sprint-31A 状态口径修正 | P0 | READY | F1-F3 进入开发 |
| T02 | F4/T05 拒绝原因提级到 P0 | P0 | READY | - |
| T03 | F3/T04 字段血缘 backfill 策略 | P1 | READY | - |
| T04 | 5 个空 evidence 目录补齐 | P1 | READY | - |

## 完成标准

- [ ] Sprint-31A README / RX README / sprint-queue.md 三处状态用同一口径表达。
- [ ] F4/T05 拒绝原因任务升级到 P0 并追加 audit schema 描述。
- [ ] 字段血缘历史 backfill 有书面策略与 dry-run 报告。
- [ ] Sprint-31A IT evidence 5 个空目录补齐占位 README 与 owner。
