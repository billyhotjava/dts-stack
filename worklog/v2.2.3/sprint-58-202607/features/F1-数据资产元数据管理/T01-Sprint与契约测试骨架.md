# T01: Sprint 与契约测试骨架

**优先级**: P0
**状态**: DONE
**依赖**: 无

## 目标

建立本 Sprint 的 feature/task/IT 文档，并先写失败的 source-contract 测试锁定菜单、路由和页面能力。

## 技术设计

- 新建 `worklog/v2.2.3/sprint-58-202607/` 及 Feature/Task/IT 工件。
- 更新 `worklog/v2.2.3/sprint-queue.md`。
- 扩展前端 source-contract 测试，要求：
  - 数据资产菜单新增 `/catalog/metadata-management`。
  - 数据源结构采集仍在数据集成菜单 `/catalog/metadata`。
  - 新页面存在并包含资产语义元数据管理文案。

## 影响范围

- `worklog/v2.2.3/sprint-58-202607/**`
- `worklog/v2.2.3/sprint-queue.md`
- `source/dts-platform-webapp/src/pages/catalog/DataAssetPortalMenu.source-contract.test.ts`
- `source/dts-platform-webapp/src/pages/catalog/MetadataManagementPage.source-contract.test.ts`

## 验证

- [x] `node --test src/pages/catalog/DataAssetPortalMenu.source-contract.test.ts src/pages/catalog/MetadataManagementPage.source-contract.test.ts` 先失败，失败原因是入口/页面尚未实现。

## 完成标准

- [x] Sprint 文档完整。
- [x] 契约测试能准确描述本 Sprint 目标。
- [x] 已记录 RED 阶段测试输出。
