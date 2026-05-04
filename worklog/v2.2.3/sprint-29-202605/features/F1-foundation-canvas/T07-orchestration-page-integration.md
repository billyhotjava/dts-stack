# T07: OrchestrationPage 接入「编排画布」Tab（壳）

**优先级**: P0
**状态**: READY
**依赖**: T02..T06

## 目标

把 OrchestrationPage 拆 Tab：「编排画布」（新，挂 WorkflowCanvas 空壳）+「运行实例」（保留原 Airflow DAG 列表）。本任务只做骨架，节点/保存逻辑在 F4 完成。

## 技术设计

### 改造前

`source/dts-platform-webapp/src/pages/explore/etl/OrchestrationPage.tsx` 当前是单页 DAG 列表表格。

### 改造后

```tsx
export default function OrchestrationPage() {
  const [activeTab, setActiveTab] = useState<'canvas' | 'runs'>('canvas');
  return (
    <PageContainer title="数据入湖编排">
      <Tabs activeKey={activeTab} onChange={k => setActiveTab(k as any)}>
        <Tabs.TabPane tab="编排画布" key="canvas">
          <WorkflowCanvas readonly={false} />     {/* 空壳，无节点 */}
        </Tabs.TabPane>
        <Tabs.TabPane tab="运行实例" key="runs">
          <DagListTable />                         {/* 抽出原内容 */}
        </Tabs.TabPane>
      </Tabs>
    </PageContainer>
  );
}
```

### 抽离

把原 OrchestrationPage 主体提取到新文件 `OrchestrationPage/DagListTable.tsx`，老逻辑零修改。

## 影响范围

| 类型 | 文件 |
|------|------|
| 修改 | `src/pages/explore/etl/OrchestrationPage.tsx` |
| 新增 | `src/pages/explore/etl/OrchestrationPage/DagListTable.tsx`（抽离原表格） |
| 引用 | `src/components/workflow/index.tsx`（WorkflowCanvas） |
| 路由 | 不变（`/explore/etl/orchestration`），符合"方案 A"决策 |

## 验证

- [ ] 老链接 `/explore/etl/orchestration` 默认显示「编排画布」Tab，画布空壳渲染正常
- [ ] 切到「运行实例」Tab 看到原 Airflow DAG 列表，**功能 0 退化**
- [ ] Tab 切换不重新挂载导致 viewport 重置（用 `destroyInactiveTabPane={false}`）
- [ ] Chrome 95 真机访问不崩

## 完成标准

- [ ] PageContainer 标题、面包屑、按钮无变化（只增 Tab）
- [ ] DagListTable 单测保留（原测试不破）
- [ ] grep 确认旧测试用例引用已修正路径
