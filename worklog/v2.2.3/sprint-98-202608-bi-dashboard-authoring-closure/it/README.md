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

## 草稿命名修复补充（2026-08-21）

| 验收项 | 状态 | 证据 |
|---|---|---|
| 空名称静默禁用复现 | PASS | RED 提交 `b027812fd`；新用例按预期失败，3 条既有用例通过 |
| 名称必填与保存反馈 | PASS | GREEN 提交 `44b03af05`；必填名称框、空名称提示与聚焦、保存动作恢复 |
| Chrome 页面旅程 | PASS_WITH_ENV_NOTE | Chrome 150：Sprint-98 2/2；1366×768 与 768×900 均通过；Chrome 95 executable 缺失 |
| 前端部署 | PASS | 镜像 `sha256:b91035e148c...`；Nginx 配置通过，本机 Traefik `/bi/dashboards/new` 返回 200 |

- `/tmp/dts-sprint98-playwright-results/.../dashboard-name-required-1366x768.png`
- `/tmp/dts-sprint98-playwright-results/.../dashboard-name-required-768x900.png`
- 新建看板验证空名称不发起请求；填写名称后只执行一次创建、一次保存，并跳转到持久化草稿编辑页。

## 环境裁决

代码与隔离自动化完成不等于运行交付。容器重建、授权账号下的真实发布/门户消费和 Chrome 95 实机证据齐备前，Sprint 保持 `IN_PROGRESS / PASS_WITH_GAPS`。
