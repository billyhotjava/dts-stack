# 交付基线探针结果（Gate G0）

**探针日期**：2026-08-20  
**环境**：本地 v2.2.3 Docker 运行栈，`https://bi.yuzhicloud.com`  
**结论**：PASS_WITH_GAPS——服务、Schema、空态数据和测试/构建 harness 可用；运行库无已发布大屏、真实登录和 Chrome 95 缺失，填充态最终验收保留 GAP。

| # | 探针 | 结果 | 实际证据 | 阻断项 |
|---|---|---|---|---|
| P1 | 可运行实例 | PASS | dts-admin/platform/analytics healthy；webapp running；UI `/`=200 | - |
| P2 | 登录/认证 | GAP | 未登录 Platform/Analytics health 均 401；无 storageState/凭据 | F3/T01 |
| P3 | Schema | PASS | `analytics_screen`、`analytics_screen_version`、`catalog_domain`、`portal_menu` 存在；所需索引存在 | - |
| P4 | 代表数据 | PASS_WITH_GAPS | 2 个有效 SECRET 大屏、7 个主题域、0 currentPublished；可验空态，填充态由隔离 IT/Mock E2E 创建 | F3/T01 |
| P5 | API harness | PASS | `ScreenResourceIT` Spring Boot/MockMvc 可创建、发布并读取大屏 | - |
| P6 | UI harness | PASS_WITH_GAPS | Playwright + Chrome 150 可用；browser-use CLI/Chrome95 不存在 | F3/T01 |
| P7 | 构建/测试 | PASS | Analytics Maven、Admin Maven、webapp node/vitest/playwright 与 legacy build 命令存在 | - |
| P8 | 外部依赖 | PASS | 只依赖现有三个 DTS 服务与 PostgreSQL；无新增第三方 | - |

## 阻断项与处置

| 阻断 | 影响哪些 Feature | 处置 | 归属 Task |
|---|---|---|---|
| 运行库 0 个已发布大屏 | 只影响真实填充态页面验收 | 不发布客户草稿；用隔离 IT 和 Mock E2E 验证，客户发布后补最终 smoke | F3/T01 |
| 无真实登录/Chrome95 | 三角色与现场兼容验收 | Chrome150 + legacy build 先验收，状态保留 GAP | F3/T01 |

## 本 Sprint 验收路径

- 后端：`ScreenResourceIT` 聚焦 IT + dts-admin 菜单契约测试。
- 前端：Node/Vitest source/unit → legacy `pnpm build` → Sprint-96 Mock Playwright。
- 页面：1366×768 与窄视口，检查空/加载/错误/成功、console/network。
- 证据：只把真实执行结果写入 `it/README.md`；不把 Mock/Chrome150 伪称真实 Chrome95 验收。
