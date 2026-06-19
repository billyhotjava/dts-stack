# T04: 画布状态 store（节点/边/选中态，zustand）

**优先级**: P0
**状态**: READY
**依赖**: S1

## 目标

用 zustand 建立画布单一状态源，持有节点、边、选中态，并暴露受控的画布操作；初始数据来自 mock service。

## 技术设计

- 库：`zustand`（对齐 web patterns：客户端状态用 zustand，不冗余存派生值）。
- 文件：
  - `app/src/canvas/canvasStore.ts`：state = `{ nodes, edges, selectedNodeId }`；actions = `setGraph` / `addNode` / `updateNode` / `connect` / `removeEdge` / `select` / `reset`。
  - `app/src/mock/services/orchestrationService.ts` / `transformService.ts`：`loadGraph(projectId): Promise<Result<{nodes, edges}>>`，返回"销售准备项目"样例图（PLM.订单 + ERP.客户 → 去重/连接 → ODS.宽表）。
- 关键实现点：
  - **不可变更新**：actions 返回新对象（`set((s) => ({ nodes: [...s.nodes, n] }))`），不就地 mutate（对齐 coding-style）。
  - 边/节点形状对齐 reactflow v12 `Node`/`Edge`（与 T01 types 一致）。
  - 选中态只存 `selectedNodeId`，选中节点对象由 selector 派生，不重复存储。
  - 加载经 `Result<T>`：成功 `setGraph`，失败时 store 暴露 error 供 UI 显示（不静默吞错）。
  - demo 节点数控制在 ~12 以内（性能约束）。

## 影响范围

- 文件：`app/src/canvas/canvasStore.ts`、`app/src/mock/services/orchestrationService.ts`、`transformService.ts`
- 状态：`canvasStore`
- 依赖：S1 mock 框架（`Result<T>`/`VITE_USE_MOCK`）；被 T02/T03/F2 全部消费

## 验证

- [ ] store 初始化后调用 `loadGraph` 注入样例，`nodes`/`edges` 填充成功。
- [ ] `addNode` 后 nodes 长度 +1 且原数组未被 mutate（新引用）。
- [ ] `connect` 追加边、`select` 更新 `selectedNodeId`、`reset` 清空。
- [ ] mock 返回 error 分支时 store 暴露错误、UI 可显示，不静默吞错。
- [ ] selectedNode 由 selector 派生，无冗余存储。

## 完成标准

- [ ] zustand store 持有 nodes/edges/selectedNodeId 并暴露全部受控 action。
- [ ] 所有更新为不可变操作。
- [ ] 数据加载经 mock `Result<T>`，含成功/失败两分支。
