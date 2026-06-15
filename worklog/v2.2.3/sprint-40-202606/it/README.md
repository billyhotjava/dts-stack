# Sprint-40 IT 记录

## 验证范围

- 数据服务中心菜单入口。
- `/services/consumption` 静态路由与动态菜单解析。
- 业务消费工作台页面客户可读文案。
- Sprint-39 运维路由回归。
- 前端生产构建。

## 验证命令

```bash
cd source/dts-platform-webapp
pnpm exec tsx src/pages/services/BusinessConsumptionPage.source-contract.test.ts
pnpm exec tsx src/routes/sections/dashboard/opsRoutes.source-contract.test.ts
pnpm build
cd /opt/prod/s10/v2.2.3
git diff --check
```

## 结果

- `BusinessConsumptionPage.source-contract.test.ts`: passed。
- `opsRoutes.source-contract.test.ts`: passed。
- `pnpm build`: passed。
- `git diff --check`: passed。
