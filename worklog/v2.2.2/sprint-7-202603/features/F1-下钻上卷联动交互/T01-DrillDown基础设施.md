# T01: DrillDown 基础设施

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标
在 ProjectCockpitContext 中新增下钻状态管理和通用 DrillDownDrawer 组件，为所有下钻场景提供统一入口

## 技术设计

### 1. Context 扩展
在 `ProjectCockpitContext.tsx` 中新增：
```typescript
interface DrillState {
  target: 'high-risk' | 'overdue' | 'delay-reason' | 'trend-week' | null;
  params: Record<string, any>;
}
interface SelectionState {
  week?: string;       // 趋势图选中的周
  nodeId?: string;     // 项目树选中的节点
}
```

Context 新增：
- `drillState` + `setDrillState` — 控制下钻 Drawer 打开/关闭和目标
- `selectionState` + `updateSelection` — 图表间联动的选中状态

### 2. DrillDownDrawer 组件
新建 `components/DrillDownDrawer.tsx`：
- 接收 `drillState`，按 `target` 渲染不同明细内容
- 内置 antd Table，支持 `sorter`、`filterSearch`
- 支持二次下钻（Drawer 内再开 Drawer）
- 关闭时清空 `drillState`

### 3. 明细数据 API
在 `analyticsApi.ts` 中新增：
- `getProjectCockpitDrillDetail(target, params)` — 按下钻类型返回明细列表

## 影响范围
- `ProjectCockpitContext.tsx` — 扩展 Context
- `components/DrillDownDrawer.tsx` — 新建
- `analyticsApi.ts` — 新增 API
- `ProjectCockpitLayout.tsx` — 挂载 DrillDownDrawer

## 验证
- [ ] DrillDownDrawer 可通过 `setDrillState({ target: 'high-risk', params: {} })` 打开
- [ ] Drawer 内 Table 支持列排序和搜索
- [ ] 关闭 Drawer 后 drillState 清空

## 完成标准
- [ ] Context 扩展后所有现有视图无回归
- [ ] DrillDownDrawer 组件可复用于所有下钻场景
