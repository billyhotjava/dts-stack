# F1: 指标工作台拖拽建模闭环

**优先级**: P0  
**状态**: DONE

## 目标

让指标工作台支持真实拖拽和连线建模：拖动节点调整布局，拖拽未绑定指标到业务对象完成绑定，并保留后端指标字段。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 抽取画布 helper 与测试 | P0 | DONE | - |
| T02 | 接入 React Flow 拖拽与绑定回写 | P0 | DONE | T01 |
| T03 | 浏览器 smoke 与文档验收 | P0 | DONE | T02 |

## 完成标准

- [x] 拖拽/连线关键逻辑有 helper 测试覆盖。
- [x] `MetricCanvas` 使用 React Flow state 管理节点、边和连接事件。
- [x] `SubjectBrowserPanel` 暴露未绑定指标拖拽源。
- [x] `MetricWorkbenchPage` 绑定指标时调用后端更新并刷新指标列表。
- [x] `MetricDetailPanel` 保存公式时保留完整指标字段。
