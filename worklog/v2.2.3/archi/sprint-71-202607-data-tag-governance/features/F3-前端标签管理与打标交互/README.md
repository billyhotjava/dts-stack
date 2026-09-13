# F3: 前端标签管理与打标交互

**优先级**: P1
**状态**: READY

## 目标

让数据标签在界面上真正可见可用：标签目录管理、资产详情页打标、按标签筛选三处闭环。

## 落点约定

遵循 Sprint-48/49「以现有页面为第一事实源，默认不新增菜单或页面」治理规则：

| 能力 | 落点 | 方式 |
|------|------|------|
| 标签目录管理 | `pages/catalog/AssetOverviewPage.tsx` | 在数据资产主页面新增「数据标签」Tab，规范深链为 `/catalog/assets?tab=catalog-tags`，**不新增菜单项** |
| 资产打标 | `pages/catalog/AssetDetailPage.tsx`、`DatasetDetailPage.tsx` | 详情页内嵌打标组件 |
| 按标签筛选 | `pages/catalog/DataSearchPage.tsx`、`DatasetsPage.tsx` | 工具栏新增标签筛选器 |

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 数据资产页新增数据标签 Tab | P1 | READY | F2/T01 |
| T02 | 资产详情页打标组件 | P1 | READY | F2/T02 |
| T03 | 资产列表与数据搜索标签筛选 | P1 | READY | F2/T03 |

## 完成标准

- [ ] 三处交互均可用，且未新增任何菜单项
- [ ] 分页遵循项目约定（默认 10 条，切换条数刷新）
- [ ] 有 source-contract 测试与真实浏览器 smoke 证据
