# T02: 6 类节点的配置表单

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

每个节点类型一个 FormComponent，挂在 NodePanel 中。表单字段实时同步到 store（使用 react-hook-form + zod schema 校验）。

## 技术设计

### 文件

```
src/components/workflow/panel/forms/
├── StartForm.tsx
├── SourceForm.tsx
├── TransformForm.tsx
├── ValidateForm.tsx
├── SinkForm.tsx
├── EndForm.tsx
├── schemas.ts                 # zod schemas
└── index.ts                   # NODE_FORMS map
```

### NODE_FORMS

```ts
export const NODE_FORMS: Record<WorkflowNodeType, React.FC<{ nodeId: string }>> = {
  Start: StartForm,
  Source: SourceForm,
  Transform: TransformForm,
  Validate: ValidateForm,
  Sink: SinkForm,
  End: EndForm,
};
```

### 实时同步

```tsx
function StartForm({ nodeId }: Props) {
  const node = useWorkflowStore(s => s.nodes.find(n => n.id === nodeId));
  const updateNode = useWorkflowStore(s => s.updateNode);
  const { control, watch } = useForm({ defaultValues: node?.data, resolver: zodResolver(startSchema) });

  // 表单变更 → 节流 200ms 同步 store
  useEffect(() => {
    const sub = watch(values => {
      updateNode(nodeId, { data: { ...node?.data, ...values } });
    });
    return () => sub.unsubscribe();
  }, [nodeId]);

  return (
    <Controller name="trigger" control={control} render={({ field }) => (
      <Select {...field}>
        <Option value="manual">手动</Option>
        <Option value="cron">Cron 定时</Option>
        <Option value="event">事件触发</Option>
      </Select>
    )} />
  );
}
```

### TransformForm 嵌入 SQL IDE

```tsx
function TransformForm({ nodeId }: Props) {
  // ... language Select + Monaco/CodeMirror code editor
}
```

### ValidateForm 规则集编辑

ValidateRule[] 编辑器：每行一条规则（type / column / params），支持新增/删除/排序。

## 影响范围

- 新增 8 个文件（每文件 ≤ 200 行）
- 引入 zod 用于 schema（项目已有 yup？需 grep 确认；若无 zod 添加 ~12KB）

## 验证

- [ ] 6 类节点选中后均能正常显示对应 Form
- [ ] 表单变更 200ms 内反映到节点摘要
- [ ] zod 校验失败时表单 highlight
- [ ] TransformForm 的 SQL IDE 正常工作（高亮、行号、补全）
- [ ] ValidateForm 规则增删改顺序操作正常

## 完成标准

- [ ] 表单 → store → 节点摘要 三段联动正常
- [ ] 单元测试：每类 Form 至少 1 用例验证字段同步
