# F1: 指标工作台主页

**优先级**: P0
**状态**: READY

## 目标

新建 `/modeling/metric-workbench` 路由和 `MetricWorkbenchPage`，实现三栏 React Flow 画布：左栏主题域树、中栏可视化画布、右栏属性/消费面板。

## 设计

```
┌──────────────────┬───────────────────────────────┬────────────────────────┐
│ SubjectBrowserPanel │      MetricCanvas            │   MetricDetailPanel   │
│  (240px)         │   (React Flow, flex-1)        │   (360px, 可收起)      │
│ 主题域树          │  BizObjectNode ──► MetricNode  │  Tab: 公式/维度/粒度  │
│ 业务对象          │  MetricBindingEdge            │  Tab: 消费数据(折线)   │
│ 指标列表          │  右键菜单                     │  Tab: 血缘/质量        │
└──────────────────┴───────────────────────────────┴────────────────────────┘
```

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 三栏骨架 + 路由注册 | P0 | READY | - |
| T02 | SubjectBrowserPanel（左栏） | P0 | READY | T01 |
| T03 | MetricCanvas + 节点/边类型 | P0 | READY | T01 |
| T04 | MetricDetailPanel（右栏 Tabs） | P0 | READY | T03 |

## 完成标准

- [ ] `/modeling/metric-workbench` 可访问，三栏渲染不崩溃
- [ ] 主题域树展示 `listSemanticSubjectDomains()` 数据
- [ ] 画布展示 BizObjectNode 和 MetricNode，有 MetricBindingEdge
- [ ] 右栏选中节点后切换 Tab 正常
- [ ] tsc 零报错，Chrome 95 无 oklch/:has
