# T04: 菜单路由 source-contract

**优先级**: P0  
**状态**: DONE  
**依赖**: T03

## 目标

用 source-contract 锁住菜单、角色、locale 和旧路由兼容映射，避免后续又回到 dts-metrics iframe。

## 技术设计

- 新增或扩展 route/menu contract 测试：
  - `portal-menu-seed.json` 不含 `/bi-apps/metrics`。
  - `role-menu-defaults.json` 不含 `/bi-apps/metrics`。
  - 新 `studioMetric*` locale key 存在。
  - `static-routes.tsx` 对旧 metrics 路由使用 redirect 组件。
  - `metricsServiceRoutes.test.ts` 更新为平台路由映射。

## 影响范围

- `source/dts-platform-webapp/src/routes/sections/dashboard/*.test.ts`
- `source/dts-platform-webapp/src/pages/modeling/metricWorkbench.source-contract.test.ts`

## 验证

- [x] `node --test src/routes/sections/dashboard/metricsServiceRoutes.test.ts`
- [x] 新增 contract 测试通过。

## 完成标准

- [x] 回归测试能阻止旧 iframe 路由重新暴露。
