# F1：统一建模工作台壳层

**优先级**：P0  
**状态**：READY

## 目标

在不新增菜单和业务状态源的前提下，把原型七模块组织为 `/modeling/workbench` 内部工作区。

## 契约

| 类型 | 契约 | 要点 |
|---|---|---|
| URL | `planId,module,assetKind,assetId` | URL 是视图上下文，刷新/分享可恢复 |
| Shell | `ModelingWorkspaceShell` | 只拥有导航与布局，不缓存业务事实 |
| 面板 | `WorkspacePanelProps` | `{planId:string, assetId?:string, onNavigate(target)}` |
| 旧深链 | plans/dimensions/models/metrics | 先保留，功能等价后带参数重定向 |

## UI/UX

```text
┌ 首页｜数仓规划｜数据标准｜维度建模｜数据指标｜通用工具｜关系图 ┐
├ 计划/分层/域 ┬ 对象树 ┬──────────────────────────────┤
│ 共享上下文   │ 搜索＋ │ 当前对象编辑画布 / 空态        │
│              │ 资产   │ 工具栏：保存 校验 发布 日志    │
└──────────────┴────────┴──────────────────────────────┘
```

- 顶部是页面内模块，不进入 portal menu。
- 切换模块只加载当前模块数据；计划上下文保持。
- 空态只给一个主要动作；错误态保留 plan/module 并提供重试。

## Tasks

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 建立 Shell 与 URL 状态 | READY | F0/T01 认证子门禁 |
| T02 | 嵌入规划与标准面板 | DRAFT | T01 |

## Definition of Ready

- [x] URL、组件边界和菜单策略已冻结。
- [x] 认证 UI 基线已恢复；系统 Chrome 150 正式页面 smoke 通过。
- [ ] 被抽取页面的 GitNexus impact 完成。

## 完成标准

- [ ] 七模块可在同页切换且无新增业务菜单。
- [ ] plan/module/asset 刷新恢复，四态可验收。
- [ ] 旧页面的业务组件被复用而非 iframe/整页嵌套。
