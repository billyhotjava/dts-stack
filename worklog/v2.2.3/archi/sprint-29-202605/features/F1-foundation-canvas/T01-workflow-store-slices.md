# T01: 创建 workflow zustand store 三层 slice

**优先级**: P0
**状态**: DONE
**依赖**: F0-T01

## 目标

抄 Dify `web/app/components/workflow/store/` 把 store 拆为 nodes / edges / ui 三层 slice，主入口 `useWorkflowStore` 组合三个 slice。所有更新走 immutable spread，禁止 mutate（项目编码规范硬要求）。

## 技术设计

### 目录

```
src/components/workflow/store/
├── workflow-store.ts          # 入口：组合 + persist 配置
├── nodes-slice.ts             # WorkflowNode CRUD + 位置 + isDragging
├── edges-slice.ts             # WorkflowEdge CRUD + handle 校验
├── ui-slice.ts                # 选中 / 缩放 / panel 开关 / help-line 显隐
└── types.ts                   # WorkflowNode / WorkflowEdge / UiState 共享类型
```

### NodesSlice 接口

```ts
type NodesSlice = {
  nodes: WorkflowNode[];
  setNodes: (nodes: WorkflowNode[]) => void;
  addNode: (node: WorkflowNode) => void;
  updateNode: (id: string, patch: Partial<WorkflowNode>) => void;
  removeNode: (id: string) => void;
  setNodePosition: (id: string, position: XYPosition) => void;
  setNodeDragging: (id: string, isDragging: boolean) => void;
};
```

`updateNode` 实现：

```ts
updateNode: (id, patch) => set(state => ({
  nodes: state.nodes.map(n => n.id === id ? { ...n, ...patch, data: { ...n.data, ...patch.data } } : n)
})),
```

### EdgesSlice 接口

类似 nodes，含 `addEdge`、`removeEdge`、`updateEdge`、handle 兼容性校验工具函数 `canConnect(source, target)`。

### UiSlice 接口

```ts
type UiSlice = {
  selectedNodeId: string | null;
  selectedEdgeId: string | null;
  setSelectedNodeId: (id: string | null) => void;
  setSelectedEdgeId: (id: string | null) => void;
  viewport: { x: number; y: number; zoom: number };
  setViewport: (vp: Viewport) => void;
  panelOpen: boolean;
  setPanelOpen: (open: boolean) => void;
  helpLine: { vertical: number | null; horizontal: number | null };
  setHelpLine: (hl: HelpLineState) => void;
};
```

### 入口组合

```ts
export const useWorkflowStore = create<WorkflowStore>()(
  persist(
    (...a) => ({
      ...createNodesSlice(...a),
      ...createEdgesSlice(...a),
      ...createUiSlice(...a),
    }),
    { name: 'workflow-canvas', partialize: s => ({ viewport: s.viewport }) }
  )
);
```

只持久化 viewport（避免 localStorage 撑大），节点/连线全部走后端 graph_dsl。

## 影响范围

- 新增 `src/components/workflow/store/*.ts` 6 个文件
- 不影响 SemanticModelCanvas / VisualFlowCanvas（独立 store）

## 验证

- [ ] 三层 slice 单测各 ≥ 3 用例（add/update/remove），覆盖率 ≥ 80%
- [ ] `pnpm tsc --noEmit` 无错
- [ ] 任意 mutate 检查：在 dev 下用 `immerable` 或 freeze 包裹 state，发现 mutate 立即抛错

## 完成标准

- [x] 5 个文件 + types 全部就位（types/nodes-slice/edges-slice/ui-slice/workflow-store），行数均 ≤ 90 行
- [x] 22 个 vitest 用例全绿（nodes 7 / edges 8 / ui 7）
- [x] 全仓 `pnpm tsc --noEmit` 零错（已修 xyflow `Node<T>` 约束的 Record 索引签名）
- [x] `WorkflowEdgeData` 与 `WorkflowNodeData` 索引签名兼容 xyflow v12 的 `Record<string, unknown>` 约束
- [x] persist 中间件仅持久化 viewport，节点/连线统一走后端 graph_dsl

## 落地位置

```
source/dts-platform-webapp/src/components/workflow/store/
├── workflow-store.ts            # 入口 + persist + resetWorkflowStoreForTest
├── types.ts                     # WorkflowNode / WorkflowEdge / UiState 共享类型
├── nodes-slice.ts               # NodesSlice + applyNodePatch 工具
├── edges-slice.ts               # EdgesSlice + canConnect 工具
├── ui-slice.ts                  # UiSlice
└── __tests__/
    ├── nodes-slice.test.ts      # 7 用例
    ├── edges-slice.test.ts      # 8 用例
    └── ui-slice.test.ts         # 7 用例
```
