# Sprint-58 IT Evidence

**状态**: DONE
**日期**: 2026-07-08

## 验证矩阵

| 验证项 | 命令 | 状态 | 证据 |
|--------|------|------|------|
| 前端菜单与页面契约 | `node --test src/pages/catalog/DataAssetPortalMenu.source-contract.test.ts src/pages/catalog/MetadataManagementPage.source-contract.test.ts src/pages/catalog/MetadataPage.source-contract.test.ts` | PASS | 5 tests, 5 pass |
| 前端类型检查 | `pnpm exec tsc --noEmit` | PASS | exit 0 |
| 前端生产构建 | `pnpm build` | PASS | exit 0, built in 2m10s；仅 Browserslist 过期和 chunk size warning |
| 后端菜单 seed 契约 | `./mvnw -Dspotless.apply.skip=true -Dspotless.check.skip=true -Dspring-boot.build-info.skip=true -Dtest=PortalMenuSeedDefaultsContractTest#dataAssetMetadataManagementSeedKeepsSeparateCollectionAndManagementEntrypoints test` | PASS | Tests run: 1, Failures: 0 |
| 后端整类基线 | `./mvnw -Dspotless.apply.skip=true -Dspotless.check.skip=true -Dspring-boot.build-info.skip=true -Dtest=PortalMenuSeedDefaultsContractTest test` | KNOWN_FAIL | 剩余既有失败：`portalMenuSeedPromotesDataScreensToRootWithoutChangingRoleDefaults` 期望“数据大屏”是一级菜单 |
| Diff 空白检查 | `git diff --check` | PASS | exit 0 |
| GitNexus 影响检查 | `mcp__gitnexus.detect_changes(scope=unstaged)` | PASS | risk low, affected processes 0 |

## 未覆盖风险

- 浏览器可视化 smoke 尚未执行；本 Sprint 以路由、契约测试、TypeScript 编译和生产 build 作为前端合并门。
- `PortalMenuSeedDefaultsContractTest` 整类仍存在一个非本 Sprint 引入的“数据大屏 root”基线失败；本 Sprint 后端交付门使用新增的元数据管理 seed targeted test。
