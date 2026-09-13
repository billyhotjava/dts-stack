# T01: source-contract 与 TypeScript 验证

**优先级**: P0  
**状态**: DONE  
**依赖**: F1 F3

## 目标

验证菜单路由迁移和平台指标页面接管不破坏前端类型和已有指标页面契约。

## 技术设计

- 执行：
  - `node --test src/routes/sections/dashboard/metricsServiceRoutes.test.ts`
  - `node --test src/pages/modeling/metricWorkbench.source-contract.test.ts`
  - `pnpm exec vitest run src/pages/modeling/semanticObjectMappings.helpers.test.ts`
  - `node --test src/pages/workbench/dataManagementThemeModel.test.ts`
  - `pnpm exec tsc --noEmit`

## 影响范围

- `source/dts-platform-webapp`

## 验证

- [x] 所有 targeted source-contract 通过。
- [x] 表映射 join graph helper 行为测试通过。
- [x] TypeScript 0 errors。

## 完成标准

- [x] 可用测试覆盖退役后的路由和页面接管。
