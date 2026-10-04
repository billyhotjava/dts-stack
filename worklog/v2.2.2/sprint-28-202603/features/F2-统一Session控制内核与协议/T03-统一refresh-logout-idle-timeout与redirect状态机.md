# T03: 统一refresh、logout、idle timeout与redirect状态机

**优先级**: P0  
**状态**: READY  
**依赖**: T01,T02

## 目标

把刷新、登出、会话过期、顶号、登录回跳的状态迁移路径收口成一套状态机，禁止多个计时器和多个 401 handler 并发消费同一个 refresh token。

## 技术设计

- 统一对外动作：
  - `refresh()`
  - `handle401()`
  - `logout(reason)`
  - `touchActivity()`
  - `scheduleRefresh()`
- 统一对内约束：
  - 一个 domain 只能有一个 proactive refresh scheduler
  - 所有 401 refresh 都必须走同一个 single-flight 锁
  - redirect builder 必须消费完整当前 route，而不是裸 `pathname`
  - logout reason 需要区分 `SESSION_EXPIRED`、`SESSION_CONFLICT`、`IDLE_TIMEOUT`、`REFRESH_FAILED`

## 影响范围

- `source/dts-platform-webapp/src/api/apiClient.ts`
- `source/dts-platform-webapp/src/analytics/api/analyticsApi.ts`
- `source/dts-platform-webapp/src/components/auth/session-manager.tsx`
- `source/dts-admin-webapp/src/api/apiClient.ts`
- `source/dts-admin-webapp/src/components/auth/session-manager.tsx`
- `source/dts-analytics-webapp/modern/src/api/platformSession.ts`

## 验证

- [ ] 并发 401 只能触发一次 refresh 请求
- [ ] refresh 成功后 refresh token 能回写到唯一 token store
- [ ] 任何超时/顶号场景都能携带正确 redirect intent

## 完成标准

- [ ] refresh/logout/redirect 生命周期有统一状态图
- [ ] 不再允许多套计时器并行刷新同一 refresh token
