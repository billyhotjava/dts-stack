# T02: SQL 建模页主工作台重构

**优先级**: P0  
**状态**: READY  
**依赖**: T01

## 目标

把 `/studio/sql-modeling` 整理为标准/dbt 联动的主工作台，先保证页面布局、模型字段区、运行按钮、诊断抽屉和发布动作可以承载后续 F2-F5。

## 技术设计

- 模型列表、模型详情、字段列表、dbt 运行区和发布区保持同一工作台内闭环。
- 编译、测试、构建、发布按钮补齐 loading、error、disabled with reason、success 状态。
- 字段列表预留标准映射承载位，但不在本 task 实现完整绑定逻辑。
- 诊断抽屉只展示证据和修复入口，不在抽屉内做复杂编辑。

## 影响范围

- `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- `source/dts-platform-webapp/src/pages/modeling/components/ModelEditDrawer.tsx`
- `source/dts-platform-webapp/src/pages/modeling/components/OdsGenerateModal.tsx`
- `source/dts-platform-webapp/src/pages/modeling/components/DbtModelDiagnosticsDrawer.tsx`
- `source/dts-platform-webapp/src/pages/modeling/sqlModeling.types.ts`

## 验证

- [ ] source-contract 覆盖编译、测试、构建、发布按钮。
- [ ] 字段区在无字段、加载中、接口失败时都有明确状态。
- [ ] Chrome95 下 1366x768 不出现按钮或表格错乱。

## 完成标准

- [ ] SQL 建模页能作为后续标准映射和发布门禁主入口。
- [ ] 页面不依赖新增菜单或额外工作台。

