# Sprint-96：客户数据门户消费闭环

**时间**：2026-08-20 ～ 2026-09-04  
**状态**：IN_PROGRESS  
**类型**：Architecture / Full-stack / UI Productization / Governed Consumption  
**目标**：登录用户从唯一“数据门户”入口进入，左侧按主题域浏览自己有权访问的已发布大屏，点击大屏名称后在右侧加载当前发布版本，并可通过稳定 URL 深链、刷新和进入大屏管理。

## 背景与价值

现有 `/bi/screens` 是创作与治理管理页，不是客户消费门户；现有 preview 默认读取草稿；平台 `screen-*` 镜像按小时同步且当前会把未发布大屏登记成可用链接。客户需要的是发布态、受权、可导航的消费入口，而不是把动态大屏逐条写入全局角色菜单。

## 架构决策记录（ADR）

| 决策 | 选择 | 理由与影响 |
|---|---|---|
| ADR-96-01 权威 owner | `dts-analytics` 继续拥有大屏、版本和访问判定 | 不新建大屏目录台账，不受小时级镜像漂移影响 |
| ADR-96-02 门户目录契约 | 扩展 `GET /api/screens`，增加 `publishedOnly:boolean=false` | 服务端保证目录不返回未发布大屏；旧管理调用保持兼容 |
| ADR-96-03 目录层级 | 主题域节点 + 大屏叶子；叶子键为 `screen:{id}` | 当前客户需求可由既有 `domainId` 满足；不引入任意文件夹模型 |
| ADR-96-04 门户路由 | 新增 `/bi/portal` 与 `/bi/portal/:screenId` | 管理页和消费页职责不同，新增页面有明确产品理由 |
| ADR-96-05 运行态复用 | 同源嵌入 `/bi/screens/{id}/preview?mode=published&embed=1` | 复用唯一运行时，隔离画布缩放，不复制 700 行渲染逻辑 |
| ADR-96-06 菜单收敛 | 复用 `sys.nav.portal.biScreens`，原位改为“数据门户”并指向 `/bi/portal` | 保留菜单 ID 与角色可见性；门户内提供“大屏管理”按钮 |
| ADR-96-07 安全 | 目录与详情都重新执行既有 `ScreenPermissionService` 与 classification 门禁 | 叶子不可见不等于授权；详情访问继续 fail-closed |
| ADR-96-08 展示顺序 | 主题域沿治理树顺序，大屏按中文名称稳定排序，未归类单列 | 本 Sprint 不引入可编辑排序/别名/多门户 |

## 端到端契约链（Vertical Slice）

| 层 | 契约/落点 | 签名要点 |
|---|---|---|
| 全局入口 | `sys.nav.portal.biScreens` → `/bi/portal` | 原 menu ID 原位更新，不删除 visibility |
| UI | `DataPortalPage`；`/bi/portal/:screenId?` | 搜索、主题域树、大屏叶子、空/加载/错误/成功四态、管理入口 |
| 目录 API | `GET /bi/api/screens?publishedOnly=true` | 返回 `ScreenListItem[]`；仅 accessible、非归档、currentPublished 存在 |
| 运行 API | `GET /bi/api/screens/{id}?mode=published&fallbackDraft=false` | 无发布版本 409；无权 403；不存在/归档 404 |
| Service | `ScreenPermissionService.listAccessibleScreenIds/snapshot` | 目录预筛 + 详情二次授权，沿用 `read/write/export` 粒度 |
| 数据 | `analytics_screen` + `analytics_screen_version` | `screen.id` 稳定；`current_published=true` 指向消费版本；`domain_id` 仅分组 |
| 迁移 | dts-admin 菜单原位变更 changelog | 只更新 name/metadata 与 seed hash，禁止删除菜单/可见性 |

## 现状勘察账本（Context Ledger）

| # | 事实 | 证据 |
|---|---|---|
| L1 | 管理页已有 accessible 大屏列表、主题域树与 publishedVersionNo | `ScreensPage.tsx:298-372,950-964` |
| L2 | 列表 API 已按 `ScreenPermissionService` 过滤，但逐屏读取发布版本 | `ScreenResource.java:132-171` |
| L3 | 详情 API 已支持 published + fallbackDraft=false | `ScreenResource.java:221-258` |
| L4 | 当前 preview 页面硬编码读取 draft | `ScreenPreviewPage.tsx:134-190` |
| L5 | 大屏运行时由 `ScreenPreviewPage` 复用 `ComponentRenderer`、`ScreenRuntimeProvider`、缩放和轮播 | `ScreenPreviewPage.tsx:484-723` |
| L6 | 静态管理路由为 `/bi/screens`，尚无门户路由 | `static-routes.tsx:655-679` |
| L7 | 全局菜单事实源当前 `sys.nav.portal.biScreens` 指向 `/bi/screens`，标题“数据大屏” | `portal-menu-seed.json:621-628` |
| L8 | 菜单 seed hash 改变可能触发重置，历史安全模式是原位迁移并同步 hash | `20260525-01_portal_menu_data_screen_root.xml` |
| L9 | 本地有 2 个有效大屏、7 个主题域（最大深度 2），2 个大屏均有 domainId 和 SECRET 密级 | 2026-08-20 G0 只读 SQL |
| L10 | 当前发布大屏为 0，但 `bi_report_link` 有 2 条启用 screen preview 镜像 | 2026-08-20 G0 只读 SQL |
| L11 | `analytics_screen_version(screen_id,current_published)` 与 `analytics_screen(domain_id)` 已有索引 | 2026-08-20 `pg_indexes` |
| L12 | Platform/Analytics/UI 运行，UI=200；未登录受保护 health=401 | `it/baseline.md` |
| L13 | Browser-use CLI、真实登录凭据和 Chrome 95 executable 缺失；Chrome 150 可用 | `it/baseline.md` |
| L14 | 共享工作区存在用户未提交的 BI/建模修改，本 Sprint 文件范围独立 | 2026-08-20 `git status --short` |

## Gate Registry

| Gate | 项目 | 状态 | 证据 | 未过则关联 Task |
|---|---|---|---|---|
| G0 | 交付基线 | PASS_WITH_GAPS | `it/baseline.md` | F3/T01 |
| G0 | 领域与真实数据画像 | PASS_WITH_GAPS | `assets/domain-profile.md` | F3/T01 |
| G0 | DTS 不变量 | PASS | ADR-96-01～08 | - |
| G1 | 契约链与 UI 规格 | PASS | 本文与 Feature README | - |
| G1 | 非功能预算 | PASS | `assets/nfr-budget.md` | - |
| G2 | 变更范围与测试先行 | PENDING | RED/GREEN commits + GitNexus staged detect | - |
| G3 | 发布安全 | PENDING | `assets/release-plan.md` | F3/T01 |
| G4 | 可运维与 DoD | PENDING | `assets/runbook.md`、`it/` | F3/T01 |

## Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 |
|---|---|---:|---|---|
| F0 | 交付基线与契约冻结 | 1 | P0 | DONE |
| F1 | 发布态目录契约 | 1 | P0 | READY |
| F2 | 门户消费体验 | 2 | P0 | DRAFT |
| F3 | 集中验收与交付 | 1 | P0 | DRAFT |

**执行顺序**：F0 → F1/T01 → F2/T01 → F2/T02 → F3/T01。

## 追溯矩阵

| 需求点 | Feature / Task | 验收证据 |
|---|---|---|
| 左侧菜单树仅展示有权已发布大屏 | F1/T01、F2/T01 | Analytics IT + portal source/unit + E2E |
| 每个菜单对应一个稳定大屏深链 | F2/T01 | `/bi/portal/:screenId` Playwright |
| 右侧只加载当前发布版本 | F1/T01、F2/T02 | API IT + preview contract + E2E request assertion |
| 全局入口标准化且保留角色绑定 | F2/T02 | dts-admin contract + changelog dry-run |
| 工业级四态与 Chrome 95 兼容 | F2/T01、F3/T01 | desktop/narrow screenshots + console/network evidence |

## 完成标准

- [ ] `publishedOnly=true` 服务端排除草稿并保持旧列表兼容，且列表水合无 N+1。
- [ ] 门户左树、搜索、深链、默认选择及空/加载/错误/成功四态可操作。
- [ ] 右侧请求明确使用 published + fallbackDraft=false，不泄漏草稿。
- [ ] 菜单原位迁移不删除 `portal_menu_visibility`，回滚可恢复标题、路由和 seed hash。
- [ ] 聚焦测试、三个受影响模块构建、Chrome 150 E2E 通过；Chrome 95 缺失则明确保留 GAP。

## 非目标

- 不实现任意目录、拖动排序、菜单别名/图标、多门户/多租户门户配置。
- 不用 public UUID 代替内部授权，不把每个大屏写入 dts-admin 全局菜单。
- 不修复 Workbench/LeaderOverview 的历史 `BiReportLink` 镜像；仅记录为后续一致性缺口。
- 不发布、改名或归档当前运行库中的客户大屏。
