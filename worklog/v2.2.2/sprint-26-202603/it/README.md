# Sprint-26 IT

## 目标

记录 Platform统一Admin Gateway 重构的集成验证命令、结果和残余风险。

## 实际验证

- `cd source/dts-platform && ./mvnw -q -Dtest=AdminGatewayTransportTest,AdminDirectoryGatewayTest,DirectoryResourceTest,AdminAuthGatewayTest,KeycloakAuthResourceTest,AdminGatewayIdentityResourceTest,SecurityAuditLogProxyResourceTest,AuditTrailServiceTest test`
- `cd source/dts-platform && ./mvnw -q -DskipTests compile`
- `cd source/dts-platform-webapp && node --import ./node_modules/.pnpm/tsx@4.19.4/node_modules/tsx/dist/loader.mjs --test src/api/adminGateway.source-contract.test.ts`
- `cd source/dts-platform-webapp && pnpm build`

## 结果

- [x] `dts-platform` 定向 gateway 测试通过
- [x] `dts-platform` compile 通过
- [x] `platform-webapp` admin 直连约束测试通过
- [ ] `platform-webapp` 全量 build 通过

## 风险记录

- `platform-webapp` 当前存在既有构建阻塞:
- `src/components/chart/chart.tsx`: 缺少 `echarts-for-react` 与 `echarts` 类型
- `src/components/chart/useChart.ts`: 缺少 `echarts` 类型
- `src/pages/ops/OpsOverviewPage.tsx`: 缺少 `echarts` 类型
- `src/pages/modeling/SqlModelingPage.tsx:934`: 既有 TS2872 “expression is always truthy”
- 本次 admin gateway 改造的定向测试和后端编译均通过，未发现新增的直连回流
