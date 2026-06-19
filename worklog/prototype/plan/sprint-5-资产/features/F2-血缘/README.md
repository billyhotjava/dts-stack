# F2: 血缘

**优先级**: P0
**状态**: READY

## 目标

用 `@antv/g6`（现网同款，`@/components/lineage` 的 `LineageGraph`）落地资产血缘可视化与配套视图（列级血缘 / 影响分析 / diff / 导入），让用户看清 `ODS.宽表` 的上游来自 S4 画布的 PLM/ERP 源表 + 去重/连接任务节点。血缘数据走 mock。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| [T01](./T01-血缘图.md) | 血缘图（@antv/g6，节点=资产/任务，边=数据流） | P0 | READY | S1, F1-T02 |
| [T02](./T02-列级血缘与影响分析.md) | 列级血缘/影响分析/diff/导入（LineageColumns/Impact/Diff/Import） | P0 | READY | T01 |

## 完成标准

- [ ] `LineageGraphPage` 用 `@antv/g6` 渲染血缘图：节点=资产/任务，边=数据流向；legacy 构建下可平移/缩放/选中。
- [ ] 样例 `ODS.宽表` 血缘上游可见 PLM/ERP 源表 + 去重/连接任务节点（连贯消费 S4 画布产出）。
- [ ] `LineageColumnsPage`（列级）/ `LineageImpactPage`（影响分析）/ `LineageDiffPage`（diff）/ `LineageImportPage`（导入）四视图可路由可访问并接 mock。
- [ ] 血缘数据全部来自 mock service，`VITE_USE_MOCK` 开关生效。
