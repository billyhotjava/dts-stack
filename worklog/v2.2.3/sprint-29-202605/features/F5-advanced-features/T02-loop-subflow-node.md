# T02: LoopNode 条件循环子流程节点

**优先级**: P0
**状态**: PARTIAL
**依赖**: F4-T06

## 目标

支持"反复执行子流程直到退出条件成立"。

> 用户场景 1：增量同步 → "拉一批 → 落库 → 还有未同步数据 → 继续"
> 用户场景 2：API 重试 → "调失败 → 等 30s → 重试，最多 5 次"

## 技术设计

### 文件

```
src/components/workflow/nodes/loop/
├── LoopNode.tsx
└── LoopCanvas.tsx
```

### 节点数据

```ts
type LoopNodeData = {
  exitCondition: string;          // 表达式：返回 true 时退出，如 "!response.hasMore"
  maxIterations: number;          // 兜底上限（防死循环），默认 1000
  iterationDelay?: number;        // 每轮间隔毫秒
  retryOnError?: boolean;         // 失败是否计入循环次数
  children: WorkflowNode[];
  childEdges: WorkflowEdge[];
};
```

### LoopNode 视觉

类似 IterationNode，但顶部摘要不同：

```tsx
<BaseNode nodeId={id} block={loopBlock}>
  <div className="wf-node-summary">
    循环：<code>{data.exitCondition}</code>
    <div className="muted">最多 {data.maxIterations} 轮</div>
  </div>
</BaseNode>
```

### 区别 iteration

- iteration：输入是 array，**N 次定长**
- loop：输入是任意，**条件不定长**，需要兜底 maxIterations

### DSL

同 iteration，children + childEdges 字段。

## 影响范围

- 新增 2 个文件
- BLOCKS 配置追加 Loop（category=advanced）
- nodeTypes 注册

## 验证

- [x] 拖出 Loop 节点后能配置 exitCondition + maxIterations
- [x] maxIterations > 0 前端表单约束；exitCondition 必填后端/运行校验待补
- [ ] 子流程可正常编辑
- [x] DSL 序列化/反序列化正确（与 iteration 类似但 type 不同）
- [x] Loop 内不允许再嵌 Loop / Iteration（一期限制，前端 schema 拒）
- [x] 单元测试：节点摘要 + 嵌套序列化/拒绝

## 完成标准

- [x] Loop 节点能描述用户的两个核心场景（增量 / 重试）
- [x] 兜底 maxIterations 强制有值
- [x] 视觉与 iteration 区分（loop 用循环箭头 icon）

## 当前实现说明

- 已新增 `LoopNode`、`LoopForm`、BlockSelector 配置、nodeTypes 注册。
- 已支持 `exitCondition`、`maxIterations`、`iterationDelay`、`retryOnError` 配置。
- 本轮未实现子画布编辑与运行预览态。
