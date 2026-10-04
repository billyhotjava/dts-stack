# Sprint-2: BI 分析卡片文件夹管理

**时间**: 2026-04
**状态**: READY
**目标**: 为 BI 分析卡片页面增加文件夹（Collection）管理功能，支持嵌套文件夹分类、移动卡片、创建/重命名/删除文件夹。

## 背景

当前分析卡片页面（CardsPage）是一个扁平列表，所有卡片混在一起，缺乏组织能力。后端 Metabase 已有 Collection API 支持（`/bi/api/collection`），前端也部分封装了 `listCollections` 和 `getCollectionItems`，但没有完整的文件夹管理 UI。

**现有基础**:
- 后端 API：Metabase 标准 REST（`GET/POST/PUT/DELETE /bi/api/collection`）
- 前端类型：`CollectionListItem`、`CollectionItem`、卡片带 `collection_id` 字段
- 前端 API：已封装 `listCollections`、`getCollectionItems`
- 路由：`/bi/collections`、`/bi/collections/:id` 已注册
- 卡片/看板编辑器：已支持设置 `collection_id`

**缺失部分**:
- 文件夹树形导航 UI
- 创建/重命名/删除 Collection 的 API 封装
- 卡片移动到文件夹的操作 UI

**约束**:
- Chrome 95 兼容（客户离线环境）
- 不使用拖拽交互
- 样式与 platform-webapp 现有页面一致（参照 DbtFileBrowserPage 左右分栏模式）
- 后端 API 不需要改动

## Feature 列表

| ID | Feature | Task 数 | 状态 | 优先级 |
|----|---------|---------|------|--------|
| F1 | 分析卡片文件夹管理 | 4 | READY | P0 |

## 完成标准
- [ ] 分析卡片页面显示左侧文件夹树 + 右侧卡片列表
- [ ] 可创建、重命名、删除文件夹（含嵌套）
- [ ] 删除文件夹时可选择「移到未分类」或「一并删除」
- [ ] 可通过按钮/弹窗将卡片移动到指定文件夹（单个和批量）
- [ ] 左侧树有「全部卡片」和「未分类」虚拟节点
- [ ] Chrome 95 下功能正常
