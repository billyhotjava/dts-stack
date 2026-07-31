# Sprint-79：智能数据建模工作台收敛

**时间**：2026-07  
**状态**：IN_PROGRESS（建模工作台主线、F3 关系图、F4 租约并发与 F5 菜单恢复已完成构建、部署、回滚和认证浏览器验收；真实 DEV 物化、Chrome 95 与 F5/T02 两版本观测门禁仍未完成）
**类型**：Architecture / UI Productization / Controlled Retirement / Full-stack  
**目标**：用户在一个建模工作台内完成规划、标准、维度、四类逻辑模型、指标、关系查看以及发布/物化交接，不再在多组解释性页面和重复入口之间切换。

## 背景与价值

`worklog/prototype/dataworks-kimball` 已验证“模块导航 + 对象树 + 单页编辑器 + 短工具栏”的低摩擦交互。DTS 现有实现已经拥有 WarehousePlan、DimensionDefinition、ModelSpec、Indicator、ReleaseCandidate、dbt/Airflow 和 Catalog 物理资产主链，但前端能力分散在多个页面，模型详情仍以三个解释性阶段呈现。

本 Sprint 是界面与旅程收敛，不是重写建模内核。原型只提供信息架构和交互参考；正式数据、权限、状态机和发布事实继续由 DTS canonical owner 持有。

## 架构决策记录（ADR）

| ID | 决策 | 理由 | 影响 |
|---|---|---|---|
| ADR-79-01 | `/modeling/workbench` 是推荐主入口；原型七个一级模块变为工作台内部 Tab，原有 canonical 菜单在退役门禁满足前继续可见 | 用户仍需要从既有菜单直达规划、维度、模型和指标；工作台收敛不能提前替代受控退役 | 保留原深链与菜单；两版本零访问、客户画像和审批齐备后再单独退役 |
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
| 菜单/迁移 | dts-admin Liquibase 菜单事实源 | 不增加一级菜单；恢复五个既有建模菜单并保留原 menu id/role binding，退役必须重新过 F5 门禁 |

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
| CL-11 | 立项基线 HEAD 为 `857c5cf45`，当时工作树干净并与 `origin/v2.2.3` 一致 | `git status --short --branch`、`git log -1`，2026-07-30 |
| CL-12 | 当前本地库：4 个 DRAFT 计划、7 个 CURRENT 维度、6 个 ModelSpec/10 个 revision；实现、Candidate、物理关系 observation 均为 0 | `assets/domain-profile.md` §3 |
| CL-13 | 本地旧业务对象、旧 SQL 模型、旧语义模型/维度均为 0，近 30 天 legacy API usage 为 0 | `assets/domain-profile.md` §3；只能支持本地退役判断，不能外推客户环境 |
| CL-14 | `DbtFileBrowserPage.tsx` 和旧注册 helper 已按 Batch A 删除；`ModelTemplatesPage`、`ModelPipeline` 仍分别被项目空间和 SQL 建模使用 | `it/evidence/IT-07/`，2026-07-30 |
| CL-15 | GitNexus 索引停留在 `4762dc9b9`、落后 HEAD 2 个提交；最新原型/UI 只以 HEAD 源码为准 | GitNexus `list_repos`，2026-07-30 |
| CL-16 | 2026-07-31 live DB 中建设规划、维度目录、模型中心、数据指标、指标工作台均为 `deleted=true`，且 `last_modified_by=sprint79-menu-convergence` | 只读查询 `portal_menu` |
| CL-17 | 菜单缺失根因同时存在于升级链和新租户链：`20260730-01` 软删除五行，当前 `portal-menu-seed.json` 也移除了同一层级 | `20260730-01_sprint79_modeling_workspace_menu_convergence.xml:17`；`portal-menu-seed.json` |
| CL-18 | review 发现 Airflow renew 路由未进入服务鉴权 allowlist、租约 CAS 使用服务端旧时钟、orphan janitor 无界 N+1 | F4/T03 |
| CL-19 | review 发现关系图先分页节点后丢弃跨页边，遍历全部 cursor 仍无法得到完整图；投影服务已达 1590 行 | F3/T03 |
| CL-20 | review 发现 workspace E2E 无执行期写屏障、自动计划未规范化写入 URL、创建计划卡存在嵌套 label | F0/T04、F1/T03 |
| CL-21 | 前向恢复迁移已将五个菜单恢复为 `deleted=false / last_modified_by=sprint79-menu-restore`，原 binding 各保留 1 条；`ROLE_INST_DATA_OWNER` 认证旅程可见完整建模菜单 | `it/evidence/IT-01/workspace-restored-modeling-menus.png`；2026-07-31 live DB 只读查询 |

**开放问题**

- 客户环境的存量模型数量、旧路由访问和旧表数据未画像；F5 删除门禁不得使用本地 0 行结论替代。
- 正式 DTS 的认证 UI/API 基线已于 2026-07-30 通过系统 Chrome 150 复验；Chrome 95 兼容仍需在 F1 实现后补证据。
- Sprint-76 的 `profileLeaseId`、租约竞态、容器归属和清理确认缺口已在源码、PostgreSQL 并发 IT 与部署健康检查层关闭；真实 DEV dbt/Airflow/relation/Catalog 链完成前，仍不得声明 PROD READY。

## Gate Registry

| Gate | 项目 | 状态 | 证据 | 未过则关联 Task |
|---|---|---|---|---|
| G0 | 交付基线 | PASS_WITH_GAPS | `it/baseline.md`；认证/API/UI 已通过，代表数据仍不足 | F0/T01 |
| G0 | 领域与数据画像 | PASS_WITH_GAPS | `assets/domain-profile.md` | F0/T02 |
| G0 | DTS 领域不变量 | PASS | ADR-79-01～08 | - |
| G1 | 契约链贯通 | PASS | 本文“端到端契约链” | - |
| G1 | 非功能预算 | PASS_WITH_GAPS | `assets/nfr-budget.md` | F0/T01、F3/T02 |
| G3 | 发布安全 | PASS_WITH_GAPS | platform/webapp/admin/menu 实际回切与恢复 PASS；Airflow bind-mounted 源码未实际回切 | F4/T02 |
| G4 | 可运维性 | PENDING | 后续 `assets/runbook.md` | F0/T02 |
| G4 | DoD 验收 | IN_PROGRESS | IT-01/02/04/07/08 已有部分真实证据；IT-03 BLOCKED，IT-05/06 与 Chrome95 未闭环 | F0/T01、F4、F5/T02 |

## Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 |
|---|---|---:|---|---|
| F0 | 交付基线与退役证据 | 4 | P0 | IN_PROGRESS |
| F1 | 统一建模工作台壳层 | 3 | P0 | IN_PROGRESS |
| F2 | 单页模型编辑器 | 4 | P0 | PASS_WITH_GAPS |
| F3 | 指标、工具与关系图 | 3 | P0 | IN_PROGRESS |
| F4 | 发布物化短流程 | 3 | P0 | IN_PROGRESS |
| F5 | 旧页面受控退役 | 3 | P0 | IN_PROGRESS |

**依赖顺序**：F0 → F1 → F2/F3 → F4 → F5。F2 与 F3 可在壳层契约冻结后并行；F5 只能在功能等价和观测期满足后执行。

## 2026-07-31 实现快照

| 范围 | 已完成 | 当前证据 | 尚未完成 |
|---|---|---|---|
| F3 指标/工具/关系图 | 工作台嵌入；完整窗口投影；复合游标、窗口指纹、HMAC、防并发重排；投影服务拆分到 800 行门禁内；GIN 索引具备结构校验与安全重建 | 关系图定向测试 38/38、Resource 22/22；PostgreSQL 17.6 + Liquibase IT 4/4；最终认证 E2E 展示非零节点/关系，1/1 PASS | Chrome 95 |
| F4 运行时安全 | `profileLeaseId` 跨语言对齐；数据库时钟续租/过期/释放 CAS；批量 orphan janitor；Docker owner label + immutable container ID；清理未确认不释放租约 | 租约定向测试 38/38、Spring 装配 1/1、PostgreSQL 17.6 并发 IT PASS；部署后 platform/Airflow healthy | DEV dbt run/relation/Catalog IT-05/06；Airflow 源码回切 |
| F5 入口收敛 | `/modeling/workbench` 为推荐入口；五个 canonical 菜单在退役门禁前恢复；原 menu id、父子关系和 role binding 保持不变 | admin 契约、Liquibase、构建和部署 PASS；live DB 五项 `deleted=false`；认证 E2E 菜单断言 1/1 PASS | 8 条 compatibility route 不满足两版本零访问门禁，暂不物理删除；客户旧表只保留提案 |

F3/F5 的“通过”包含本轮构建、运行态、回滚和认证浏览器证据；F4 这里只证明运行时加载与健康，不代表真实物化或发布完成。

## 追溯矩阵

| 需求点 | Feature/Task | 测试 | 验收证据 |
|---|---|---|---|
| 原型全部进入 DTS 而不复制内核 | F1/T01、F1/T02 | workspace route/source-contract | `it/evidence/IT-01` |
| 对象上下文 + 单页编辑器 | F2/T01、F2/T02、F2/T04 | editor state/field mapping/workbench asset contract | `it/evidence/IT-02`、`it/evidence/IT-03` |
| 指标、工具、关系图可达 | F3/T01、F3/T02 | indicator owner + graph projection IT | `it/evidence/IT-04` |
| 发布与物化短流程 | F4/T01、F4/T02 | intent/candidate contract + real DEV build | `it/evidence/IT-05`、`it/evidence/IT-06` |
| 不能复用的旧页面删除 | F0/T03、F5/T01、F5/T02 | orphan/import/route/usage guards | `it/evidence/IT-07`、`it/evidence/IT-08` |
| Review 阻断修复与旧菜单恢复 | F0/T04、F1/T03、F3/T03、F4/T03、F5/T03 | 只读屏障、URL、分页、租约、菜单契约 | `it/evidence/IT-01/workspace-restored-modeling-menus.png`；生产只读 E2E 1/1、清理四项为 0 |

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
