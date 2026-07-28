# 交付基线探针结果（Gate G0）

**探针日期**：2026-07-28
**环境**：v2.2.3 当前运行环境 — `https://bi.yuzhicloud.com`
**范围**：纯前端全局帮助功能
**结论**：BLOCKED（阻断真实 UI 验收，不阻断静态内容和源代码编译）

| # | 探针 | 结果 | 证据 | 阻断项 |
|---|---|---|---|---|
| P1 | 可运行实例 | PASS_WITH_GAP | Web 容器 running；平台、管理、Keycloak healthy；首页 HTTP 200 | Web 容器无 healthcheck，非本 Feature 阻断 |
| P2 | 登录路径 | FAIL | 现有 `e2e/auth.setup.ts` 默认凭据请求登录返回 HTTP 401 | F0/T01 |
| P3 | 迁移状态 | N/A | 不修改数据库 | - |
| P4 | Seed/真实数据 | N/A | 帮助内容不依赖业务数据 | - |
| P5 | API 验收 | N/A | 不新增/调用业务 API | - |
| P6 | UI 验收工具 | FAIL | Playwright MCP 共享浏览器被占用；旧 storageState 不能证明当前有效 | F0/T01 |
| P7 | 构建/测试命令 | PASS | 定向契约 39/39；`pnpm build` 成功 | - |
| P8 | 外部依赖 | PASS | 无 DB/API/第三方运行依赖 | - |

## 阻断与处置

| 阻断 | 影响 | 处置 | Task |
|---|---|---|---|
| 默认 E2E 账号 401 | 无法进入受保护壳层验收 | 由环境负责人提供/刷新有效测试凭据 | F0/T01 |
| 共享浏览器被占用 | 无法形成真实截图 | 验收时使用独立浏览器会话 | F0/T01 |
| E2E 默认 baseURL 指向 localhost | 可能误连开发端口 | 最终显式设置 `E2E_BASE_URL=https://bi.yuzhicloud.com` | F0/T01 |

## 本 Sprint 验收路径

- 构建：`cd source/dts-platform-webapp && pnpm build`
- 定向契约：一次执行本 Sprint 涉及的 `node --test --experimental-strip-types`
- UI：有效账号登录后验证 Header 入口、Sheet、完整帮助页、Ctrl/Cmd+K、桌面和窄屏。
- 证据：`it/evidence/`
