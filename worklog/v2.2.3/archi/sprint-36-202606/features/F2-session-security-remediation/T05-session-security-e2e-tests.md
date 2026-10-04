# T05: 集成/E2E 测试（token 不落盘、生产无 Authorization 日志、prod 拒旁路、idle 软锁）

**优先级**: P0
**状态**: READY
**依赖**: T01-T04

## 目标

为 F2 会话整改提供端到端守护测试，证明四项 P0 缺口已闭合且不回归：裸 token 不落 localStorage、生产构建无 Authorization 日志、`TEST_SESSION` 旁路在生产 profile 被拒、idle 无操作触发软锁。沉淀 IT 证据供机密级评审准入。

## TDD 测试先行（RED）

- E2E `session-token-storage.e2e.ts`（Playwright）：登录后断言 `localStorage` 无 access/refresh/admin token；跨 tab 同步不写 token 键。
- E2E `prod-no-secret-log.e2e.ts`：以生产构建运行，捕获 `console` 事件，断言全程无 `Authorization`/响应体打印。
- E2E `prod-reject-bypass.e2e.ts`：生产 profile 注入 `VITE_TEST_SESSION/VITE_DEV_LOGIN_FALLBACK=true`，断言旁路不生效且启动断言阻断/`handleDevFallback` 返回 null。
- E2E `idle-soft-lock.e2e.ts`：无操作达阈值触发锁屏，业务请求被拦截，重认证后恢复。
- 运行确认 FAIL（实现前）。

## 技术设计（GREEN）

- 复用 `e2e-runner` agent 与现有 webapp test 基建，新增上述 4 条 smoke 至 `dts-platform-webapp/test/`（admin 端关键路径同步覆盖）。
- 生产构建用例对 `vite build` 产物运行，确保编译期 strip 与运行期断言均被覆盖。
- IT 证据归档：截图/日志/断言结果写入 `worklog/v2.2.3/sprint-36-202606/it/evidence/session-security/`。
- 覆盖率门禁：会话/旁路/软锁路径分支覆盖 ≥80%。

## 影响范围

- `source/dts-platform-webapp/test/`（新增 E2E/集成用例）
- `source/dts-admin-webapp/test/`（双端关键路径）
- `worklog/v2.2.3/sprint-36-202606/it/evidence/session-security/`

## 验证

- [ ] token 不落 localStorage 用例通过。
- [ ] 生产构建无 Authorization 日志用例通过。
- [ ] 生产 profile 拒绝 `TEST_SESSION`/`handleDevFallback` 旁路用例通过。
- [ ] idle 无操作触发软锁用例通过。

## 完成标准

- [ ] 四项 P0 缺口有 E2E 守护并产出 IT 证据，可作为机密级评审准入依据。
