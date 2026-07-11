# Sprint-64 集成验证计划

## 测试命令（可复制）

- `cd source/dts-platform-webapp && node --test "src/components/journey/*.source-contract.test.ts" "src/pages/governance/*.source-contract.test.ts"`
- `cd source/dts-platform-webapp && pnpm vitest run src/pages/governance/businessProcess.test.ts src/pages/governance/warehouseLayerRegistry.test.ts src/pages/governance/conformedDimensions.test.ts`（按最终文件名调整）
- `cd source/dts-platform-webapp && pnpm exec tsc --noEmit && pnpm build && git diff --check`
- GitNexus `detect_changes`

## Source Contract

- [ ] 业务过程创建/恢复/降级；processId 旅程透传与快照回归。
- [ ] 分层注册表结构完整（5 层×职责×allowedUpstream×前缀）；workbench 消费注册表而非硬编码。
- [ ] 依赖红线校验：合法流向 ready、违规流向（如 ADS←ODS）blocked 且给原因。
- [ ] grain 门禁：无声明 blocked、声明后 ready；grainKeys 为空视为未声明。
- [ ] 一致性维度种子 8 项齐全；矩阵勾选可保存恢复；建模页复用推荐渲染。

## Browser Smoke（挂靠 Sprint-61 F9）

- [ ] `/governance/subjects?journey=e2e-data-product`：业务过程列表与创建。
- [ ] 带 `processId` 的完整链路 URL 进入建模页，门禁显示 grain 缺口。
- [ ] 总线矩阵视图渲染与勾选。

## 证据记录

实现过程中按任务追加 RED/GREEN、命令输出摘要与 blocker。
