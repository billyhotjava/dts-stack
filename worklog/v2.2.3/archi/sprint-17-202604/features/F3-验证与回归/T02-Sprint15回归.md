# T02: 回归 Sprint-15 测试

**优先级**: P0
**状态**: READY
**依赖**: F1

## 目标

确保 Sprint-17 改动没破坏 Sprint-15 既有的 leader-overview 功能。

## 步骤

### 后端

```bash
cd source/dts-platform
./mvnw test -Dtest='WorkbenchLeaderOverviewServiceTest,ClassificationMapperTest,WorkbenchRoleResolverTest,WorkbenchAuditRateLimiterTest,ScreenReportLinkSyncServiceTest,DtsAnalyticsClientIT' \
    -DfailIfNoTests=false
```

预期：5+5+ … = 全部 PASS。

### 前端

```bash
cd source/dts-platform-webapp
pnpm vitest run \
    src/pages/workbench/LeaderOverviewPage.test.tsx \
    src/pages/workbench/LeaderOverviewPage.integration.test.tsx \
    src/pages/workbench/components \
    src/pages/workbench/hooks \
    src/analytics/pages/screens/hooks/useScreenVisitTracker.test.ts
```

预期：全套绿。

## 产物

- `it/regression-backend.log`（mvn test 输出末段）
- `it/regression-frontend.log`（vitest 输出末段）

## 完成标准

- [ ] 后端 Sprint-15 4 个测试全绿（共 40+ test cases）
- [ ] 后端 Sprint-17 新测试全绿
- [ ] 前端 LeaderOverviewPage + 组件 + hooks 全绿
- [ ] 前端 useScreenVisitTracker 全绿
- [ ] 日志文件存档
