# T01: React Flow 基础架构

**优先级**: P0
**状态**: READY
**依赖**: F1

## 目标

在 `dts-metrics-webapp` 中引入 `@xyflow/react`，建立画布、节点、边、布局、选择和属性面板基础。

## 技术设计

- 新增 canvas feature 模块，拆分 `nodes/edges/panels/hooks/types`。
- 使用 React Flow provider 管理节点和边。
- 节点类型用显式 registry 管理。
- 支持画布缩放、mini map、controls、fit view。

## 影响范围

- `source/dts-metrics-webapp/package.json`
- `source/dts-metrics-webapp/src/features/metric-flow/**`
- `source/dts-metrics-webapp/src/pages/semantic/**`

## 验证

- [ ] `pnpm run typecheck`
- [ ] `pnpm run build`
- [ ] Playwright 截图确认画布非空、可拖拽、可选择。

## 完成标准

- [ ] `/metrics/semantic/metrics` 进入 React Flow 主画布。
