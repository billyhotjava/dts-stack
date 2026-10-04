# T02: 接入 React Flow 拖拽与绑定回写

**优先级**: P0  
**状态**: DONE  
**依赖**: T01

## 目标

把指标工作台画布从静态关系展示改为可操作建模画布。

## 技术设计

- `MetricCanvas` 使用 `useNodesState` / `useEdgesState` 管理节点和边。
- 节点拖动结束后将位置写入浏览器 `localStorage`。
- React Flow `onConnect` 解析业务对象与指标节点，触发绑定。
- HTML5 drag/drop 支持从指标目录拖指标到画布或业务对象节点。
- `MetricWorkbenchPage` 调用 `updateSemanticMetric` 回写指标 `objectId`。
- `MetricDetailPanel` 保存公式时使用完整 payload，避免清空已绑定对象。

## 影响范围

- `MetricCanvas.tsx`
- `SubjectBrowserPanel.tsx`
- `MetricWorkbenchPage.tsx`
- `MetricDetailPanel.tsx`

## 验证

- [x] source-contract 覆盖未绑定指标分组和拖拽 payload。
- [x] Playwright mock smoke 验证拖拽触发 `PUT /api/semantic/metrics/metric-2`。

## 完成标准

- [x] 画布节点可拖。
- [x] 未绑定指标可拖。
- [x] 拖拽绑定请求带完整指标 payload。
