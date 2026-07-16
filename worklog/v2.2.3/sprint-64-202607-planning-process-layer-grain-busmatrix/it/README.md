# Sprint-64 集成验证计划

## 测试命令（可复制）

- `cd source/dts-platform-webapp && node --experimental-strip-types --test src/pages/governance/businessProcess.test.ts src/pages/governance/conformedDimensions.test.ts src/pages/governance/warehouseLayerRegistry.test.ts src/pages/modeling/grainDeclaration.test.ts`
- `cd source/dts-platform-webapp && pnpm vitest run src/pages/modeling/modelingLedger.test.ts src/api/sprint64GovernanceApi.test.ts`
- `cd source/dts-platform-webapp && pnpm exec tsc --noEmit && pnpm build`
- `git diff --check && xmllint --noout source/dts-admin/src/main/resources/config/liquibase/changelog/20260716-01_advanced_modeling_menu_label.xml source/dts-admin/src/main/resources/config/liquibase/master.xml`
- GitNexus `detect_changes`

## Source Contract

- [x] 业务过程创建/恢复/降级；processId 旅程透传与快照回归。
- [x] 分层注册表结构完整（5 层×职责×allowedUpstream×前缀）；workbench 消费 API 注册表并保留静态回退。
- [x] 依赖红线校验：合法流向 ready、违规流向（如 ADS←ODS）blocked 且给原因。
- [x] grain 门禁：无声明 blocked、声明后 ready；grainKeys 为空视为未声明。
- [x] 一致性维度种子 8 项齐全；矩阵勾选可保存恢复；建模页复用推荐、同名提示与引用 metadata 写入。

## Browser Smoke（挂靠 Sprint-61 F9）

- [ ] `/governance/subjects?journey=e2e-data-product`：业务过程列表与创建（依赖 Sprint-61/F9 登录基线）。
- [ ] 带 `processId` 的完整链路 URL 进入建模页，门禁显示 grain 缺口（依赖可登录部署环境）。
- [ ] 总线矩阵视图渲染与勾选（依赖可登录部署环境）。

## 证据记录

- RED：新增四个前端契约测试、API 客户端测试、LowCode/SQL/Workbench source-contract 测试，以及 `Sprint64GovernanceContractTest`；均先在缺少实现时失败。
- GREEN：本轮前端 source-contract 29/29、治理/建模纯 Node 测试 13/13、建模/API Vitest 7/7 通过；`pnpm exec tsc --noEmit` 与 `pnpm build` 通过；`git diff --check` 与 Liquibase XML 校验通过。
- Backend：历史证据记录 `Sprint64GovernanceContractTest`/`Sprint64GovernanceResourceTest` 4 tests 通过；本轮普通用户重跑被现有 `source/dts-common/target` root 权限阻断，切换 root 后 Maven 超过 6 分钟无 target 进展，已中止，需在构建机清理 target/依赖缓存后复核。
- Runtime：API 失败时 UI 保留 session 回退；真实迁移、浏览器 smoke、Chrome 95 视觉证据仍未完成。
