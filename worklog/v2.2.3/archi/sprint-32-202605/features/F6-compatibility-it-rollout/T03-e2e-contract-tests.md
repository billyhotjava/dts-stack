# T03: E2E 与 contract 测试

**优先级**: P0
**状态**: READY
**依赖**: F1-F5

## 目标

建立覆盖 React Flow 工作台和 platform/dbt 验证链路的测试证据。

## 技术设计

- source contract：App 薄入口、React Flow 模块存在、platform API 使用边界。
- frontend build/typecheck。
- Playwright：拖资产、连 Join、配置指标、生成 DWS/ADS、查看验证报告。
- backend focused tests：platform contract client、graph preflight、artifact generator、validation gateway。

## 影响范围

- `source/dts-metrics-webapp/test/**`
- `source/dts-metrics/src/test/**`
- `source/dts-platform/src/test/**`

## 验证

- [ ] `pnpm run typecheck && pnpm run build`
- [ ] focused Maven tests
- [ ] Playwright smoke with screenshots

## 完成标准

- [ ] IT 证据写入 `worklog/v2.2.3/sprint-32-202605/it/evidence/`。
