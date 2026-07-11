# Sprint-64 集成验证计划

## 测试命令（可复制）

- `cd source/dts-platform-webapp && node --test "src/components/journey/*.source-contract.test.ts" "src/pages/governance/*.source-contract.test.ts"`
- `cd source/dts-platform-webapp && pnpm vitest run src/pages/governance/businessProcess.test.ts src/pages/governance/warehouseLayerRegistry.test.ts src/pages/governance/conformedDimensions.test.ts`（按最终文件名调整）
- `cd source/dts-platform-webapp && pnpm exec tsc --noEmit && pnpm build && git diff --check`
- GitNexus `detect_changes`

## Source Contract

- [x] 业务过程创建/恢复/降级；processId 旅程透传与快照回归。
- [x] 分层注册表结构完整（5 层×职责×allowedUpstream×前缀）；workbench 消费 API 注册表并保留静态回退。
- [x] 依赖红线校验：合法流向 ready、违规流向（如 ADS←ODS）blocked 且给原因。
- [x] grain 门禁：无声明 blocked、声明后 ready；grainKeys 为空视为未声明。
- [x] 一致性维度种子 8 项齐全；矩阵勾选可保存恢复；建模页复用推荐渲染。

## Browser Smoke（挂靠 Sprint-61 F9）

- [ ] `/governance/subjects?journey=e2e-data-product`：业务过程列表与创建。
- [ ] 带 `processId` 的完整链路 URL 进入建模页，门禁显示 grain 缺口。
- [ ] 总线矩阵视图渲染与勾选。

## 证据记录

- RED：新增四个前端契约测试、API 客户端测试、LowCode/SQL/Workbench source-contract 测试，以及 `Sprint64GovernanceContractTest`；均先在缺少实现时失败。
- GREEN：前端 `node --experimental-strip-types --test` 契约套件通过；Vitest journey/API/dimension gate 共 26+ 测试通过；`LEGACY_BROWSER_BUILD=1 pnpm exec tsc --noEmit` 通过。
- Backend：`./mvnw -Dtest=Sprint64GovernanceContractTest,Sprint64GovernanceResourceTest test` 通过（4 tests）；Liquibase XML 已通过 `xmllint`。
- Runtime：API 失败时 UI 保留 session 回退，等待部署环境执行真实迁移与浏览器 smoke。
