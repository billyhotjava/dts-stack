# F3: 验证与回归

**优先级**: P0
**状态**: READY

## 目标

完整端到端走通"创建大屏 → preview 停留 → 我常用的报表出现"链路；同时确保 Sprint-15 既有测试无回归。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 端到端验证 | P0 | READY | F1, F2 |
| T02 | 回归 Sprint-15 测试 | P0 | READY | F1 |

## 完成标准

- [ ] E2E 证据放 `worklog/v2.2.3/sprint-17-202604/it/`
- [ ] Sprint-15 全套 40+ 后端测试 + 前端 LeaderOverviewPage 全绿
- [ ] 实测 dts-platform 启动 60s 内 reconcile 写入 `bi_report_link`
- [ ] 测试账号停留 4s 后 `bi_report_visit` 多 1 行；leader-overview "我常用的报表" 显示该大屏
