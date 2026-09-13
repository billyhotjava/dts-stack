# T04: 集成验证与 IT 证据

**优先级**: P0
**状态**: DONE
**依赖**: T03

## 目标

完成前端、后端、菜单、路由和 TypeScript 验证，并把证据写入 Sprint IT 文档。

## 技术设计

- 前端契约测试覆盖菜单、路由、页面文案和 API 使用。
- 后端契约测试覆盖 seed/defaults。
- TypeScript 检查覆盖页面编译。
- GitNexus detect changes 检查影响范围。

## 影响范围

- `worklog/v2.2.3/sprint-58-202607/it/README.md`
- 本 Sprint 修改过的前后端文件。

## 验证

- [x] `node --test src/pages/catalog/DataAssetPortalMenu.source-contract.test.ts src/pages/catalog/MetadataManagementPage.source-contract.test.ts src/pages/catalog/MetadataPage.source-contract.test.ts`
- [x] `pnpm exec tsc --noEmit`
- [x] `pnpm build`
- [x] `./mvnw -Dspotless.apply.skip=true -Dspotless.check.skip=true -Dspring-boot.build-info.skip=true -Dtest=PortalMenuSeedDefaultsContractTest#dataAssetMetadataManagementSeedKeepsSeparateCollectionAndManagementEntrypoints test`
- [x] `git diff --check`
- [x] `gitnexus detect_changes`

## 完成标准

- [x] 验证命令退出码为 0。
- [x] IT 文档记录命令、结果和未覆盖风险。
- [x] Sprint/Feature/Task 状态更新为 DONE。
