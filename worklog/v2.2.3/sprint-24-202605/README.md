# Sprint-24: 大屏密级管理 UX 修复（入口前移 + 列表可见 + 强制设密 + 裸屏盘点）

**时间**: 2026-05
**状态**: IN_PROGRESS（F1-F4 已完成，F5 待评估）
**类型**: UX / Compliance（dts-platform-webapp + dts-analytics + dts-analytics 后端）
**目标**: 把"大屏密级"从一个隐蔽、可漏填、需要专门去找的设置项，改造成进入即可见、创建即必填、漏填可盘点的合规底线能力。

## 背景

Sprint-21 / 大屏密级特性 Step 1-3 把后端密级控制（DashboardAccessGuard、决策树、越级共享、审计）落地完整。但 review 时发现前端 UX 与合规底线存在四个连贯的缺陷：

1. **入口隐蔽** — 密级修改唯一入口埋在编辑器顶栏「分享」按钮 → 弹出 ScreenSharePanel → 顶部「大屏密级」区块。普通用户连开发者本人都很难找到。
2. **列表不可见** — 大屏列表 / 卡片不展示每张大屏的密级，运维想知道"哪些屏没设密级"必须逐个点开。
3. **创建可漏填** — 创建大屏 endpoint 不要求 classification，老路径产生大量 `classification=null` 的"裸屏"。
4. **裸屏不可见** — 由于 `DashboardAccessGuard.hasLevelClearance` 把 `null` classification 视作 PUBLIC（line 202-203），`classification=null` 的大屏对所有登录用户开放，构成合规风险。当前生产中已确认存在此类裸屏（4-23 创建的"项目综合看板" id=60，含真实业务 SQL）。

review 评估这四个问题是相互绑定的：只修入口不强制设密，老裸屏永远不会被回填；只强制设密不暴露盘点，存量裸屏没人发现。所以本 Sprint 一次性修完。

## 范围

- 编辑器属性面板「基础信息」区块顶部新增密级 select（与分享面板共存）。
- 大屏列表卡片显示密级 Tag，`null` 显示明显的「未设密级」警示。
- 创建大屏对话框强制选择密级，前端必填校验 + 后端拒绝 `null/blank`。
- 后端新增 `GET /api/screens/admin/unclassified`：列出所有 `classification IS NULL` 大屏，仅 OP_ADMIN / superuser 可调；前端管理页加「裸屏盘点」入口。
- 老裸屏数据回填策略：不做静默回填（避免误判密级），改为通过盘点入口暴露 + 通知 owner 补登。

## 非目标

- 不改后端 DashboardAccessGuard 决策树（review H1/H2/H3 已修）。
- 不改 Liquibase classification 列约束（保持 nullable，兼容历史数据）；改"前端必填 + 后端 create 时拒 null"，老裸屏走盘点路径。
- 不引入"密级降级二次确认"——这是单独议题，留给下一个 sprint。
- 不动 dts-platform 端 BiReportLink 的 classification（那条线已是创建时必填）。

## Feature 列表

| Feature | Task 数 | 优先级 | 状态 |
|---------|---------|--------|------|
| F1-编辑器属性面板密级入口 | 3 | P0 | DONE |
| F2-列表卡片密级 Tag | 2 | P0 | DONE |
| F3-创建对话框强制选择密级 | 4 | P0 | DONE |
| F4-裸屏盘点入口 | 4 | P1 | DONE |
| F5-降级二次确认（可选） | 3 | P2 | READY |

**统计**: READY=1, IN_PROGRESS=0, DONE=4, BLOCKED=0

## 已交付变更

- 分支：`feat/sprint-24-classification-ux`（4 commits）
  - `88d00b312` F1 ClassificationSelect 共享组件 + 属性面板入口前移 + ScreenSharePanel 改用共享组件
  - `66b57cead` F2 ClassificationTag + 列表表格新增「密级」列 + ScreenListItem type 补 classification 字段
  - `50c1c462c` F3 normalizeRequiredClassification static helper + 7 单测 + 前端 CreateScreenIntakeModal
  - `ef6940268` F4 GET /admin/unclassified 端点 + ScreenAuditService.logCrossScreenEvent + UnclassifiedScreensModal
- F1-F4 累计：5 个新 React 组件、1 个新后端端点、1 个新 Repository 方法、1 个新审计方法、1 个 JUnit 测试类（7 个 case）
- 静态扫描（build-error-resolver）：3 轮 0 阻塞错误

## 验收标准

- 创建一张新大屏：必须选择密级，否则创建按钮 disabled / 后端 400。
- 进入任一已有大屏编辑器：「基础信息」区块顶部能看到当前密级（select 形态），owner 可直接修改无需打开分享面板。
- 大屏列表：每张卡片右上角显示密级 Tag；`null` 显示橙色「未设密级」并附 tooltip 解释合规风险。
- 管理员（OP_ADMIN）能在管理页打开「裸屏盘点」面板，看到所有 `classification IS NULL` 的大屏列表，含 creator / 创建时间 / 上次访问时间。
- 所有改密操作仍写审计 `screen.classification.update`（沿用 Step 3.3 已实现）。
- 老裸屏（如 id=60 的"项目综合看板"）在盘点列表中可见。

## 风险与缓解

- **老裸屏数量未知** → 上线前先跑一次 `SELECT COUNT(*) FROM analytics_screen WHERE classification IS NULL`，评估通知 owner 的工作量。
- **强制必填会卡住"快速保存草稿"工作流** → 创建时给一个合理默认（INTERNAL）作为 placeholder，但仍要求用户 confirm 一次（防止盲点确认）。
- **裸屏盘点端点要求 OP_ADMIN，普通运维看不到** → 跟现有 `/admin/backfill-grants` 端点（`ScreenResource:2418`）一致，后续按需放宽。

## 上下文与依赖

- review 来源：本 sprint 起源于 fix/dashboard-access-h1-h3 PR review 时发现的 UX 缺陷链（参见 v2.2.3 commit 历史 H1/H2/H3 fix）。
- 后端密级控制：依赖 Step 1-3 已落地的 `DashboardAccessGuard` / `ScreenPermissionService` / `PATCH /api/screens/{id}/classification`，本 sprint 仅做 UX 入口前移与边界硬化，不改决策树。
- 涉及模块：`dts-analytics`（后端）、`dts-platform-webapp/src/analytics`（前端）。
