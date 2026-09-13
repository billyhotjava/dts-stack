# IT-07 commands

执行时间：2026-07-30（Asia/Shanghai）

## RED

```bash
node --test --test-name-pattern='retired dbt surfaces' \
  source/dts-platform-webapp/src/pages/modeling/dataDevelopmentWorkbench.source-contract.test.ts
```

结果：FAIL，`DbtFileBrowserPage.tsx` 仍存在，符合预期。

## GREEN

```bash
node --test --test-name-pattern='backend menu fallback' \
  source/dts-platform-webapp/src/pages/modeling/dataDevelopmentWorkbench.source-contract.test.ts
node --test --test-name-pattern='retired dbt surfaces' \
  source/dts-platform-webapp/src/pages/modeling/dataDevelopmentWorkbench.source-contract.test.ts
node --test --test-name-pattern='development and operations pages preserve journey context' \
  source/dts-platform-webapp/src/pages/dataProductDeliveryJourney.source-contract.test.ts
node --test --test-name-pattern='Sprint-45 Studio converges' \
  source/dts-platform-webapp/src/pages/explore/etl/Sprint45IngestionStudio.source-contract.test.ts
```

结果：4/4 PASS。

```bash
pnpm build
```

执行目录：`source/dts-platform-webapp`；结果：PASS，10661 modules transformed。

```bash
./mvnw -Dtest=PortalMenuSeedDefaultsContractTest test
```

执行目录：`source/dts-admin`；结果：15 tests，0 failures，0 errors。

## 影响与引用检查

- GitNexus：`DbtFileBrowserPage`、`registerLegacyDbtModel`、`canEditLegacyAsset` 均为 LOW，0 direct caller。
- `PortalMenuService` 为 MEDIUM，12 个直接依赖、0 execution process；仅修改一条静态 component fallback。
- current HEAD 全仓引用检查：无运行时引用，只保留文件不存在断言。
