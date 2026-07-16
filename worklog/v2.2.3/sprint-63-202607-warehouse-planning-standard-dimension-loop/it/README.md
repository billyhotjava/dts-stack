# Sprint-63 集成验证计划

## 测试命令（可复制）

- `cd source/dts-platform-webapp && node --test src/components/journey/*.source-contract.test.ts src/pages/governance/*.source-contract.test.ts src/pages/modeling/*.source-contract.test.ts`
- `cd source/dts-platform-webapp && pnpm vitest run src/pages/governance/warehousePlanningContext.test.ts src/pages/modeling/dimensionCandidateGate.test.ts src/components/journey/journeyContext.test.ts src/components/journey/journeySnapshot.test.ts src/components/journey/journeyArtifactValidation.test.ts src/components/journey/journeyStageState.test.ts`

## Source Contract

- [x] 规划上下文创建、恢复、版本失效和 storage 降级。
- [x] 主题域页选中主题域后进入 DWD/维度建模规划。
- [x] 数据元页显示规划来源并输出带规划元数据的标准草稿。
- [x] 低代码/SQL 建模页显示规划、主题域、标准草稿和维度模式。
- [x] 缺少规划或标准时模型候选被阻断。
- [x] 旅程条继续/返回/清参与旅程快照透传四个规划参数；快照恢复但 session 草稿失效时正确降级。

## Build

- [x] `cd source/dts-platform-webapp && pnpm exec tsc --noEmit`
- [x] `cd source/dts-platform-webapp && pnpm build`
- [x] `git diff --check`
- [x] GitNexus `detect_changes`

## Browser Smoke

- [ ] `/governance/subjects?journey=e2e-data-product`
- [ ] `/governance/standards/elements?journey=e2e-data-product&planningId=<id>&domainId=<id>&warehouseLayer=DWD&modelingMode=dimension`
- [ ] `/studio/low-code-development?journey=e2e-data-product&planningId=<id>&domainId=<id>&warehouseLayer=DWD&modelingMode=dimension&standardDraftId=<id>`
- [ ] `/studio/sql-modeling?journey=e2e-data-product&planningId=<id>&domainId=<id>&warehouseLayer=DWD&modelingMode=dimension&standardDraftId=<id>`

浏览器登录/DNS 证据继续挂靠 Sprint-61 F9，不在本 Sprint 虚标通过。

## 证据记录

### 2026-07-11 F1/F2 RED/GREEN

- RED：主题域/数据元 4 个 source-contract 全部因缺少规划入口、来源条和草稿元数据失败。
- GREEN：同一组测试 4/4 通过；规划 helper、旅程参数与快照测试 31/31 通过。

### 2026-07-11 F3/F4 RED/GREEN

- RED：维度候选门禁模块不存在；低代码/SQL 页面未消费规划上下文；session 与不同 `planningId` URL 的裁决错误地采信 URL fallback。
- GREEN：门禁/规划组合矩阵 15/15 通过；旅程与规划行为测试 44/44 通过；source-contract 83/83 通过。
- 覆盖率（仅本 Sprint 两个新增纯函数）：statements 85.45%，branches 76.85%，functions 90.47%，lines 89.36%。分支覆盖低于 80%，主要为 defensive storage fallback 与非法层/模式分支；核心 ready/missing/blocked、版本失效、storage 异常、URL/session 不一致均已覆盖。

### 2026-07-11 Build / Compatibility

- `pnpm build`：GREEN，legacy browser 构建成功，10,565 modules transformed；仅有 Browserslist 数据过期和既有大 chunk 告警。
- Chrome 95 静态检查：未引入 `:has()`、新视口单位、`structuredClone`、`toSorted`、`Object.groupBy` 等不兼容能力。
- Preview：`http://127.0.0.1:4173/` 及三个业务路由返回 200；1366×768、390×844 均能渲染证书登录页，无空白或布局重叠。
- Browser blocker：未持有证书登录态，业务路由跳转 `#/auth/login`，本地认证资源出现 500；因此四个业务页 smoke 保持未勾选，真实登录/DNS 证据继续挂靠 Sprint-61 F9。

### 2026-07-11 GitNexus

- 页面符号 `SubjectAreasPage`、`ElementsPage`、`LowCodeDevelopmentPage`、`SqlModelingPage` 与 `openCreateModelFromStandardDraft` 上游影响均为 LOW；新增 helper 尚未进入索引，以行为测试和源码契约覆盖。

### 2026-07-11 F2/T03 Review Closure

- RED：规划上下文测试新增四类标准缺口契约后，因缺少 `resolveStandardDraftGate` 出现 2 个预期失败；页面源码契约同时要求客户可见 blocker 和修复动作。
- GREEN：无规划、无数据元、字段未落标、无治理权限均返回稳定原因和修复动作；数据元页可返回主题域规划、直接打开新增数据元或生成字段落标草稿。
- 覆盖率（`warehousePlanningContext.ts` + `dimensionCandidateGate.ts`）：statements 91.12%，branches 86.5%，functions 91.3%，lines 95.37%；规划 helper branches 84.9%，维度候选门禁 branches 95%。
- 完整回归：source-contract 85/85、行为测试 51/51、`pnpm exec tsc --noEmit`、`git diff --check` 和 Chrome 95 legacy `pnpm build` 通过；最终构建日志为 `✓ built in 2m 7s`。
- Preview mock smoke：通过本地开发令牌、规划 session 草稿和 API mock 渲染真实数据元路由；1366×768 的“字段未落标”和 390×844 的“无数据元”均显示修复按钮，控制台与 page error 为 0，窄屏操作组和搜索组可换行且无裁切。
- 截图：[`assets/screenshots/2026-07-11-elements-field-binding-1366x768.png`](../assets/screenshots/2026-07-11-elements-field-binding-1366x768.png)、[`assets/screenshots/2026-07-11-elements-no-data-390x844.png`](../assets/screenshots/2026-07-11-elements-no-data-390x844.png)。
- Browser Smoke 仍受证书登录/DNS 外部依赖阻塞，保持第 24-31 行未勾选，不计入 F2/T03 实现完成度。
