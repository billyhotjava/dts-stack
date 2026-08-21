# Sprint-98 集中验收证据

本目录只记录实际运行结果；2026-08-21 自动化证据如下。

| 验收项 | 状态 | 证据 |
|---|---|---|
| RED 测试 | PASS | 提交 `729ee413d`；前端缺失 helper/组件、后端 2 个治理绑定用例按预期失败 |
| GREEN 聚焦测试 | PASS | Node 8/8；Maven 11/11（Dashboard resource/publication） |
| webapp legacy build | PASS | `LEGACY_BROWSER_BUILD=1` 的 `pnpm build`，TypeScript 与 Vite 均通过 |
| dts-analytics 聚焦测试 | PASS | `DashboardResourceGovernedBindingTest` 4/4、semantic query 3/3、publication 4/4 |
| mock 页面旅程 | PASS | Sprint-98 1/1；Sprint-94 发布回归 1/1；无 console/page/HTTP failure |
| Chrome 95 静态/实机检查 | PASS_WITH_ENV_NOTE | 禁用 CSS/API 扫描无命中；Chrome 150 完成 1366×768 与 768×900；Chrome 95 executable 缺失 |
| 容器重建与健康 | NOT_RUN | 本轮尚未获得部署动作；范围与回滚见 `../assets/release-plan.md` |
| 真实保存→校验→发布→门户消费 | BLOCKED | 当前真实登录态过期 |

## 页面证据

- `/tmp/dts-sprint98-playwright-results/.../dashboard-composer-1366x768.png`
- `/tmp/dts-sprint98-playwright-results/.../dashboard-composer-768x900.png`
- Sprint-98 覆盖分析拖入、尺寸修改、保存刷新、历史组件替换、真实目录选值、保存后校验和发布注册。
- Sprint-94 覆盖治理分析发布、看板发布和注册失败重试；其 mock 已补正式自动预览响应契约。

## 环境裁决

代码与隔离自动化完成不等于运行交付。容器重建、授权账号下的真实发布/门户消费和 Chrome 95 实机证据齐备前，Sprint 保持 `IN_PROGRESS / PASS_WITH_GAPS`。
