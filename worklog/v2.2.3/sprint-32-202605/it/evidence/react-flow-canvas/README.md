# React Flow 画布交互证据

**状态**: IN_PROGRESS

## 验证目标

验证指标与语义中心不再是占位页或静态列表，而是可编辑、可保存、可验证的 React Flow 工作台。

## 已执行检查

- [x] `/metrics/semantic/metrics` 加载 React Flow 画布。
- [x] fallback 语义元数据下渲染 2 个模型节点和 1 条 Join 边。
- [x] 候选模型节点可拖拽，Playwright 验证节点坐标发生变化。
- [x] 点击候选模型节点后 Join 变为已选，节点状态显示“已选”。
- [x] fanout 风险在 Join 选中后展示为画布诊断提示。
- [x] metric 字段从字段树拖入 React Flow 画布后生成 field 节点。

## 证据文件

- `sprint32-react-flow-canvas.png`
- `sprint32-field-drop-canvas.png`

## 待执行检查

- [ ] 从 platform 资产节点池拖入 source asset。
- [ ] 创建 business object、Join、dimension、metric、model、publish 节点。
- [ ] 保存 graph draft 后刷新页面可恢复。
- [ ] graph preflight 错误能定位到节点和边。
- [ ] platform/dbt validation 报告能定位到模型节点或字段节点。

## 证据命令

```bash
cd source/dts-metrics-webapp
pnpm test:source
pnpm run typecheck
pnpm run build
```

Playwright smoke:

- `hasFlow=true`
- `nodeCount=2`
- drag result: `draggable=true`
- selected join result: `selectedJoinCount=1`, `edgeCount=1`
- field drop result: `before=2`, `after=3`, `nodes` 包含 `项目数指标项目明细主体project_cnt`
