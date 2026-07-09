# Sprint-61 集成验证计划

## Source Contract

- [x] `DataManagementWorkbenchPage.source-contract.test.ts` 覆盖 8 阶段旅程、下一步、上下文参数。
- [x] `dataDevelopmentWorkbench.source-contract.test.ts` 覆盖标准草稿在低代码/SQL 建模传递。
- [ ] 新增或扩展 `e2eDataProductJourney.source-contract.test.ts` 覆盖集成、规划、标准、建模、指标、服务、证据路由。
- [x] `portalGoldenLineMenu.source-contract.test.ts` 确认菜单顺序与 DataWorks-like 旅程一致，但 UI 不依赖频繁菜单跳转。

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

## Build

- [x] `cd source/dts-platform-webapp && node --test ...`
- [x] `cd source/dts-platform-webapp && pnpm build`
- [x] `git diff --check`
- [x] GitNexus `detect_changes`

## Chrome 95 风险

- [ ] 不使用仅现代浏览器支持的 CSS container query 作为关键布局。
- [ ] 旅程轨道在 1366x768 与窄屏下不遮挡按钮。
- [ ] 标准草稿、门禁、证据卡片的长文本要换行，不撑破卡片。

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
