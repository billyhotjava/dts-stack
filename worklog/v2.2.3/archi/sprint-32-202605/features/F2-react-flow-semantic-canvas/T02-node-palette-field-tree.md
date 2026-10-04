# T02: 节点池和字段树

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: T01

## 目标

从 platform 资产契约加载可建模资产和字段，形成画布节点池和字段树。

## 技术设计

- 节点池包含 source asset、business object、dimension、metric、model、publish。
- 字段树展示 schema、字段类型、治理标准、可用权限和分类分级。
- 拖拽字段到画布生成 dimension/metric 节点。

## 当前进展

- 已继承旧配置台的 `application/vnd.dts-metrics-field` 拖拽 payload。
- `SemanticFieldExplorer` 字段行支持 drag start，携带 fieldId、fieldKind、modelId、label。
- `SemanticModelCanvas` 作为 React Flow drop target，接收 metric/dimension 字段并生成 field 节点。
- 已选字段通过 React Flow 节点和 base 模型虚线边展示。

## 影响范围

- `source/dts-metrics-webapp/src/features/semantic/SemanticFieldExplorer.tsx`
- `source/dts-metrics-webapp/src/features/semantic/SemanticModelCanvas.tsx`
- `source/dts-metrics-webapp/src/pages/semantic/SemanticDesignerPage.tsx`
- `source/dts-metrics-webapp/src/styles.css`
- `source/dts-metrics-webapp/test/source-contract.test.mjs`
- `source/dts-metrics/src/main/java/**PlatformContractClient*`（后续）

## 验证

- [ ] 无权限资产不显示或显示为不可拖拽。
- [x] 字段搜索、筛选、拖拽生成节点可用。
- [x] `pnpm test:source`
- [x] `pnpm typecheck`
- [x] `pnpm build`
- [x] Playwright smoke: metric 字段拖入画布后 React Flow 节点数从 2 增加到 3。
- [x] 截图: `worklog/v2.2.3/sprint-32-202605/it/evidence/react-flow-canvas/sprint32-field-drop-canvas.png`

## 完成标准

- [ ] 节点池不使用静态 mock 作为主要数据源。
