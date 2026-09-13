# T03: 生产 profile 硬关闭 TEST_SESSION_ENABLED 与 handleDevFallback 旁路 + 启动断言

**优先级**: P0
**状态**: READY
**依赖**: —

## 目标

在生产构建/profile 下硬关闭 `TEST_SESSION_ENABLED` 会话失效旁路与 `handleDevFallback` 前端伪造账号绕过认证；二者编译期 strip 或运行期 fail-fast，存在即拒绝构建/启动，杜绝 DEV 旁路误发布到生产或内网测试环境的"无密码登录 / 异地顶替被静默禁用"。

## TDD 测试先行（RED）

- 新增 `bypass-guard.source-contract.test.ts`：断言生产构建下 `VITE_TEST_LONG_SESSION/VITE_TEST_SESSION` 与 `VITE_DEV_LOGIN_FALLBACK` 不生效；`shouldForceLogout` 不被 `TEST_SESSION_ENABLED` 抑制。
- 新增 `handleDevFallback.test.ts`（vitest + jsdom）：`import.meta.env.PROD=true` 时 `handleDevFallback` 返回 null（不签发 `dev-access-*` 假 token、不赋 `ROLE_OP_ADMIN`）；仅 DEV + host allowlist 命中才生效。
- 新增 `prod-bundle.no-bypass.test.ts`：生产产物 grep `dev-access-`、`TEST_SESSION` 旁路分支，命中即 FAIL。
- 运行确认 FAIL（当前 `apiClient.ts:43,503` 与 `userStore.ts:280` 旁路随生产发布）。

## 技术设计（GREEN）

- 修改 `dts-platform-webapp/src/api/apiClient.ts:43,503`：`TEST_SESSION_ENABLED` 计算加 `import.meta.env.PROD` 短路为 false，并以 `define` 编译期常量折叠 strip 死分支。
- 修改 `dts-platform-webapp/src/store/userStore.ts:280`（`handleDevFallback`，调用于 `userStore.ts:250`）：生产 profile 直接返回 null；DEV 增 host allowlist + 显著 banner。
- 新增前端启动断言模块：app bootstrap 检测 `PROD` 下任一旁路 flag 为真即抛错阻断初始化（fail-fast）。
- `dts-admin-webapp` 同步整改其 `userStore.ts` 与启动断言。

## 影响范围

- `source/dts-platform-webapp/src/api/apiClient.ts:43,503`（改既有 symbol，先 gitnexus_impact）
- `source/dts-platform-webapp/src/store/userStore.ts:250,280`（改既有 symbol，先 gitnexus_impact）
- `source/dts-admin-webapp/src/store/userStore.ts`
- `source/dts-platform-webapp/vite.config.ts`

## 验证

- [ ] 生产 profile 下 `handleDevFallback` 不签发任何假 token。
- [ ] 生产 profile 下 session 失效信号不被 `TEST_SESSION_ENABLED` 忽略。
- [ ] 生产构建产物无旁路死分支；旁路 flag 在 PROD 为真时启动断言阻断。

## 完成标准

- [ ] 生产环境不存在任何测试会话旁路与前端伪造账号通路。
