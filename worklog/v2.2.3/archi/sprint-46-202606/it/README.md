# Sprint-46 IT 验收计划

## 验收目标

证明工作台首页已经收敛为唯一入口，并且每个登录用户可以用 Chrome 95 兼容的简单控件勾选组件和调整顺序。

## 验收分层

| 层级 | 范围 | 方式 | 阻断条件 |
|------|------|------|----------|
| 路由 | `/workbench`、旧数据管理入口、旧消费入口 | source-contract / Playwright | 旧入口 404、白屏或继续显示独立首页 |
| 后端 | 偏好表、GET/PUT/reset API、权限过滤 | 单元测试 / IT | 可保存未知组件、可保存他人配置 |
| 前端 | 注册表、首页容器、自定义抽屉 | source-contract / Vitest | 勾选、上移/下移、保存、恢复默认任一不可用 |
| 兼容 | Chrome 95 目标构建 | `pnpm build` | 使用不兼容 API 或构建失败 |
| 证据 | 截图、命令输出、接口证据 | `it/evidence/` | 无法复现验收路径 |

## 必跑用例

| 用例 | 起点 | 终点 | 覆盖 |
|------|------|------|------|
| 唯一首页 | `/workbench` | 渲染个人工作台组件 | F1/F3 |
| 旧数据管理入口 | `/workbench/data-management` | 跳到 `/workbench?section=data-management` | F1/F5 |
| 旧消费入口 | `/services/consumption` | 跳到 `/workbench?section=consumption` | F1/F5 |
| 自定义打开 | `/workbench?customize=1` | 抽屉打开 | F4 |
| 勾选组件 | 自定义抽屉 | 首页组件显示/隐藏变化 | F4 |
| 调整顺序 | 自定义抽屉 | 上移/下移后首页顺序变化 | F4 |
| 保存刷新 | 保存配置后刷新 | 服务端恢复个人配置 | F2/F4 |
| 恢复默认 | 点击恢复默认 | 回到角色模板 | F2/F4 |
| 无权限过滤 | 低权限用户 | 不展示不可访问组件 | F2 |
| 空态 | 后端无摘要数据 | 显示空态或不可用原因 | F3/F5 |

## 证据目录

- `it/evidence/F1-route-menu/`
- `it/evidence/F2-backend-preferences/`
- `it/evidence/F3-workbench-container/`
- `it/evidence/F4-customize-drawer/`
- `it/evidence/F5-data-management-components/`
- `it/evidence/F6-chrome95-smoke/`

## 完成标准

- [x] 所有 P0 source-contract 通过。
- [x] 后端偏好 API 单测通过。
- [x] 前端构建通过。
- [x] Playwright smoke 覆盖 4 条核心路由。
- [x] Chrome 95 兼容约束无违规。
- [x] 旧入口兼容证据完整。
- [x] worklog feature/task 状态与证据一致。

## 本轮执行证据

| 类型 | 命令/动作 | 结果 |
|------|-----------|------|
| 前端契约 | `node --test --experimental-strip-types src/pages/workbench/WorkbenchPersonalization.source-contract.test.ts src/pages/workbench/workbenchPersonalizationModel.test.ts src/pages/workbench/DataManagementWorkbenchPage.source-contract.test.ts src/pages/services/BusinessConsumptionPage.source-contract.test.ts src/pages/foundation/DataSourcesPage.sprint45-actions.source-contract.test.ts` | PASS |
| 前端回归 | `pnpm exec vitest run src/pages/workbench/LeaderOverviewPage.test.tsx src/pages/workbench/LeaderOverviewPage.integration.test.tsx` | PASS, 15 tests |
| Chrome 95 构建 | `pnpm build` | PASS |
| 后端服务 | `./mvnw -q -DskipITs -Dtest=WorkbenchPreferencesServiceTest test` | PASS |
| 后端 REST | `./mvnw -q -DskipITs -Dtest=WorkbenchResourceIT test` | PASS |
| Chrome smoke | `vite preview` + Playwright 打开 `#/workbench`、`#/workbench?section=data-management&customize=1`、`#/workbench/data-management`、`#/services/consumption` | PASS |
| 交互 smoke | 自定义抽屉点击“下移” | PASS，组件顺序实时调整 |

## Chrome/Playwright 说明

- Playwright 使用本地 `vite preview` 产物验证，注入 dev fallback token 仅用于绕过本机 preview 的登录守卫。
- 本地未启动 dts-platform / dts-analytics 后端，浏览器控制台出现 `/api/*` 代理 500/ECONNREFUSED，属于本地 smoke 环境限制；页面 fallback、空态和抽屉交互正常。
- 截图: `worklog/v2.2.3/sprint-46-202606/it/evidence/F6-chrome95-smoke/sprint-46-workbench-personalization.png`
