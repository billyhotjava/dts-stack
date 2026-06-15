# T03-UI契约与构建验证

**状态**: DONE

## 验证命令

- `cd source/dts-platform-webapp && pnpm exec tsx src/pages/services/BusinessConsumptionPage.source-contract.test.ts`
- `cd source/dts-platform-webapp && pnpm exec tsx src/routes/sections/dashboard/opsRoutes.source-contract.test.ts`
- `cd source/dts-platform-webapp && pnpm build`
- `git diff --check`

## 结果

- 业务消费工作台 source-contract: 2/2 passed。
- 运维路由 source-contract: 2/2 passed。
- 前端生产构建通过。
- whitespace 检查通过。

## 备注

- 构建仍提示 Browserslist 数据过期和大 chunk warning，属于既有构建环境提示，不是本次 UI 改动引入的失败。
