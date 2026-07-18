# T01：下钻配置解除 Card 限制

**优先级**：P0
**状态**：DONE
**依赖**：F1

## 目标

取消设计器只有 Card 数据源可配置下钻的限制，并保持无数据源组件不出现无效配置。

## 技术设计

- 以 `DRILL_CONFIGURABLE_TYPES + 有效 DataSourceConfig` 判断是否显示。
- 新增层级默认包含空 label、当前支持的数据源结构和空 mappings，不再默认 `cardId: 0`。
- CardIdPicker 只在用户选择 Card 数据源时出现。
- 不复制数据源表单；通过临时组件适配器直接复用现有 `renderDataSourceConfig`，把更新结果写回当前 DrillLevel。

## 影响范围

- `source/dts-platform-webapp/src/analytics/pages/screens/components/propertyPanel/BehaviorConfigSection.tsx`
- `source/dts-platform-webapp/src/analytics/pages/screens/components/propertyPanel/PropertyPanel.tsx`
- `source/dts-platform-webapp/src/analytics/pages/screens/components/propertyPanel/BehaviorConfigSection.source.test.ts`

## 验证

- [x] SQL、API、Dataset、Metric、Card 组件均可启用下钻。
- [x] 静态或无数据源组件不生成不可执行配置。
- [x] 旧 Card 层级仍正确展示。

## 完成标准

- [x] source-contract 先红后绿。
- [x] 直接复用 `renderDataSourceConfig`，未引入重复的数据源表单实现。
