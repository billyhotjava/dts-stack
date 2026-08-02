# Sprint-84：数据建模七入口真实能力收敛

**时间**：2026-08  
**状态**：IN_PROGRESS（代码/契约已冻结并完成构建，待部署与一次联合 E2E）  
**类型**：UI Productization / Contract Wiring / Controlled Capability Retirement  
**目标**：让用户在现有“数据建模”七个入口中读取真实数据、执行真实业务动作并获得完整状态反馈；没有 canonical owner 的演示能力直接删除，不新增菜单、平行台账或万能工具引擎。

## 架构决策

| ID | 决策 | 约束 |
|---|---|---|
| ADR-84-01 | 页面是产品能力第一真值 | 组件/API/后台存在均不能单独证明功能完成 |
| ADR-84-02 | 复用 WarehousePlan、ModelSpec、标准、指标、Catalog lineage 与公共审计 | 不复制事实、不新建前端审计调用 |
| ADR-84-03 | 概览和关系图只做既有事实投影 | 不建统计表、最近访问表、任务表或关系图库 |
| ADR-84-04 | 无既有 owner 的工具控件删除 | 不保留禁用占位或模拟成功 |
| ADR-84-05 | Sprint-83 继续拥有 ModelSpec/dbt 主链 | 本 Sprint 不重复实现高级 dbt、ZIP 导入、发布或物化 owner |
| ADR-84-06 | 全部编码完成后只执行一次联合 E2E | 编码期只跑聚焦 RED/GREEN 与必要模块检查 |
| ADR-84-07 | 新业务维度和基于现行定义的维度表统一由 v2 单事务组合命令创建 | 同一事务必须完成维度绑定、ModelSpec seed r1、完整逻辑设计 r2 和严格公共审计；不允许前端追加 PUT，不新增操作台账 |
| ADR-84-08 | 操作完成修订与当前模型指针分离 | `operationId` 只定位既有幂等记录；固定 r2 是完成凭据，当前模型可继续演进到 r3+，恢复接口同时返回二者 |
| ADR-84-09 | 未确认响应使用短期浏览器编辑态恢复 | 仅用 `sessionStorage` 保存绑定用户、短 TTL、带指纹且不含令牌/SQL/样例的待提交命令；它不是业务事实、审计或平行台账 |
| ADR-84-10 | UI 只展示 canonical owner 可持久化的字段 | 读取与写回必须逐字段保真；不允许把两个服务端字段合并成一个 UI 值，也不允许显示会被 serializer 丢弃的输入 |
| ADR-84-11 | 所有可重入读取采用 latest-request-wins | 路由、目录或对象切换后，迟到响应不得覆盖新上下文；写操作必须绑定当前对象与版本 |
| ADR-84-12 | 代码、测试、构建、部署和真实 E2E 分层登记 | 任一前置证据不能替代部署后用户可操作能力；E2E 只在最终镜像部署后执行一次 |

## 端到端契约链

| 入口 | UI 触发 | API/owner | canonical data / audit |
|---|---|---|---|
| 建模概览 | 进入、刷新、新建模型 | 并发读取 WarehousePlan、ModelSpec、MetadataStandard、Indicator；新建跳转 ModelSpec 工作台 | 只读投影；写操作由目标服务审计 |
| 数仓规划 | 选择计划、维护域/过程/层级/策略 | `warehousePlanApi`、CatalogDomain、Sprint64 planning adapter | WarehousePlan、Catalog Domain、既有过程/层级；服务端审计 |
| 数据标准 | 查询、新建、编辑、导入、映射 | `/modeling/standards/**`、`/modeling/metadata-standards/**`、standard packages/reference codes/glossary | 既有标准仓储与审计 |
| 维度建模 | 新建、编辑、保存、提交、发布、物化、预览、ZIP 导入 | Sprint-83 `ModelSpec → Lifecycle → Candidate → Gateway` | ModelSpec/revision、implementation、run/evidence、公共审计 |
| 数据指标 | 查询、新建、校验、保存、发布、归档 | `/governance/indicators/**` | GovIndicator definition/version/reference/run + 审计 |
| 关系图 | 查询、筛选、刷新布局、跳转 | WarehousePlan relationship graph + 标准引用 + 指标依赖 + Catalog lineage | 只读组合投影，不另存图 |
| 通用工具 | 打开真实导入/检查流程、查看其运行结果 | dbt ZIP、标准包、标准代码、lineage import 的既有入口 | 各 owner 的 run/history/audit；无统一工具台账 |

## 现状勘察账本（Recon once）

| # | 事实 | 证据 |
|---|---|---|
| CL-84-01 | 七个生产入口与 27 个叶子路由已存在 | `source/dts-platform-webapp/src/pages/data-modeling/navigation.ts:3` |
| CL-84-02 | 概览统计、最近记录和任务为硬编码 | `.../pages/HomeWorkspace.tsx:24` |
| CL-84-03 | 规划八类目录与数据为硬编码 | `.../pages/PlanningWorkspace.tsx:24` |
| CL-84-04 | 标准五类目录与数据为硬编码 | `.../pages/StandardsWorkspace.tsx:23` |
| CL-84-05 | 维度目录、固定修订表示、高级 dbt、物理预览、ZIP 导入已接真实 API；新建/保存/提交/发布/物化未闭环 | `.../pages/DimensionalModelingWorkspace.tsx:91`、`.../components/ModelingEditor.tsx:421` |
| CL-84-06 | 指标目录、编辑表单与动作仍为演示实现 | `.../pages/MetricsWorkspace.tsx:24`、`.../components/MetricEditor.tsx:210` |
| CL-84-07 | 工具卡和运行记录为演示实现 | `.../pages/ToolsWorkspace.tsx:14` |
| CL-84-08 | 关系节点/边为硬编码，刷新/筛选没有 handler | `.../pages/RelationshipGraphWorkspace.tsx:30` |
| CL-84-09 | WarehousePlan 已有计划 CRUD、baseline、policy、stage projection、candidate 与 relationship graph | `source/dts-platform-webapp/src/api/warehousePlanApi.ts:272` |
| CL-84-10 | 标准、标准包、码表、术语和单位已有真实 API | `source/dts-platform-webapp/src/api/platformApi.ts:842` |
| CL-84-11 | ModelSpec 已有 CRUD、CAS、Gate、依赖、build/publish intents；部分旧 lifecycle client 无服务端 route，禁止误接 | `source/dts-platform-webapp/src/api/modelSpecApi.ts:193` |
| CL-84-12 | 指标已有 CRUD、校验、引用、发布、版本、预览和看板 API | `source/dts-platform-webapp/src/api/platformApi.ts:1155` |
| CL-84-13 | 业务分类、应用层、主题域、建模空间和规划参数缺少统一 CRUD；不得继续用本地数组冒充 | 本 Sprint API 映射审计 2026-08-02 |
| CL-84-14 | 通用工具没有统一执行/导入导出台账，现有能力属于各自 owner | 本 Sprint API 映射审计 2026-08-02 |

勘察到此冻结。下游 Task 只引用本账本；新事实只能追加，禁止重复宽扫。

## Gate Registry

| Gate | 项目 | 状态 | 证据 | 未过则关联 Task |
|---|---|---|---|---|
| G0 | 页面能力矩阵 | PASS | `assets/page-capability-matrix.md` | - |
| G0 | 按钮/组件矩阵 | PASS | `assets/button-component-matrix.md` | - |
| G1 | 契约与 owner 映射 | PASS | 本文契约链、页面/按钮矩阵与独立 Review | - |
| G2-UI-TRUTH | 无演示数据、无无处理器控件 | PASS_CODE | 30 files / 122 Vitest + source-contract；总代码 Review PASS | 部署后复核 |
| G2-CONTRACT-WIRING | 写动作完整追踪到服务端审计 | PASS_CODE | 原子维度 POST/GET、标准/指标/规划 owner 与 87/87 后端测试 | 部署后审计抽样 |
| G2-STATE-COMPLETE | 七态完整 | PASS_CODE | 页面聚焦测试与错误/权限/重试契约 | 浏览器验收 |
| G2-NO-FALLBACK-DEMO | API 空/失败不回退示例 | PASS_CODE | 页面架构门禁与独立 Review | 浏览器验收 |
| G2-EVIDENCE | RED/GREEN、构建、最终一次 E2E | PASS_BUILD | Chrome 95 build PASS；E2E 尚未执行 | F5/T02 |
| G4 | 联合 DoD | PENDING_E2E | `it/README.md` | F5/T02、Sprint-83 F6 |

## Feature 列表

| ID | Feature | Task 数 | 状态 |
|---|---|---:|---|
| F0 | 真实性基线与纠偏门禁 | 2 | DONE |
| F1 | 规划与建模概览真实化 | 3 | CODE_COMPLETE |
| F2 | 数据标准真实化 | 3 | CODE_COMPLETE |
| F3 | 数据指标真实化 | 3 | CODE_COMPLETE |
| F4 | 关系图与通用工具收敛 | 3 | CODE_COMPLETE |
| F5 | 集中验证与交付证据 | 2 | IN_PROGRESS（T01 DONE / T02 READY） |

**顺序**：F0 → F1/F2 → Sprint-83 F2～F5 → F3 → F4 → Sprint-83 F6 + F5。

## 追溯矩阵

| 用户结果 | Feature | 验收证据 |
|---|---|---|
| 概览与规划无假数据且可完成真实维护 | F1 | IT-84-01 |
| 标准目录、CRUD、导入和引用真实可用 | F2 | IT-84-02 |
| 指标目录、编辑、校验和发布真实可用 | F3 | IT-84-03 |
| 关系图只投影真实关系，工具只保留真实入口 | F4 | IT-84-04 |
| 七入口与 Sprint-83 主链统一验收 | F5 + Sprint-83 F6 | IT-84-05 |

## 完成标准与纠偏硬规则

- [x] 生产页面没有内置客户/财务/项目示例数据，也不会在 API 空或失败时回退示例。
- [x] 每个可见写控件可追踪到 handler、API、service、canonical data 和服务端审计。
- [x] 每个切片具备 default/loading/empty/disabled reason/error-retry/permission/success。
- [x] 生产页面已清除 `UiStageNotice`、`BackendPendingButton`、无 handler 按钮和模拟记录。
- [x] 状态汇报分列“代码/契约完成度”和“部署后用户可操作能力”。
- [x] 冻结快照已完成聚焦测试、Chrome 95 构建和 Java/TypeScript/安全/总代码 Review。
- [ ] 部署最终镜像后只执行一次联合 E2E，并归档真实认证、审计与截图证据。

## 本轮约束纠偏（2026-08-02）

| 先前偏差 | 纠偏后的硬约束 | 本轮落实方式 |
|---|---|---|
| 把后台重构、API 存在或静态页面迁移计入“功能完成” | 完成度只按用户从页面触发到 canonical owner、持久化结果、服务端审计和可恢复反馈的闭环计算 | 七入口逐页建立页面/按钮/组件矩阵；后台能力仅作为依赖证据 |
| 前端仍显示演示数据和无 handler 控件，却被描述为“已重构” | 生产页面不得包含假目录、假记录、模拟成功或后台待接入按钮 | 六个 FAKE 页面全部改接真实 owner；维度页补齐 CRUD、标准绑定、release intent 与导入恢复；零消费者占位组件物理删除 |
| 编码完成度、聚焦测试、构建、部署和真实 E2E 混为一个状态 | 五类证据分开登记，缺任一项不得把用户可操作能力标为 DONE | Feature 保持 `IN_PROGRESS`，直到 F5 完成集中 build、独立 review、部署和联合 E2E |
| 在编码过程中重复宽扫、重复构建和提前跑 E2E，消耗大量 token | Recon 只做一次；编码期只执行与当前切片相关的 RED/GREEN；全部代码冻结后统一验证 | 使用 CL-84 账本复用事实；最终只运行一次联合 E2E |
| 为补齐原型页面而新建万能后端、平行台账或本地 fallback | 缺 canonical owner 的能力必须删除或显示真实空态，不得由前端猜测数据 | 概览不建最近访问/任务表，关系图不落库，工具不建统一历史，规划无 owner 项不伪造 CRUD |
| 关系图只“跳到页面”而不定位真实资产 | 深链参数必须由目标页面消费；未命中需显式反馈，不能静默忽略 | `planId`、`standardId`、`indicatorId` 进入目标页面真实选择上下文并保留其他查询参数 |
| 新维度采用前端 `create definition → confirm → create ModelSpec`，刷新或响应丢失会留下孤儿定义 | 跨聚合写入必须由服务端单事务编排；重放固定使用首次确认的维度 revision 2，并要求维度/模型幂等状态一致 | `POST /api/modeling/model-specs/dimension` 复用既有 service、权限与幂等记录；严格组合审计失败时整笔事务回滚 |
| 第一版组合命令只创建 ModelSpec seed r1，前端随后独立 PUT；却被误判为“原子创建完成” | 用户一次保存的原子边界必须覆盖完整逻辑设计，服务端在一个事务内形成固定 ModelSpec r2 后才能返回成功 | v2 组合命令同时支持新建定义和绑定现行定义；前端不再追加 PUT，POST 重放以 seed r1 + 完整命令重算并核验固定 r2 checksum |
| 只把 `dmOperationId` 放进 URL，却没有恢复 selection、表单和未知响应状态 | URL 标识只能定位操作，不能替代编辑态；恢复顺序固定为操作恢复 → 显式深链 → 创建意图 → 默认选择 | GET 操作状态只读取既有幂等记录、不建表；未提交或回滚时从短期 `sessionStorage` 恢复同一完整命令，无有效草稿则进入显式错误态，禁止自动重提 |
| 保存中仍可取消、切换目录，旧响应可清参数并覆盖新页面；维度表失败重试还可能静默复用旧定义引用 | 每个异步保存必须绑定 editor instance、request epoch 和 operationId；未知结果不得清理恢复锚点，定义引用必须进入完整请求指纹 | 保存期间禁用内部取消和目录切换；卸载仅中止客户端等待；workspace 完成模型选中和 canonical URL ack 后才清 operationId/临时草稿 |
| 请求边界只做语义校验，未限制 JSON 体积、深度和集合预算；资源测试关闭过滤器后把注解存在当成授权证据 | 新写端点必须在 Jackson 前限制字节数，并覆盖普通用户拒绝、维护角色成功；语义解码还需限制层级、层次数和字符串长度 | v2 请求仅接收 JSON，使用固定 1 MiB wire budget、严格字段/深度/集合预算和稳定 413；权限与事务回滚分别用 WebMvc 和 Postgres 聚焦测试证明 |
| 以一次构建通过掩盖后续 review 修复尚未重新验证 | 每次状态只引用最后一次修改之后的证据；旧构建结果只能作为历史证据 | 当前代码冻结后只补一次聚焦测试/模块构建；部署与最终 E2E 仍独立登记 |
| 标准代码把 `bizCatalog` 与 `stdLevel` 合并显示，普通名称编辑也可能互相覆盖 | UI 字段必须与 owner 字段一一映射并可无损读写；serializer 不支持的输入不得显示为可编辑 | 标准代码分别映射“数据域/适用范围”，新增 name-only round-trip；码表隐藏业务定义、词典隐藏适用范围 |
| 路由或对象切换期间的旧读取响应可能覆盖新选择，甚至将旧版本与新对象组合提交 | 可重入读取统一使用 request epoch；迟到成功、失败和 finally 均不得写状态，写动作在 loading 期间禁用 | `PlanningWorkspace`、`MetricEditor` 增加 latest-request-wins；规划参数加载中不可保存 |
| 将 Node `node:test` 契约文件交给 Vitest，真实断言通过却产生“无 suite”噪声 | 测试执行器必须与文件契约一致，失败归因不能混淆业务缺陷和 runner 配置 | Vitest 只运行组件/adapter 30 files / 122 tests；两个 Node 契约由 `node --test` 独立运行 36/36 |
| 审计目录源码已更新，但 Maven 把共享资源复制到 classpath 根目录，运行时仍读取旧 `config/` 资源 | 公共治理资源必须验证最终 classpath/镜像路径，不能只看源码三份一致 | `attach-audit-common-resources` 固定 `targetPath=config`，`process-resources` 已证明复制到 `target/classes/config` |
| 为赶进度可能放宽 800 行架构门禁 | 质量门禁本身是交付契约；超限必须最小拆分且不得搬动事务、幂等或并发控制 | 抽出纯展示 `ModelingEditorSections`，主编辑器 split 口径 797 行，原子保存与 request epoch 保留在父组件 |
| 恢复接口只按 tenant 可见性读取，业务拒绝没有专项失败审计 | 操作恢复必须复用 plan/actor replay 授权；预期业务失败写严格、脱敏、fail-closed 的专项审计 | GET recovery 在任何版本读取前校验 actor；POST FAIL 仅记录 `errorCode/errorKind`；公共 create 禁用 `dm:v2:` 保留前缀 |

后续状态汇报固定使用两列：`代码/契约完成度` 与 `部署后用户可操作能力`。前者通过不能替代后者。

## 冻结快照证据（2026-08-02）

- 前端：Vitest `30 files / 122 tests`；Node 契约 `36/36`；标准对话框/adapter `7/7`；原子创建与字段保真 `2/2`。
- 后端：维度组合命令、权限、body limit、保留前缀、ModelSpec 共享回归和 PostgreSQL 回滚合计 `87/87`；其中真实 PostgreSQL 严格审计回滚 `4/4`。
- Review：Java、TypeScript、安全和最终总代码 Review 均 PASS；最终总代码 Review 为 `CRITICAL/HIGH/MEDIUM/LOW = 0`。
- 构建：`pnpm build` 使用 `LEGACY_BROWSER_BUILD=1`、Vite `chrome95` target，TypeScript 与生产 bundle 构建成功。
- 尚未完成：最终镜像构建/部署、真实认证浏览器 E2E、审计后台抽样和 1366×768/窄屏截图；不得据此把 Sprint 标为 DONE。

## 非目标

- 不新增菜单或页面。
- 不新建 Home、最近访问、任务、关系图或通用工具任务表。
- 不恢复旧 `/modeling/plans`、`/etl/dbt/run`、共享 projectDir 或旧 preview。
- 不为尚无 owner 的规划目录和工具能力伪造本地 CRUD。
