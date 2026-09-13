# F4: 验证收尾

**优先级**: P0  
**状态**: DONE

## 目标

用契约测试、类型检查和文档证据证明指标建模 UI 与运行监控收敛可交付。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | source-contract 与 TypeScript | P0 | DONE | F1 F2 F3 |
| T02 | Sprint 文档和截图/烟测证据 | P1 | DONE | T01 |

## 完成标准

- [x] source-contract 覆盖菜单、路由、工作台、页面流程。
- [x] `pnpm exec tsc --noEmit` 通过。
- [x] IT README 有真实命令和结果。
