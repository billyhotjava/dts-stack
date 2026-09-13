# T13: SchemaTree（react-arborist 虚拟树 + 右键菜单 + hover 卡片）

**优先级**: P0
**状态**: READY
**依赖**: T11, T12

## 目标

实现高性能 Schema 浏览：虚拟滚动树、惰性加载子节点、hover 表详情卡、右键菜单快速生成 SQL。

## 技术设计

### 依赖

```
npm i react-arborist@^3
```

### 层级

```
DataSource (顶部 Select，非树节点)
└─ Catalog (Trino 有、Hive/PG 可选)
   └─ Schema
      └─ Table/View
         └─ (子节点列表，hover 卡片显示)
```

### 惰性加载

```typescript
const loadChildren = async (node: TreeNode) => {
  switch (node.kind) {
    case 'schema': return await fetchTables(node.datasourceId, node.name);
    case 'table':  return await fetchColumns(...);
  }
};
```

- 节点首次展开时触发 fetch，loading 用 skeleton
- 展开状态保存到 `useTabStore` 当前 Tab 的 `schemaContext`

### Hover 表详情卡

- 500ms hover 后浮出，含：列清单、类型、注释、行数估计
- 鼠标移出 200ms 隐藏
- 用 Ant Design `Popover`

### 右键菜单

| 菜单项 | 动作 |
|---|---|
| `Generate SELECT` | 插入 `SELECT col1, col2, ... FROM schema.table LIMIT 100` |
| `Generate INSERT` | 插入 `INSERT INTO schema.table (cols) VALUES (?)` 模板 |
| `Show DDL` | 弹对话框显示 `SHOW CREATE TABLE` 结果 |
| `Copy Name` | 复制全限定名 `schema.table` |

列级右键：

| 菜单项 | 动作 |
|---|---|
| `Copy Name` | 复制列名 |
| `Filter by This` | 插入 `WHERE col = ` 到光标 |
| `Use in ORDER BY` | 插入 `ORDER BY col` |

简洁模式只保留 SELECT 和 Copy 两项。

### 搜索框

- 面板顶部搜索框，300ms 防抖调 `/catalog/{ds}/search`
- 匹配结果高亮，点击跳到树节点

## 影响范围

- 新增 `schema/SchemaTree.tsx`
- 新增 `schema/TableDetail.tsx`（hover 卡片）
- 新增 `schema/ContextMenu.tsx`

## 验证

- [ ] 一万表 schema 展开无卡顿（虚拟滚动只渲染可视区）
- [ ] 双击表名插入到编辑器当前光标
- [ ] 右键 4 个菜单项全部生效
- [ ] hover 500ms 卡片出现
- [ ] 搜索框输入实时匹配
- [ ] 简洁模式菜单只 2 项

## 完成标准

- [ ] 虚拟滚动实测 10k+ 节点流畅
- [ ] 手动测试右键/hover/搜索全部通过
