# F0：可验收基线

**优先级**：P0
**状态**：BLOCKED

## 目标

恢复一个有效账号和独立浏览器会话，使全局帮助可以在真实受保护壳层完成桌面、窄屏和 Chrome95 验收。

## 契约与 DoR

- 输入：有效 `E2E_USERNAME/E2E_PASSWORD`、显式 `E2E_BASE_URL`。
- 输出：登录成功的 storageState 和真实页面截图。
- 不修改生产用户、密码或 Keycloak 配置。
- 当前因账号 401 未过 DoR；源代码实施仅依据用户明确例外继续，Sprint 不得提前 DONE。

## Task

| ID | Task | 状态 |
|---|---|---|
| T01 | 恢复登录与独立 UI 验收链 | BLOCKED |
