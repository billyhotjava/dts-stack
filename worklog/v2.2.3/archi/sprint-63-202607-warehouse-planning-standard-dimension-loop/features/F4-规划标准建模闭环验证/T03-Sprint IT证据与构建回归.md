# T03: Sprint IT 证据与构建回归

**优先级**: P0  
**状态**: DONE
**依赖**: T01/T02

## 目标

记录本 Sprint 的 RED/GREEN、构建、差异检查和已知环境阻塞。

## 技术设计

- 证据逐条追加进 `it/README.md`，格式沿用 Sprint-62（任务号+日期+GREEN/RED+命令输出摘要）。

## 影响范围

- 仅 `it/README.md` 与 sprint 状态文档

## 验证

- [x] `node --test "src/components/journey/*.source-contract.test.ts" "src/pages/governance/*.source-contract.test.ts"` 通过（按最终文件位置调整 glob）。
- [x] `pnpm vitest run src/pages/governance/warehousePlanningContext.test.ts src/components/journey/`（行为测试按文件名单跑，vitest 目录模式会误收 node:test 契约文件）。
- [x] `pnpm exec tsc --noEmit`、`pnpm build`、`git diff --check` 通过。
- [x] 浏览器登录/DNS blocker 与后端缺口（设计文档第 9 节四项）明确记录。
