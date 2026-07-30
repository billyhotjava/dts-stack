# Sprint-79：智能数据建模工作台收敛

**时间**：2026-07  
**状态**：IN_PROGRESS（F1 七模块壳层已部署并通过 IT-01 空数据旅程；F2–F5、代表数据、物化和退役门禁继续按 Feature 控制）
**类型**：Architecture / UI Productization / Controlled Retirement / Full-stack  
**目标**：用户在一个建模工作台内完成规划、标准、维度、四类逻辑模型、指标、关系查看以及发布/物化交接，不再在多组解释性页面和重复入口之间切换。

## 背景与价值

`worklog/prototype/dataworks-kimball` 已验证“模块导航 + 对象树 + 单页编辑器 + 短工具栏”的低摩擦交互。DTS 现有实现已经拥有 WarehousePlan、DimensionDefinition、ModelSpec、Indicator、ReleaseCandidate、dbt/Airflow 和 Catalog 物理资产主链，但前端能力分散在多个页面，模型详情仍以三个解释性阶段呈现。

本 Sprint 是界面与旅程收敛，不是重写建模内核。原型只提供信息架构和交互参考；正式数据、权限、状态机和发布事实继续由 DTS canonical owner 持有。

## 架构决策记录（ADR）

| ID | 决策 | 理由 | 影响 |
|---|---|---|---|
| ADR-79-01 | `/modeling/workbench` 是唯一主入口；原型七个一级模块变为工作台内部 Tab，不新增业务菜单 | 遵守“现有页面第一事实源”，减少跳转和菜单层级 | 保留原深链兼容；菜单最终只保留工作台与高级 SQL/dbt |
| ADR-79-02 | 原型 HTML/CSS/JS 不进入产品运行时，仅作为 UI 规格和验收参考 | 防止形成第二套模型状态和 API 模拟层 | 正式组件从现有 DTS 页面抽取、组合 |
| ADR-79-03 | WarehousePlan、DimensionDefinition、ModelSpec、Indicator、ReleaseCandidate、CatalogAssetKey 继续作为唯一 owner | 现有服务已被规划、导入、指标和发布链复用 | 不新建模型、指标、运行、发布或图谱台账 |
| ADR-79-04 | 模型详情改为“对象树 + 单页编辑器”；三个阶段门禁保留为后台状态和顶部进度，不再拆成三张解释页 | 降低填写成本但不削弱 DRAFT_SAVE、IMPLEMENTATION_READY、RELEASE_READY | 复用 stage-gates API；重组而非绕过门禁 |
| ADR-79-05 | “业务维度”落到 DimensionDefinition；维度表/明细表/汇总表/应用表落到四类 ModelSpec；“贴源表”改为来源注册/逆向候选，不新增第五类 ModelSpec | 保持 DTS 四类表契约和 ODS 不可变边界 | 原型六类新建菜单按 DTS 语义重新映射 |
| ADR-79-06 | 发布短流程只编排 Build Intent、Publish Intent 和 Candidate 工作台；不得自动替代 reviewer/operator | 发布与物化存在职责分离和真实关系核验 | 快捷弹窗可缩短路径，但不会一键越权发布 |
| ADR-79-07 | 删除采用“孤儿代码立即移除、在用页面先抽组件、旧路由两版本零访问后移除、旧表另行审批”的四级策略 | 当前本地旧表虽为 0 行，但不能代表客户环境 | 删除项统一进入退役登记和可回滚门禁 |
| ADR-79-08 | 改造不得继续增长超过 800 行的页面；先抽工作台 Shell、面板和命令组件 | 当前 `SqlModelingPage`、计划详情、模型详情已有明显维护风险 | Feature 内先拆分再嵌入，禁止在巨型页直接堆功能 |

## 端到端契约链（Vertical Slice）

| 层 | 契约/落点 | 签名要点 |
|---|---|---|
| UI 主入口 | `/modeling/workbench?planId={uuid}&module={home\|planning\|standards\|models\|metrics\|tools\|graph}&assetKind={kind}&assetId={id}` | 顶部模块、左侧对象树、右侧编辑画布共享同一 `planId`；刷新可恢复上下文 |
| 规划 API | `/api/modeling/warehouse-plans/**` | 复用计划 header、baseline、sources、policy、stage-projection 与 model-candidates；写操作保留 If-Match |
| 维度 API | `/api/modeling/dimension-definitions/**` | 系统编码首次保存生成；CURRENT revision 才可被模型引用 |
| 模型 API | `/api/modeling/model-specs/**` | 复用 create/update/stage-gates/dependencies；ModelSpec 四类表和 CAS 不变 |
| 指标 API | `/api/governance/indicators/**` | 复用原子/派生指标、版本、发布和 ModelSpec 字段引用 |
| 发布/物化 API | `POST .../{id}/build-intents`、`POST .../{id}/publish-intents`、`/api/modeling/plans/{planId}/release-candidates/**` | Build/Publish Intent 只编排 canonical Candidate；If-Match + Idempotency-Key 不变 |
| 关系图投影 | `GET /api/modeling/warehouse-plans/{planId}/relationship-graph` | 只读聚合投影：`nodes[{id,kind,label,status,route}]`、`edges[{source,target,kind,label}]`；不落新图谱表 |
| 兼容访问观测 | `POST /api/modeling/compatibility-usage` | allowlist body：`{route,target,result:REDIRECT|RECOVERY}`；复用 `modeling_legacy_api_usage`，不接收 tenant/caller |
| 数据 | 既有 `modeling_warehouse_plan`、`modeling_dimension_definition`、`modeling_model_spec*`、`gov_indicator_*`、`modeling_model_release_candidate`、`catalog_dataset` | 不新增业务表；任何删除均先 dry-run、零消费者和回滚演练 |
| 菜单/迁移 | dts-admin Liquibase 菜单事实源 | 不增加一级菜单；旧菜单继续软删除，最终删除路由前保留原 menu id/role binding 回滚证据 |

## 现状勘察账本（Context Ledger）

| # | 事实 | 证据 |
|---|---|---|
| CL-01 | 原型明确采用对象树、单页编辑器、七模块内导航和发布/物化短流程 | `worklog/prototype/dataworks-kimball/README.md:19`、`:35` |
| CL-02 | 原型要求系统生成维度编码，属性类型只在模型字段维护，主键由字段映射表达 | `worklog/prototype/SCREEN-INVENTORY.md:51` |
| CL-03 | 当前正式建模能力分布在 workbench、plans、dimensions、models、metric-workbench 和 sql-modeling 路由 | `source/dts-platform-webapp/src/routes/sections/dashboard/static-routes.tsx:395`、`:411`、`:419`、`:435`、`:443`、`:459` |
| CL-04 | 当前菜单仍将规划、标准、维度建模和指标作为建模菜单的多组子项 | `source/dts-platform-webapp/src/routes/sections/dashboard/portalGoldenLineMenu.source-contract.test.ts:110` |
| CL-05 | 8 条旧路由已经统一进入兼容页并映射到 canonical 页面 | `source/dts-platform-webapp/src/pages/modeling/modelingCompatibilityRoute.ts:3`、`:82` |
| CL-06 | ModelSpec 详情仍显式装配 logical、implementation、physical 三个阶段组件 | `source/dts-platform-webapp/src/pages/modeling/ModelSpecDetailPage.tsx:880`、`:902`、`:945` |
| CL-07 | 规划 API 已具备 header、baseline、sources、policy、stage projection 和候选确认 | `source/dts-platform-webapp/src/api/warehousePlanApi.ts:239`、`:272`、`:297`、`:319`、`:337`、`:342` |
| CL-08 | ModelSpec API 已具备 CRUD、门禁、依赖、实现迁移和 CAS | `source/dts-platform-webapp/src/api/modelSpecApi.ts:193`、`:208`、`:235`、`:261` |
| CL-09 | 发布快捷入口和 Candidate 工作台契约已经存在 | `source/dts-platform-webapp/src/api/modelSpecApi.ts:788`、`:817`、`:832` |
| CL-10 | `ModelSpecApplicationService` 影响评估为 MEDIUM：14 个直接依赖、21 个总影响点 | GitNexus `impact(ModelSpecApplicationService, upstream)`，2026-07-30 |
| CL-11 | 当前 HEAD 为 `857c5cf45`，工作树在立项时干净并与 `origin/v2.2.3` 一致 | `git status --short --branch`、`git log -1`，2026-07-30 |
| CL-12 | 当前本地库：4 个 DRAFT 计划、7 个 CURRENT 维度、6 个 ModelSpec/10 个 revision；实现、Candidate、物理关系 observation 均为 0 | `assets/domain-profile.md` §3 |
| CL-13 | 本地旧业务对象、旧 SQL 模型、旧语义模型/维度均为 0，近 30 天 legacy API usage 为 0 | `assets/domain-profile.md` §3；只能支持本地退役判断，不能外推客户环境 |
| CL-14 | `DbtFileBrowserPage.tsx` 和旧注册 helper 已按 Batch A 删除；`ModelTemplatesPage`、`ModelPipeline` 仍分别被项目空间和 SQL 建模使用 | `it/evidence/IT-07/`，2026-07-30 |
| CL-15 | GitNexus 索引停留在 `4762dc9b9`、落后 HEAD 2 个提交；最新原型/UI 只以 HEAD 源码为准 | GitNexus `list_repos`，2026-07-30 |

**开放问题**

- 客户环境的存量模型数量、旧路由访问和旧表数据未画像；F5 删除门禁不得使用本地 0 行结论替代。
- 正式 DTS 的认证 UI/API 基线已于 2026-07-30 通过系统 Chrome 150 复验；Chrome 95 兼容仍需在 F1 实现后补证据。
- Sprint-76 生产物化链仍为 PROD NO-GO；本 Sprint 只消费其已冻结契约，不声称修复其生产缺口。

## Gate Registry

| Gate | 项目 | 状态 | 证据 | 未过则关联 Task |
|---|---|---|---|---|
| G0 | 交付基线 | PASS_WITH_GAPS | `it/baseline.md`；认证/API/UI 已通过，代表数据仍不足 | F0/T01 |
| G0 | 领域与数据画像 | PASS_WITH_GAPS | `assets/domain-profile.md` | F0/T02 |
| G0 | DTS 领域不变量 | PASS | ADR-79-01～08 | - |
| G1 | 契约链贯通 | PASS | 本文“端到端契约链” | - |
| G1 | 非功能预算 | PASS_WITH_GAPS | `assets/nfr-budget.md` | F0/T01、F3/T02 |
| G3 | 发布安全 | PASS_WITH_GAPS | F1 前端回滚演练 PASS；F4/F5 仍见 `assets/release-plan.md` | F4/T02、F5/T02 |
| G4 | 可运维性 | PENDING | 后续 `assets/runbook.md` | F0/T02 |
| G4 | DoD 验收 | PENDING | `it/README.md` | 全部 Feature |

## Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 |
|---|---|---:|---|---|
| F0 | 交付基线与退役证据 | 3 | P0 | IN_PROGRESS |
| F1 | 统一建模工作台壳层 | 2 | P0 | PASS_WITH_GAPS |
| F2 | 单页模型编辑器 | 3 | P0 | DRAFT |
| F3 | 指标、工具与关系图 | 2 | P1 | DRAFT |
| F4 | 发布物化短流程 | 2 | P0 | DRAFT |
| F5 | 旧页面受控退役 | 2 | P0 | DRAFT |

**依赖顺序**：F0 → F1 → F2/F3 → F4 → F5。F2 与 F3 可在壳层契约冻结后并行；F5 只能在功能等价和观测期满足后执行。

## 追溯矩阵

| 需求点 | Feature/Task | 测试 | 验收证据 |
|---|---|---|---|
| 原型全部进入 DTS 而不复制内核 | F1/T01、F1/T02 | workspace route/source-contract | `it/IT-01` |
| 对象树 + 单页编辑器 | F2/T01、F2/T02 | editor state/field mapping contract | `it/IT-02`、`IT-03` |
| 指标、工具、关系图可达 | F3/T01、F3/T02 | indicator owner + graph projection IT | `it/IT-04` |
| 发布与物化短流程 | F4/T01、F4/T02 | intent/candidate contract + real DEV build | `it/IT-05`、`IT-06` |
| 不能复用的旧页面删除 | F0/T03、F5/T01、F5/T02 | orphan/import/route/usage guards | `it/IT-07`、`IT-08` |

## 完成标准

- [ ] 用户从 `/modeling/workbench` 完成财务 Demo 和项目 Demo 的规划 → 维度/模型 → 指标 → 发布交接，不依赖旧页面菜单。
- [ ] 工作台刷新保持 `planId/module/asset`，空、加载、错误、成功四态有 Chrome 95 证据。
- [ ] Build/Publish Intent、Candidate、dbt/Airflow 与 Catalog 所有权未被复制或绕过。
- [ ] 立即删除项通过无引用/构建检查；延迟删除项具备两版本零访问、客户画像、回滚和审批证据。
- [ ] `it/` 中存在真实认证 API、PostgreSQL、浏览器、DEV 物化链证据；无占位截图。

## 非目标

- 不复制 DataWorks 的品牌、代码或服务端模型。
- 不新增 DIM 层、第五类“贴源 ModelSpec”或第二套 FML/图谱/发布引擎。
- 不修改客户 Excel ODS 表。
- 不在本 Sprint 物理删除客户存量业务表。
- 不替代 Sprint-76 尚未关闭的生产物化、安全和调度工作。
