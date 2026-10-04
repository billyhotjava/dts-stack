# T01: 资产字段标准映射与 SQL 建模绑定收敛

**状态**: IN_PROGRESS
**优先级**: P0

## 目标

让资产字段落标和 SQL/dbt 模型字段绑定使用同一套数据元事实，不再出现资产目录一套、SQL 建模一套。

## 任务

- [ ] 对齐 `CatalogColumnSchema.standardId` 与 `SqlModel.standardBindings[]` 的字段含义。
- [ ] 确认自动匹配规则：字段名、注释 STD hint、中文名、英文名、标准编码。
- [ ] 输出冲突策略：资产字段和模型字段绑定不同数据元时，谁阻断、谁提示。
- [ ] 在 SQL 建模字段列表中展示资产字段已绑定标准和模型字段绑定差异。
- [x] 数据元列表输出字段落标草稿，作为 SQL 建模标准绑定的输入。
- [x] 字段落标草稿保存为后端快照，避免只依赖浏览器会话状态。
- [x] SQL 建模页读取草稿并可应用到当前模型标准绑定。

## 验收

- [ ] 同一字段从资产到 SQL 模型能追踪标准来源。
- [ ] 自动匹配不会覆盖人工绑定，除非用户确认 overwrite。
- [ ] 差异状态能进入标准门禁。

## 2026-07-09 实施证据

- 后端：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/StandardBindingDraftResource.java`
- 后端：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/StandardBindingDraftService.java`
- 前端：`source/dts-platform-webapp/src/pages/governance/ElementsPage.tsx`
- 前端：`source/dts-platform-webapp/src/pages/modeling/LowCodeDevelopmentPage.tsx`
- 前端：`source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- 验证：`./mvnw -q -Dtest=StandardBindingDraftServiceTest test`
- 验证：`node --test src/pages/modeling/dataDevelopmentWorkbench.source-contract.test.ts`
- 验证：`pnpm build`
