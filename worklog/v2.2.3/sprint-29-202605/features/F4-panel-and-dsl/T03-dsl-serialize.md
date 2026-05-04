# T03: DSL 序列化（画布 → JSON）

**优先级**: P0
**状态**: READY
**依赖**: T02

## 目标

把 store 中的 nodes/edges/viewport 序列化为版本化的 DSL JSON，供后端持久化。Schema 写入 `assets/dsl-schema.json` 作为契约文档。

## 技术设计

### DSL Schema v1.0

```jsonc
{
  "dslVersion": "1.0",
  "metadata": {
    "createdAt": "2026-05-04T10:00:00Z",
    "updatedAt": "2026-05-04T10:00:00Z"
  },
  "nodes": [
    {
      "id": "n_xxx",
      "type": "Start",
      "position": { "x": 0, "y": 0 },
      "data": {
        "trigger": "cron",
        "cron": "0 0 * * *",
        "classification": "internal"
      }
    }
    // 嵌套节点示例（F5）：
    // { "id": "iter_1", "type": "Iteration", "data": { "inputArray": "...", "itemAlias": "row" }, "children": [ ... ] }
  ],
  "edges": [
    { "id": "e_xxx", "source": "n_a", "target": "n_b", "sourceHandle": "out", "targetHandle": "in" }
  ],
  "viewport": { "x": 0, "y": 0, "zoom": 1 }
}
```

### 文件

```
src/components/workflow/utils/dsl/
├── serialize.ts
├── deserialize.ts          # T04
├── schema.ts               # zod schema
└── version.ts              # CURRENT_DSL_VERSION = '1.0'
```

### serialize 实现

```ts
export function serializeDsl(state: WorkflowStoreState): WorkflowDsl {
  return {
    dslVersion: CURRENT_DSL_VERSION,
    metadata: {
      createdAt: state.metadata?.createdAt ?? new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    },
    nodes: state.nodes.map(stripRuntimeFields),
    edges: state.edges.map(({ id, source, target, sourceHandle, targetHandle }) =>
      ({ id, source, target, sourceHandle, targetHandle })
    ),
    viewport: state.viewport,
  };
}

function stripRuntimeFields(node: WorkflowNode): SerializedNode {
  const { isDragging, status, error, ...persistable } = node.data ?? {};
  return {
    id: node.id,
    type: node.type,
    position: node.position,
    data: persistable,
    ...(node.children ? { children: node.children.map(stripRuntimeFields) } : {})
  };
}
```

### Schema 文档

`worklog/v2.2.3/sprint-29-202605/assets/dsl-schema.json` - 完整 JSON Schema，供前后端双方校验依据。

## 影响范围

- 新增 4 个文件
- assets/dsl-schema.json 完整 schema 文档
- 不修改后端（后端字段 T05 处理）

## 验证

- [ ] 序列化输出过滤掉 isDragging / status / error 等运行时字段
- [ ] viewport 完整保留（x / y / zoom）
- [ ] 嵌套子流程节点 children 字段正确（F5 用）
- [ ] 单元测试：含嵌套的 graph 序列化结果与 fixture 一致
- [ ] zod schema 与 JSON Schema 文档一致

## 完成标准

- [ ] CURRENT_DSL_VERSION = '1.0'，未来变更走 migration
- [ ] schema 文档 + 4 个核心 fixture（空 graph / 简单 DAG / 含 Validate 双输出 / 含嵌套 iteration）
- [ ] 单元测试覆盖率 ≥ 90%
