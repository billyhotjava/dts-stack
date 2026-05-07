# T01: IterationNode 迭代子流程节点

**优先级**: P0
**状态**: PARTIAL
**依赖**: F4-T06

## 目标

支持"对一批数据/表/文件循环执行同一子流程"。用户输入数组（如 tableList），子流程内部定义"单条记录的处理路径"。

> 用户场景：100 张分库分表逐一清洗 → 每张表都跑「读 → 标准化 → 写」。

## 技术设计

### 文件

```
src/components/workflow/nodes/iteration/
├── IterationNode.tsx
└── IterationCanvas.tsx        # 内嵌子画布
```

### 节点数据

```ts
type IterationNodeData = {
  inputArray: string;          // 表达式：来自上游某节点的数组输出，如 "$.upstream.tables"
  itemAlias: string;           // 子流程内部引用的别名，如 "row"
  parallel: boolean;           // 是否并发（vs 串行）
  maxParallel?: number;        // 并发上限
  children: WorkflowNode[];    // 嵌套节点
  childEdges: WorkflowEdge[];  // 嵌套边
};
```

### IterationNode 视觉

容器节点：上下两个 handle（in / out）；中间是子画布区域，内嵌 ReactFlow（或共用主 ReactFlow + 视觉 group）。

### 子画布实现思路

复用主 `WorkflowCanvas`，但传入 `subState`（来自 IterationNodeData.children/childEdges）+ 局部 store namespace。或简化版：iteration 子画布只支持非嵌套（一层），用主画布同样组件渲染缩放后的小画布。

> 一期不做"无限深嵌套"——iteration 内不允许再嵌 iteration（YAGNI），强校验阻止。

### 摘要展示

```tsx
<BaseNode nodeId={id} block={iterationBlock}>
  <div className="wf-node-summary">
    迭代：<code>{data.inputArray}</code>
    <div className="muted">{data.children?.length ?? 0} 个子节点 · {data.parallel ? '并发' : '串行'}</div>
  </div>
</BaseNode>
```

### DSL 序列化（已在 F4-T03 设计）

`children` 字段递归序列化即可。

## 影响范围

- 新增 2 个文件
- BLOCKS 配置（F2-T01）追加 Iteration 节点（category=advanced）
- nodeTypes 注册新增 Iteration

## 验证

- [x] 拖出 Iteration 节点显示子流程摘要
- [ ] 双击节点 → 进入子画布编辑模式（或抽屉打开子画布编辑器）
- [ ] 子流程能添加 Source/Transform/Sink 等节点 + 互连
- [x] DSL 保存 + 还原后子流程完整
- [x] 校验：iteration 内不允许再嵌 iteration/loop（前端 schema 拒）
- [x] 单元测试：序列化嵌套 + 反序列化 + 校验拒绝

## 完成标准

- [ ] iteration 节点能在画布展示 + 编辑子流程（展示/配置已完成，子画布编辑待补）
- [x] 子流程 DSL 嵌套结构正确
- [x] 一层嵌套限制清晰提示

## 当前实现说明

- 已新增 `IterationNode`、`IterationForm`、BlockSelector 配置、nodeTypes 注册。
- DSL 已支持 `children` / `childEdges` 顶层字段，并在反序列化时还原到 `config` 供节点摘要使用。
- 本轮未实现内嵌子画布编辑器；当前可通过 DSL 结构保存/还原子流程。
