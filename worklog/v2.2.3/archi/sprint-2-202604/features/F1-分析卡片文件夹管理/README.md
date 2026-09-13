# F1: 分析卡片文件夹管理

**优先级**: P0
**状态**: READY

## 目标
在分析卡片页面（CardsPage）增加左侧文件夹树，支持嵌套文件夹对卡片进行分类管理。

## 设计概要

### 页面布局
改造现有 `CardsPage.tsx`，采用左右分栏布局（对齐 DbtFileBrowserPage 风格）：
- 左侧：文件夹树（w-64），包含虚拟节点「全部卡片」「未分类」+ 真实 Collection 树
- 右侧：现有卡片表格，保留搜索、批量导入、批量删除等全部现有功能

### 文件夹树节点
```
📋 全部卡片          ← 虚拟节点 key="__all__"
📋 未分类            ← 虚拟节点 key="__uncategorized__"
───────────────
📁 项目管理          ← 真实 collection，可嵌套
  📁 周报
  📁 月报
📁 技术分析
───────────────
[+ 新建文件夹]       ← 底部按钮
```

### 交互
- 单击树节点 → 右侧按 `collection_id` 过滤卡片
- 右键菜单（仅真实文件夹）→ 重命名 / 新建子文件夹 / 删除
- 删除确认弹窗 → 二选一：移到未分类 / 一并删除
- 操作列新增「移动」→ 弹出 MoveToCollectionModal 选择目标
- 批量选中后工具栏新增「移动到...」

### 无拖拽
所有移动操作通过按钮和右键菜单完成，不使用 dnd-kit。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | analyticsApi 补充 Collection CRUD | P0 | READY | - |
| T02 | CollectionTree 文件夹树组件 | P0 | READY | T01 |
| T03 | MoveToCollectionModal 移动弹窗 | P0 | READY | T01 |
| T04 | CardsPage 集成改造 | P0 | READY | T02, T03 |

## 完成标准
- [ ] 文件夹树正常展示，支持嵌套
- [ ] 文件夹 CRUD 操作可用
- [ ] 卡片单个/批量移动到文件夹可用
- [ ] 样式与 DbtFileBrowserPage 一致
- [ ] Chrome 95 兼容
