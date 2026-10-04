# T02: 修复platform登录回跳、公开路由与drill边界

**优先级**: P0  
**状态**: READY  
**依赖**: T01

## 目标

在 `platform` 端统一 hash/browser route 的 return URL 规则，明确公开路由豁免边界，并确保 drill 页面不会在登录后退回默认 `/workbench`。

## 技术设计

- 将 return URL 生成统一交给 redirect builder，禁止直接拼 `window.location.pathname`。
- 明确 `public` 页、匿名分享页、drill 页、screen 详情页的守卫边界。
- 统一 login/auth-guard/session-timeout 三处跳转行为。

## 影响范围

- `source/dts-platform-webapp/src/routes/components/login-auth-guard.tsx`
- `source/dts-platform-webapp/src/pages/sys/login/login-form.tsx`
- `source/dts-platform-webapp/src/analytics/api/analyticsApi.ts`
- `source/dts-platform-webapp/src/components/auth/session-manager.tsx`

## 验证

- [ ] 第三层 drill 页登录后仍能回到原路径
- [ ] 公开分享页 401 不会被误导向登录
- [ ] session 过期后重新登录能回到触发页面

## 完成标准

- [ ] redirect 语义在 platform 内统一
- [ ] `/workbench` 只作为显式默认入口，不再成为错误回退页
