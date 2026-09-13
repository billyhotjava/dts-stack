# T02: CollectionTree 文件夹树组件

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
新建 `CollectionTree.tsx` 组件，实现左侧文件夹树，包含虚拟节点、嵌套文件夹、右键菜单。

## 技术设计

### 文件位置
`source/dts-platform-webapp/src/analytics/components/CollectionTree.tsx`

### Props 接口
```typescript
type CollectionTreeProps = {
    selectedKey: string;                    // 当前选中节点 key
    onSelect: (key: string) => void;        // 选中回调
    onCollectionsChange?: () => void;       // 文件夹增删改后的刷新回调
};
```

### 数据加载
- 调用 `analyticsApi.listCollections()` 获取所有 collection
- 根据 `parent_id` 在前端构建嵌套树结构
- 转换为 antd `<Tree>` 的 `treeData` 格式

### 树节点结构
- 虚拟节点「全部卡片」（key=`__all__`）和「未分类」（key=`__uncategorized__`）固定在顶部
- 分隔线（通过 CSS `border-bottom` 实现）
- 真实 Collection 节点按名称排序，递归嵌套

### 右键菜单（仅真实 Collection 节点）
- 使用 antd `<Dropdown>` + `menu` 触发方式为 `contextMenu`
- 菜单项：新建子文件夹 / 重命名 / 删除
- 新建子文件夹：弹出 `Modal` 输入名称，调用 `createCollection({ name, parent_id })`
- 重命名：行内编辑或弹出 `Modal`，调用 `updateCollection(id, { name })`
- 删除：弹出确认 `Modal`，显示内容统计，提供「移到未分类」/「一并删除」两个选项
  - 移到未分类：先批量 `updateCard(cardId, { collection_id: null })` 再 `deleteCollection`
  - 一并删除：直接 `deleteCollection`（Metabase 后端会级联处理）

### 底部按钮
- 固定在树底部的「+ 新建文件夹」按钮
- 点击弹出 `Modal` 输入名称，`parent_id` 为 null（顶级文件夹）

### 样式
对齐 `DbtFileBrowserPage` 左侧面板样式：
- 容器：`w-64 min-w-[256px] max-w-[280px] border-r border-border bg-card overflow-y-auto flex flex-col`
- 标题区域：`text-xs font-bold uppercase text-muted-foreground mb-2`
- antd Tree 样式覆盖：`[&_.ant-tree-title]:block [&_.ant-tree-title]:whitespace-nowrap`

## 影响范围
- 新建 `source/dts-platform-webapp/src/analytics/components/CollectionTree.tsx`

## 验证
- [ ] 树正确展示虚拟节点和真实 Collection 嵌套结构
- [ ] 单击节点触发 onSelect 回调
- [ ] 右键菜单可新建子文件夹、重命名、删除
- [ ] 删除确认弹窗显示两个选项且逻辑正确
- [ ] 底部新建文件夹按钮可用
- [ ] 样式与 DbtFileBrowserPage 侧边栏一致

## 完成标准
- [ ] 组件可独立渲染，数据自加载
- [ ] 所有交互功能可用
- [ ] Chrome 95 兼容
