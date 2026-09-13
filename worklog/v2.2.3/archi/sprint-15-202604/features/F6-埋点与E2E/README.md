# F6: 埋点与 E2E

**优先级**: P1
**状态**: READY

## 目标

1. 把 F3-F5 里临时桩的 `auditLog` 接入真实的审计上报通路。
2. Playwright E2E 覆盖所领导整路流（切域 / 切部门 / 打开 TOP 报表）。
3. 补齐单测覆盖率短板，尤其是 hook + 服务层。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 审计埋点接入 auditService | P0 | READY | F3/T05, F4/T03, F5/T04 |
| T02 | hook / service 单测补齐 | P1 | READY | F3/T01, F4/*, F5/T04 |
| T03 | LeaderOverviewPage 集成测试 | P1 | READY | F5/T04 |
| T04 | Playwright E2E · 所领导整路流 | P0 | READY | F5/T05 |

## 完成标准

- [ ] `auditLog` 桩替换为真实 `auditService.record`（或项目既有等价 API）。
- [ ] 单测覆盖率在 `src/pages/workbench/**` 下 ≥ 80%。
- [ ] E2E 脚本在本地 `pnpm test:e2e` 可运行通过，生成 trace / screenshot 存入 `assets/`。
- [ ] `it/README.md` 给出真实接口 curl 输出 + E2E 运行截图证据。
