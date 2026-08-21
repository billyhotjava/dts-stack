# Sprint-98：BI 看板发布与可视化编排闭环

**时间**：2026-08-21 ～ 2026-09-04
**状态**：IN_PROGRESS
**类型**：Full-stack / UI Productization / Governed BI
**目标**：看板作者在同一编辑器中完成“选择已发布分析 → 拖放编排 → 编辑组件 → 保存最新草稿 → 选择发布范围 → 校验并发布”，且错误可直接定位和修复。

## 背景与价值

Sprint-95 已接通治理分析、联动和发布，但当前运行态暴露出三个产品缺口：发布校验读取旧的持久化草稿、发布范围仍要求手输编码、存量旧 Card 无可见的替换路径。现有 `react-grid-layout` 虽已支持拖动和缩放，但新增仍以顺序追加和弱提示呈现，用户无法把它识别为可视化编排器。

## 架构决策记录（ADR）

| 决策 | 选择 | 理由与影响 |
|---|---|---|
| ADR-98-01 页面 owner | 复用 `/bi/dashboards/:id/edit` | 不新增菜单、路由或平行看板编辑器 |
| ADR-98-02 发布一致性 | 发布校验前保存当前草稿；校验只针对该次持久化结果 | 禁止页面显示 2 个组件而服务端校验 1 个旧组件 |
| ADR-98-03 受众门禁 | 保留“至少一个部门或角色”，复用 `/api/directory/orgs|roles` | 不用自由文本伪造组织编码，不放宽数据门户可见性 |
| ADR-98-04 卡片边界 | 看板只接收已发布治理分析；历史旧 Card 可读、可替换、不可发布 | 保留历史诊断能力，同时关闭新写绕过 |
| ADR-98-05 布局引擎 | 继续使用 `react-grid-layout@1.5.3` 和 `row/col/size_x/size_y` | 无 schema 迁移，编辑态与消费态同源 |
| ADR-98-06 分析编辑 | 完整查询编辑跳转既有 `/bi/questions/:id/edit` | 看板属性面板只拥有组件级覆盖，不复制分析工作台 |

## 端到端契约链

| 层 | 契约/落点 | 签名要点 |
|---|---|---|
| UI | `DashboardEditorPage` | 分析库拖入/按钮添加、卡片选中/替换、布局属性、发布范围、保存状态 |
| Dashboard API | `POST /bi/api/dashboard/save` | `dashboard{id,...}` + `dashcards[{id?,card_id,row,col,size_x,size_y,...}]`；返回 `ordered_cards` |
| Directory API | `GET /api/directory/orgs`、`GET /api/directory/roles` | 部门使用 `deptCode`，角色使用 `name` |
| Publication API | `POST /bi/api/dashboard/{id}/validate|publish` | `deptCodes[]/roleCodes[]/classification/expiresAt`；返回 blockers/warnings/snapshot |
| Service | `DashboardPublicationService.validateDashboard` | 只接受 `analysis + PUBLISHED + publishedRevisionId + PUBLISHED revision` |
| 数据 | `analytics_dashboard_card`、`analytics_card`、`analytics_revision` | 复用现有表和布局列；本 Sprint 无迁移 |

## 现状勘察账本（Context Ledger）

| # | 事实 | 证据 |
|---|---|---|
| L1 | 当前编辑器已把 RGL 布局回写 `row/col/size_x/size_y` | `DashboardEditorPage.tsx:163-180`、`DashboardEditorGrid.tsx:55-94` |
| L2 | 新组件默认按 `maxBottom` 顺序追加 | `DashboardEditorPage.tsx:200-228` |
| L3 | 卡片头只有参数映射、联动、删除，没有分析编辑/替换 | `DashboardEditorCard.tsx:69-101` |
| L4 | 打开发布抽屉直接调用 validate，不先保存当前页面状态 | `DashboardEditorPage.tsx:372-405` |
| L5 | 发布抽屉把部门/角色作为自由 tags 输入并直接展示英文 blocker | `DashboardEditorPage.tsx:676-765` |
| L6 | 保存接口接受任意正 `card_id`；响应中的嵌套 Card 未返回 type/lifecycle/revision | `DashboardResource.java:324-372,1208-1238` |
| L7 | 运行库实测：2 个有效看板、2 个组件、4 张有效卡片、3 张 analysis，仅 1 张已发布治理分析、1 个组件不合格 | 2026-08-21 G0 只读 SQL；见 `assets/domain-profile.md` |
| L8 | 同时段 `/bi/api/dashboard/2/validate` 返回 200；持久化组件仍指向 `question/DRAFT` | 运行日志 requestId=`3b415880-fd0f-42ff-b901-4941a15941c1` + G0 SQL |
| L9 | 平台已有组织树和角色目录，无需新增目录 API | `DirectoryResource.java:26-41` |
| L10 | `ReportRegistrationService` 同样拒绝空部门+空角色 | `ReportRegistrationService.java:66-86` |
| L11 | RGL 及布局列均已有 Chrome 95 静态兼容证据 | `sprint-12-202604/it/chrome-95-evidence/T01-grid-layout/static-checks.md` |
| L12 | 共享工作区只有 SQL Workbench 用户改动，本 Sprint 不触碰、不暂存、不回滚 | 2026-08-21 `git status --short` |

## Gate Registry

| Gate | 项目 | 状态 | 证据 | 未过则关联 Task |
|---|---|---|---|---|
| G0 | 交付基线 | PASS_WITH_GAPS | `it/baseline.md` | 真实登录态过期、Chrome 95 缺失；F3/T01 |
| G0 | 领域与真实数据画像 | PASS | `assets/domain-profile.md` | - |
| G0 | DTS 不变量 | PASS | ADR-98-01～06 | - |
| G1 | 契约链与 UI 规格 | PASS | 本文、`assets/ui-wireframe.md` | - |
| G1 | 非功能预算 | PASS | `assets/nfr-budget.md` | - |
| G2 | RED→GREEN 与范围守卫 | IN_PROGRESS | 聚焦测试与 GitNexus detect | F1/F2 |
| G3 | 发布安全 | PENDING | `assets/release-plan.md` | F3/T01 |
| G4 | 可运维与 DoD | PENDING | `it/README.md` | F3/T01 |

## Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 |
|---|---|---:|---|---|
| F0 | 交付基线与契约冻结 | 1 | P0 | DONE |
| F1 | 发布一致性与治理修复 | 2 | P0 | IN_PROGRESS |
| F2 | 可视化看板编排 | 2 | P0 | READY |
| F3 | 发布安全与集中验收 | 1 | P0 | DRAFT |

**执行顺序**：F0 → F1/T01 → F1/T02 → F2/T01 → F2/T02 → F3/T01；全部编码结束后集中执行一次 E2E。

## 追溯矩阵

| 需求点 | Feature / Task | 验收证据 |
|---|---|---|
| 发布不再校验旧草稿 | F1/T01 | source/unit + mock E2E 请求顺序 |
| 发布范围可识别且必填 | F1/T01 | 目录 API mock + UI 四态 |
| 旧 Card 可定位替换 | F1/T02 | Java resource test + UI E2E |
| 拖入、移动、缩放并持久化 | F2/T01 | Playwright 坐标/刷新断言 |
| 组件属性与编辑分析入口 | F2/T02 | UI source contract + 页面走查 |
| 发布到数据门户链 | F3/T01 | 运行实例 validate/publish/registration 证据 |

## 完成标准

- [ ] 发布动作保存当前草稿后再校验，服务端组件数与页面一致。
- [ ] 部门/角色来自真实目录，空范围在表单内中文提示，不展示英文错误码。
- [ ] 历史非治理组件显示名称、原因和“替换分析”操作；新绑定不能绕过治理边界。
- [ ] 分析可从左侧资源库拖入 12 列画布，移动/缩放/属性修改刷新后保持。
- [ ] 完整分析编辑继续进入 canonical 分析编辑器。
- [ ] 聚焦测试、构建、mock E2E、目标浏览器检查和运行页面验收有真实证据。

## 非目标

- 不新增看板/分析 schema，不复制 Tableau/FineBI 全部能力。
- 不在看板内复制字段货架、计算或查询编译器。
- 不放宽发布受众、密级、版本钉定和查询预算门禁。
- 不修改数据门户大屏目录、SQL Workbench 或数据建模模块。
