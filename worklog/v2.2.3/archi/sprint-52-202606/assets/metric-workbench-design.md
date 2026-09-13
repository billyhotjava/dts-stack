# 指标工作台设计规范

> 设计讨论产出，2026-06-29

## 架构总览

### 路由结构

```
/modeling/metric-workbench          ← 新主入口（三栏画布）
/modeling/semantic/subjects         ← SemanticSubjectsPage（替换壳）
/modeling/semantic/objects          ← SemanticObjectsPage（替换壳，含 join 画布）
/modeling/semantic/metrics          ← SemanticMetricsPage（替换壳）
/modeling/semantic/models           ← SemanticModelsPage（替换壳）
/modeling/semantic/publish          ← SemanticPublishPage（替换壳）
/modeling/semantic/runs             ← SemanticRunsPage（替换壳）
/modeling/semantic-center           ← 保持 MetricsServiceFrame（不动）
```

### 文件结构

```
src/pages/modeling/
  MetricWorkbenchPage.tsx                    ← 三栏容器 (~200 行)
  SemanticSubjectsPage.tsx                   ← 替换壳 (~180 行)
  SemanticObjectsPage.tsx                    ← 替换壳，含 join 画布 (~250 行)
  SemanticMetricsPage.tsx                    ← 替换壳 (~200 行)
  SemanticModelsPage.tsx                     ← 替换壳 (~300 行)
  SemanticPublishPage.tsx                    ← 替换壳 (~280 行)
  SemanticRunsPage.tsx                       ← 替换壳 (~220 行)
  metricWorkbench.source-contract.test.ts   ← 新增契约测试
  metric-workbench/
    SubjectBrowserPanel.tsx                  ← 左栏 (~130 行)
    MetricCanvas.tsx                         ← 中栏 (~200 行)
    MetricDetailPanel.tsx                    ← 右栏 (~280 行)
    nodes/
      BizObjectNode.tsx                      ← (~70 行)
      MetricNode.tsx                         ← (~70 行)
    edges/
      MetricBindingEdge.tsx                  ← (~40 行)
```

## 数据流

### 页面加载

```
MetricWorkbenchPage 挂载
  ├─ listSemanticSubjectDomains()  → domains（左栏树）
  ├─ listSemanticBusinessObjects() → BizObjectNode
  └─ listSemanticMetrics()        → MetricNode + 左栏指标列表
      └─ 懒加载 getSemanticModelBindings(objectId) → MetricBindingEdge
```

### 节点类型与样式（Chrome 95 HSL）

| 节点 | border | background | 内容 |
|------|--------|------------|------|
| BizObjectNode | `hsl(220,80%,55%)` | `hsl(220,95%,97%)` | name + code + tableCount |
| MetricNode(ACTIVE) | `hsl(142,60%,45%)` | `hsl(142,80%,96%)` | name + formulaType |
| MetricNode(DRAFT) | `hsl(0,0%,70%)` | `hsl(0,0%,98%)` | name + DRAFT badge |
| MetricBindingEdge | animated dashed | — | — |

## API 映射

| 操作 | API 函数 |
|------|---------|
| 主题域列表 | `listSemanticSubjectDomains()` |
| 业务对象列表 | `listSemanticBusinessObjects()` |
| 表映射列表 | `listSemanticObjectTableMappings(objectId)` |
| 指标列表 | `listSemanticMetrics()` |
| 模型列表 | `listSemanticModels({ type })` |
| 运行历史 | `listSemanticModelRuns(modelId)` |
| 触发运行 | `triggerSemanticModelRun(modelId)` |
| 发布 dbt | `publishSemanticModelToDbt(modelId)` |
| 注册 BI 数据集 | `registerSemanticBiDataset(modelId)` |
| 注册血缘 | `registerSemanticLineage(modelId)` |
| 审核通过 | `approveSemanticModelReview(modelId, comment)` |
| 审核拒绝 | `rejectSemanticModelReview(modelId, comment)` |

## Chrome 95 约束清单

- 颜色：HSL 或 HEX，禁 oklch()
- 选择器：禁 `:has()`
- 布局：禁 `container` / `@container` / `subgrid`
- React Flow：`@xyflow/react ^12.10.2`（Sprint-29 已验证 Chrome 95 兼容）
