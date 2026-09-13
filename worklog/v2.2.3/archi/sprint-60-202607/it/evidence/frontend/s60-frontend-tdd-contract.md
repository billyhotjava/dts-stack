# Sprint-60 前端 TDD/契约证据（当前阶段）

日期：2026-07-14

## 已执行

| 范围 | 命令 | 结果 |
|---|---|---|
| vNext/PJM/兼容契约 | `node --test --experimental-strip-types src/api/modelingApi.source-contract.test.ts src/pages/modeling/modelingCompatibility.test.ts src/pages/modeling/modelingVnextContract.test.ts src/pages/modeling/pjmGoldenPathContract.test.ts` | 9 tests passed |
| 高级 dbt SQL 入口 RED → GREEN | `node --test --experimental-strip-types src/pages/modeling/SemanticModelsPage.source-contract.test.ts` | 先因入口缺失失败，补充台账跳转后 1 test passed |
| 台账/向导上下文 | `pnpm exec vitest run src/pages/modeling/businessModelingContext.test.ts src/pages/modeling/modelingLedger.test.ts` | 6 tests passed |
| 类型检查 | `pnpm exec tsc --noEmit` | passed |
| 生产构建 | `pnpm build` | passed（Vite 2m09s；仅有既有 Browserslist/大 chunk warning） |
| Playwright 用例发现 | `pnpm exec playwright test e2e/modeling-vnext-ledger.spec.ts --list` | 1 test listed |
| Playwright cookie-only 登录 RED → GREEN | `node --test --experimental-strip-types e2e/auth.setup.source-contract.test.ts` | 先因只接受 JWT 失败；兼容 `portal_session` cookie 后 1 test passed |
| Playwright 系统 Chrome 配置 RED → GREEN | `node --test --experimental-strip-types playwright.config.source-contract.test.ts` | 先因未支持可执行路径失败；支持 `PLAYWRIGHT_EXECUTABLE_PATH` 后 1 test passed |
| 真实域名 Playwright | `E2E_BASE_URL=https://bi.yuzhicloud.com E2E_USERNAME=opadmin E2E_PASSWORD=opadmin123 PLAYWRIGHT_EXECUTABLE_PATH=/usr/bin/google-chrome pnpm exec playwright test e2e/modeling-vnext-ledger.spec.ts` | 2 passed（普通用户对象台账 + 高级开发模型台账发布门禁/dbt 入口；真实 Traefik/重建容器） |
| 服务端发布门禁契约 | `node --test --experimental-strip-types src/api/modelingApi.source-contract.test.ts src/pages/modeling/SemanticModelsPage.source-contract.test.ts` | 8 tests passed；模型台账读取 `/release-gate` 结果并显示可发布/阻断 |
| 业务对象直接入口 RED → GREEN | `node --test --experimental-strip-types src/pages/modeling/SemanticObjectsPage.source-contract.test.ts` | 先因无 `processId` 时按钮被禁用失败；改为可点击并引导业务过程目录后 1 test passed |
| 业务对象创建入口部署验收 | `E2E_BASE_URL=https://bi.yuzhicloud.com ... pnpm exec playwright test e2e/modeling-vnext-ledger.spec.ts` | 3 passed；覆盖普通用户对象台账、高级 dbt 入口、无业务过程时新建按钮引导 |

## 说明

- 业务对象台账、模型台账和向导 query context 已使用可测试的视图模型与契约类型。
- 台账读取优先消费 `/modeling/vnext`，租户迁移期间无数据或接口不可用时回退旧 semantic 读取，详情维护入口保持不变。
- 真实验收通过的是 cookie-only 会话 + 系统 Chrome + `https://bi.yuzhicloud.com` 的部署页面；本机 3001/8084 未映射不再作为阻断条件。
- 该用例的业务数据仍通过 Playwright route fixture 注入，验证的是部署后的 UI 兼容链路；真实租户 vNext 台账数据初始化、普通用户提交/运行和旧 dbt 全链路仍需后续环境级验收。
- 直接使用裸 `node --test` 执行 Vitest 测试会因 TypeScript 无扩展解析失败；应使用仓库既有 `pnpm exec vitest run`。本轮重跑遇到主机 inotify watcher 上限（ENOSPC），不影响 `tsc`/生产构建和已记录的 Vitest 通过结果。
