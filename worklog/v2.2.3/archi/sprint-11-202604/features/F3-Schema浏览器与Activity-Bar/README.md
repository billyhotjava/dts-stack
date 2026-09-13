# F3: Schema 浏览器与 Activity Bar

**优先级**: P0
**状态**: READY

## 目标

实现最左 Activity Bar（icon 导航栏）与可展开的左侧面板，核心是高性能 Schema 树浏览（惰性加载、虚拟滚动）。包含 History 和 Saved Query 面板的新版实现。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T11 | Catalog 惰性加载 API | P0 | READY | F1 |
| T12 | Activity Bar + SidePanel 布局 | P0 | READY | F1 |
| T13 | SchemaTree（react-arborist 虚拟树 + 右键菜单 + hover 卡片） | P0 | READY | T11, T12 |
| T14 | HistoryPanel（复用 query_execution） | P1 | READY | T12 |
| T15 | SavedPanel（saved_query + folder 分组） | P1 | READY | T12 |

## 完成标准

- [ ] 一万张表的 schema 树渲染无卡顿
- [ ] 展开节点响应 ≤300ms（命中缓存 ≤50ms）
- [ ] 双击表名插入 `schema.table` 到编辑器
- [ ] 右键表名菜单 4 项（SELECT/INSERT/DDL/Copy）全部生效
- [ ] History 面板双击可新开 Tab 填入 SQL
- [ ] Saved 面板支持按文件夹分组
- [ ] Schema 元数据访问审计埋点（`SQL_CATALOG_BROWSE`）
