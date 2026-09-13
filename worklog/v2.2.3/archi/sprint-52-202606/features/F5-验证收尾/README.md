# F5: 验证收尾

**优先级**: P0
**状态**: READY

## 目标

新增 `metricWorkbench.source-contract.test.ts`，补充 `dataDevelopmentWorkbench.source-contract.test.ts` 新断言，过全量 tsc + build + source-contract。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | source-contract 测试补全 | P0 | READY | F1/T01, F2/T01, F3/T01, F4/T01 |
| T02 | tsc + build + 全量验证 | P0 | READY | T01 |

## 完成标准

- [ ] `metricWorkbench.source-contract.test.ts` 全部通过
- [ ] `dataDevelopmentWorkbench.source-contract.test.ts` 新增断言通过
- [ ] baseline source-contract 失败数不增加（当前 baseline = 10 个失败）
- [ ] `pnpm exec tsc --noEmit` 零报错
- [ ] `pnpm build` 成功（Chrome 95 legacy bundle）
