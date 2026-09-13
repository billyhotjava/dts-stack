# T04: MetricDetailPanel（右栏 Tabs）

**优先级**: P0
**状态**: READY
**依赖**: T03

## 目标

实现右栏属性与消费面板，选中不同节点类型显示对应 Tab 内容：公式配置、消费趋势、血缘来源。

## 技术设计

```tsx
// src/pages/modeling/metric-workbench/MetricDetailPanel.tsx
// antd Tabs: ["公式 / 维度", "消费数据", "血缘来源"]
//
// selectedNode 类型判断:
//   type === "bizObject" → Tab[公式/维度]: 展示 table mappings 只读摘要
//                          + "编辑" 按钮 → router.push("/modeling/semantic/objects")
//   type === "metric"   → Tab[公式/维度]: formulaType Select + formulaJson Input
//                          [保存] → updateSemanticMetric(id, data)
//
// Tab[消费数据]:
//   listSemanticModelRuns(modelId) → 运行记录列表
//   最近 10 条 status Tag 时序展示（用 antd Tag，不用图表库额外引入）
//   [触发运行] → triggerSemanticModelRun(modelId)
//   [发布 dbt] → publishSemanticModelToDbt(modelId)
//                 成功后 registerSemanticBiDataset + registerSemanticLineage
//
// Tab[血缘来源]:
//   VisualFlowCanvas (read-only, height=200)
//   nodes/edges 来自 GET /catalog/lineage/graph?datasetId={assetKey} (现有接口)
//   数据不可用时: EmptyState "发布后自动生成血缘"
//
// 未选中节点: EmptyState "请在画布中选择节点"
```

新建文件：
- `src/pages/modeling/metric-workbench/MetricDetailPanel.tsx`（~280 行）

## 影响范围

- 新建 `MetricDetailPanel.tsx`
- 复用 `VisualFlowCanvas`（已有）
- 复用 `semanticModelingApi.ts` 中 `updateSemanticMetric`, `listSemanticModelRuns`, `triggerSemanticModelRun`, `publishSemanticModelToDbt`, `registerSemanticBiDataset`, `registerSemanticLineage`

## 验证

- [ ] 选中 MetricNode → 公式 Tab 显示 formulaType/formulaJson
- [ ] [保存] 调 updateSemanticMetric 且 toast.success
- [ ] Tab[消费数据] 展示 runs 列表（空态不崩溃）
- [ ] [触发运行] 调 triggerSemanticModelRun 且 toast.success
- [ ] [发布 dbt] 成功后连续调 register 两个接口
- [ ] 未选中节点时显示 EmptyState
- [ ] tsc 零报错

## 完成标准

- [ ] 三个 Tab 均可切换且内容正确
- [ ] 保存/发布操作有 loading 状态和 toast 反馈
