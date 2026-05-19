# T01: React Flow 基础架构

**优先级**: P0
**状态**: DONE
**依赖**: F1

## 目标

在 `dts-metrics-webapp` 中引入 `@xyflow/react`，建立画布、节点、边、布局、选择和属性面板基础。

## 技术设计

- 在迁移后的 `SemanticModelCanvas` 上引入 `@xyflow/react`，替换原手写 SVG 画布。
- 使用 React Flow 节点、边、Background、Controls、MarkerType 和 `useNodesState` 管理画布。
- 节点保留 base/selected/candidate 状态、指标/维度数量、密级和主题域展示。
- 支持候选节点拖拽整理布局，点击节点选择/取消 Join，点击已选边取消 Join。
- 保留 fanout/approval 风险提示，后续 T03 扩展为边级诊断和连接规则。

## 影响范围

- `source/dts-metrics-webapp/package.json`
- `source/dts-metrics-webapp/pnpm-lock.yaml`
- `source/dts-metrics-webapp/src/features/semantic/SemanticModelCanvas.tsx`
- `source/dts-metrics-webapp/src/polyfills/legacyBrowser.ts`
- `source/dts-metrics-webapp/src/styles.css`
- `source/dts-metrics-webapp/test/source-contract.test.mjs`

## 验证

- [x] `pnpm test:source`
- [x] `pnpm typecheck`
- [x] `pnpm build`
- [x] Playwright smoke: `/metrics/semantic/metrics` 渲染 `.react-flow`，2 个节点、1 条边，候选节点可拖拽。
- [x] Playwright smoke: 点击候选节点后 Join 变为已选，并显示 fanout 风险。
- [x] 截图: `worklog/v2.2.3/sprint-32-202605/it/evidence/react-flow-canvas/sprint32-react-flow-canvas.png`

## 完成标准

- [x] `/metrics/semantic/metrics` 进入 React Flow 主画布。
- [x] 旧手写 `<svg>` 边绘制从 `SemanticModelCanvas` 移除。
