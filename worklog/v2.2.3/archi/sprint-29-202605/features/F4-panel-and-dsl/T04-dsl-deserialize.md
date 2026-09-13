# T04: DSL 反序列化（JSON → 画布）+ 幂等性

**优先级**: P0
**状态**: DONE
**依赖**: T03

## 目标

接收后端 DSL JSON，校验 schema，恢复成 store state。保证 `serialize(deserialize(dsl)) === dsl` 幂等。

## 技术设计

### deserialize 实现

```ts
export function deserializeDsl(dsl: unknown): DeserializeResult {
  const parsed = workflowDslSchema.safeParse(dsl);
  if (!parsed.success) {
    return { success: false, error: parsed.error };
  }
  const data = parsed.data;

  // 版本兼容：未来 dslVersion > CURRENT 时走 migration 链
  if (data.dslVersion !== CURRENT_DSL_VERSION) {
    const migrated = migrateDsl(data);
    return deserializeDsl(migrated);
  }

  return {
    success: true,
    state: {
      nodes: data.nodes.map(rebuildNode),
      edges: data.edges,
      viewport: data.viewport,
      metadata: data.metadata,
    }
  };
}

function rebuildNode(serialized: SerializedNode): WorkflowNode {
  return {
    id: serialized.id,
    type: serialized.type,
    position: serialized.position,
    data: { ...serialized.data, isDragging: false },     // 注入运行时默认
    ...(serialized.children ? { children: serialized.children.map(rebuildNode) } : {}),
  };
}
```

### 幂等性测试

```ts
test('serialize-deserialize 幂等', () => {
  for (const fixture of FIXTURES) {
    const dsl = JSON.parse(JSON.stringify(fixture));
    const restored = deserializeDsl(dsl);
    expect(restored.success).toBe(true);
    const reSerialized = serializeDsl(restored.state);
    expect(reSerialized.nodes).toEqual(dsl.nodes);
    expect(reSerialized.edges).toEqual(dsl.edges);
    expect(reSerialized.viewport).toEqual(dsl.viewport);
  }
});
```

### 版本迁移占位

```ts
// migrate.ts
export function migrateDsl(dsl: any): any {
  // 未来 v1.0 → v1.1 在此添加 transformer
  throw new Error(`Unsupported DSL version: ${dsl.dslVersion}`);
}
```

## 影响范围

- 新增 `deserialize.ts` 与 `migrate.ts`
- 修改 utils/dsl/index.ts 导出

## 验证

- [x] fixture 反序列化成功
- [x] schema 校验失败时返回 success: false + 详细错误
- [x] 未知 dslVersion 返回错误，不静默吞
- [x] 幂等性单测全过
- [ ] 反序列化恢复后画布渲染与原 graph 视觉一致（待 T06 接入后冒烟）

## 完成标准

- [x] 单元测试覆盖核心路径
- [x] 错误信息对人类友好（含路径）
