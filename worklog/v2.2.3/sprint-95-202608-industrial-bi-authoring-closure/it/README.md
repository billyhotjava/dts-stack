# Sprint-95 集中验证与证据

本文件只登记真实执行结果，不预建截图或伪造 PASS。

## IT 槽位

| IT | 旅程 | 当前状态 | 证据 |
|---|---|---|---|
| IT-01 | Analysis spec/reducer/unit | PASS | Node focused tests 18/18 |
| IT-02 | Analysis source-contract + backend export contract | PASS | Analytics focused tests 10/10；CSV/XLSX/403/权限映射 |
| IT-03 | Webapp legacy build + Analytics tests/package | PASS | 隔离 commit Docker build 内完整 `pnpm build` PASS；Analytics package/tests PASS |
| IT-04 | Mock API 完整链：拖拽→图→样式→计算→联动→发布→导出 | PASS | Chrome 150：静态包 1/1 6.6s；部署包 1/1 9.3s；console/page/network error 0 |
| IT-05 | 真实账号/真实 QueryDataset 页面旅程 | BLOCKED_INPUT | 当前无 E2E 凭据 |
| IT-06 | Chrome 95 1366×768 + 窄屏 | BLOCKED_INPUT | 当前无 Chrome 95 executable |
| IT-07 | 容器、API、下载头、console/network、DB revision 对账 | PASS_WITH_GAPS | 两容器重建、health/HTTPS/401/log PASS；真实账号导出与 DB revision 对账待凭据 |

## 当前基线

- 前端既有 Analysis/Dashboard 契约：9/9 PASS。
- Analytics 既有 AnalysisApplicationService/AnalysisQueryGateway：5/5 PASS。
- 当前三目标容器运行，UI 200；未登录 API 401。
- 运行数据：25 published datasets、2 governed analyses、2 dashboards。

## 2026-08-20 实施证据

- 前端聚焦命令：`node --experimental-strip-types --test ...`，18 tests PASS。
- 后端聚焦命令：`mvn -DskipITs -Dtest=AnalysisResourceExportTest,AnalysisExportSourceContractTest,AnalysisExportPermissionContractTest,AnalysisApplicationServiceTest,AnalysisQueryGatewayTest test`，10 tests PASS。
- 共享脏工作区的 `pnpm build` 曾被非 Sprint-95 owner `modelWorkbenchService.ts:487-508` 的并行类型错误阻断；从 commit `ed836636b` 创建隔离 worktree 后，Docker 内完整 `pnpm build`（TypeScript + legacy bundle，10593 modules）通过，证明 Sprint-95 交付物本身可构建且没有吸收并行建模代码。
- Playwright 静态生产包旅程覆盖：HTML5 拖拽、自动预览、柱状图、显示数值、派生指标、草稿保存、受众阻断、发布只读、CSV 下载、联动配置保存/重载、来源不自筛、目标卡注入筛选、768×900 无页面级溢出。
- 截图：`analysis-authoring-published-1366x768.png`、`dashboard-targeted-linkage-768x900.png`，保存在 `/tmp/dts-sprint95-playwright-results/.../`。
- 运行部署与镜像/健康证据见 `it/deployment-20260820.md`。
