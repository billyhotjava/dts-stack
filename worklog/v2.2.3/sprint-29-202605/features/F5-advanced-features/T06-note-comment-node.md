# T06: NoteNode 便签节点

**优先级**: P1
**状态**: READY
**依赖**: F3-T01

## 目标

便签节点：纯标注用，不参与 DSL 执行。可换色、可调整大小、可输入富文本（基础 markdown）。便签**不能连线**。

## 技术设计

### 文件

```
src/components/workflow/nodes/note/
├── NoteNode.tsx
└── NoteContent.tsx          # 简单 markdown 编辑器
```

### NoteNode

```tsx
export function NoteNode({ id, data }: NodeProps<NoteNodeData>) {
  const updateNode = useWorkflowStore(s => s.updateNode);
  const [editing, setEditing] = useState(false);

  return (
    <div
      className="wf-note-node"
      style={{ background: data.color ?? '#fef3c7', width: data.width ?? 200, height: data.height ?? 120 }}
      onDoubleClick={() => setEditing(true)}
    >
      {editing ? (
        <NoteContent
          value={data.content ?? ''}
          onChange={(v) => updateNode(id, { data: { ...data, content: v } })}
          onBlur={() => setEditing(false)}
        />
      ) : (
        <ReactMarkdown>{data.content || '双击编辑...'}</ReactMarkdown>
      )}
      <ResizeHandle nodeId={id} />
    </div>
  );
}
```

### 排除执行

- 反序列化时不参与 graph 拓扑（不进 nodeTypes 的执行节点列表，但仍渲染）
- 后端校验 graph_dsl 时跳过 type=Note 的节点
- 不允许从 Note 拖出连线（NodeHandles 中 type=Note 时不渲染 handles）

### 颜色集

```ts
const NOTE_COLORS = ['#fef3c7' /*黄*/, '#dbeafe' /*蓝*/, '#fce7f3' /*粉*/, '#dcfce7' /*绿*/, '#f3e8ff' /*紫*/];
```

右键菜单（T03）中"换色"使用此集合。

## 影响范围

- 新增 2 个文件
- BLOCKS 配置追加 Note（category=basic 或 advanced）
- nodeTypes 注册
- 后端 graph_dsl 校验跳过 Note 节点（在 IngestionTaskService 中）

## 验证

- [ ] 拖出 NoteNode，双击进入编辑模式，markdown 渲染正确
- [ ] 调整大小生效，DSL 中保存 width/height
- [ ] 颜色切换通过右键菜单
- [ ] 不能从 NoteNode 拖出连线
- [ ] DSL 反序列化时 Note 完整还原（位置、大小、颜色、内容）
- [ ] 后端运行时跳过 Note 节点（不当作 ETL 步骤执行）

## 完成标准

- [ ] NoteNode 视觉与"便签纸"接近（圆角、淡阴影）
- [ ] 富文本一期支持加粗/斜体/列表/链接（react-markdown 默认能力）
- [ ] 单元测试：编辑模式切换 + 内容同步 + 颜色切换
