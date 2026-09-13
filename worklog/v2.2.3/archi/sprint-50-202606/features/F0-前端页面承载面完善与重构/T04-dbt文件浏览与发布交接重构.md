# T04: dbt 文件浏览与发布交接重构

**优先级**: P0  
**状态**: READY  
**依赖**: T01

## 目标

明确 `/modeling/dbt-files` 是 dbt 文件证据面，而不是业务发布主入口；发布、门禁和标准映射必须回到 SQL 建模页。

## 技术设计

- 文件树、编辑器、保存、运行 dbt、预览模型继续保留。
- 发布相关动作保持禁用或引导回 `/studio/sql-modeling`。
- schema.yml、seeds、manifest、run_results 作为证据展示，不在该页面发起绕过门禁的发布。

## 影响范围

- `source/dts-platform-webapp/src/pages/modeling/DbtFileBrowserPage.tsx`
- `source/dts-platform-webapp/src/api/platformApi.ts`

## 验证

- [ ] source-contract 覆盖“预览模型”跳转到 SQL 建模页。
- [ ] source-contract 覆盖发布禁用说明或交接提示。
- [ ] Chrome95 下文件树和编辑区不遮挡主操作。

## 完成标准

- [ ] dbt 文件浏览页面定位清晰，不与 SQL 建模页职责重复。
- [ ] 用户能从文件证据回到模型发布闭环。

