# T01: 收口platform三套session控制器

**优先级**: P0  
**状态**: READY  
**依赖**: F2/T03

## 目标

让 `dts-platform-webapp` 的 `apiClient`、`analyticsApi`、`SessionManager` 不再各自维护 refresh、logout 和 redirect 逻辑，而是统一委派给一个 coordinator。

## 技术设计

- 清点当前 platform 内的 session 入口：
  - `src/api/apiClient.ts`
  - `src/analytics/api/analyticsApi.ts`
  - `src/components/auth/session-manager.tsx`
- 保留一个主动 refresh 调度器，其余调用方只做委派。
- 把 token 回写、logout broadcast、last activity、return URL 生成集中到共享 core。

## 影响范围

- `source/dts-platform-webapp/src/api/apiClient.ts`
- `source/dts-platform-webapp/src/analytics/api/analyticsApi.ts`
- `source/dts-platform-webapp/src/components/auth/session-manager.tsx`
- `source/dts-platform-webapp/src/auth/**`

## 验证

- [ ] `platform` 运行时只存在一套 refresh 调度
- [ ] 401、idle timeout、顶号不再分别走三套代码路径

## 完成标准

- [ ] platform session 控制入口收口完成
- [ ] 相关历史辅助函数只保留兼容壳或被删除
