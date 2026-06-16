# Sprint-42 IT 记录

**状态**: DONE

## 验证范围

- 主题聚合模型。
- 数据管理工作台页面。
- `/workbench/data-management` 菜单、路由、locale 和动态解析。
- 旧 `/services/consumption` 兼容入口。
- dts-admin 菜单 seed 默认项和可见性继承。

## 验证命令

```bash
cd source/dts-platform-webapp
pnpm exec vitest run src/pages/workbench/dataManagementThemeModel.test.ts
node --test src/pages/workbench/DataManagementWorkbenchPage.source-contract.test.ts
pnpm build

cd ../dts-admin
./mvnw -q -Dtest=PortalMenuSeedDefaultsContractTest test

cd /opt/prod/s10/v2.2.3
git diff --check
```

## 结果

- `pnpm exec vitest run src/pages/workbench/dataManagementThemeModel.test.ts`: passed，4/4。
- `node --test src/pages/workbench/DataManagementWorkbenchPage.source-contract.test.ts`: passed，3/3。
- `node --test src/pages/services/BusinessConsumptionPage.source-contract.test.ts`: passed，3/3。
- `./mvnw -q -Dtest=PortalMenuSeedDefaultsContractTest test`: passed。
- `./mvnw -q -Dtest=PortalMenuSeedDefaultsContractTest#legacyBusinessConsumptionSeedMenuIsHiddenAfterDataManagementWorkbenchMigration test`: passed。
- `pnpm build`: passed；仅保留既有 browserslist 数据陈旧和 chunk size warning。
- `git diff --check`: passed。
