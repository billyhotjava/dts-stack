# T07: OrchestrationPage 接入「编排画布」Tab（壳）

**优先级**: P0
**状态**: DONE
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

- [x] 路由 `/explore/etl/orchestration` 默认进入「编排画布」Tab，挂 `WorkflowCanvas readonly={false}`（calc(100vh-220px) 高度，最小 480px）
- [x] 「运行实例」Tab 复用原 OrchestrationPage 主体（原文件改名为 `OrchestrationRunsTab.tsx`，0 行业务逻辑变更，仅函数名 `OrchestrationPage` → `OrchestrationRunsTab`）
- [x] `destroyInactiveTabPane={false}` 切换不重置 viewport / store
- [x] PageHeader/Card/Filter/Selector 等原有逻辑全部保留在 OrchestrationRunsTab；外部 API/triggerAirflowJob/openLogPreview 调用零修改
- [x] 2 vitest 用例：默认激活 canvas / 切换到 runs Tab `aria-selected=true`（mock 子组件隔离）
- [x] tsc 0 错；workflow 整模块 40/40 + OrchestrationPage 2/2 全绿
