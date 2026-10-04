# T06: OrchestrationPage 真实替换 + 顶部"保存 DSL"按钮

**优先级**: P0
**状态**: PARTIAL（`?taskId` 后端保存路径已接入，任务选择器/e2e 待补）
**依赖**: T04, T05

## 目标

完成 OrchestrationPage 接入闭环：选中某个 IngestionTask → 编排画布 Tab 加载其 graph_dsl → 用户编辑 → 顶部"保存"按钮 PUT 回后端。

> 2026-05-05 当前实现：页面支持 `?taskId=<id>` 加载/保存 `IngestionTask.graphDsl`；未带 taskId 时仍保留本地 DSL 草稿保存/恢复能力，用于前端闭环冒烟。

## 技术设计

### 页面结构

```tsx
export default function OrchestrationPage() {
  const [taskId, setTaskId] = useState<string | null>(null);
  const { data: task } = useIngestionTask(taskId);
  const [activeTab, setActiveTab] = useState<'canvas' | 'runs'>('canvas');

  return (
    <PageContainer
      title="数据入湖编排"
      extra={[
        <TaskSelector value={taskId} onChange={setTaskId} />,
        <Button type="primary" onClick={onSave} disabled={!taskId}>保存 DSL</Button>,
      ]}
    >
      <Tabs activeKey={activeTab} onChange={k => setActiveTab(k as any)}>
        <Tabs.TabPane tab="编排画布" key="canvas">
          {taskId
            ? <WorkflowCanvas initialDsl={task?.graphDsl} onChange={setLocalDsl} />
            : <Empty description="请先选择任务" />
          }
        </Tabs.TabPane>
        <Tabs.TabPane tab="运行实例" key="runs">
          <DagListTable taskId={taskId} />
        </Tabs.TabPane>
      </Tabs>
    </PageContainer>
  );
}

const onSave = async () => {
  const dsl = serializeDsl(useWorkflowStore.getState());
  await ingestionTaskApi.updateGraphDsl(taskId!, dsl);
  message.success('保存成功');
};
```

### WorkflowCanvas 增强

接受 `initialDsl` prop，在 mount 时调用 deserializeDsl 注入 store。

### API 客户端

```ts
// src/api/ingestion-tasks.ts
export async function updateGraphDsl(taskId: string, dsl: WorkflowDsl) {
  return axios.put(`/api/ingestion-tasks/${taskId}/graph-dsl`, dsl);
}
```

## 影响范围

| 类型 | 文件 |
|------|------|
| 修改 | `OrchestrationPage.tsx` |
| 修改 | `WorkflowCanvas.tsx` 接 initialDsl |
| 新增 | `src/api/ingestion-tasks.ts` 中 updateGraphDsl |
| e2e | `e2e/explore/etl/orchestration.spec.ts`（新增端到端） |

## 验证

- [x] 本地 DSL 草稿 → 画布加载完整还原（节点位置、连线、参数、viewport）
- [x] 编辑后保存 → 带 `?taskId` 时调用 IngestionTask update 保存 `graphDsl`（真实环境冒烟待补）
- [x] 切换 task 时按 URL `taskId` 重新加载 DSL
- [x] 保存按钮可用，本地保存 DSL 草稿
- [ ] e2e：完整一遍"创建 → 编辑 → 保存 → 刷新 → 还原"流程
- [ ] Chrome 95 真机一遍

## 完成标准

- [ ] OrchestrationPage 用户故事闭环完成（`?taskId` 后端任务闭环已接入，任务选择器/e2e 待补）
- [ ] e2e 用例 ≥ 3（创建编辑保存 / 切换 task / 反序列化错误降级）
- [ ] 老 OrchestrationPage 测试用例修正（如有）
- [ ] sprint README 完成标准对应项打勾
