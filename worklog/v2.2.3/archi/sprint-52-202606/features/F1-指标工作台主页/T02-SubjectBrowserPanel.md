# T02: SubjectBrowserPanel（左栏）

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

实现左栏主题域树，展示 主题域 → 业务对象 → 指标 三级结构，点击节点可高亮画布对应节点，支持新建导航。

## 技术设计

```tsx
// src/pages/modeling/metric-workbench/SubjectBrowserPanel.tsx
// antd Tree 组件，treeData 由 domains + objects + metrics 派生
// 节点点击 → onNodeHighlight(id) prop 回调 → MetricCanvas setSelectedId
// 底部: "前往主题域页" 按钮 → router.push("/modeling/semantic/subjects")
// 底部: "前往业务对象页" 按钮 → router.push("/modeling/semantic/objects")
// 空态: "暂无主题域，请先创建" + 导航按钮
```

节点图标规则（Chrome 95 兼容，用文字/emoji 替代 CSS filter）：
- 主题域：`📁` + domain.name
- 业务对象：`◎` + object.name
- 指标：`📈` + metric.name + formulaType badge

## 影响范围

- 新建：`src/pages/modeling/metric-workbench/SubjectBrowserPanel.tsx`（~130 行）

## 验证

- [ ] Tree 展示 3 级结构
- [ ] 点击指标节点 → 画布对应 MetricNode 高亮（selectedId 联动）
- [ ] 空态时显示引导文字
- [ ] 导航按钮跳转正确路由
- [ ] tsc 零报错

## 完成标准

- [ ] 左栏主题域树可交互
- [ ] 与 MetricCanvas 通过 selectedId prop 双向联动
