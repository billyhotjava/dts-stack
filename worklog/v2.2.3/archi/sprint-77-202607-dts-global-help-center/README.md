# Sprint-77：DTS 全局帮助中心与页面说明收敛

**时间**：2026-07
**状态**：IN_PROGRESS（源代码实施已获授权；G0 真实 UI 验收仍 BLOCKED）
**类型**：UI Productization / Global Help / Content Convergence
**目标**：让首次使用 DTS 的用户从任意登录后页面打开全局帮助，获得与当前页面匹配的任务说明，并可在完整帮助中心检索整个 DTS；建模页面只保留完成当前任务所需的状态、阻塞和动作。

## 背景与价值

当前多个页面同时承担业务操作和产品说明，首次用户难以区分“现在要做什么”与“系统为什么这样设计”。已有 Word/PDF/Markdown 操作手册，但尚未进入产品壳层。该 Sprint 把稳定教程集中到一个全局帮助能力，并保留业务页面上的动态真值。

用户明确要求本轮先集中编码、最后做一次合并验证，避免反复构建和扫描。G0 发现登录与浏览器会话不可用；该例外只允许源代码实施继续，不改变最终 DoD。

## 架构决策记录（ADR）

| ID | 决策点 | 选择 | 理由 | 影响 |
|---|---|---|---|---|
| ADR-77-01 | 全局入口 | Dashboard Header 中使用问号帮助按钮 | 全局可达，不与大屏右下角 FAB 冲突，语义区别于设置 | `Header` |
| ADR-77-02 | 快速帮助 | 右侧 `Sheet` 显示当前路由主题 | 不离开任务即可查看前置条件、步骤和阻塞 | 新 `HelpCenter` |
| ADR-77-03 | 完整帮助 | 静态 `/settings/help?topic=:id` | 复用 always-allowed 前缀，不新增菜单/角色绑定 | 静态路由 |
| ADR-77-04 | 内容真值 | 本地类型化主题注册表，路由只读匹配 | 无 API/数据库依赖，离线可用，Chrome95 风险低 | `helpTopics.ts` |
| ADR-77-05 | 页面减负 | 通用教程迁移；权限、错误、阻塞、证据、恢复动作保留 | 防止“去文案化”删除业务必需反馈 | 建模页面 |
| ADR-77-06 | 测试节奏 | 编码完成后统一运行契约测试、构建和差异检查 | 遵循用户的 token/时间约束 | IT |

## 端到端契约链（Vertical Slice）

| 层 | 契约/落点 | 签名要点 |
|---|---|---|
| UI 入口 | Dashboard Header `HelpCenter` | 点击带可访问名称的帮助按钮打开右侧 Sheet |
| 当前页解析 | `resolveHelpTopic(pathname, search)` | 路由匹配失败时回退 `getting-started` |
| 完整中心 | `GET /settings/help?topic=:id&q=:query` | 静态前端路由；支持主题选择、搜索与深链 |
| 命令搜索 | Ctrl/Cmd+K 帮助分组 | 选择主题跳转完整中心 |
| 内容 | `HELP_TOPICS: HelpTopic[]` | `id/section/title/summary/routePatterns/keywords/prerequisites/steps/blockers/relatedTopicIds` |
| API/Service | N/A | 不新增后端调用 |
| 数据/迁移 | N/A | 不建表、不改数据 |

## 现状勘察账本（Context Ledger）

| # | 事实 | 证据 |
|---|---|---|
| 1 | Dashboard 三种布局共享同一 Header | `source/dts-platform-webapp/src/layouts/dashboard/index.tsx:19` |
| 2 | Header 右侧已有 SearchBar 与 AccountDropdown | `source/dts-platform-webapp/src/layouts/dashboard/header.tsx:36` |
| 3 | 现有 Sheet 已提供焦点、标题、描述和关闭能力 | `source/dts-platform-webapp/src/ui/sheet.tsx:47` |
| 4 | Ctrl/Cmd+K 已由 SearchBar 监听 | `source/dts-platform-webapp/src/layouts/components/search-bar.tsx:78` |
| 5 | `/settings` 属于 always-allowed 前缀 | `source/dts-platform-webapp/src/layouts/dashboard/main.tsx:116` |
| 6 | 静态 Dashboard 路由可直接承载帮助页 | `source/dts-platform-webapp/src/routes/sections/dashboard/static-routes.tsx:88` |
| 7 | 大屏预览已有右下角固定控制按钮 | `source/dts-platform-webapp/src/analytics/pages/screens/ScreenPreviewPage.tsx:631` |
| 8 | 建设工作台副标题和空状态含通用说明 | `source/dts-platform-webapp/src/pages/modeling/ModelingWorkbenchPage.tsx:366` |
| 9 | 模型中心副标题和旅程加入条含通用说明 | `source/dts-platform-webapp/src/pages/modeling/ModelCenterPage.tsx:235` |
| 10 | 指标工作台当前新增全生命周期说明 | `source/dts-platform-webapp/src/pages/modeling/MetricWorkbenchPage.tsx:240` |
| 11 | SQL/dbt 页面内置完整“建模与上线操作流程” | `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx:4274` |
| 12 | Word/PDF/Markdown 手册已存在但未进入 Web 镜像 | `docs/user-guide/README.md:1`、`builds/dts-platform-webapp/Dockerfile:68` |
| 13 | 当前默认 E2E 登录返回 401，共享浏览器不可用 | `it/baseline.md` |

## Gate Registry

| Gate | 项目 | 状态 | 证据 | 未过则关联 Task |
|---|---|---|---|---|
| G0 | 交付基线 | BLOCKED | `it/baseline.md` | F0/T01 |
| G0 | 领域与数据画像 | PASS | `assets/domain-profile.md` | - |
| G0 | DTS 不变量自检 | PASS | ADR-77-01/03/04/05 | - |
| G1 | 契约链贯通 | PASS | 本文“端到端契约链” | - |
| G1 | 非功能预算 | PASS | `assets/nfr-budget.md` | - |
| G2 | 变更范围与代码评审 | PASS | `it/evidence/it-06-quality/README.md`；GitNexus overall LOW | - |
| G3 | 发布安全 | PASS | 完整前端构建、差异检查与回滚边界见 IT-06 | - |
| G4 | 可运维性 | N/A | 静态前端能力，无新服务/告警/容量面 | - |
| G4 | DoD 验收 | BLOCKED | 源代码门禁已过；真实 UI/Chrome95 等待 F0 | F0/T01 |

## Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 |
|---|---|---:|---|---|
| F0 | 可验收基线 | 1 | P0 | BLOCKED |
| F1 | 全局帮助入口与主题中心 | 3 | P0 | IN_PROGRESS（2 DONE，1 等待 UI） |
| F2 | 建模页面说明收敛 | 2 | P0 | IN_PROGRESS（1 DONE，1 等待 UI） |

**依赖顺序**：F0 缺口登记 → F1/F2 源代码实现 → F2/T02 合并验证；真实 UI DoD 最终等待 F0/T01。

## 追溯矩阵

| 需求点 | Feature/Task | 验收证据 |
|---|---|---|
| 任意登录后页面可打开帮助 | F1/T02 | `it/evidence/f1-help-launcher/` |
| 帮助覆盖整个 DTS | F1/T01 | 主题契约测试 |
| 支持完整帮助页与深链 | F1/T02 | 路由契约 + 浏览器截图 |
| Ctrl/Cmd+K 可搜索帮助 | F1/T03 | 搜索契约 + UI 走查 |
| 建模页面删除通用说明 | F2/T01 | source-contract |
| 运行态反馈仍保留 | F2/T01 | source-contract + UI 走查 |

## 完成标准

- [x] Header、上下文 Sheet、完整帮助页和命令搜索的源代码链已贯通。
- [x] 15 个主题覆盖 DTS 总览、工作台、数据集成、建模、治理、资产、指标分析、数据服务、运维和管理端。
- [x] 建设工作台、模型中心、指标工作台、SQL/dbt 与旅程加入条不再展示通用教程。
- [x] 权限、错误、阻塞、证据和恢复动作由源契约确认保留。
- [x] 一次合并契约测试、`pnpm build`、`git diff --check` 和 GitNexus 变更检测通过。
- [ ] 有效登录后补桌面、窄屏及 Chrome95 证据，方可标记 DONE。

## 非目标

- 不新增业务菜单、后端 API、数据库、CMS 或 Markdown 运行时。
- 不把管理端和分析端复制成独立帮助系统。
- 不在本 Sprint 重写全部 69 页手册或发布匿名 PDF 下载。
- 不为解决验收账号问题修改生产身份、密码或 Keycloak 配置。
