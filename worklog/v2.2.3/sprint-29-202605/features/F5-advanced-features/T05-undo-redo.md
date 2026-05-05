# T05: 撤销/重做（最多 50 步历史）

**优先级**: P0
**状态**: PARTIAL
**依赖**: F1-T01

## 目标

为 nodes/edges 操作建立历史栈，支持撤销重做。最多保存 50 步，超出 FIFO 丢弃。

## 技术设计

### 选型

**优先 zundo**（zustand 官方时间旅行 middleware，~1KB），与现有 zustand 4.5.x 兼容；如不兼容则自实现 history slice。

本轮采用自实现 `history-slice`，避免新增依赖和安装网络风险。

### zundo 集成

```ts
import { temporal } from 'zundo';

export const useWorkflowStore = create<WorkflowStore>()(
  temporal(
    persist(
      (...a) => ({
        ...createNodesSlice(...a),
        ...createEdgesSlice(...a),
        ...createUiSlice(...a),
      }),
      { name: 'workflow-canvas', partialize: s => ({ viewport: s.viewport }) }
    ),
    {
      limit: 50,
      partialize: (state) => ({ nodes: state.nodes, edges: state.edges }), // 只追踪 nodes/edges，不追踪 ui state
      equality: (a, b) => isEqual(a.nodes, b.nodes) && isEqual(a.edges, b.edges),
    }
  )
);

// 用法
const undo = useWorkflowStore.temporal.getState().undo;
const redo = useWorkflowStore.temporal.getState().redo;
```

### 节流入栈

频繁操作（拖动节点）每 300ms 入一次栈，避免 50 步全被一次拖动占满：

```ts
import { throttle } from 'lodash';
const throttledRecord = throttle(() => useWorkflowStore.temporal.getState().pause(), 300);
```

或 zundo `throttle: 300` 选项。

### Operator 工具栏接通

T06 F1 中占位的撤销/重做按钮启用 + disabled state：

```tsx
const canUndo = useWorkflowStore.temporal.getState().pastStates.length > 0;
const canRedo = useWorkflowStore.temporal.getState().futureStates.length > 0;
```

### 持久化排除

- 不持久化历史栈到 localStorage（刷新即清）
- 切换 task 时清空历史栈：`useWorkflowStore.temporal.getState().clear()`

## 影响范围

- 修改 `workflow-store.ts` 接 zundo
- 新增依赖 zundo（如未安装）
- 修改 Operator UndoRedoButtons 启用
- 修改 useShortcuts（T04）调用 undo/redo

## 验证

- [x] 增删节点/边后 Ctrl+Z 撤销，Ctrl+Shift+Z 重做
- [ ] 拖动节点节流：连续拖 5 秒只占 ~16 步而非 ~300 步
- [x] 50 步上限：超出后最早的丢弃
- [x] 切换/重置 task 清空历史；DSL 初始加载 pause + clear
- [x] Operator 按钮状态正确（无可撤销时 disabled）
- [x] DSL 反序列化时不入栈（避免初始加载占用栈）

## 完成标准

- [x] 自实现 history slice 与 persist 中间件正常组合
- [ ] 单元测试：基础 undo/redo + 节流 + 上限 + 清空（节流待补）
- [ ] 性能：50 步历史的 graph 切换不卡顿

## 当前实现说明

- 新增 `store/history-slice.ts`，只记录 nodes/edges 快照，最多 50 步，不进入 localStorage。
- `UndoRedoButtons` 已从占位按钮切换为真实 store action。
- `WorkflowCanvas` 初始 DSL / initialNodes 加载时暂停历史并清空，避免初始状态占用撤销栈。
