# Sprint-48 IT 验收记录

## 验收范围

本 sprint 起点是治理/约束/矩阵基线，随后开始按矩阵执行首批页面整改。验收重点是：

- 新增 DTS 专属 skills 是否有效
- worklog sprint/feature/task 是否形成
- 页面矩阵是否能追溯到真实菜单和路由
- 后续 TDD/Chrome95 验收规则是否清晰
- 首批整改是否用 RED/GREEN 测试、构建和浏览器证据闭环

## 已执行验证

| 验证项 | 命令/方式 | 结果 |
|--------|-----------|------|
| skill 结构校验 | `python3 /home/billy/.codex/skills/.system/skill-creator/scripts/quick_validate.py /home/billy/.codex/skills/dts-page-capability-audit` | PASS |
| skill 结构校验 | `python3 /home/billy/.codex/skills/.system/skill-creator/scripts/quick_validate.py /home/billy/.codex/skills/dts-frontend-feature-matrix` | PASS |
| skill 结构校验 | `python3 /home/billy/.codex/skills/.system/skill-creator/scripts/quick_validate.py /home/billy/.codex/skills/dts-menu-route-convergence` | PASS |
| skill 结构校验 | `python3 /home/billy/.codex/skills/.system/skill-creator/scripts/quick_validate.py /home/billy/.codex/skills/dts-customer-language-polish` | PASS |
| skill 结构校验 | `python3 /home/billy/.codex/skills/.system/skill-creator/scripts/quick_validate.py /home/billy/.codex/skills/dts-chrome95-regression` | PASS |
| 菜单事实源抽取 | `jq -r ... portal-menu-seed.json` | PASS，已抽取 portal 主菜单和叶子路径 |
| 路由事实源检查 | 读取 `static-routes.tsx`、`dynamic-resolver.tsx` | PASS，确认工作台兼容跳转和核心路由来源 |

## 首批整改切口（2026-06-18）

| 验证项 | 命令/方式 | 结果 |
|--------|-----------|------|
| RED：工作台主题模型测试夹具不得残留内置 demo 场景词 | `pnpm exec vitest run src/pages/workbench/dataManagementThemeModel.test.ts` | FAIL，命中 `customer-demo`，证明测试能抓住残留 |
| GREEN：清理为现场定义链路语义 | `pnpm exec vitest run src/pages/workbench/dataManagementThemeModel.test.ts` | PASS，5/5 |
| 工作台/旧消费入口 source-contract | `node --test --experimental-strip-types src/pages/workbench/DataManagementWorkbenchPage.source-contract.test.ts src/pages/services/BusinessConsumptionPage.source-contract.test.ts src/pages/workbench/WorkbenchPersonalization.source-contract.test.ts` | PASS，13/13 |
| 残留扫描 | `rg -n "customer-demo\|客户服务\|经营分析\|质量管理\|项目交付" source/dts-platform-webapp/src/pages/workbench source/dts-platform-webapp/src/pages/services -g '*.ts' -g '*.tsx' -g '!*.source-contract.test.ts'` | PASS，无运行代码和普通单测残留 |
| 格式检查 | `pnpm exec biome check src/pages/workbench/dataManagementThemeModel.test.ts` | PASS |

## 第二批整改切口（2026-06-18）

| 验证项 | 命令/方式 | 结果 |
|--------|-----------|------|
| GitNexus impact | `npx gitnexus impact 'Const:source/dts-platform-webapp/src/global-config.ts:GLOBAL_CONFIG' --repo s10-stack --direction upstream` | LOW |
| GitNexus impact | `npx gitnexus impact 'Function:source/dts-platform-webapp/src/pages/workbench/index.tsx:WorkbenchPage' --repo s10-stack --direction upstream` | LOW |
| RED：工作台偏好 API 必须默认 opt-in | `node --experimental-strip-types src/pages/workbench/WorkbenchPersonalization.source-contract.test.ts` | FAIL，缺少 `enableWorkbenchPreferenceApi` |
| RED：生产 entrypoint 必须注入同一个运行时开关 | `node --experimental-strip-types src/pages/workbench/WorkbenchPersonalization.source-contract.test.ts` | FAIL，缺少 `WEBAPP_ENABLE_WORKBENCH_PREFERENCE_API` |
| GREEN：工作台个性化 source-contract | `node --experimental-strip-types src/pages/workbench/WorkbenchPersonalization.source-contract.test.ts` | PASS，7/7 |
| 工作台/旧消费入口 source-contract | `node --test --experimental-strip-types src/pages/workbench/DataManagementWorkbenchPage.source-contract.test.ts src/pages/services/BusinessConsumptionPage.source-contract.test.ts src/pages/workbench/WorkbenchPersonalization.source-contract.test.ts src/pages/workbench/workbenchLocalPreferences.test.ts` | PASS，4/4 文件 |
| 本地偏好存储回归 | `node --test --experimental-strip-types src/pages/workbench/workbenchLocalPreferences.test.ts` | PASS，1/1 |
| 数据管理主题模型回归 | `pnpm exec vitest run src/pages/workbench/dataManagementThemeModel.test.ts` | PASS，5/5 |
| 局部 Biome | `pnpm exec biome check src/pages/workbench/index.tsx src/pages/workbench/WorkbenchPersonalization.source-contract.test.ts src/pages/workbench/dataManagementThemeModel.test.ts` | PASS |
| 前端构建 | `pnpm build` | PASS；仅 Browserslist 数据陈旧和既有 chunk size warning |
| 浏览器网络检查 | Playwright 打开 `http://127.0.0.1:18012/workbench`，过滤 `workbench/preferences` | PASS，未出现 `/api/workbench/preferences` 请求 |
| 浏览器截图 | `worklog/v2.2.3/sprint-48-202606/it/evidence/sprint-48-workbench-login-redirect.png` | PASS，1366x768 登录重定向页 |
| 浏览器 console/snapshot | `worklog/v2.2.3/sprint-48-202606/it/evidence/workbench-login-console-2026-06-18.log`、`worklog/v2.2.3/sprint-48-202606/it/evidence/workbench-login-page-2026-06-18.yml` | PASS，目标接口无请求；本地未接后端导致 `/api/session/status` 500 |
| Diff 空白检查 | `git diff --check` | PASS |

## Review 收口复核（2026-06-18）

| 验证项 | 命令/方式 | 结果 |
|--------|-----------|------|
| Sprint queue 口径同步 | 更新 `worklog/v2.2.3/sprint-queue.md` Sprint-48 类型与进度说明 | PASS，queue 与 README/IT 一致 |
| 配置文件 Biome 全检 | `pnpm exec biome check src/global-config.ts vite.config.ts` | PASS |
| 工作台个性化 source-contract | `node --experimental-strip-types src/pages/workbench/WorkbenchPersonalization.source-contract.test.ts` | PASS，7/7 |
| 工作台/旧消费入口 source-contract | `node --test --experimental-strip-types src/pages/workbench/DataManagementWorkbenchPage.source-contract.test.ts src/pages/services/BusinessConsumptionPage.source-contract.test.ts src/pages/workbench/WorkbenchPersonalization.source-contract.test.ts src/pages/workbench/workbenchLocalPreferences.test.ts` | PASS，4/4 文件 |
| 数据管理主题模型回归 | `pnpm exec vitest run src/pages/workbench/dataManagementThemeModel.test.ts` | PASS，5/5 |
| 前端构建 | `pnpm build` | PASS；仅 Browserslist 数据陈旧和既有 chunk size warning |
| GitNexus detect changes | `npx gitnexus detect-changes --repo s10-stack` | PASS，7 files / 54 symbols / affected processes 0 / risk low |
| Diff 空白检查 | `git diff --check` | PASS |

## 当前实现结论

- `WEBAPP_ENABLE_WORKBENCH_PREFERENCE_API` 默认未设置时，工作台只读写浏览器本地偏好，不请求 `/api/workbench/preferences`。
- 后端升级并需要服务端保存时，可在容器运行时设置 `WEBAPP_ENABLE_WORKBENCH_PREFERENCE_API=true` 打开接口调用。
- 本地浏览器 smoke 中 `/api/session/status` 因未连接后端返回 500，属于 dev server 未接平台后端的环境问题；目标接口 `/api/workbench/preferences` 未出现。

## 未执行项

| 项目 | 原因 |
|------|------|
| 后端单元测试 | 本 sprint 未修改后端代码 |

## 后续实现型 task 必须补充

- RED/GREEN 测试证据
- `pnpm build` 输出
- 浏览器截图路径
- console/network 检查结论
- GitNexus impact 和 detect changes 记录
