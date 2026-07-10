# Sprint-63 集成验证计划

## 测试命令（可复制）

- `cd source/dts-platform-webapp && node --test "src/components/journey/*.source-contract.test.ts" "src/pages/governance/*.source-contract.test.ts"`
- `cd source/dts-platform-webapp && pnpm vitest run src/pages/governance/warehousePlanningContext.test.ts`（行为测试按文件名单，避免 vitest 误收 node:test 契约文件）

## Source Contract

- [ ] 规划上下文创建、恢复、版本失效和 storage 降级。
- [ ] 主题域页选中主题域后进入 DWD/维度建模规划。
- [ ] 数据元页显示规划来源并输出带规划元数据的标准草稿。
- [ ] 低代码/SQL 建模页显示规划、主题域、标准草稿和维度模式。
- [ ] 缺少规划或标准时模型候选被阻断。
- [ ] 旅程条继续/返回/清参与旅程快照透传四个规划参数；快照恢复但 session 草稿失效时正确降级。

## Build

- [ ] `cd source/dts-platform-webapp && pnpm exec tsc --noEmit`
- [ ] `cd source/dts-platform-webapp && pnpm build`
- [ ] `git diff --check`
- [ ] GitNexus `detect_changes`

## Browser Smoke

- [ ] `/governance/subjects?journey=e2e-data-product`
- [ ] `/governance/standards/elements?journey=e2e-data-product&planningId=<id>&domainId=<id>&warehouseLayer=DWD&modelingMode=dimension`
- [ ] `/studio/low-code-development?journey=e2e-data-product&planningId=<id>&domainId=<id>&warehouseLayer=DWD&modelingMode=dimension&standardDraftId=<id>`
- [ ] `/studio/sql-modeling?journey=e2e-data-product&planningId=<id>&domainId=<id>&warehouseLayer=DWD&modelingMode=dimension&standardDraftId=<id>`

浏览器登录/DNS 证据继续挂靠 Sprint-61 F9，不在本 Sprint 虚标通过。

## 证据记录

实现过程中追加 RED/GREEN、构建结果、GitNexus 风险和已知 blocker。
