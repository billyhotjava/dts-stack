# F5: Chart / Pivot / Query Plan / 二次查询

**优先级**: P1
**状态**: READY

## 目标

在底部面板多 Tab 中实现深度分析能力：结果集可视化（Chart）、透视表（Pivot）、执行计划树（Query Plan）、二次查询（把结果集当临时视图继续写 SQL）。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T21 | ResultChart（ECharts 快速可视化） | P1 | READY | F4 |
| T22 | ResultPivot（透视表） | P1 | READY | F4 |
| T23 | Query Plan（后端 EXPLAIN 封装 + 前端 React Flow 渲染） | P1 | READY | F1 |
| T24 | 二次查询（DuckDB 临时视图） | P1 | READY | F4 |
| T25 | Log 面板（含重写后 SQL 展示） | P2 | READY | F4 |

## 完成标准

- [ ] Chart 支持 5 种基础图表（柱/折/饼/散点/面积），可一键保存为看板组件
- [ ] Pivot 支持行/列/值拖拽聚合（SUM/COUNT/AVG/MIN/MAX/DISTINCT）
- [ ] Query Plan 树形展示，节点代价着色，点击显示详情
- [ ] 二次查询 DuckDB 临时视图可用，30 分钟自动清理
- [ ] Log 面板展示重写前/后 SQL（高级模式）
- [ ] 所有动作纳入审计
