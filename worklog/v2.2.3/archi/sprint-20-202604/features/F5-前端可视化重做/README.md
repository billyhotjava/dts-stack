# F5: 前端可视化重做

**优先级**: P0
**状态**: READY
**修补断点**: ❼（前端固定栅格、无 minimap、无字段级、无影响高亮、无 PNG 导出）

## 目标

把 `LineagePage` 从"占位"升级为"企业级数据血缘工具"：自动布局、节点形状区分、列级 toggle、影响范围高亮、PNG/SVG 导出、HTTP 缓存。

## 现状（来自 review）

- 库：`@xyflow/react` 12.x（保留）
- 布局：按 layer 写死 X 坐标 0/250/500/750/1000，节点过密堆叠
- 节点样式：仅 layer 颜色，无 source/dataset/view/ETL job 区分
- 边：统一灰色，无标签
- 控件：仅 Background + Controls；**无 minimap、无 fitView 智能、无 onClick 跳转**
- 缓存：无（每次参数变化都重新请求）
- 导出：仅 CSV
- 字段级：占位、API 没参数

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | dagre/elk 自动布局接入 | P0 | READY | - |
| T02 | 节点类型与图标系统（dataset/job/view/source） | P0 | READY | F4.T02 |
| T03 | 列级 toggle 与影响范围高亮 | P0 | READY | F3.T04 |
| T04 | PNG/SVG 导出 + minimap | P1 | READY | T01 |
| T05 | 缓存、虚拟化、体验优化 | P1 | READY | T01,T02 |

## 完成标准

- [ ] 200 节点图可读、能定位中心节点、能 fit 视图
- [ ] 节点按类型有不同图标 + 颜色 + 悬浮提示
- [ ] 列级 toggle 打开后能看到 dwd_orders.amount ← ods_orders.gross_amount
- [ ] 点中节点 → 高亮其上下游（BFS 染色）
- [ ] PNG 导出 + SVG 导出可用
- [ ] minimap 在右下角，可点击跳转
- [ ] TanStack Query 接管 API 调用，参数变化时命中缓存
- [ ] 节点超 100 时自动收紧 depth 提示用户
