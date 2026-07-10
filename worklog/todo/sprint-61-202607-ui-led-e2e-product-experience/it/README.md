# Sprint-61 集成验证计划

## Source Contract

- [x] `DataManagementWorkbenchPage.source-contract.test.ts` 覆盖 8 阶段旅程、下一步、上下文参数。
- [x] `dataDevelopmentWorkbench.source-contract.test.ts` 覆盖标准草稿在低代码/SQL 建模传递。
- [ ] 新增或扩展 `e2eDataProductJourney.source-contract.test.ts` 覆盖集成、规划、标准、建模、指标、服务、证据路由。
- [x] `portalGoldenLineMenu.source-contract.test.ts` 确认菜单顺序与 DataWorks-like 旅程一致，但 UI 不依赖频繁菜单跳转。
- [x] `JourneyContextBar.source-contract.test.ts` 覆盖子页面上下文条、返回工作台和继续下一步。
- [x] `JourneyStageState.source-contract.test.ts` 覆盖阶段状态、缺口、blocker 和下一步动作。
- [x] `DataProductAcceptancePackage.source-contract.test.ts` 覆盖客户验收包证据分组和缺失项。
- [ ] API 数据源 normalizer、已保存连接测试和新建表单 source config 的一致性测试。

## Browser Smoke

- [ ] `/workbench?journey=e2e-data-product`
- [ ] `/foundation/data-sources?journey=e2e-data-product`
- [ ] `/foundation/standard-package?journey=e2e-data-product`
- [ ] `/governance/standards/elements?journey=e2e-data-product&bindingDraft=1`
- [ ] `/studio/low-code-development?journey=e2e-data-product&standardDraftId=<id>`
- [ ] `/studio/sql-modeling?journey=e2e-data-product&standardDraftId=<id>`
- [ ] `/modeling/metric-workbench?journey=e2e-data-product&modelId=<id>`
- [ ] `/services/apis?journey=e2e-data-product&modelId=<id>`
- [ ] `/ops/instances?journey=e2e-data-product&modelId=<id>`
- [ ] `/workbench?journey=e2e-data-product&modelId=<id>&serviceId=<id>` 验收包区域

## Build

- [x] `cd source/dts-platform-webapp && node --test ...`
- [x] `cd source/dts-platform-webapp && pnpm build`
- [x] `git diff --check`
- [x] GitNexus `detect_changes`

## Chrome 95 风险

- [ ] 不使用仅现代浏览器支持的 CSS container query 作为关键布局。
- [ ] 旅程轨道在 1366x768 与窄屏下不遮挡按钮。
- [ ] 子页面 `JourneyContextBar` 在窄屏下不遮挡原页面主按钮。
- [ ] 标准草稿、门禁、证据卡片的长文本要换行，不撑破卡片。

## Next Feature Gate

| Feature | 必过验证 | 证据 |
|---------|----------|------|
| F6-旅程上下文组件化与页面接入 | source-contract + `pnpm build` + 3 页 browser smoke | 组件引用、query 保持、截图 |
| F7-阶段状态与缺口计算模型 | source-contract + blocker 场景测试 | 状态模型、缺口文案、下一步动作 |
| F8-客户验收包与证据聚合 | source-contract + 可登录 smoke | 验收包分组、缺失证据、导出入口 |
| F9-可登录浏览器验收与回归基线 | Playwright + Chrome 95/narrow viewport | 登录态、console、截图、失败诊断 |

## 验收证据

完成实现时，在本目录补充：

- source-contract 输出摘要
- `pnpm build` 输出摘要
- Playwright 截图路径或 smoke 日志
- 已知后端 API 缺口和 UI blocker 列表

## 2026-07-09 TDD 证据

- RED：`node --test src/pages/workbench/DataManagementWorkbenchPage.source-contract.test.ts`，`workbench exposes the end-to-end data product journey from integration to service evidence` 因缺少 `E2E_DATA_PRODUCT_JOURNEY` 失败。
- GREEN：同一命令 6/6 通过。
- 覆盖点：`journey=e2e-data-product`、`withE2EJourney`、`end-to-end-journey-continue`、8 阶段主/辅动作、负责角色、当前缺口、下一步。
- 回归：`node --test src/pages/workbench/DataManagementWorkbenchPage.source-contract.test.ts src/pages/modeling/dataDevelopmentWorkbench.source-contract.test.ts src/routes/sections/dashboard/portalGoldenLineMenu.source-contract.test.ts`，16/16 通过。
- 构建：`pnpm build` 通过，产物包含 `DataManagementWorkbenchPage-Dio5cpRR.js`；现有提示为 `caniuse-lite` 过期和部分 chunk 超过 1500 kB。
- 范围：`git diff --check` 通过；GitNexus `detect_changes(scope=all)` 为 `medium`，原因是当前工作区同时包含菜单、标准落标、SQL 建模等前序 sprint 未提交改动，本轮工作台改动未出现 HIGH/CRITICAL 影响链。
- Browser smoke：`http://localhost:3001/workbench?journey=e2e-data-product` 被前端守卫重定向到 `#/auth/login`，同时 dev proxy 解析 `platform.dts.local` 失败；Playwright 证据见本目录下 `sprint-61-workbench-login-redirect-snapshot.md` 与 `sprint-61-workbench-login-redirect.png`。

## 2026-07-09 下一阶段 Feature 补充

- F6：补共享 `JourneyContextBar` 与 query context 保持，解决子页面割裂问题。
- F7：补阶段状态与缺口计算模型，避免工作台继续依赖静态说明。
- F8：补客户验收包与证据聚合，让数据产品交付有可复制、可点击的验收材料。
- F9：补可登录浏览器验收与 Chrome 95/narrow viewport 基线，解除当前 smoke 只能停在登录页的问题。

## 2026-07-10 F6 实施证据

- RED：`node --test src/components/journey/JourneyContextBar.source-contract.test.ts`，6 个用例因共享组件、hook、上下文模型和页面接入缺失而失败。
- GREEN：同一命令 6/6 通过，覆盖 `JourneyContextBar`、`useDataProductJourneyContext`、`buildJourneyUrl`、8 个核心页面 stage 接入。
- 回归：`node --test src/components/journey/JourneyContextBar.source-contract.test.ts src/pages/workbench/DataManagementWorkbenchPage.source-contract.test.ts src/pages/modeling/dataDevelopmentWorkbench.source-contract.test.ts src/routes/sections/dashboard/portalGoldenLineMenu.source-contract.test.ts src/pages/modeling/LowCodeDevelopmentPage.source-contract.test.ts`，26/26 通过。
- 构建：`pnpm build` 通过；现有提示为 `caniuse-lite` 过期和部分 chunk 超过 1500 kB。
- 静态检查：`git diff --check` 通过。
- 待补：可登录 Playwright 3 页 smoke 与 Chrome 95/narrow viewport 截图，归入 F9 验证基线继续处理。

## 2026-07-10 F7 实施证据

- RED：`node --test src/components/journey/JourneyStageState.source-contract.test.ts`，4 个用例因缺少统一阶段状态模型、工作台/上下文条未接入而失败。
- GREEN：同一命令 4/4 通过，覆盖 8 阶段状态、缺口、blocker、nextAction 和路由。
- 回归：`node --test src/components/journey/JourneyContextBar.source-contract.test.ts src/components/journey/JourneyStageState.source-contract.test.ts src/pages/workbench/DataManagementWorkbenchPage.source-contract.test.ts src/pages/modeling/dataDevelopmentWorkbench.source-contract.test.ts src/routes/sections/dashboard/portalGoldenLineMenu.source-contract.test.ts src/pages/modeling/LowCodeDevelopmentPage.source-contract.test.ts`，30/30 通过。
- 构建：`pnpm build` 通过；现有提示仍为 `caniuse-lite` 过期和部分 chunk 超过 1500 kB。
- 静态检查：`git diff --check` 通过。
- 待补：T03 的可登录 Playwright 窄屏和 Chrome 95 视觉证据，继续归入 F9 验证基线。

## 2026-07-10 F8 实施证据

- RED：`node --test src/components/journey/DataProductAcceptancePackage.source-contract.test.ts`，4 个用例因缺少验收包模型、导出构造器和工作台卡片而失败。
- GREEN：同一命令 4/4 通过，覆盖 9 类证据分组、`ready/missing/blocked`、证据路由、缺失项、复制 Markdown 和下载 JSON 入口。
- 回归：`node --test src/components/journey/JourneyContextBar.source-contract.test.ts src/components/journey/JourneyStageState.source-contract.test.ts src/components/journey/DataProductAcceptancePackage.source-contract.test.ts src/pages/workbench/DataManagementWorkbenchPage.source-contract.test.ts src/pages/modeling/dataDevelopmentWorkbench.source-contract.test.ts src/routes/sections/dashboard/portalGoldenLineMenu.source-contract.test.ts src/pages/modeling/LowCodeDevelopmentPage.source-contract.test.ts`，34/34 通过。
- 构建：`pnpm build` 通过；现有提示仍为 `caniuse-lite` 过期和部分 chunk 超过 1500 kB。
- 静态检查：`git diff --check` 通过。
- GitNexus：`detect_changes(scope=all)` 为 `medium`，变更集中在 UI 页面符号，受影响执行流为 `SqlModelingPage -> NormalizeText` 与 `SqlModelingPage -> Get_key`，未出现 HIGH/CRITICAL。
- 待补：可登录 Playwright 点击复制/下载、验收包截图和 Chrome 95/narrow viewport 证据，归入 F9 回归基线。
