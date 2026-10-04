# T02: SemanticObjectsPage 真实实现（含 join 画布）

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

替换 `SemanticObjectsPage.tsx` 重定向壳，实现业务对象管理 + 表映射 join 关系的可视化画布。

## 技术设计

```tsx
// 替换 src/pages/modeling/SemanticObjectsPage.tsx
// 左侧: 业务对象列表（CompactTable）
//   列: code, name, description, mainTable, primaryKey
//   [新建] → createSemanticBusinessObject(data)
//   行选中 → 右侧展示 join 画布

// 右侧: VisualFlowCanvas (read-only join 图)
//   数据来源: listSemanticObjectTableMappings(objectId)
//   每个 mapping → 一个 TableNode（tableName + tableRole badge）
//   mapping.joinExpression 非空 → JoinEdge（label 显示 joinExpression 前 30 字）
//   [编辑表映射] 按钮 → antd Drawer 编辑 mappings
//     [保存] → saveSemanticObjectTableMappings(objectId, mappings)

// data-testid: "semantic-objects-page", "semantic-objects-create"
```

路由：`path: "modeling/semantic/objects"` → `<SemanticObjectsPage />`

## 影响范围

- 覆盖：`src/pages/modeling/SemanticObjectsPage.tsx`（从 6 行壳扩展为 ~250 行）
- 修改：`static-routes.tsx`, `dynamic-resolver.tsx`

## 验证

- [ ] 左侧业务对象列表可见
- [ ] 选中对象后右侧 VisualFlowCanvas 渲染 table mappings（空态不崩溃）
- [ ] [编辑表映射] Drawer 可提交
- [ ] 页面不含 `window.location.replace`
- [ ] tsc 零报错

## 完成标准

- [ ] `data-testid="semantic-objects-page"` 存在
- [ ] join 画布（VisualFlowCanvas）在有 mappings 时渲染节点
