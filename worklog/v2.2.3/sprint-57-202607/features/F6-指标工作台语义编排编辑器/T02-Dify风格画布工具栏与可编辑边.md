# T02: Dify 风格画布工具栏与可编辑边

**优先级**: P0  
**状态**: READY  
**依赖**: T01

## 目标

将指标关系画布升级为可编辑工作台：提供选择/连线/自动布局/预检/保存编排入口，并让关系边可选中、可区分类型、可进入配置。

## 技术设计

- 在 `MetricCanvas` 上方增加编辑器工具栏：
  - 选择模式。
  - 连线模式。
  - 自动布局。
  - 预检。
  - 保存编排。
- `MetricBindingEdge` 支持：
  - `OBJECT_METRIC` 与 `METRIC_DERIVES` 两种视觉样式。
  - 选中态。
  - 删除入口或删除动作回调。
- React Flow selection 扩展到节点和边：
  - 选中节点时沿用节点详情。
  - 选中边时进入关系配置。
- 自动布局规则：
  - 业务对象置左。
  - 普通指标置中。
  - 派生指标按依赖层级向右展开。

## 影响范围

- `source/dts-platform-webapp/src/pages/modeling/metric-workbench/MetricCanvas.tsx`
- `source/dts-platform-webapp/src/pages/modeling/metric-workbench/edges/MetricBindingEdge.tsx`
- `source/dts-platform-webapp/src/pages/modeling/metricWorkbench.source-contract.test.ts`

## 验证

- [ ] source-contract 锁定工具栏关键文案和能力入口。
- [ ] source-contract 锁定 `METRIC_DERIVES` 边类型。
- [ ] 手工或 Playwright smoke 验证边可选中且不影响节点拖动。

## 完成标准

- [ ] 画布不再只是查看图表框，用户能从工具栏进入编辑/预检。
- [ ] 原有业务对象 -> 指标连线保存不回退。
- [ ] 指标 -> 指标连线能进入边配置流程。
