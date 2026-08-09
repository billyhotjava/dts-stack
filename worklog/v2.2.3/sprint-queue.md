# Sprint Queue — v2.2.3

## 2026-07-16 Goal 完成度审计

本次审计把“代码实现完成”和“可交付闭环完成”分开判定：必须同时具备实现、测试、构建、数据库迁移、运行容器和浏览器证据，才能称为真正完成。

| Sprint | 代码/契约 | 测试与构建 | 当前运行/验收 | 结论 |
|---|---|---|---|---|
| Sprint-60 | F1/F2 已完成；F3～F7 仍有明确未完成标准 | 前端专项契约可运行；后端/dbt/Addax/Airflow 全链路未闭环 | 运行容器健康，但未证明 PJM 全链路与真实租户验收 | **未完成** |
| Sprint-61 | F1～F8 已完成 | source-contract/build 证据完整 | F9 登录、DNS、Playwright、Chrome95 smoke 未完成 | **未完成** |
| Sprint-62 | F1～F4 实现完成 | 35/35 journey source-contract 通过 | 浏览器恢复卡、门禁卡、打印视图仍挂靠 Sprint-61/F9 | **实现完成，交付未完成** |
| Sprint-63 | F1～F4 实现完成 | 规划/门禁/契约证据通过；Node 与 Vitest 需按测试类型运行 | 4 个业务页浏览器 smoke 仍被登录/DNS 阻断 | **实现完成，交付未完成** |
| Sprint-64 | F1～F4、F6 源码实现完成；F5/T03 未完成 | source-contract 29/29、治理/建模 Node 13/13、Vitest 7/7、tsc/build 通过 | 平台健康、Sprint64 表已迁移；当前容器未应用 20260716 菜单迁移，且未包含本轮概念卡/维度推荐产物，浏览器 smoke 未完成 | **未完成** |

审计证据：`/management/health` 返回 `UP`；平台库存在 `sprint64_*` 与 `modeling_*` 表；`20260711_01_sprint64_governance`、`20260714-01_modeling_vnext` 已执行；`20260716-01_advanced_modeling_menu_label` 尚未执行，运行库菜单仍为“逻辑建模（SQL）”。

## Sprint-1: 架构加固 -- 高可用、安全、可观测性 (202604)
**状态**: DONE（代码与 focused 验证完成；现场 smoke 待部署补证）
**类型**: Design Only（仅设计，不实施）

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-高可用与灾备 | 3 | READY |
| F2-安全加固 | 3 | READY |
| F3-可观测性体系 | 3 | READY |
| F4-调度引擎升级 | 2 | READY |
| F5-异步通信改造 | 2 | READY |
| F6-数据治理链路补全 | 3 | READY |
| F7-部署规范化 | 2 | READY |
| F8-前端架构收敛 | 2 | READY |

**统计**: READY=20, IN_PROGRESS=0, DONE=0, BLOCKED=0

## Sprint-2: BI 分析卡片文件夹管理 (202604)
**状态**: DONE
**类型**: Implementation（实施型）

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-分析卡片文件夹管理 | 4 | READY |

**统计**: READY=4, IN_PROGRESS=0, DONE=0, BLOCKED=0

## Sprint-3: IAM 修复 -- displayName 链路 bug 修复 (202604)
**状态**: DONE
**类型**: Implementation（实施型，为 v2.3.0 IAM 重构做铺垫）

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-修复displayName链路bug | 4 | READY (T04 DONE) |

**统计**: READY=3, IN_PROGRESS=0, DONE=1, BLOCKED=0

## Sprint-4: 数据质量管控体系重构 (202604)
**状态**: CONTRACT_DONE / ENFORCEMENT_IN_PROGRESS
**类型**: Implementation（实施型）

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-规则模板引擎 | 4 | DONE |
| F2-中文清洗函数库 | 3 | DONE |
| F3-质量检测增强 | 4 | DONE |
| F4-数据编辑器 | 4 | DONE |
| F5-前端重构 | 3 | DONE |
| F6-质量规则Wizard | 4 | DONE |
| F7-质量报告 | 3 | DONE |
| F8-数据修复工作台 | 3 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=28, BLOCKED=0

## Sprint-5: 指标驱动建模体系 (202604)
**状态**: DONE
**类型**: Implementation（实施型）

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-指标元数据模型扩展 | 3 | DONE |
| F2-指标模板库 | 3 | DONE |
| F3-dbt 自动生成引擎 | 4 | DONE |
| F4-配置工作台前端 | 4 | DONE |
| F5-质量评分修复 | 2 | DONE |
| F6-运行追踪 | 3 | DONE |
| F7-指标看板 | 4 | DONE |
| F8-指标商店 | 4 | DONE |
| F9-LLM 接口预留 | 2 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=29, BLOCKED=0

## Sprint-6: Airflow 运维对接体系重构 (202604)
**状态**: DONE
**类型**: Implementation（实施型）

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-日志中心页面 | 3 | DONE |
| F2-日志预览抽屉 | 2 | DONE |
| F3-任务编排增强 | 2 | DONE |
| F4-运行概览增强 | 2 | DONE |
| F5-任务实例监控增强 | 3 | DONE |
| F6-后端接口补全 | 2 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=14, BLOCKED=0

## Sprint-7: 数据目录与元数据体系完善 (202604)
**状态**: DONE
**类型**: Implementation（实施型）
**完成日期**: 2026-04-05

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-主题域与数据集双向绑定 | 5 | DONE |
| F2-指标创建接入数据目录 | 2 | DONE |
| F3-dbt血缘导入 | 3 | DONE |
| F4-指标↔数据集血缘 | 2 | DONE |
| F5-Data Product | 3 | DONE |
| F6-资产地图入口重构 | 4 | DONE |
| F7-资产列表页重构 | 4 | DONE |
| F8-资产详情页重构 | 5 | DONE |
| F9-数据搜索页重构 | 3 | DONE |
| F10-血缘图UX优化 | 4 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=35, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-7-202604/README.md`

## Sprint-8: 指标中心重构 (202604)
**状态**: DONE
**类型**: Implementation（实施型）
**完成日期**: 2026-04-05

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-后端API改造 | 4 | DONE |
| F2-指标中心页面重构 | 3 | DONE |
| F3-主题域管理增强 | 1 | DONE |
| F4-指标模板导入导出 | 2 | DONE |
| F5-数据资产关联 | - | DONE（Sprint-7 已完成） |

**统计**: READY=0, IN_PROGRESS=0, DONE=10, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-8-202604/README.md`

## Sprint-9: GPMC 大屏跳转修复 + 甘特图弹层下钻 + Session 管理加固 (202604)
**状态**: IN_PROGRESS
**类型**: Implementation（实施型）
**完成日期**: F1-F7 完成于 2026-04-09

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-跳转引擎"无目标=不跳"修复 | 5 | DONE |
| F2-编辑器 ScreenJumpPicker UI 修复 | 4 | DONE |
| F3-JSON 实例死链清理 | 1 | DONE |
| F4-父项目汇总甘特组件改造 | 4 | DONE |
| F5-ProjectDetailGanttModal 弹层组件 | 2 | DONE |
| F6-gpmc-execution-gantt JSON 改造 | 1 | DONE |
| F7-文档与索引 | 1 | DONE |
| F8a-Platform直接对接Keycloak(架构修复) | 9 | READY |
| F8b-Session防护层加固 | 5 | READY |

**统计**: READY=14, IN_PROGRESS=0, DONE=18, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-9-202604/README.md`

## Sprint-10: 大屏编辑器 Phase 1 — 组件配置增强 + 主题修复 + 发布弹窗 (202604)
**状态**: IN_PROGRESS
**类型**: Implementation（实施型）

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-Schema 基础设施(类型 + 通用渲染器 + 4 个复合编辑器) | 6 | READY |
| F2-47 个组件 Schema 定义(basic/enterprise/table/charts/datav/filters/3d) | 7 | READY |
| F3-PropertyPanel 切换到 SchemaConfigRenderer | 2 | READY |
| F4-主题系统修复(切换自动应用 + Schema 驱动 patch) | 2 | READY |
| F5-发布弹窗重构(PublishResultModal + ScreenGrantManager 提取) | 3 | READY |

**统计**: READY=20, IN_PROGRESS=0, DONE=0, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-10-202604/README.md`

## Sprint-11: 数据开发 SQL IDE 重构 (202604)
**状态**: DONE
**类型**: Implementation（实施型，全新重写 + Feature Flag 切换）
**完成日期**: 2026-04-14

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-架构骨架与Monaco编辑器 | 6 | DONE |
| F2-多Tab持久化 | 4 | DONE |
| F3-Schema浏览器与Activity-Bar | 5 | DONE |
| F4-专业结果表格与导出 | 5 | DONE |
| F5-Chart-Pivot-Plan-二次查询 | 5 | DONE |
| F6-简洁高级模式与Copilot插槽 | 3 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=28, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-11-202604/README.md` + `plan.md`

## Sprint-12: BI 大屏响应式改造（C 方案）(202604)
**状态**: DONE（代码阶段 1-4 完成，IT 真机待客户侧）
**类型**: Implementation（破坏性重构，demo 阶段可重做）
**约束**: 客户 Chrome 95

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-响应式布局引擎（核心） | 3 | DONE |
| F2-ScreenConfig v2 schema | 3 | DONE |
| F3-编辑器重构（网格编辑） | 4 | DONE |
| F4-组件内部响应式 | 4 | DONE |
| F5-新建流程与 v1 兼容 | 3 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=17, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-12-202604/README.md`
**IT**: `worklog/v2.2.3/sprint-12-202604/it/` — 静态 Chrome 95 兼容已过；真机 smoke 需客户侧补证据

## Sprint-13: 自助 BI 与可视化语义层（Phase 1）(202604)
**状态**: DONE（Phase 1 MVP 已交付；治理统计与现场 IT 证据待补）
**类型**: Implementation（新架构落地，非破坏——新语义层与老 Metabase fork 并行）
**目标**: 在 dbt 产出的 DWS/ADS 之上建薄语义层，让分析师通过拖拽组合指标/维度/join 建 Card；工程师用 dbt schema.yml 声明原子指标与 join 关系图作为唯一真源；LLM 本 Sprint 不上但 schema 预留字段
**参考架构**: Lightdash（dbt-native semantic layer），非 Cube 级自研

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-接口合约与DSL规范 | 4 | DONE（接口与 TS 类型已固化到实现） |
| F2-语义层后端核心 | 5 | DONE |
| F3-Join与虚拟数据集 | 4 | DONE |
| F4-派生指标引擎 | 3 | DONE（表达式主链可用） |
| F5-前端建模与CardEditor | 5 | DONE |
| F6-治理护栏与提升通道 | 4 | DONE（白名单/密级/提升主链完成；限流统计延后） |

**统计**: READY=0, IN_PROGRESS=0, DONE=25, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-13-202604/README.md`

## Sprint-14: Excel 导入内核统一与解析重构 (202604)
**状态**: READY
**类型**: Implementation（兼容式重构，平台侧先收口，保持现有 REST 与 Addax CSV 契约）
**目标**: 统一 `dts-platform` 与 `dts-ingestion` 的 Excel 解析规则，用 POI-based Excel Core 解决负数、本地化日期、公式与合并单元格等长期兼容问题

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-统一Excel解析内核 | 3 | READY |
| F2-平台侧ExcelImport流水线重构 | 3 | READY |
| F3-ingestion预检与正式导入一致性收敛 | 3 | READY |
| F4-兼容样本库与回归体系 | 2 | READY |

**统计**: READY=11, IN_PROGRESS=0, DONE=0, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-14-202604/README.md`

## Sprint-15: 平台工作台 · 领导视角重构 (202604)
**状态**: READY
**类型**: Implementation（UI 重构 + 后端聚合端点新增 + 遗弃功能清理）
**目标**: 把 `dts-platform-webapp` 工作台首页从"数据治理 / 资产沉淀"通用视角重构为**领导视角概览**，只保留**报表**与**数据资产**两块，按登录人角色（员工 / 部门领导 / 所领导）自适应默认范围；同时彻底清理已失联的收藏功能

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-后端聚合端点与业务域过滤 | 7 | READY |
| F2-收藏功能彻底清理 | 5 | READY |
| F3-前端角色与筛选器 | 5 | READY |
| F4-前端KPI与业务域矩阵 | 4 | READY |
| F5-前端报表块与核心资产块 | 5 | READY |
| F6-埋点与E2E | 4 | READY |

**统计**: READY=31, IN_PROGRESS=0, DONE=0, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-15-202604/README.md` + `docs/superpowers/specs/2026-04-24-platform-workbench-leader-overview-design.md`

## Sprint-17: 大屏访问对接 Leader-Overview (202604)
**状态**: IN_PROGRESS
**类型**: Implementation（跨服务对接 + 前端埋点 + 数据同步）
**目标**: 让 dts-bi 的"大屏管理"中的大屏访问能体现在工作台"我的概览"的"我常用的报表"中，闭环 Sprint-15 上线后用户实际看到列表为空的设计断层。

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-后端Screen同步与配置 | 5 | READY |
| F2-前端Preview埋点 | 3 | READY |
| F3-验证与回归 | 2 | READY |

**统计**: READY=10, IN_PROGRESS=0, DONE=0, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-17-202604/README.md`

## Sprint-18: 企业级数据接入中心 Phase 1 (202604)
**状态**: IN_PROGRESS
**类型**: Implementation（接入中心主链路收敛 + ODS 契约固化 + 离线文件接入）
**目标**: 在不引入 Airbyte 的前提下，把 DTS 数据接入中心第一阶段做成可交付能力：数据库、Excel、CSV 均统一落 ODS，源数据不做业务计算，允许追加 DTS 技术血缘字段，后续所有清洗、映射、标准化和业务口径都从 dbt `stg` 开始。

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-ODS原样落地契约与技术字段 | 5 | DONE |
| F2-数据库接入自动建ODS收敛 | 5 | DONE |
| F3-Excel/CSV离线文件接入 | 5 | DONE |
| F4-执行批次血缘与运行观测 | 4 | DONE |
| F5-dbt stg建模入口与source元数据 | 5 | DONE |
| F6-前端向导与验收门禁 | 4 | IN_PROGRESS |

**统计**: READY=1, IN_PROGRESS=0, DONE=27, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-18-202604/README.md`

## Sprint-19: OpenMetadata 元数据采集闭环修复 (202604)
**状态**: DONE
**类型**: Implementation（OpenMetadata 集成修复 + 元数据采集闭环 + 运维验收）
**目标**: 把现有 OpenMetadata 相关配置、采集、血缘、质量和平台查询能力从“部分接入但不稳定”收敛为可交付闭环：服务可用、采集可触发、FQN 可解析、血缘可注册、失败可观测、本地 catalog 回退边界清晰。

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-配置与部署基线 | 4 | DONE |
| F2-Ingestion适配层 | 4 | DONE |
| F3-血缘注册与标签治理 | 4 | DONE |
| F4-OpenMetadata采集作业运维化 | 4 | DONE |
| F5-平台读路径与本地回退 | 4 | DONE |
| F6-测试验收与发布材料 | 3 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=23, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-19-202604/README.md`

## Sprint-20: Data Lineage 端到端可视化打通 (202604)
**状态**: READY
**类型**: Implementation（跨 dts-ingestion / dts-platform / dts-platform-webapp 三模块）
**目标**: 把"采集 → 编排 → 加工 → 资产 → 可视化"主链路上散落的 lineage 信号收敛成统一血缘图，前端 LineagePage 能从源系统追到 BI 报表，支持影响分析、列级追溯、时间旅行。基于 ELT 链路 review 的 8 个断点（Addax 入湖未回写血缘、Airflow DAG 无 inlets/outlets、IngestionExecution 不记 source/target、列级缺失、无 job 节点维度、前端固定栅格、无时间旅行、API 缺 includeColumns 等）。

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-Addax入湖血缘自动回写 | 4 | READY |
| F2-Airflow执行级血缘 | 4 | READY |
| F3-列级血缘 | 4 | READY |
| F4-Job节点与Pipeline维度 | 3 | READY |
| F5-前端可视化重做 | 5 | READY |
| F6-时间旅行与Diff | 3 | READY |
| F7-集成验收 | 3 | READY |

**统计**: READY=26, IN_PROGRESS=0, DONE=0, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-20-202604/README.md`

## Sprint-21: DTS Connector Center 工业级数据接入中心 (202604)
**状态**: IN_PROGRESS
**类型**: Implementation（接入中心 Phase 2，产品化接入内核 + 运行治理）
**目标**: 在 Sprint-18 已完成数据库/文件入 ODS 主链路、Sprint-20 正在打通血缘可视化的基础上，把 DTS 数据接入能力升级为工业级 Connector Center：连接器可治理、数据源可管理、Schema 可探测、任务可向导生成、运行可观测、质量可预检、权限和审计可交付。

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-Connector Registry 连接器目录 | 4 | DONE |
| F2-数据源中心与凭据治理 | 4 | DONE |
| F3-Schema Discover 探测服务 | 5 | DONE |
| F4-ODS 与 dbt source 自动生成 | 4 | DONE |
| F5-同步任务向导与批量建任务 | 5 | DONE |
| F6-接入任务运行中心与可观测 | 5 | DONE |
| F7-质量预检与增量治理 | 5 | DONE |
| F8-安全审计、验收与发布材料 | 6 | IN_PROGRESS |

**统计**: READY=0, IN_PROGRESS=1, DONE=37, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-21-202604/README.md`

## Sprint-22: Portal Session 安全架构升级（Admin Token 剥离 + 短 TTL + BFF/HttpOnly Cookie） (202604)
**状态**: READY
**类型**: Architecture / Security（dts-platform-webapp + dts-platform + Keycloak realm）
**目标**: 把当前"前端持有 portal + admin 双套 token、access/refresh 全部明文写 localStorage"的会话模型，分三阶段升级为"凭据由服务端持有、浏览器只见 SID cookie"的 BFF 架构，从根本消除 XSS 直取凭据的可能性，并把"被盗 token 的可重放窗口"从小时级压到分钟级。

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-Admin Token 前端剥离 | 4 | READY |
| F2-Portal Token 短 TTL + Refresh Rotation | 5 | READY |
| F3-Leader 选举切换至 navigator.locks | 3 | READY |
| F4-生产构建剔除 TEST_SESSION 旁路 + Dev Fallback host allowlist | 3 | READY |
| F5-移除生产 console 中的 Authorization / 响应体打印 | 2 | READY |
| F6-BFF 层骨架（Spring Security OAuth2 Client + Session Cookie） | 6 | READY |
| F7-前端切换：axios withCredentials + 移除 token 持久化 + CSRF 注入 | 5 | READY |
| F8-Keycloak Backchannel Logout 接入 | 3 | READY |
| F9-SessionManager 简化（删除 leader/refresh/tokenSync） | 3 | READY |
| F10-行为级测试与 e2e 闭环 | 5 | READY |

**统计**: READY=39, IN_PROGRESS=0, DONE=0, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-22-202604/README.md`
**评审研判**: `worklog/v2.2.3/sprint-22-202604/review/session-management-audit.md`

## Sprint-24: 大屏密级管理 UX 修复（入口前移 + 列表可见 + 强制设密 + 裸屏盘点） (202605)
**状态**: DONE（F1-F5 全部完成，待 CI 验证）
**类型**: UX / Compliance（dts-platform-webapp + dts-analytics）
**目标**: 把"大屏密级"从一个隐蔽、可漏填、需要专门去找的设置项，改造成进入即可见、创建即必填、漏填可盘点的合规底线能力。源起于 fix/dashboard-access-h1-h3 PR review 时发现的 UX 缺陷链——后端密级控制已完整（Step 1-3），但前端入口埋在分享弹窗顶部、列表不可见、创建可漏填，导致生产中已存在 `classification=null` 的"裸屏"对所有登录用户开放。

| Feature | Task 数 | 优先级 | 状态 |
|---------|---------|--------|------|
| F1-编辑器属性面板密级入口 | 3 | P0 | DONE |
| F2-列表卡片密级 Tag | 2 | P0 | DONE |
| F3-创建对话框强制选择密级 | 4 | P0 | DONE |
| F4-裸屏盘点入口 | 4 | P1 | DONE |
| F5-降级二次确认 | 3 | P2 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=5, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-24-202605/README.md`
**集成测试**: `worklog/v2.2.3/sprint-24-202605/it/README.md`
**实施分支**: `feat/sprint-24-classification-ux`（5 commits）

## Sprint-28: 服务间鉴权方案 B 中期落地（202605）
**状态**: DONE（代码 + 文档闭环;真链路 E2E 留运维 IT）
**类型**: Architecture / Security（dts-platform + dts-ingestion + dts-analytics）
**目标**: 拆 `DtsAdminProperties` 双重语义为 outbound/inbound 两个独立 bean，每对调用独立 secret，filter 强校验关闭"白名单即权限"越权面，旧 env 兼容 fallback 实现零停机切换。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-platform-properties-split | P0 | 6 | DONE |
| F2-platform-inbound-per-pair-secret | P0 | 5 | DONE |
| F3-platform-filter-strict-auth | P0 | 5 | DONE |
| F4-ingestion-outbound-rename | P0 | 6 | DONE |
| F5-analytics-outbound-rename | P0 | 7 | DONE |
| F6-auth-audit-logging | P1 | 4 | DONE |
| F7-compat-matrix-and-it | P0 | 7 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=40, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-28-202605/README.md`
**集成测试**: `worklog/v2.2.3/sprint-28-202605/it/README.md`
**部署文档**: `worklog/v2.2.3/sprint-28-202605/assets/env-migration-matrix.md` + `sprint-28-deploy-runbook.md`
**实施分支**: `feat/sprint-28-platform-auth-split`

## Sprint-29: Dify 风格工作流编辑器（reactflow 抄 dify 架构）(202605)
**状态**: IN_PROGRESS
**类型**: Feature / Frontend Architecture（dts-platform-webapp + dts-platform 后端 schema）
**目标**: 把当前 ETL/数据入湖任务的"表单式配置"升级为画布式可视化编排，参照 Dify `web/app/components/workflow/` 整套架构（reactflow 之上自建 BlockSelector/CandidateNode/CustomEdge/HelpLine/Panel/DSL 等子系统），全程严格遵守 Chrome 95 兼容性约束。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F0-chrome95-precondition (structuredClone polyfill) | P0 | 1 | DONE |
| F1-foundation-canvas (zustand store + 画布壳 + 自定义边/对齐线/工具栏) | P0 | 7 | DONE |
| F2-block-selector-dnd (节点库面板 + popover + CandidateNode + 自动连边) | P0 | 4 | READY |
| F3-etl-node-set (BaseNode + 6 类 ETL 节点) | P0 | 7 | READY |
| F4-panel-and-dsl (NodePanel 抽屉 + 表单 + DSL 序列化 + 后端 graph_dsl 字段 + OrchestrationPage 接入) | P0 | 6 | READY |
| F5-advanced-features (iteration/loop subflow + 右键菜单 + 快捷键 + 撤销重做 + 便签) | P0 | 6 | READY |

**统计**: READY=23, IN_PROGRESS=0, DONE=8, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-29-202605/README.md`
**集成测试**: `worklog/v2.2.3/sprint-29-202605/it/README.md`
**关键决策**: 接入点替代 OrchestrationPage（保留运行实例 Tab）；后端新增 `IngestionTask.graph_dsl jsonb`；F5 保留 iteration（多表批量）+ loop（增量同步），不做嵌套子流程模板复用。
**实施分支**: `feat/sprint-29-workflow-canvas`（已创建，HEAD 与 v2.2.3 对齐）
**F0 复核**: polyfill 在历史 `fix:chrome95` 提交链已落地，证据见 `worklog/v2.2.3/sprint-29-202605/assets/chrome95-polyfill-test-evidence.md`，本 Sprint 无新增代码，直接进入 F1。

## Sprint-30: 地铁小模型 CSV 训练快照正式版 (202605)
**状态**: ABANDONED（废弃）
**类型**: Implementation（dts-ingestion / dts-platform / dbt package / metro-stack）
**目标**: 把演示版地铁 LSTM 小模型链路升级为正式 CSV 训练快照闭环：运营商 CSV 经 DTS 入湖、dbt 治理建模和专家经验融合后，导出标准训练快照包，metro-stack 按契约读取快照并训练。
**废弃说明**: 2026-05-16 决策，正式实现复杂度超出 v2.2.3 维护分支目标；Sprint-30 在 v2.2.3 废弃，后续新 feature 统一回到 `main` 主干重新规划和开发，`v2.2.3` 分支仅保留维护、缺陷修复和必要兼容性调整。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-csv-snapshot-contract | P0 | 2 | DONE（已归档） |
| F2-dts-snapshot-export | P0 | 3 | ABANDONED |
| F3-metro-stack-snapshot-consumer | P0 | 3 | ABANDONED |
| F4-end-to-end-it | P0 | 3 | ABANDONED |
| F5-large-csv-performance | P0 | 5 | ABANDONED |

**统计**: ABANDONED=1 sprint；已完成并归档=F1；不再继续推进=F2/F3/F4/F5
**设计文档**: `worklog/v2.2.3/sprint-30-202605/README.md`
**实施计划**: `worklog/v2.2.3/sprint-30-202605/assets/implementation-plan.md`
**性能要求**: 正式链路需支撑 100MB-500MB / 几十万到 1,000,000 行 CSV；该能力转入 `main` 后续规划，v2.2.3 不再承诺 Sprint-30 完成交付。
**关键决策**: Sprint-30 不做 Parquet；正式版交换格式为治理后的 CSV 训练快照包，包含 `manifest.json`、`schema.json`、`quality_report.json`、`lineage.json`、`data.csv`。

## Sprint-31A: 企业级数据资产事实源重构 (202605)
**状态**: CONTRACT_DONE / RUNTIME_PARTIAL（运行时收口见 Sprint-31B）
**类型**: Architecture / Implementation（dts-platform + dts-platform-webapp）
**目标**: 在 Sprint-31 主链路补齐和 Sprint-32 `dts-metrics` 独立服务之前，先把 `dts-platform` 的数据资产模块收敛为企业级唯一事实源，统一资产身份、生命周期、治理字段、权限校验、血缘入口和对外读取契约。
**执行约束**: 按当前执行决策，Sprint-31A -> Sprint-31 -> Sprint-32 过程中不做完整中间编译、镜像构建和容器重建；评审问题需要代码级闭环时允许执行 focused contract/unit tests，并将证据归档到最终验收目录。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-asset-identity-lifecycle | P0 | 5 | DONE |
| F2-governance-contract | P0 | 5 | DONE |
| F3-lineage-provenance | P0 | 5 | DONE |
| F4-permission-classification | P0 | 5 | DONE |
| F5-asset-portal-ux | P1 | 4 | DONE |
| F6-migration-compatibility | P0 | 5 | DONE |
| RX-architect-review-hardening | P0 | 5 | CONTRACT: DONE / RUNTIME: PARTIAL（Sprint-31B 收口） |

**统计**: READY=0, RUNTIME_PARTIAL=1, CONTRACT_DONE=1, DONE=33, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-31a-202605/README.md`
**能力契约**: `worklog/v2.2.3/sprint-31a-202605/assets/asset-capability-contract.md`
**集成测试**: `worklog/v2.2.3/sprint-31a-202605/it/README.md`

## Sprint-31: 企业级数据平台主链路补齐 (202605)
**状态**: DONE
**类型**: Architecture / Implementation（dts-platform + dts-ingestion + dts-analytics + dts-platform-webapp）
**目标**: 基于 Sprint-31A 的企业级资产事实源，把 DTS 从“数据接入、dbt 建模、资产目录、语义指标、大屏消费的初级功能集合”收敛成一条可验收的企业级数据产品主链路：连接器接入、ODS/DWD/DWS/ADS、发布门禁、运行血缘、资产治理、语义指标、BI/大屏消费、platform 统一权限。
**依赖**: Sprint-31A 先提供资产身份、治理字段、血缘、权限和读取契约；Sprint-31 不再重新定义资产事实源。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-golden-path-contract | P0 | 5 | DONE |
| F2-connector-center-hardening | P0 | 5 | DONE |
| F3-runtime-lineage-governance | P0 | 6 | DONE |
| F4-dbt-release-gate | P0 | 5 | DONE |
| F5-semantic-metric-productization | P0 | 6 | DONE |
| F6-platform-permission-consumption | P0 | 5 | DONE |
| F7-observability-performance-admission | P1 | 5 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=37, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-31-202605/README.md`
**评审报告**: `worklog/v2.2.3/sprint-31-202605/assets/full-code-review.md`
**集成测试**: `worklog/v2.2.3/sprint-31-202605/it/README.md`

## Sprint-32: React Flow 指标与语义工作台 (202605)
**状态**: IN_PROGRESS
**类型**: Productization / Implementation（dts-metrics-webapp + dts-metrics + dts-platform + dbt gateway）
**目标**: 在 `dts-metrics` 独立服务基线已完成后，用 React Flow 重构指标与语义中心，把资产、业务对象、Join、指标、筛选、DWS/ADS、验证、发布和消费收敛到同一张可验证图。
**依赖**: `dts-metrics` 只能消费 platform 的资产、字段、权限、RLS、治理解析、审批、审计、dbt 验证/发布、BI Dataset 和血缘契约；模型检测入口调用 `dts-platform`，由 platform 内部执行 dbt compile/test/build/release gate。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-platform-contracts-and-dbt-validation | P0 | 5 | IN_PROGRESS |
| F2-react-flow-semantic-canvas | P0 | 5 | IN_PROGRESS |
| F3-metric-formula-designer | P0 | 5 | READY |
| F4-dws-ads-model-composer | P0 | 5 | READY |
| F5-validation-publish-consumption | P0 | 5 | READY |
| F6-compatibility-it-rollout | P0 | 4 | READY |

**统计**: READY=26, IN_PROGRESS=2, DONE=1, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-32-202605/README.md`
**服务拆分设计**: `worklog/v2.2.3/sprint-32-202605/assets/dts-metrics-service-design.md`
**React Flow 契约**: `worklog/v2.2.3/sprint-32-202605/assets/react-flow-metrics-contract.md`
**集成测试**: `worklog/v2.2.3/sprint-32-202605/it/README.md`

## Sprint-31B: Sprint-31A RX 运行时收口与代码质量加固 (202605)
**状态**: IN_PROGRESS
**类型**: Implementation / Hardening（dts-platform + dts-metrics + dts-platform-webapp）
**目标**: 收尾 Sprint-31A 的 RX 运行时强制（T03/T04/T05 后半段），修复阶段性提交的性能与安全 review 发现，统一 Sprint-31A 状态口径与 evidence，并为 Sprint-32 最终统一 IT 提供 cheap compile 前置验证。
**前置依赖**: Sprint-31A RX/T03 主体 + T04/T05 部分已完成；本 Sprint 完成后立即跑 cheap compile-only，完整 IT 仍统一留给 Sprint-32 最终阶段。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-rx-runtime-closure | P0 | 6 | DONE |
| F2-rls-publish-and-masking-closure | P0 | 5 | IN_PROGRESS（T01/T02/T03 DONE；audit / live IT 待接入） |
| F3-code-quality-and-security-hardening | P0 | 5 | DONE |
| F4-sprint-31a-gap-and-status-rectification | P1 | 4 | DONE |
| F5-sprint-31-cheap-compile-verification | P0 | 4 | READY |
| F6-frontend-acceptance-recovery | P0 | 5 | DONE |

**统计**: READY=6, IN_PROGRESS=0, DONE=23, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-31b-202605/README.md`
**集成测试**: `worklog/v2.2.3/sprint-31b-202605/it/README.md`
**关键决策**:
- 把 Sprint-31A RX/T03-T05 未闭环的运行时项收尾，包括 `findAll().stream()` hot path 替换、IdentityResolver 兼容代理、policy publish gate 复用与 column masking。
- 修正 Sprint-31A 状态口径：契约 DONE / 运行时 PARTIAL（由 Sprint-31B 收口）。
- 本 Sprint 结束时跑 cheap compile-only 验证（platform + metrics + webapp tsc），把跨模块签名漂移在最低成本暴露。
- 2026-05-17 已完成 hot path 索引化、`urn:uuid` 旧引用解析、DataStandard/Glossary/SvcApi writer、policy v1 + 403、service-auth/capability 同步、dataset miss warn+counter、`apply_rls=true` 空策略失败；SQL masking 已接入，strict policy miss 与 full cheap compile 仍未闭环。
- 2026-05-18 完成 dts-metrics 发布预检路径：重新解析 platform policy、计算 predicate hash、调用 platform release gate，并在响应返回 `appliedPolicySource` / `appliedPredicateHash`。
- 2026-05-18 新增前端验收口径：数据资产、语义指标、BI 消费能力必须以“页面可操作”为 DONE 标准；已先补资产解析失败报告入口，其余 assets-v2 详情、数据产品成员配置、治理缺口处置、dts-metrics 真实页面仍待闭环。

## Sprint-33: 角色管理成员分配重构 (202605)
**状态**: DONE（聚焦验证通过；运行时手工 smoke 待现场环境补证）
**类型**: UX / Refactor / Contract（dts-admin + dts-admin-webapp）
**目标**: 把角色编辑页从“按部门下拉逐个添加成员”重构为“角色基础信息模块 + 可查询分页用户表”，支持按部门、姓名、用户名筛选，已在角色内的用户默认勾选，并通过现有审批流提交成员增删差异。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-assignment-user-query-contract | P0 | 3 | DONE |
| F2-role-edit-member-table | P0 | 4 | DONE |
| F3-verification-and-it | P0 | 3 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=10, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-33-202605/README.md`
**实施计划**: `worklog/v2.2.3/sprint-33-202605/assets/implementation-plan.md`
**集成测试**: `worklog/v2.2.3/sprint-33-202605/it/README.md`
**完成记录**:
- 2026-05-19 完成 `GET /api/admin/roles/{name}/assignment-users` 查询契约、角色编辑页成员分配表格、差异审批 payload 接入。
- 2026-05-19 通过后端 focused test、前端 source-level test 和 `dts-admin-webapp` 生产构建；运行时浏览器 smoke 留给联调环境补证。

## Sprint-34: 审计目录 DB 化与 Platform/Analytics 人工操作审计重构 (202605)
**状态**: DONE（代码与 focused 验证完成；现场 smoke 待部署补证）
**类型**: Architecture / Implementation / Compliance（dts-admin + dts-platform + dts-analytics + dts-common）
**目标**: 将审计模块、动作、路由映射的运行时事实源从中心 JSON/路径猜测迁移到 dts-admin 数据库目录，确保 platform 与 analytics 持续新增模块时可以通过可治理目录注册审计动作，并只记录人工操作痕迹。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-db-audit-catalog | P0 | 4 | DONE |
| F2-admin-classification-runtime | P0 | 4 | DONE |
| F3-platform-human-audit-actions | P0 | 4 | DONE |
| F4-verification-review-it | P0 | 3 | DONE |
| F5-analytics-human-audit-actions | P0 | 4 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=19, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-34-202605/README.md`
**实施计划**: `worklog/v2.2.3/sprint-34-202605/assets/implementation-plan.md`
**集成测试**: `worklog/v2.2.3/sprint-34-202605/it/README.md`
**关键决策**:
- DB catalog 是唯一运行时审计分类权威；JSON 只保留 seed/export/迁移兼容地位。
- 2026-05-23 Loop 2 完成审计中心筛选项 DB catalog 化与 HTTP fallback 降噪；现场 smoke 留部署环境补证。
- 2026-05-23 Loop 3 完成 common catalog 缺失动作启动导入和 miss 累计，降低升级后既有 platform 动作大面积未分类风险。
- 2026-05-23 Loop 4 完成 dts-analytics actionCode 转发、analytics catalog seed、未分类治理和审计中心展示映射。
- 未注册 actionCode 不再被 URI 猜成业务模块，统一进入未分类治理队列。
- HTTP fallback 只作为漏埋点保护网，不能产出大量支撑查询审计。
**Loop 1 结果**:
- dts-admin DB catalog、sourceSystem 透传、unknown action miss 已完成并通过 focused test。
- platform 主题域和报表动作码已收敛；剩余审计中心筛选项 DB 化和 fallback 降噪进入下一轮。

## Sprint-35: dts-metrics 数据仓库可视化设计重构 (202605)
**状态**: IN_PROGRESS
**类型**: Architecture / Productization / Implementation Plan（dts-metrics-webapp + dts-metrics + dts-platform + dbt gateway）
**目标**: 基于 Sprint-32 的 React Flow 指标工作台方案，补齐“源数据库清洗到数据仓库之后，dts-metrics 从哪一层开始进行可视化设计”的硬边界，并按“架构与 PRD -> 前后端 API -> 前端 -> 后端 -> 安全与评审”顺序拆成可执行 feature/task。
**关键决策**: 默认从已发布、已治理、可授权读取的 DWS/ADS 资产进入指标可视化；DWD 只作为高级建模上游，用于生成新的 DWS 候选模型；ODS/STG 只用于 lineage 和诊断，不进入普通指标画布。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-architecture-prd | P0 | 5 | READY |
| F2-api-contracts | P0 | 5 | IN_PROGRESS（T01/T02 DONE；T03-T05 IN_PROGRESS） |
| F3-frontend-visual-workbench | P0 | 5 | IN_PROGRESS（T03 DONE；T01/T02/T04/T05 IN_PROGRESS） |
| F4-backend-modeling-dbt-gateway | P0 | 5 | IN_PROGRESS（T01/T03 DONE；T02/T04 IN_PROGRESS） |
| F5-security-review-it | P0 | 5 | IN_PROGRESS（T03 DONE） |

**统计**: READY=10, IN_PROGRESS=9, DONE=6, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-35-202605/README.md`
**ELT 分层 PRD**: `worklog/v2.2.3/sprint-35-202605/assets/dts-metrics-elt-layer-prd.md`
**API 契约**: `worklog/v2.2.3/sprint-35-202605/assets/dts-metrics-api-contract.md`
**评审机制**: `worklog/v2.2.3/sprint-35-202605/assets/review-mechanism.md`
**集成测试**: `worklog/v2.2.3/sprint-35-202605/it/README.md`

## Sprint-36: 数据安全与机密级合规整改专项 (202606)
**状态**: PLANNING
**类型**: Security / Compliance / Implementation Plan（dts-keycloak + dts-admin + dts-platform + dts-admin-webapp + dts-platform-webapp）
**目标**: 闭合协议 2.3.2.5（数据安全）与 2.3.2.10（安全保密）中阻断机密级（BMB17.1/17.2-2024）测评验收的 P0 缺口，并补齐敏感数据自动识别 P1 能力，TDD 驱动。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-password-policy-lockout | P0 | 5 | READY |
| F2-session-security-remediation | P0 | 5 | READY |
| F3-operation-permission-matrix | P0 | 5 | READY |
| F4-sensitive-data-auto-discovery | P1 | 5 | READY |
| F5-bmb-baseline-assessment-ledger | P0 | 5 | READY |
| F6-security-review-it-gate | P0 | 5 | READY |

**统计**: READY=31, IN_PROGRESS=0, DONE=0, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-36-202606/README.md`
**差距分析报告**: `worklog/v2.2.3/sprint-36-202606/assets/protocol-gap-analysis-v3.md`（协议 11 模块，13 项 P0 + 29 项 P1）
**证据底稿**: `worklog/v2.2.3/sprint-36-202606/assets/gap-evidence/M01..M11.md`
**集成测试**: `worklog/v2.2.3/sprint-36-202606/it/README.md`
**主题聚焦决策**:
- 协议 13 项 P0 横跨 6 模块，单 sprint 不可全包；本期选「数据安全+机密级合规」单主题，因其是验收硬门槛、P0 最密集（独占 6 项）、技术内聚、单 sprint 可落地。
- 其余 P0 排期：M04 生命周期/销毁 → Sprint-37；M09 告警规则 + M11 高可用/性能 → Sprint-38（见报告 §6 roadmap）。
- sprint-32~35 未闭合 M05/M10 任何前序缺口；机密级口令控制/操作权限矩阵/敏感识别/BMB 映射均缺失。

## Sprint-37: 数据入湖上传文件加密专项 (202606)
**状态**: PLANNING
**类型**: Security / Compliance / Implementation Plan（dts-ingestion + services/dts-airflow/runner + dts-airflow DAG + docker compose）
**目标**: 入湖上传的 Excel/CSV 实现「磁盘恒密文 + 明文仅容器内存(tmpfs)运行期即焚」，宿主机（含 root）从文件系统目录不可见明文，且不影响 Addax 入湖功能。TDD 驱动。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-upload-file-encryption | P0 | 5 | READY |
| F2-addax-runtime-decryption | P0 | 5 | READY |
| F3-security-verification-it | P0 | 5 | READY |

**统计**: READY=15, IN_PROGRESS=0, DONE=0, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-37-202606/README.md`
**现状调查**: `worklog/v2.2.3/sprint-37-202606/assets/upload-file-exposure-investigation.md`
**集成测试**: `worklog/v2.2.3/sprint-37-202606/it/README.md`
**关键决策**:
- 现场实锤：入湖上传 Excel/CSV 明文落盘 bind 目录 + chmod o+r，宿主机含 root 可直接查看；逐一排除证明无自动清理机制（容器销毁/--force-recreate/dts-reset 均不删 uploads）。
- 方案：复用 InfraSettingsCryptoService（AES-GCM）加密落盘；AddaxEnvRunner（dts 自有 wrapper）运行期解密到 tmpfs，Addax 零改动。
- 残余边界：明文运行期在容器 tmpfs（内存），root 经 docker exec 仍可读；达成口径为「宿主机磁盘目录不可见明文」，消除内存明文需换入湖引擎（Backlog）。
- 独立于 Sprint-36；原 roadmap 数据管理生命周期能力已并入 Sprint-39 主链路规划，可观测性与高可用顺延后续 sprint。

## Sprint-38: 基于应用系统 API 的数据入湖重构 (202606)
**状态**: IN_PROGRESS
**类型**: Refactor / Feature（dts-ingestion + dts-platform + dts-platform-webapp）
**目标**: 以「我方调用应用系统 API 拉取数据入湖」为第一目标，端到端重构：数据源连接、凭据安全、Java 执行器（替换 385 行内嵌 Python）、Airflow 瘦触发、前端任务向导；消除 API 路径全部硬编码与逻辑缺陷。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-API数据源与凭据安全 | P0 | 4 | IN_PROGRESS |
| F2-Java执行器 | P0 | 6 | IN_PROGRESS |
| F3-任务编排与调度集成 | P0 | 4 | IN_PROGRESS |
| F4-前端改造 | P1 | 3 | IN_PROGRESS |
| F5-配置外部化与旧路径下线 | P1 | 3 | IN_PROGRESS |

**统计**: READY=2, IN_PROGRESS=11, DONE=7, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-38-202606/README.md`
**审计底稿**: `worklog/v2.2.3/sprint-38-202606/assets/api-ingestion-audit.md`
**集成测试**: `worklog/v2.2.3/sprint-38-202606/it/README.md`
**关键决策**:
- 方向 = 出站拉取（我方持客户凭据调对方 API）；入站推送（对方推我方接收端点）为后续 sprint。
- 执行引擎 = Java 执行器（死代码 SPI ApiHttpSourceConnector 落地），调度 = C1 瘦触发（Airflow 只触发+轮询，业务逻辑回归 dts-ingestion 进程，与 JDBC/文件运维一致）。
- 密钥 = 数据源 secrets 加密落库 + 进程内解密（复用 IngestionSourceResolver/InfraSettingsCryptoService），废除 env 明文路径；明文不出服务边界。
- 鉴权已确认（2026-06-12）：客户对接用 **JWT token**（对方应用登录端点换短时 token，在对方给出的 apikey/basic/jwt 三选项中选定）。GA = jwtLogin(P0 主路径)+bearer/apikey/basic+OAuth2(同形态)；签名/mTLS 维持 PREVIEW。
- **编号顺延**：原 roadmap 的 M04 生命周期能力并入 Sprint-39“结构化数据黄金链路与商业化闭环”，M09 告警+M11 高可用顺延后续 sprint（客户 API 对接和产品主链路为现场优先需求）。

## Sprint-35b: dts-metrics 架构收口与持久化硬化 (202606)
**状态**: 主体 DONE（6 缺陷骨架全闭；F5 record 类型化与若干 followup 余留）
**类型**: Architecture Hardening / Implementation（dts-metrics，跨服务依赖 dts-platform）
**目标**: 闭合 Sprint-35 的 dts-metrics 完成标准，落实架构评审 6 缺陷修复（metrics 侧），把服务从"内存态原型"推到"可水平部署、可验收"。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-领域持久化层 | P0 | 5 | DONE |
| F2-安全链路统一与审计收口 | P0 | 4 | DONE |
| F3-发布一致性与跨服务收口 | P0 | 3 | DONE (metrics 侧) |
| F4-韧性与契约对齐 | P1 | 3 | DONE (T01/T02/T03) |
| F5-领域类型化 | P1 | 2 | IN_PROGRESS (拆分 DONE; record 化 followup) |
| F6-IT准入与验收证据 | P0 | 3 | DONE |

**统计**: DONE=5, IN_PROGRESS=1（F5 类型化）, READY=0, BLOCKED=0
**进度（2026-06-14）**: 6 缺陷收口——#1 持久化 ✅ / #2 安全对等 ✅ / #3 发布闭环 ✅（metrics 侧幂等+有序注册+PUBLISH_BLOCKED；outbox 重试=followup）/ #4 类型化 🟡（artifact builder 拆分 ✅ lifecycle 799→502 行，record 化 followup）/ #5 超时 ✅ / #6 契约漂移 ✅（visual-assets 文档收口 + 逐资产 permissionDecision 透传）。F6 IT 证据已落 `it/evidence/`（持久化/安全对等/发布闭环/韧性，逐条阻断条件映射 + 原始 surefire）。**验收基线**: 单元 96 + 持久化/artifact IT 7 = **103 例全绿**（含真实 Postgres 并发锁 409 验证）。**跨服务 followup**: platform 侧 register/audit/visual-assets 端点本体 + 端到端联调。**余留**: F5 record 化、#3 outbox 重试、F4 受限重试。提交 77fae55/3ecc46a/95d5c316/33c4834/4ba6851，分支 feat/sprint-35b-dts-metrics-hardening。
**设计文档**: `worklog/v2.2.3/sprint-35b-202606/README.md`
**架构评审底稿**: `worklog/v2.2.3/sprint-35b-202606/assets/architecture-review.md`
**集成测试**: `worklog/v2.2.3/sprint-35b-202606/it/README.md`
**关键决策**:
- 定位为 Sprint-35 硬化续期（参照 31a/31b 对 31）；后续 Sprint-39 已升级为“结构化数据黄金链路与商业化闭环”，原 M04 生命周期能力并入 Sprint-39 F1/F3，M09+M11 仍顺延后续 sprint。
- 🔴 最严重缺陷=事实源零持久化（状态全在 ConcurrentHashMap）；修复序=持久化→安全对等→发布一致性→韧性/契约→类型化→IT。
- 持久化 datasource/Liquibase **mirror 同仓 dts-platform/dts-admin**，dts-metrics 用独立 schema；不自创基础设施。
- 跨服务边界：BI/lineage/audit register 端点本体属 platform 职责，本期只做 metrics 侧编排与调用 + 联调标注。
- F1+F2+F4-T01 经 Workflow 多代理在分支 `feat/sprint-35b-dts-metrics-hardening` 实现，design-first + 末段并行 build/test/review/gitnexus 影响分析。

## Sprint-39: 结构化数据黄金链路与商业化闭环 (202606)
**状态**: DONE
**类型**: Product Foundation / Implementation（dts-platform + dts-ingestion + dts-metrics + dts-platform-webapp）
**目标**: 面向传统行业结构化数据客户，把现有数据接入、入湖、建模、治理、资产、权限、报表、数据服务和运维能力串成一条可验收主链路，先打牢商业产品基础，再演进现代湖仓路线。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-结构化数据黄金链路状态机 | P0 | 4 | DONE |
| F2-接入入湖到建模产品闭环 | P0 | 5 | DONE |
| F3-治理资产权限硬门禁 | P0 | 4 | DONE |
| F4-任务运维中心产品化 | P1 | 4 | DONE |
| F5-业务消费闭环 | P1 | 4 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=21, BLOCKED=0
**进度（2026-06-15）**: F1/F2/F3/F4/F5 全部完成；F2 已补齐平台建模闭环 API。Sprint-39 已覆盖黄金链路状态机、接入入湖到建模闭环、治理资产权限硬门禁、任务运维中心产品化和业务消费闭环。
**设计文档**: `worklog/v2.2.3/sprint-39-202606/README.md`
**能力契约**: `worklog/v2.2.3/sprint-39-202606/assets/product-capability-contract.md`
**集成测试**: `worklog/v2.2.3/sprint-39-202606/it/README.md`
**关键决策**:
- 客户主场景 = 传统行业结构化数据；JDBC/API/file 是 GA 主路径，现代湖仓表格式、流批一体、成本优化进入后续增强。
- 产品主线 = `数据源 -> 入湖任务 -> ODS -> DWD/DWS/ADS -> 质量/血缘/资产登记 -> 权限/审批 -> 指标/报表/数据服务 -> 运维监控`。
- dbt 仍是当前主建模引擎，但用户侧表达为“建模方案/指标模型/数据集发布”，不暴露手工导入作为默认流程。
- 治理从“登记项”升级为“发布门禁”：owner、分级、质量、血缘、权限缺失时阻断发布或进入待治理状态。
- dts-metrics 只承接治理后的 DWS/ADS 业务语义和候选 artifact；platform 仍是资产、权限、RLS、审计、审批、dbt 发布和 BI 注册控制面。

## Sprint-40: 业务消费闭环 UI 补强 (202606)
**状态**: DONE
**类型**: UI Productization / Implementation（dts-platform-webapp + dts-admin menu seed）
**目标**: 在 Sprint-39 已完成黄金链路后端与工作日志基础上，把业务消费闭环补成客户可见的服务中心入口。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-业务消费工作台页面与菜单入口 | P0 | 3 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=3, BLOCKED=0
**进度（2026-06-14）**: 新增 `/services/consumption` “业务消费工作台”，接入菜单 seed、角色默认项、静态路由和动态菜单解析；页面覆盖报表数据集、指标入口、数据 API、数据产品、权限一致和客户验收包；前端契约、运维路由回归、生产构建和 whitespace 检查均通过。
**设计文档**: `worklog/v2.2.3/sprint-40-202606/README.md`
**集成测试**: `worklog/v2.2.3/sprint-40-202606/it/README.md`
**关键决策**:
- Sprint-39 保持 DONE；本 sprint 作为 UI 补强独立记录，避免重新打开已完成的后端主线。
- 页面只做工作台编排与导航，不新增后端接口，不替换已有报表、指标、API、数据产品页面。

## Sprint-41: 语义层整合 Phase 1 — 受控建模逻辑移植 (202606)
**状态**: DONE（F1+F2+F3 全部完成；源表层级/标准码 gating 依赖 SP-2，已标注）
**类型**: Architecture Consolidation / Implementation（仅后端 dts-platform）
**目标**: 把 dts-metrics 的受控派生指标 DSL + ELT 分层准入移植进权威语义层 dts-platform `SemanticModelingService`，绞杀者式按模型级 governanceMode 切换。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-受控模式基座（governanceMode + 切换骨架 + 存量兼容） | P0 | 2 | DONE |
| F2-受控派生指标DSL（ControlledMetricDslCompiler + 委托） | P0 | 3 | DONE |
| F3-ELT分层准入闸（EltLayerGate + 校验集成） | P0 | 3 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=8, BLOCKED=0
**进度（2026-06-16）**: 全部 DONE。`governance_mode` 受控开关 + `ControlledMetricDslCompiler`（独立可抽取，白名单/方言 quote/注入防御/拒 raw SQL）+ `EltLayerGate`（DWS/ADS 入口、DWD 需 grain、ODS/STG 禁）+ 受控集成（buildMetricExpression 委托 + validateModelForReview 调闸）+ 错误码（unsafe_expression→422、分层码→400）。验收 **36 例全绿**（编译器 15 + 闸 7 + service 12 + resource 2）。提交 c5234a75a/bef64345d/01899129f/9919cbbd3/12560de3d/834c755b3，分支 feat/semantic-consolidation（已 merge 回 v2.2.3）。
**背景**: 决策以 dts-platform 为唯一权威语义层（更成熟：评审工作流/runs/业务对象映射/可用 BI+血缘注册/持久化/前端 + 已具备 generateArtifacts→DbtFileService 物化），dts-metrics 亮点移植后逐步退役。本 sprint 为整合大计划 SP-1；后续 SP-2 语义富化契约、SP-3 React Flow 工作台移植、SP-4 dts-metrics 退役。
**设计文档**: `worklog/v2.2.3/sprint-41-202606/README.md` + `assets/sp1-controlled-modeling-design.md` + `assets/semantic-consolidation-roadmap.md`
**集成测试**: `worklog/v2.2.3/sprint-41-202606/it/README.md`
**关键决策**:
- 平台侧权威；dts-metrics 取逻辑（DSL/分层）不取基础设施；sprint-35b 硬化遗留作废、不合入 v2.2.3。
- 绞杀者并存：受控路径与现有 permissive 路径按 governanceMode 切换，PERMISSIVE 字节不变。
- 受控 DSL 取**安全/语义一致**而非字节复刻 dts-metrics（两者输入模型不同：字符串表达式 vs JSON formula）。
- 源表层级 gating + DWD 标准码强制依赖 SP-2 语义富化（平台模型当前不携带源表层级/标准码）。
- 架构边界：SP-1 模块化可抽取；dts-platform 领域解耦评审排入 v2.3。

## Sprint-42: 数据管理主题看板 (202606)
**状态**: DONE
**类型**: Product Architecture / Implementation（dts-platform-webapp + dts-admin menu seed）
**目标**: 将 Sprint-39 黄金链路和 Sprint-40 消费工作台重构为面向数据管理员的业务主题看板，让客户先按经营分析、质量管理、项目交付、客户服务理解数据交付状态。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-数据管理主题看板 | P0 | 4 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=4, BLOCKED=0
**进度（2026-06-16）**: 完成主题聚合模型、数据管理工作台页面、工作台菜单入口、旧消费入口兼容、dts-admin seed 可见性契约和前端构建验证。
**设计文档**: `worklog/v2.2.3/sprint-42-202606/README.md`
**集成测试**: `worklog/v2.2.3/sprint-42-202606/it/README.md`
**关键决策**:
- 服务对象 = 数据管理员；客户偏业务，通常没有专职数据工程师。
- 第一对象 = 业务主题/场景，第二对象 = 关联数据资产，技术动作只作为下一步入口。
- 入口放在“工作台 -> 数据管理工作台”，旧 `/services/consumption` 保持兼容，避免现场旧链接断开。

## Sprint-43: 语义层整合 Phase 2 — 语义富化贯通 (202606)
**状态**: READY
**类型**: Architecture Consolidation / Implementation（dts-platform catalog + dbt 约定 + 消费端）
**目标**: 把 dbt 列 meta（semantic_type/grain/standard_code/time）贯通 dbt→OM/catalog 列同步→列契约→schema-contract/assets-v2→建模消费，解锁 Sprint-41 F3 源表层级 gating + dts-metrics visual-assets 空列族 + DWD 标准码强制。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-dbt-meta语义契约标准化 | P1 | 2 | READY |
| F2-catalog列同步捕获meta（含 OM-meta spike） | P0 | 3 | READY |
| F3-契约透出列族 | P0 | 2 | READY |
| F4-接通消费端（F3 源表 gating + visual-assets 列族） | P0 | 2 | READY |

**统计**: READY=9, IN_PROGRESS=0, DONE=0, BLOCKED=0
**背景**: 整合大计划 SP-2。关键发现——dbt schema.yml **已有** `meta.semantic_type` 约定（dws/semantic/schema.yml），但 catalog 不透出；故 SP-2 = **贯通已有 meta** 而非发明契约。**前置**: F2-T00 spike 验证 OM dbt ingestion 是否已抓 meta（决定走 OM 镜像 or 直读 dbt）。
**依赖**: 上游 SP-1 = Sprint-41（DONE）；本 sprint 解锁其 F3-T02 落地说明记录的"依赖 SP-2"项。后续 SP-3 工作台移植、SP-4 dts-metrics 退役；v2.3 dts-platform 领域解耦评审。
**设计文档**: `worklog/v2.2.3/sprint-43-202606/README.md` + `assets/sp2-semantic-enrichment-design.md`
**集成测试**: `worklog/v2.2.3/sprint-43-202606/it/README.md`
**关键决策**:
- 契约已存在于 dbt（semantic_type），SP-2 只贯通 + 小幅扩展（grain/standard_code/time），不发明、不重写 OM ingestion。
- 绞杀者：无 meta 的存量列/资产回退（catalog 行为字节不变）。
- grain 倾向复用平台 `semantic_model.grain`（Sprint-41 已有），dbt meta 作来源同步。

## Sprint-44: 语义层整合 Phase 3 — 平台原生语义 UI 重建与治理呈现 (202606)
**状态**: READY（**已按现状勘察修订**：平台原生语义页是跳转壳，需先重建）
**类型**: Frontend / Implementation（dts-platform-webapp 原生语义建模 UI）
**目标**: 在 /api/semantic 上重建平台原生语义建模 UI（替换刻意的跳转壳），成为唯一权威 UI 并承载 SP-1 受控治理。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F0-原生语义建模页骨架与 CRUD（替换跳转壳，消费 /api/semantic） | P0 | 4 | READY |
| F1-受控建模治理前端呈现（governanceMode + 受控 DSL 提示 + 分层诊断） | P0 | 4 | READY |
| F2-React Flow 可视化工作台亮点移植 | P1 | 4 | READY |
| F3-菜单/路由收敛 + iframe/跳转壳退役（配合 SP-4） | P1 | 3 | READY |

**统计**: READY=15, IN_PROGRESS=0, DONE=0, BLOCKED=0
**现状修正（方案 A 重写）**: 勘察发现平台 `Semantic*Page` 全是 5 行跳转壳 → `window.location.replace("/metrics/semantic/*")`（跳 dts-metrics-webapp）；`semanticModelingApi.ts`(/api/semantic 客户端)=死代码；提交 `1648fda0d fix: isolate metrics frontend boundary` 表明语义前端**被有意隔离进 dts-metrics**。故"平台权威"要落地必须**先重建原生 UI（新增 F0）**，治理/工作台/收敛再叠上。此举逆转 `1648fda0d` 的隔离决定，是整合代价。
**执行序**: **F0（重建原生页，前置）** → F1（治理呈现）→ F2（图形工作台，复用 analytics React Flow + SP-2 列族）→ F3（路由收敛，配合 SP-4）。
**依赖**: 上游 Sprint-41（后端 /api/semantic 34 端点 DONE）；F1/F2/F3 均依赖 F0；F2 另依赖 SP-2 列族；F3 依赖 SP-4 退役节奏。
**设计文档**: `worklog/v2.2.3/sprint-44-202606/README.md`（含现状修正）
**集成测试**: `worklog/v2.2.3/sprint-44-202606/it/README.md`
**关键决策**:
- 重建平台原生语义 UI（消费 /api/semantic），替换刻意的跳转壳；灰度 flag 保回退。
- 不改后端 /api/semantic（SP-1 已落）。
- 不在 F0 重建完成前切路由（F3）；绞杀者：permissive 行为不变。

## Sprint-45: 数据中台 UI 产品化整改大 Sprint (202606)
**状态**: DONE
**类型**: UI Productization / Product Architecture / Implementation（dts-platform-webapp + dts-admin menu seed + dts-analytics-webapp/modern）
**目标**: 把当前“后台能力强、前端页面割裂”的状态整改为一个可演示、可验收、可持续开发的数据中台产品，用页面把 `数据源 -> 入湖任务 -> ODS/DWD/DWS/ADS -> 治理/权限/血缘 -> 指标/报表/API/数据产品/大屏 -> 运维审计` 串成真实主链路。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-产品壳与全局导航闭环 | P0 | 5 | DONE |
| F2-工作台与黄金链路产品化 | P0 | 4 | DONE |
| F3-数据接入到开发链路贯通 | P0 | 4 | DONE |
| F4-治理与资产门户闭环 | P0 | 4 | DONE |
| F5-指标BI大屏消费体验统一 | P1 | 4 | DONE |
| F6-数据服务与运维验收闭环 | P1 | 5 | DONE |
| F7-页面级视觉规范与验收体系 | P0 | 4 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=30, BLOCKED=0
**背景**: Sprint-39 已打通结构化数据黄金链路，Sprint-40/42 已补消费工作台和数据管理主题看板，Sprint-44 另行承担语义工作台前端整合；本 sprint 专门承担全局 UI 整改，消除假入口、断路由、命名漂移、页面割裂和按钮假实现风险。
**P0 断点**: `/workbench/todo`、`/studio/projects`、`/studio/sql-modeling`、服务中心数据产品命名、共享交换/令牌语义、隐藏运维审计入口。
**设计文档**: `worklog/v2.2.3/sprint-45-202606/README.md`
**页面矩阵**: `worklog/v2.2.3/sprint-45-202606/assets/ui-page-capability-matrix.md`
**按钮组件矩阵**: `worklog/v2.2.3/sprint-45-202606/assets/button-component-inventory.md`
**集成测试**: `worklog/v2.2.3/sprint-45-202606/it/README.md`
**完成证据**: source-contract 26/26 通过；`pnpm build` 通过；Playwright preview smoke 8 条关键路由通过并生成截图证据。
**关键决策**:
- 本 sprint 不重写后端能力，重点是菜单、路由、页面、按钮、组件、状态和验收证据。
- 所有菜单叶子必须真实可达，或明确外链/下线；不保留假入口。
- 每个核心页面必须具备主按钮、次按钮、危险按钮、空态、异常态、权限态、加载态和真实下一步。
- 客户可见页面使用业务语言，不默认暴露 sprint/F1/F2、iframe、artifact、dbt 文件等内部表达。

## Sprint-46: 工作台首页收敛与个人定制 (202606)
**状态**: DONE
**类型**: UI Productization / Workbench Personalization / Backend Preference Contract（dts-platform-webapp + dts-platform + dts-admin menu seed）
**目标**: 将“工作台”“数据管理工作台”“业务消费工作台”收敛为唯一 `/workbench` 首页，并支持每个登录用户通过 checkbox 勾选组件、上移/下移调整显示顺序；不做拖拽门户，不内置客户 demo 场景，兼容 Chrome 95。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-唯一工作台路由与菜单收敛 | P0 | 3 | DONE |
| F2-个人工作台偏好后端契约 | P0 | 4 | DONE |
| F3-前端工作台容器与组件注册表 | P0 | 3 | DONE |
| F4-自定义工作台抽屉 | P0 | 3 | DONE |
| F5-数据管理能力组件化迁移 | P0 | 4 | DONE |
| F6-验收兼容与发布材料 | P0 | 3 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=20, BLOCKED=0
**背景**: Sprint-45 已完成全局 UI 产品化整改，但客户现场定制首页诉求要求进一步从架构上去重：保留唯一工作台，把数据管理能力拆成用户可勾选的首页组件。
**设计文档**: `worklog/v2.2.3/sprint-46-202606/README.md`
**外部设计源**: `docs/superpowers/specs/2026-06-16-workbench-home-personalization-design.md`
**组件矩阵**: `worklog/v2.2.3/sprint-46-202606/assets/workbench-ui-control-matrix.md`
**集成测试**: `worklog/v2.2.3/sprint-46-202606/it/README.md`
**关键决策**:
- `/workbench` 是唯一工作台首页，`/workbench/data-management` 和 `/services/consumption` 只保留兼容跳转。
- 每个登录用户可以勾选首页组件并调整显示顺序，配置保存到服务端。
- 自定义方式只用 checkbox、上移、下移、保存、取消、恢复默认，不做拖拽和自由栅格。
- 首页组件必须来自注册表，绑定真实页面或真实接口；无数据时显示空态或不可用原因。
- 删除“经营分析、质量管理、项目交付、客户服务”等内置 demo 场景，客户场景到现场再定义。
- Chrome 95 是硬约束，不引入 `structuredClone`、container query、复杂拖拽库或新浏览器 API。

## Sprint-47: 语义层整合 Phase 4 — dts-metrics 退役 (202606)
**状态**: READY（执行 gate：SP-2/SP-3 完成——原生页平价 + 路由收敛）
**类型**: Decommission / 破坏性下线（dts-metrics 服务 + dts-metrics-webapp）
**目标**: 平台原生治理页平价后安全退役 dts-metrics，消除两套并行语义层，整合大计划闭环。原则 verify-first → 灰度切断 → 回退窗口 → 先归档再删。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-退役前置核验与决策（gate） | P0 | 3 | READY |
| F2-流量切断与部署下线（traefik 路由 + compose，灰度+回退） | P0 | 3 | READY |
| F3-平台侧依赖清理（service-auth 授权 + metrics 配置） | P1 | 2 | READY |
| F4-代码归档与整合收口（源码归档 + 分支处置 + 文档/记忆 + 闭环） | P1 | 3 | READY |

**统计**: READY=11, IN_PROGRESS=0, DONE=0, BLOCKED=0
**退役足迹**（已勘察）: docker-compose-app.yml `dts-metrics`(:8084) + traefik `dts-metrics-api`(/api/metrics) `dts-metrics-ui`(/metrics)；平台 `MetricsInternalAccess`/`ServiceDependencyAuthenticationFilter`/`DtsMetricsCapabilityProperties`/application.yml；源码 source/dts-metrics(-webapp)；opmanager 文档。数据：v2.2.3 内存态无持久化数据需迁移。
**依赖**: gate = SP-3（Sprint-44 F2 平价 + F3 路由切原生）；F2 依赖 F1 决策；F3/F4 依赖 F2 回退窗口稳定。
**设计文档**: `worklog/v2.2.3/sprint-47-202606/README.md` + `assets/sp4-retirement-plan.md`
**集成测试**: `worklog/v2.2.3/sprint-47-202606/it/README.md`
**关键决策**:
- 不在平价前退役（执行 gate）；每步可回退、保留回退窗口、先归档再删、不删 git 历史。
- 平台 service-auth 清理须精确（仅 metrics 身份），勿误伤其他内部调用方。
- sprint-35b 硬化随退役作废（逻辑亮点已 SP-1 移植进平台）。

## Sprint-48: 前端页面驱动的数据中台重构治理 (202606)
**状态**: DONE
**类型**: Architecture Governance + Implementation（skills + 页面矩阵 + 后续编码约束 + 首批页面整改）
**目标**: 固化 DTS 企业级数据中台后续前端重构的执行规则：以现有前端页面为第一事实源，尽量不新增菜单或页面，通过页面、按钮、组件、路由和接口契约把功能点串成真实产品闭环。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-DTS前端重构skills固化 | P0 | 3 | DONE |
| F2-现有页面能力矩阵基线 | P0 | 4 | DONE |
| F3-菜单路由收敛规则 | P0 | 3 | DONE |
| F4-TDD与Chrome95验收基线 | P0 | 3 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=13, BLOCKED=0
**进度（2026-06-18）**: 完成 DTS 前端重构 skills、页面/按钮矩阵、菜单路由收敛规则和 TDD/Chrome95 验收基线；同时完成首批整改：清理工作台内置 demo 场景夹具，工作台偏好 API 默认关闭并保留本地偏好，避免后端未升级时 `/api/workbench/preferences` 404 噪音。
**背景**: Sprint-45/46 已完成全局 UI 产品化与唯一工作台整改，后续继续编码前需要先固化页面优先、菜单收敛、按钮组件真实闭环和 Chrome95 验收规则，避免再次出现页面割裂、假实现或内置客户 demo 场景。
**设计文档**: `worklog/v2.2.3/sprint-48-202606/README.md`
**页面矩阵**: `worklog/v2.2.3/sprint-48-202606/assets/page-capability-matrix.md`
**按钮组件矩阵**: `worklog/v2.2.3/sprint-48-202606/assets/button-component-matrix.md`
**规则固化**: `worklog/v2.2.3/sprint-48-202606/assets/dts-frontend-refactor-rules.md`
**集成测试**: `worklog/v2.2.3/sprint-48-202606/it/README.md`
**关键决策**:
- 前端页面是产品重构的第一事实源；后台能力只补页面闭环。
- 默认不新增菜单或页面；优先复用、合并、兼容跳转或退役。
- 每个客户可见按钮和组件必须有状态、路由/API/外部交接点和测试证据。
- 客户业务场景不内置为产品 demo；由现场配置或客户定义。
- 后续 UI 编码必须先写 source-contract/unit test，再做最小实现，并补 Chrome95/Playwright 证据。

## Sprint-49: 前端 P0 页面真实闭环整改 (202606)
**状态**: DONE
**类型**: Implementation（页面闭环 + TDD + Chrome95）
**目标**: 基于 Sprint-48 页面矩阵，优先修复客户已能看到的 P0 页面割裂和布局问题，不新增菜单或页面，通过现有页面把数据接入、资产、消费和工作台入口串成可验收的数据中台产品闭环。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-数据接入基础页面闭环 | P0 | 3 | DONE |
| F2-资产与消费页面闭环 | P0 | 3 | DONE |
| F3-工作台入口闭环 | P0 | 2 | DONE |
| F4-Chrome95证据与Review | P0 | 3 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=11, BLOCKED=0
**进度（2026-06-18）**: Sprint-49 完成。F1 数据接入基础页面闭环：`/foundation/connectors` 表格布局稳定，“配置/查看模板”区分 Drawer 模式，“启用/停用”有禁用说明，“创建数据源”可带 `connectorKey` 打开 `/foundation/data-sources` 新增弹窗并预选连接器。F2 资产与消费页面闭环：`/catalog/assets` 台账操作列固定宽度，数据产品“查看消费”默认进入唯一工作台消费发布 section，`/services/consumption` 兼容跳转保留 `productId` 上下文。F3 工作台入口闭环：`/workbench` 首页 7 个组件动作均有真实路由落点，自定义抽屉保持勾选+上移/下移，默认本地偏好降级不请求 `/api/workbench/preferences`。F4 证据与 Review：source-contract 42/42、三条 Chrome smoke、生产构建和 GitNexus 低风险检测完成。
**设计文档**: `worklog/v2.2.3/sprint-49-202606/README.md`
**来源矩阵**: `worklog/v2.2.3/sprint-48-202606/assets/page-capability-matrix.md` + `worklog/v2.2.3/sprint-48-202606/assets/button-component-matrix.md`
**集成测试**: `worklog/v2.2.3/sprint-49-202606/it/README.md`
**关键决策**:
- 不新增菜单或页面，优先修复现有客户可见页面。
- 每个修复先 source-contract 红灯，再做最小实现。
- Chrome95 和 1366x768 表格截图是 UI 完成条件之一。

## Sprint-50: 数据标准与 dbt 模型契约联动设计 (202606)
**状态**: IMPLEMENTED
**类型**: Architecture Design + Frontend-first Product Matrix + Implementation
**目标**: 先完善和重构现有前端页面承载面，再把数据标准和 dbt 模型挂钩，形成“标准定义 -> 模型开发 -> dbt 校验 -> 发布门禁 -> 资产同步”的数据开发中心闭环。默认不新增菜单或页面。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F0-前端页面承载面完善与重构 | P0 | 5 | DONE |
| F1-标准到模型的页面矩阵与契约定义 | P0 | 4 | DONE |
| F2-SQL建模页字段标准映射 | P0 | 5 | DONE |
| F3-公共码表到dbt Seeds联动增强 | P0 | 4 | DONE |
| F4-dbt schema.yml与标准元数据生成 | P0 | 4 | DONE |
| F5-发布门禁接入标准校验 | P0 | 4 | DONE |
| F6-Chrome95与source-contract验收 | P0 | 5 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=31, PARTIAL=0, BLOCKED=0
**进度（2026-06-19）**: 已完成 Sprint-50 编码与验收：SQL 建模页支持字段标准自动匹配并保存到 `semanticContract.dts.standardBindings`，后端提供标准绑定、schema.yml 生成写入、标准门禁接口，公共码表 `codeSet` 进入 dbt schema.yml relationship test。source-contract 7/7、平台 Webapp Chrome95 生产构建通过；Playwright mock smoke 已补桌面/窄屏截图并验证标准绑定区域无 console error。后端 `ModelingSqlModelServiceTest` 全类存在既有批量导入/文件删除失败，目标新增用例已通过；SQL 建模页窄屏仍按桌面工作台横向承载，移动端响应式需另排。
**设计文档**: `worklog/v2.2.3/sprint-50-202606/README.md`
**架构设计**: `worklog/v2.2.3/sprint-50-202606/assets/data-standards-dbt-modeling-architecture.md`
**Feature 台账**: `worklog/v2.2.3/sprint-50-202606/features/`
**集成测试**: `worklog/v2.2.3/sprint-50-202606/it/README.md`
**关键决策**:
- Sprint-50 第一优先级是前端页面承载面完善与重构；F1-F6 均依赖 F0。
- 不新增“标准建模中心”菜单，复用现有标准管理、逻辑建模、项目文件浏览和任务编排。
- 数据元作为 dbt column contract 的字段级标准来源，公共码表作为 dbt seeds 和码值校验来源。
- SQL 建模页是字段标准映射、dbt 契约生成和发布门禁的主工作台。
- 项目文件浏览只作为底层 dbt 文件证据面，不允许绕过 SQL 建模页发布。
- 客户现场业务规则不内置为 demo 或模板，只提供映射、确认和门禁机制。

## Sprint-53: dts-metrics 默认退役与指标路由收敛 (202606)
**状态**: DONE
**类型**: Retirement / Menu Route Convergence / Frontend-first Migration
**目标**: 将旧 `dts-metrics` 服务从默认产品入口、默认运行面和默认构建链路中退役，把指标与语义能力收敛到 v2.2.3 现有平台页面，保留必要旧链接兼容和可回滚证据。
**设计文档**: `worklog/v2.2.3/sprint-53-202606/README.md`
**退役矩阵**: `worklog/v2.2.3/sprint-53-202606/assets/dts-metrics-retirement-matrix.md`
**页面矩阵**: `worklog/v2.2.3/sprint-53-202606/assets/page-capability-matrix.md`
**集成测试**: `worklog/v2.2.3/sprint-53-202606/it/README.md`

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F0-退役边界与能力盘点 | P0 | 3 | DONE |
| F1-菜单角色路由收敛 | P0 | 4 | DONE |
| F2-默认运行面退役 | P0 | 4 | DONE |
| F3-平台语义指标接管 | P0 | 4 | DONE |
| F4-验证收尾 | P0 | 3 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=18, BLOCKED=0
**关键决策**:
- 先做“默认退役”，不直接删除 `source/dts-metrics`、`source/dts-metrics-webapp` 或历史 Liquibase。
- 旧 `/bi-apps/metrics/*`、`/modeling/semantic-center/*` 和 `/bi/semantic-modeling` 只作为兼容入口，目标是 redirect 到平台新页面，不再 iframe 旧服务。
- 默认 compose/build/init 不再启动或构建 `dts-metrics`；legacy/rollback 路径必须文档化。
- Sprint-54 再打通数据源 -> 数据连接 -> 数据资产 -> 数据质量黄金线；Sprint-55 再完善可视化指标并评估物理删除。

## Sprint-57: 基础数据闭环——标准包导入管道与内置国标包 (202607)
**状态**: IN_PROGRESS
**类型**: Backend Pipeline / Frontend Wizard / Builtin GB Standard Packs
**目标**: 打通"下载标准包模板 -> 客户填写 -> 上传 -> 校验/应用/回滚"闭环，并以同一管道交付内置国标包，使基础数据模块可交付。
**设计文档**: `worklog/v2.2.3/sprint-57-202607/README.md`
**集成测试**: `worklog/v2.2.3/sprint-57-202607/it/README.md`

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-标准包导入管道 | P0 | 4 | DONE |
| F2-内置国标包 | P0 | 2 | DONE |
| F3-基础数据页面完善 | P1 | 3 | DONE |
| F4-数据资产重构 | P0 | 4 | DONE |
| F5-治理运营三模块重构 | P0 | 4 | IN_PROGRESS |
| F6-指标工作台语义编排编辑器 | P0 | 4 | IN_PROGRESS |

**统计**: READY=1, IN_PROGRESS=7, DONE=17, BLOCKED=0
**关键决策**:
- "标准模板" = 数据标准包（数据元+码表+术语打包），非 TemplatesPage 建模模板。
- 一条 preview/apply/rollback 管道两个来源：客户上传 zip 与官方内置包，内置国标包不做独立种子机制。
- 收编 `/metadata-standards/import` 旧直导路径，数据元校验逻辑单一来源。
- 2026-07-03 范围已调整：F4 数据资产、F5 治理运营、F6 指标工作台语义编排均并入 Sprint-57；不再保留“单列 Sprint-58”作为本批计划约束。
- 指标工作台语义编排编辑器作为 F6 进入 Sprint-57：线条代表真实指标建模关系，前端优先复用现有指标 PUT，不新增后端关系表。

## Sprint-56: 指标工作台拖拽建模闭环 (202607)
**状态**: DONE
**类型**: Frontend Productization / React Flow DnD / Metric Binding
**目标**: 将指标工作台从“可视化关系画布”推进为可拖拽、可连线、可回写绑定关系的建模工作台。
**设计文档**: `worklog/v2.2.3/sprint-56-202607/README.md`
**集成测试**: `worklog/v2.2.3/sprint-56-202607/it/README.md`

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-指标工作台拖拽建模闭环 | P0 | 3 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=3, BLOCKED=0
**关键决策**:
- 绑定关系是业务事实，拖拽或连线后必须回写 `PUT /api/semantic/metrics/{id}`。
- 节点布局先作为个人操作偏好保存在浏览器 `localStorage`，暂不新增后端 layout API。
- 未绑定指标必须出现在目录侧分组，否则无法完成首次拖拽挂载。
- 后端指标更新是全量 PUT，前端必须保留 `code/name/formula/status` 等字段，避免绑定后被后续保存清空。

## Sprint-55: 黄金线菜单架构重排 (202606)
**状态**: DONE
**类型**: Menu IA Refactor / dts-admin Seed / Runtime Reparent Migration
**目标**: 以 dts-admin 菜单种子为事实源，将 portal 侧栏重排为“数据基础 -> 数据集成 -> 数据开发 -> 指标建模 -> 数据资产 -> 数据消费 -> 治理运营 -> 运维与监控”的黄金线信息架构。
**设计文档**: `worklog/v2.2.3/sprint-55-202606/README.md`
**集成测试**: `worklog/v2.2.3/sprint-55-202606/it/README.md`

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-黄金线菜单架构收敛 | P0 | 3 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=3, BLOCKED=0
**关键决策**:
- dts-admin `portal-menu-seed.json` 是菜单事实源；前端只消费菜单与路由，不在平台侧另建菜单结构。
- “数据基础”前置主题域、业务术语、数据元、公共码表和标准模板；指标建模只引用治理主题域。
- “指标建模”提升为一级分区，运行实例仍统一归入任务运维中心。
- “数据消费”统一承载数据 API、数据推送、共享交换、BI 和数据大屏。
- 运行态 DB 使用 reparent 迁移保留原 `portal_menu.id` 和 `portal_menu_visibility` 绑定。

## Sprint-54: 指标建模产品化 UI 与运行监控收敛 (202606)
**状态**: IN_PROGRESS
**类型**: Frontend Productization / Ops Convergence
**目标**: 将指标工作台、业务对象、指标管理、模型管理、发布审核打磨成统一指标建模工作区；主题域统一引用数据治理中心，并把运行监控收敛到任务运维中心。
**设计文档**: `worklog/v2.2.3/sprint-54-202606/README.md`
**集成测试**: `worklog/v2.2.3/sprint-54-202606/it/README.md`

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-指标建模统一工作区 | P0 | 3 | DONE |
| F2-语义页面产品化完善 | P0 | 3 | DONE |
| F3-运行监控运维收敛 | P0 | 3 | DONE |
| F4-验证收尾 | P0 | 2 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=11, BLOCKED=0
**关键决策**:
- 指标建模菜单聚焦建模、管理和发布审核；运行实例、日志、失败、补数统一归入任务运维中心。
- 主题域唯一维护入口是数据治理中心 `/governance/subjects`；指标建模只引用治理主题域，旧 `/modeling/semantic/subjects` 做兼容跳转。
- `/modeling/semantic/runs`、旧 `/metrics/operations` 等路径保留兼容，但目标页进入运维监控。
- 不新增 `/v2` 路由，不回引旧 `dts-metrics` iframe，优先使用现有平台语义 API。

## Sprint-52: 指标工作台 & 语义建模全面整合 (202606)
**状态**: READY
**类型**: Frontend Product Capability / React Flow Canvas
**目标**: 构建 React Flow 三栏指标工作台 + 替换 6 个 SemanticXxxPage 重定向壳为真实页面，实现 DWS/ADS 建模→指标可视化→消费看板端到端闭环。
**设计文档**: `worklog/v2.2.3/sprint-52-202606/assets/metric-workbench-design.md`
**集成测试**: `worklog/v2.2.3/sprint-52-202606/it/README.md`

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-指标工作台主页 | P0 | 4 | READY |
| F2-主题域与业务对象 | P0 | 2 | READY |
| F3-指标与模型页 | P0 | 2 | READY |
| F4-发布与运行监控 | P1 | 2 | READY |
| F5-验证收尾 | P0 | 2 | READY |

**统计**: READY=12, IN_PROGRESS=0, DONE=0, BLOCKED=0
**关键约束**:
- Chrome 95: 颜色用 HSL/HEX，禁 oklch/:has/container
- `/modeling/semantic-center` 在 Sprint-53 中已改为兼容 redirect 到平台语义页面
- 不触碰 `addax-env-runner.jar`，不新增 `/v2` 路由

## Sprint-51: 现有页面横切职责域重构 (202606)
**状态**: DONE
**类型**: Frontend-first Refactor Planning / Existing Pages Only
**目标**: 参考 v2.2.4 Sprint-2 的横切职责域思想，在 v2.2.3 现有页面上规划字典、血缘、治理、元数据和状态联动重构；先不改代码，不新增 `/v2` 命名空间，后续实施以现有页面和真实 API 缺口为第一约束。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F0-现有页面事实源与重构边界 | P0 | 3 | DONE |
| F1-字典域现有页面收敛 | P0 | 3 | DONE |
| F2-血缘与元数据详情闭环 | P0 | 3 | DONE |
| F3-治理域跨页面复用 | P0 | 3 | DONE |
| F4-工作台主链路串联 | P1 | 2 | DONE |
| F5-API缺口与验收证据 | P0 | 3 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=17, BLOCKED=0
**进度（2026-06-27）**: Sprint-51 全部 DONE。F1 字典域：dictionaryService.ts 接线 /platform/dict/system-types + TYPE_OPTIONS 兜底，DataSourceFormModal 系统类型下拉 + 标准管理维护入口。F2 血缘/元数据：DatasetDetailPage 补 tags 显示 + profile 解析摘要，血缘 tab 带 ?datasetId 上下文导航，TransformDetailPage 执行成功后 Addax 血缘同步入口。F3 治理：资产详情概览 Alert 对 BLOCKED/WARNING 状态挂质量/授权快捷动作，SqlModelingPage 已有 standardGateResult + testResult 展示满足 F3-T02，F3-T03 通过 buildAssetGrantUrl 串联。F4 工作台：headerActions + 详情面板增加血缘/字典横切入口（遵守 SQL 字面量约束）。F5 验收：tsc EXIT:0；source-contract 133 tests 123 pass（10 项为预存基线失败，零净增）；pnpm build 2m4s 通过；API 缺口 6/6 以前端收敛关闭。
**背景**: v2.2.4 Sprint-2 提供了“字典 / 血缘 / 治理 / 元数据 / Store 编排”的横切域重构思想，但 v2.2.3 已经通过 Sprint-45~50 建立了客户可见的现有页面闭环。本 sprint 明确不复制 v2.2.4 的 `/src/v2` 新骨架，而是把思想转译到 `/foundation/data-sources`、`/catalog/assets`、`/catalog/datasets/:id`、`/catalog/lineage/*`、`/governance/*`、`/studio/sql-modeling` 和 `/workbench` 等现有页面。
**设计文档**: `worklog/v2.2.3/sprint-51-202606/README.md`
**页面矩阵**: `worklog/v2.2.3/sprint-51-202606/assets/existing-page-cross-domain-matrix.md`
**API缺口登记**: `worklog/v2.2.3/sprint-51-202606/assets/api-gap-register.md`
**集成测试计划**: `worklog/v2.2.3/sprint-51-202606/it/README.md`
**关键决策**:
- 现有页面是第一事实源；默认不新增菜单、不新增 `/v2` 路由、不回植 `src/v2` UI 骨架。
- 优先界面重构和页面链路闭环，后端只补现有页面验收所需的真实 API 缺口。
- 字典、血缘、治理、元数据、状态联动都必须落到客户可见页面、按钮、抽屉、空态和错误态。
- 后续编码前先补 source-contract，再做最小实现，并记录 Chrome95/页面 smoke 证据。

### 整合大计划 SP-1~SP-4 总览
| 阶段 | Sprint | 状态 |
|------|--------|------|
| SP-1 受控建模逻辑（后端） | 41 | DONE（36 测试绿） |
| SP-2 语义富化贯通（catalog+dbt） | 43 | 计划就绪 |
| SP-3 前端整合（治理呈现+工作台+路由收敛） | 44 | 计划就绪 |
| SP-4 dts-metrics 退役 | 47 | 计划就绪 |
| dts-platform 领域解耦评审 | v2.3 | Backlog |

## Sprint-58: 数据资产元数据管理入口重构 (202607)
**状态**: IN_PROGRESS
**类型**: Implementation
**目标**: 在数据资产菜单组新增“元数据管理”模块，把资产业务语义治理与数据源结构采集拆成两个清晰入口。
**设计文档**: `worklog/v2.2.3/sprint-58-202607/README.md`
**集成测试**: `worklog/v2.2.3/sprint-58-202607/it/README.md`

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-数据资产元数据管理 | 4 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=4, BLOCKED=0
**关键决策**:
- 数据集成继续保留“数据源结构采集” `/catalog/metadata`，用于采集任务、采集历史和 Schema 漂移。
- 数据资产新增“元数据管理” `/catalog/metadata-management`，用于资产语义元数据补齐、治理缺口识别和资产详情跳转。
- OpenMetadata 作为技术主目录或缓存；DTS 继续维护业务描述、权属、密级、生命周期、权限和映射扩展。

## Sprint-59: 低代码无 SQL 数据开发与指标设计一体化 (202607)
**状态**: DONE
**类型**: Product Journey / Frontend-first Implementation / ELT-Metrics Convergence
**目标**: 在数据开发区提供一条面向不懂 SQL 用户的低代码向导，把“接入业务表、确认业务对象、定义指标、生成 DWS/ADS、发布报表数据集”串成连续旅程，同时保留 SQL、脚本、任务编排作为高级开发入口。
**设计文档**: `worklog/v2.2.3/sprint-59-202607/README.md`
**边界说明**: `worklog/v2.2.3/sprint-59-202607/assets/low-code-elt-metric-boundary.md`
**编码指导**: `worklog/v2.2.3/sprint-59-202607/assets/coding-guidance.md`
**集成测试**: `worklog/v2.2.3/sprint-59-202607/it/README.md`

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-低代码开发入口与边界收敛 | P0 | 3 | DONE |
| F2-无SQL业务表到模型向导 | P0 | 4 | DONE |
| F3-指标设计与DWSADS生成联动 | P0 | 3 | DONE |
| F4-编码护栏与集成验收 | P0 | 3 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=13, BLOCKED=0
**关键决策**:
- 低代码开发入口落在数据开发信息架构里，但实现为连接 ELT、资产治理、指标建模、发布审核和任务运维的编排页，不复制现有页面逻辑。
- 数据接入、ODS、DWD 基座由专业用户保障；低代码用户可发起需求、确认字段和生成 DWD 草案，但不直接发布基础层模型。
- DWS/ADS 是低代码主要生成目标，由指标、维度、粒度、筛选条件和消费目标驱动。
- 指标设计是低代码开发的核心步骤，必须和指标工作台、指标管理、模型管理、发布审核共享业务对象和上下文。
- 普通用户主流程不暴露 SQL/dbt 作为必需理解成本；SQL、脚本、dbt 文件和任务编排保留为高级开发入口。
**完成记录**:
- 新增 `/studio/low-code-development` 低代码开发向导和菜单/角色默认项。
- 指标工作台支持 `journey=low-code-development` 上下文提示和返回向导动作。
- 验证：前端 source-contract 26/26、`pnpm exec tsc --noEmit`、`pnpm build`、后端 targeted seed test、Playwright smoke 通过。

## Sprint-60: 建模 vNext、标准控制面与 dbt 双模式运行闭环 (202607)
**状态**: IN_PROGRESS
**类型**: Architecture / Standards Control Plane / Frontend + API + Backend + dbt + Airflow
**目标**: 建立全新的 ModelSpec 建模版本，并把标准管理升级为开发与发布控制面；普通建模与高级 dbt SQL 两条路径统一进入 Addax、dbt、Airflow、PostgreSQL 运行链路。
**设计文档**: `worklog/v2.2.3/sprint-60-202607/README.md`
**架构说明**: `worklog/v2.2.3/sprint-60-202607/assets/modeling-vnext-architecture.md`
**PJM 黄金主线**: `worklog/v2.2.3/sprint-60-202607/assets/pjm-golden-path.md`
**API 矩阵**: `worklog/v2.2.3/sprint-60-202607/assets/modeling-api-contract-matrix.md`
**集成测试**: `worklog/v2.2.3/sprint-60-202607/it/README.md`

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-建模vNext核心契约与PJM黄金主线 | P0 | 3 | DONE |
| F2-业务对象模型台账与低代码前端 | P0 | 4 | DONE |
| F3-建模API契约与兼容入口 | P0 | 4 | IN_PROGRESS |
| F4-PostgreSQL持久化与后端治理服务 | P0 | 4 | IN_PROGRESS |
| F5-dbt产物生成与SQL双模式 | P0 | 3 | IN_PROGRESS |
| F6-AddaxAirflowPostgreSQL运行闭环 | P0 | 4 | IN_PROGRESS |
| F7-TDDPlaywright与旧资产迁移验收 | P0 | 4 | IN_PROGRESS |
| S-F1-标准控制面与事实源收敛 | P0 | 3 | READY |
| S-F2-数据元到逻辑建模强约束 | P0 | 4 | IN_PROGRESS |
| S-F3-业务术语到指标口径绑定 | P0 | 4 | READY |
| S-F4-标准模板到低代码和发布门禁 | P1 | 4 | READY |
| S-F5-标准到物理模型生成与SQL微调 | P0 | 4 | IN_PROGRESS |

**统计**: READY=11, IN_PROGRESS=27, DONE=7, BLOCKED=0
**关键决策**:
- 业务对象是业务语义锚点，不等于物理表或 dbt 模型。
- ModelSpec 驱动普通用户建模，dbt SQL/manifest 驱动高级开发登记；两种模式显式区分，不做无提示双向覆盖。
- 新 API 采用 `/api/modeling/*`，旧 `/api/semantic/*` 保留兼容，不新增 `/v2` URL 命名空间。
- Addax 负责 ODS 接入，dbt 负责转换，Airflow 负责编排，PostgreSQL 负责数据与建模元数据。
- PJM“项目节点计划闭环”只作为黄金主线和回归夹具，不把 PJM 业务字段硬编码进平台。

## Sprint-61: UI 主导的端到端数据产品体验闭环 (202607)
**状态**: IN_PROGRESS
**类型**: Frontend Productization / Journey / Playwright
**目标**: 把数据集成、数仓规划、标准、建模、指标、发布和消费串成可验证的客户旅程。
**设计文档**: `worklog/v2.2.3/sprint-61-202607-ui-led-e2e-product-experience/README.md`
**集成测试**: `worklog/v2.2.3/sprint-61-202607-ui-led-e2e-product-experience/it/README.md`

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-端到端旅程工作台与上下文保持 | P0 | 3 | DONE |
| F2-数据集成到数仓规划的首屏引导 | P0 | 4 | DONE |
| F3-标准落标到建模与指标的可见传递 | P0 | 3 | DONE |
| F4-数据开发到发布门禁与运行证据 | P0 | 3 | DONE |
| F5-数据服务消费闭环与客户验收 | P1 | 3 | DONE |
| F6-旅程上下文组件化与页面接入 | P0 | 3 | DONE |
| F7-阶段状态与缺口计算模型 | P0 | 3 | DONE |
| F8-客户验收包与证据聚合 | P1 | 3 | DONE |
| F9-可登录浏览器验收与回归基线 | P0 | 3 | READY |

**统计**: READY=3, IN_PROGRESS=0, DONE=25, BLOCKED=0

## Sprint-62: 旅程可信化与门禁证据结构化 (202607)
**状态**: DONE
**类型**: Journey Persistence / Gate Evidence / Playwright
**目标**: 让旅程状态、阶段真实性和 dbt 式门禁证据可持久化、可恢复、可打印。
**设计文档**: `worklog/v2.2.3/sprint-62-202607-journey-trust-and-gate-evidence/README.md`
**集成测试**: `worklog/v2.2.3/sprint-62-202607-journey-trust-and-gate-evidence/it/README.md`

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-旅程实例持久化与恢复 | P0 | 3 | DONE |
| F2-阶段真实性校验 | P0 | 3 | DONE |
| F3-dbt式门禁证据结构化 | P0 | 3 | DONE |
| F4-菜单直达旅程感知与验收包打印 | P1 | 3 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=12, BLOCKED=0

## Sprint-63: 数仓规划、数据标准与维度建模闭环 (202607)
**状态**: DONE
**类型**: Warehouse Planning / Data Standards / Dimension Modeling
**目标**: 将数仓规划上下文、数据标准草稿和维度建模候选打通。
**设计文档**: `worklog/v2.2.3/sprint-63-202607-warehouse-planning-standard-dimension-loop/README.md`
**集成测试**: `worklog/v2.2.3/sprint-63-202607-warehouse-planning-standard-dimension-loop/it/README.md`

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-数仓规划上下文与主题域入口 | P0 | 3 | DONE |
| F2-规划上下文到数据标准与字段草稿 | P0 | 3 | DONE |
| F3-标准草稿到维度建模候选 | P0 | 3 | DONE |
| F4-规划-标准-建模闭环验证 | P0 | 3 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=12, BLOCKED=0

## Sprint-64: 数仓规划能力升维——业务过程、分层注册、粒度与总线矩阵 (202607)
**状态**: IN_PROGRESS（当前重点）
**类型**: Warehouse Planning / Modeling Governance / Frontend + API + Backend
**目标**: 在 Sprint-63 的规划-标准-建模闭环上，补齐业务过程、分层注册、粒度声明、一致性维度和总线矩阵，让模型管理具备可执行的规划依据。
**设计文档**: `worklog/v2.2.3/sprint-64-202607-planning-process-layer-grain-busmatrix/README.md`
**API 契约**: `worklog/v2.2.3/sprint-64-202607-planning-process-layer-grain-busmatrix/assets/api-contract.md`
**集成测试**: `worklog/v2.2.3/sprint-64-202607-planning-process-layer-grain-busmatrix/it/README.md`

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-业务过程管理 | P0 | 4 | DONE |
| F2-分层注册表与依赖红线 | P0 | 4 | DONE |
| F3-粒度声明与建模门禁 | P0 | 3 | DONE |
| F4-一致性维度登记与总线矩阵 | P1 | 3 | DONE |
| F5-规划升维闭环验证 | P0 | 3 | IN_PROGRESS |
| F6-建模动线与命名收敛 | P0 | 3 | DONE |
| F7-核心页面精简 | P0 | 1 | IN_PROGRESS |

**统计**: READY=0, IN_PROGRESS=1, DONE=20, BLOCKED=0
**执行顺序**: F1 → F2/F3 → F4 → F5；F6 与 F1 并行但必须在发布前完成命名和入口收敛。

## Sprint-65: 经典数仓规划内核与黄金主线重构 (202607)
**状态**: READY
**类型**: Architecture Convergence / Full-stack Refactor / Controlled Retirement
**目标**: 以经典数仓规划作为默认主线，建立 BUSINESS_FIRST 与 ASSET_FIRST 双起点、WarehousePlan 单内核和平台黄金主线；将关系建模降为可选设计视图，将 dbt 调整为高级实现工具，并受控退役旧旅程。
**设计文档**: `worklog/v2.2.3/sprint-65-202607/README.md`
**总体架构**: `worklog/v2.2.3/sprint-65-202607/assets/classic-warehouse-planning-golden-path-design.md`
**领域/API 契约**: `worklog/v2.2.3/sprint-65-202607/assets/domain-and-api-contract.md`
**受控退役登记**: `worklog/v2.2.3/sprint-65-202607/assets/controlled-retirement-register.md`
**集成测试**: `worklog/v2.2.3/sprint-65-202607/it/README.md`

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-架构边界与受控退役 | P0 | 3 | READY |
| F2-WarehousePlan持久化与API | P0 | 4 | READY |
| F3-双起点规划基线 | P0 | 4 | READY |
| F4-经典数仓架构与维度模型 | P0 | 4 | READY |
| F5-黄金主线与数据建设工作台 | P0 | 4 | READY |
| F6-模型中心与高级dbt分离 | P0 | 4 | READY |
| F7-菜单路由兼容与旧旅程退役 | P0 | 5 | READY |
| F8-集成验收与交付证据 | P0 | 3 | READY |

**统计**: READY=31, IN_PROGRESS=0, DONE=0, BLOCKED=0
**执行顺序**: F1 → F2 → F3 → F4/F5 → F6 → F7 → F8；F7 可提前完成菜单/旧资产盘点，但最终切换必须等待 F3-F6 验收。
**关键决策**:
- `modeling_warehouse_plan` 是 canonical 方案级聚合，旧 `modeling_plan*` 迁移后冻结，不新增第三套主计划表，也不在本 Sprint 物理删除。
- 两种起点只影响首次编辑顺序，必须汇合于同一个 PlanningBaseline 和发布流程。
- 平台主线为连接、接入/盘点、规划、标准、模型、构建/质量/发布、资产、指标、服务/运维；阶段完成来自真实证据投影。
- 模型中心持有事实/维度/关系真值；高级 dbt 只持有 SQL、宏、manifest、compile/test/run 等实现产物，并回写同一证据链。
- 旧菜单 ID、角色绑定和深链先兼容、再冻结、后移除；PJM 等行业内容只作为示例和回归夹具。

## v2.3 Backlog: 企业级资产与指标增强

| Item | Owner | 来源 | 状态 |
|------|-------|------|------|
| schema_version / metric_version_pin / breaking change review | Platform Catalog + Metrics Service | Sprint-31A RX / X4 | BACKLOG |
| SCD / conformed dimension / hierarchy 运行时 | Metrics Service | Sprint-32 F3 follow-up | BACKLOG |
| window / time intelligence / cohort / funnel DSL | Metrics Service | Sprint-32 F3 follow-up | BACKLOG |
| cube cache / cost-based routing | Platform Architecture | Sprint-31 F7 follow-up | BACKLOG |
| GraphQL / OData / semantic query API | Platform Architecture | Sprint-31/32 consumption follow-up | BACKLOG |
| differential privacy / k-anonymity | Security Architecture | Sprint-31A X5 follow-up | BACKLOG |

## Sprint-66: BI 大屏通用下钻重构 (202607)

**目录**: `worklog/v2.2.3/sprint-66-202607-bi-board-drilldown-refactoring`（2026-07-18 自误建的 workflow/ 目录归位）
**状态**: DONE
**目标**: 在不引入业务领域模型的前提下，将大屏下钻收敛为“点击事件 → 参数映射 → 目标动作 → 状态恢复”的通用交互通道。
**实现提交**: `a33fcd2bc`；**文档归位**: `6091aa657`（均已推送至 `origin/v2.2.3`）

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-通用交互契约 | 2 | DONE |
| F2-运行时交互内核 | 3 | DONE |
| F3-设计器配置体验 | 3 | DONE |
| F4-兼容回归与交付 | 3 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=11, BLOCKED=0
**执行顺序**: F1 → F2 → F3 → F4；F3 可在 F1 契约评审完成后与 F2 后半段并行，但 F4 必须等待 F1-F3 全部通过。

## Sprint-67: 建模主线与业务对象退役收敛 (202607)

**目录**: `worklog/v2.2.3/sprint-67-202607-modeling-mainline-convergence`
**状态**: IN_PROGRESS
**类型**: Product Journey / Modeling Contract / UI Convergence / Controlled Migration
**目标**: 保留数据域作为业务分类，删除业务对象这一重复中间产物，使用户从建设计划和业务分类直接登记维度、创建明细/维度/汇总/应用四类表，并用明确的页面输入、输出和跳转形成唯一建模主线。
**依赖**: 复用 Sprint-65 已落地的 WarehousePlan、ModelSpec、planId、StageProjection 和 canonical 词表（commit `24103098a`）；不建立新的规划聚合或前端状态源。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-关键对象与主线契约 | P0 | 3 | DONE |
| F2-规划输入与分阶段门禁 | P0 | 5 | IN_PROGRESS |
| F3-维度与四类表直接建模 | P0 | 10 | IN_PROGRESS |
| F4-菜单页面与跳转收敛 | P0 | 4 | DONE |
| F5-业务对象迁移与受控退役 | P0 | 4 | DONE |
| F6-专业模块交接与集成验收 | P0 | 8 | IN_PROGRESS |

**统计**: READY=2, IN_PROGRESS=10, DONE=22, BLOCKED=0；当前已关闭 22/34 Task
**执行顺序**: F1 → F2/F3 → F4 → F5 → F6；F2 与 F3 在概念契约冻结后可并行，F4 默认入口切换等待目标页面可用，F5 冻结旧写等待新写路径通过，F6 负责 Go/No-Go。

**关键决策**:

- 数据域/主题域对外收敛为“业务分类”，内部复用唯一 `catalog_domain/domainId`。
- 业务对象不再是登记实体、菜单入口、模型创建条件、指标锚点或发布门禁；旧页面不能只换名为维度目录。
- 维度性质旧对象迁入 `DimensionDefinition` 业务维度正文，DIMENSION ModelSpec 只保存稳定引用；事实性质的键、粒度、来源和 JOIN 迁入四类表 ModelSpec；歧义记录进入人工清单。
- 业务活动降为明细表的可选来源说明，不作为四类表的全局前置门禁。
- 新 ModelSpec 不要求 `objectId`；旧 API/表先冻结写入和迁移，消费者归零并对账后再物理删除。
- 数据标准在模型字段设计时引用，指标锚定已发布模型/字段；SQL/dbt、发布、运行、资产和血缘回写同一 modelSpecId/revision。
- 2026-07-21：新增 F2-T05 建设规划台账已完成代码、分层后端测试、production build 与 Chrome 95 mock-API 验收；Sprint 保持 24/25、IN_PROGRESS，等待部署后真实认证/API/PostgreSQL 联动及增量 Go/No-Go。
- 2026-07-21：新增 F6-T05 收敛全局数据元/业务分类与正式规划上下文；移除 session 伪计划和数据元 page-wide 草稿门禁，字段落标回归 ModelSpec。代码、定向测试和生产构建已完成；Sprint 当前 24/26、IN_PROGRESS，等待真实 Chrome 95/API 联动和增量 Go/No-Go。
- 2026-07-22：新增 F6-T06 收敛模型来源和系统编码；来源按当前计划的业务名称查询选择，`bindingId/ref/kind/version` 自动关联，维度/层级唯一码自动生成只读，后端提交时实时复验并 fail closed。建模前需元数据同步和计划来源确认，不需要先完成 ETL/ELT；Sprint 当前 24/27、IN_PROGRESS，真实 Chrome 95/API/PostgreSQL 尚待验收。
- 2026-07-22：F6 实际含 T01-T07，其中 T07 为 READY；新增 F3-T06 纠正 FACT 输入语义。FACT DRAFT 可不绑定输入，IMPLEMENTATION_READY 接受有效物理 `sourceRefs` 或锁定 revision 的上游 `dependsOn`，两类同时存在时全部校验；目标模型与上游来源分层分开。Sprint 当前 24/29、IN_PROGRESS。
- 2026-07-23：新增 F3-T07 冻结模型类型与分层依赖矩阵。ODS_RAW/ODS_STANDARDIZED/STG 属于接入/技术层，不走四类 ModelSpec；DIMENSION/FACT→DWD、SUMMARY→DWS、APPLICATION→ADS，并按类型限制 `sourceRefs/dependsOn/generationStrategy`。历史 ODS/STG ModelSpec 的专属只读分类和迁移 UI 尚待实现。Sprint 当前 24/30、IN_PROGRESS。
- 2026-07-24：T02 因概念维度、逻辑维度表和实现来源混用而从 DONE 重开；新增 F3-T08/T09/T10 与 F6-T08，采用 `DimensionDefinition → ModelSpecRevision → ModelImplementation → PhysicalAssetRevision` 四层最小闭环，并以现有 API 采集任务生成的 Landing 资产作为统一物理输入。Sprint 当前 23/34、IN_PROGRESS。
- 2026-07-25：F6-T01 因指标 owner 回跳与能力等价缺口从 DONE 重开。默认 `dts-metrics` 继续退役，唯一顶级菜单仍为指标工作台；以治理指标 owner 补齐原子/派生编辑、预检、发布、版本和 ModelSpec 精确版本回写。Sprint 当前 22/34、IN_PROGRESS。

## Sprint-68: DTS 标准内容库与通用基线产品化 (202607)

**目录**: `worklog/v2.2.3/sprint-68-202607-standard-content-library`
**状态**: IN_PROGRESS
**类型**: Content Platform / Data Governance / Safe Upgrade / Offline Delivery
**目标**: 把 Sprint-57 标准包管道升级为可版本化、可追溯、可安全升级的 DTS 标准内容库，使新部署客户可直接安装通用基线并只做少量本地调整；PJM/dbt 资产仅在通过严格准入后进入可选项目管理扩展包。
**依赖**: 复用 Sprint-57 `preview/apply/rollback/runs/builtin` 单一管道、当前计量单位 owner 和 Sprint-50/67 的标准稳定引用边界；PJM 候选来源限定为 `worklog/v2.2.3/s10/v4/pjm/dbt_model` 的受控元数据/SQL，不读取测试数据行。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-标准内容契约与来源治理 | P0 | 4 | IN_PROGRESS |
| F2-客户覆盖层与安全升级 | P0 | 4 | READY |
| F3-PJM候选审计与准入 | P0 | 4 | READY |
| F4-通用基线与内容仓库 | P0 | 5 | READY |
| F5-安装更新与部署体验 | P0 | 4 | READY |
| F6-验证发布与证据闭环 | P0 | 4 | READY |

**统计**: READY=23, IN_PROGRESS=0, DONE=2, BLOCKED=0
**执行顺序**: F1 → F2/F3 → F4 → F5 → F6；F3 只能产出候选和准入报告，F4 只消费来源许可与六道门禁均通过的内容，F6 是最终 Go/No-Go 出口。

**关键决策**:

- 采用“外置版本化内容仓库 + 离线签名包 + DTS 基线/客户扩展/本地改写三层模型”，不继续以 classpath 大 CSV 作为长期升级边界。
- 未完成客户覆盖保护前，不批量推送大规模内置内容；升级冲突必须 fail closed。
- 首批目录目标为 10～12 个包，包含通用码表、SI/常用计量单位、300～500 个通用数据元和 150～300 个参考术语；条目数量不能替代来源与质量证据。
- 通用术语默认 REFERENCE/DRAFT，不伪装成客户正式 ACTIVE 口径；`source_system` 与标准内容来源分离。
- PJM `models.tsv` 当前仍为 DRAFT，关键 dbt tests 多为 warn；只有晋升、阻断测试、血缘、去重、适用性和内容审查全部通过的记录才可进入可选项目管理包。
- PJM `target/logs/test/BI/ZIP/.bak`、ODS/测试数据行、DWS/ADS 指标公式和有损/上下文丢失 alias 映射均不得进入 DTS 通用基线。

## Sprint-69: 模型构建、质量与发布交付工作台重构 (202607)

**目录**: `worklog/v2.2.3/sprint-69-202607-build-quality-release-workbench`
**状态**: IN_PROGRESS
**类型**: Model Delivery / Quality Gate / Release Governance / UI Refactoring
**目标**: 把数据建设第六步从跳转卡和零散台账重构为计划级交付工作台，使选定的 ModelSpec revision 能完成可审计的构建、质量、审核、发布、外部注册与回滚闭环，并以真实发布证据驱动 StageProjection。
**依赖**: 复用 Sprint-67 canonical WarehousePlan/ModelSpec/ModelLifecycle；交付控制面仅消费稳定 `modelSpecId/revision/checksum/implementationMode`，最终 DIMENSION/SCD2 真实 E2E 等待 Sprint-67 四层模型接口稳定。Sprint-68 可并行。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-发布候选与证据真值 | P0 | 4 | IN_PROGRESS |
| F2-构建运行与版本绑定 | P0 | 4 | READY |
| F3-结构化质量门禁 | P0 | 4 | READY |
| F4-审核发布回滚治理 | P0 | 4 | READY |
| F5-交付工作台UI重构 | P0 | 5 | READY |
| F6-真实集成验收与发布 | P0 | 4 | READY |

**统计**: READY=21, IN_PROGRESS=3, DONE=1, BLOCKED=0
**执行顺序**: F1 → F2/F3/F5 壳层 → F4 → F5 完整交互 → F6；F2 与 F3 在候选契约冻结后并行，F5 只读壳层可先行，写动作等待对应 API，F6 是唯一 Go/No-Go 出口。

**关键决策**:

- 新增 ReleaseCandidate 作为本次交付范围和状态控制面，但不复制 WarehousePlan 或 ModelSpec 正文。
- artifact 与真实 dbt run 分开留证；外部 run 必须由服务端校验 selector、target、revision、checksum 和终态。
- QualityRun/QualityCheckResult 保存规则版本、阈值、数据快照和规则级结果；旧 TEST 布尔值不满足新门禁。
- 审核、批准和发布是显式命令，提交人与批准人分离；SQL/dbt 页面不再自动审核发布。
- Catalog、BI、Lineage 注册允许 PARTIAL 和失败步骤幂等重试；回滚追加事件，不删除历史。
- `/modeling/plans/:planId/implementation` 重构为唯一计划级交付工作台，不新增一级菜单。
- StageProjection 第六步只接受当前有效的真实 PUBLISHED 候选，READY、STALE、PARTIAL 和 ROLLED_BACK 均不算完成。
- mock UI 不能关闭 Sprint；最终必须通过真实 Spring Security、PostgreSQL、dbt target 和 Chrome 95。
- 25 个 Task 均增加可判定的 UI 完成标准；后端 Task 验证 UI 契约，F5 验证 UI 实现，F6 验证真实 UI，任一层缺证据均不得标记 DONE。
- 测试采用 Task focused、Feature 组合、Sprint 全量三层节奏；禁止每完成一个小 Task 就重复执行全量测试，F1-F6 全部实现后再统一执行最终门禁。

## Sprint-70: dbt 模型包转换与普通模型导入闭环 (202607)

**目录**: `worklog/v2.2.3/sprint-70-202607-dbt-model-package-import`
**状态**: IN_PROGRESS
**类型**: Model Import / Canonical ModelSpec / UI Journey / Safe Migration
**目标**: 将已有 dbt 项目转换为可预检、可确认、可幂等导入的 DTS 模型包，在当前建设计划下自动创建四类 canonical ModelSpec，并将无法安全降级的 SQL 保留为受治理的 dbt 实现。
**依赖**: 复用 Sprint-60 manifest/SQL 解析与 artifact 导入、Sprint-67 四层 ModelSpec 主线和 Sprint-69 构建发布工作台；不新建第二套 dbt parser、模型台账或发布控制面。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-模型包契约与转换器 | P0 | 6 | IN_PROGRESS |
| F2-导入预检与差异分析 | P0 | 4 | DONE |
| F3-canonical模型应用引擎 | P0 | 4 | IN_PROGRESS |
| F4-建模工作台导入体验 | P0 | 5 | IN_PROGRESS |
| F5-集成验收与交付 | P0 | 4 | READY |

**统计**: READY=8, IN_PROGRESS=7, DONE=8, BLOCKED=0
**执行顺序**: F1 → F2 → F3 → F4 → F5；F4 页面壳层可在 F2 契约冻结后并行，F1-F4 全部实现后再统一执行一次后端组合测试、一次前端 production build 和一次 Chrome 95 真实验收。

**关键决策**:

- 普通和高级模式接收同一个标准 dbt 项目 ZIP；服务端优先读取 artifact，缺失时执行隔离、无数据库写入的解析并生成内部 `dts.model-package/v1`。`models.tsv` 只是可选索引，不能再把完整项目降级成另一种 legacy 产品格式。
- 转换结果分为 `DESIGNER_GENERATED / DBT_BACKED / BLOCKED`；复杂 SQL 仍创建普通 ModelSpec，但实现所有权保持 DBT_MANAGED。
- STG/ephemeral 作为技术节点进入依赖和 artifact 图，不创建 ODS/STG 四类 ModelSpec，也不能在导入时丢失。
- 导入采用 preview/apply 双阶段，preview 零写入；apply 重验 previewHash、来源版本、revision pin、CAS 和幂等键。
- 建模工作台当前计划卡片提供主要入口，模型中心提供共享入口；两者使用同一四步向导，不新增一级菜单。
- 旧 SQL/ZIP 导入和 `/vnext/dbt/import` 保持兼容，但不是 canonical 普通模型导入主线。

## Sprint-71: 数据标签体系与资产打标闭环 (202607)

**目录**: `worklog/v2.2.3/sprint-71-202607-data-tag-governance`
**状态**: READY
**类型**: Data Governance / Catalog Capability / Frontend-first Implementation
**目标**: 把当前的自由文本 `tags` 字段升级为「标签目录 + 预置标签库 + 结构化打标 + 按标签检索」的数据标签管理体系。
**依据**: 闭合协议 2.3.2.4 数据管理模块 P1 缺口「数据标签管理」，证据见 `sprint-36-202606/assets/gap-evidence/M04-数据管理.md` §3（该缺口自 2026-04 识别以来，sprint-32~70 从未触达）。
**依赖**: 复用 `CatalogAssetType` + `CatalogAssetKey` 资产标识、sprint-34 审计目录、sprint-68 标准内容库安装管道；不新建第二套资产标识或内容分发机制。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-标签领域模型与目录 | P1 | 3 | READY |
| F2-打标与检索契约 | P1 | 4 | READY |
| F3-前端标签管理与打标交互 | P1 | 3 | READY |
| F4-存量标签迁移与兼容 | P2 | 2 | READY |

**统计**: READY=12, IN_PROGRESS=0, DONE=0, BLOCKED=0
**执行顺序**: F1 → F2 → F3；F4 依赖 F2 完成后执行，可与 F3 并行。全部 task 采用 TDD（RED→GREEN→REFACTOR）。

**关键决策**:

- 不新增菜单：标签目录管理挂载到既有「数据资产 → 元数据管理」页新增 Tab，遵循 Sprint-48/49「以现有页面为第一事实源」治理规则。
- 打标锚点复用 `CatalogAssetType`（20 类）+ `CatalogAssetKey`，一套关联表覆盖 dataset/dbt_model/metric/data_product 等全部资产类型，不为 dataset 单造关系表。
- 标签与密级严格分离：预置标签不含任何密级语义，界面上数据标签 chip 必须与 `ClassificationTag` 视觉可区分，避免污染合规判定面。
- 预置标签走 Sprint-68 标准内容库管道，以 `code` 为幂等键，升级不覆盖客户改名与停用状态。
- 存量 `CatalogDataset.tags` 字段保留不删、不双写；F4 提供可 dry-run、可按批次回滚的迁移工具，下线仅做评估不执行。
- 多标签检索语义明确为 AND（交集），后端与前端一致，界面须明示「同时包含」。

**已知风险**: F3 的浏览器 smoke（IT-09~IT-12）依赖登录/DNS 验证基线，该基线在 Sprint-61~64 长期阻断导致多个 sprint 停留「实现完成、交付未完成」。实施启动时须先确认基线可用，否则应即时标注 BLOCKED。

## Sprint-72: 数据密级全生命周期与不可降级传播闭环 (202607)

**目录**: `worklog/v2.2.3/sprint-72-202607-classification-lifecycle-governance`
**状态**: IN_PROGRESS
**类型**: P0 Implementation Epic / Data Governance / Security / Full-stack / Cross-service
**目标**: 在数据首次落盘前封存来源密级，沿字段血缘、资产血缘和消费引用自动传播最高密级，保证全链路只能升密不能降密，并把创建、存储、使用、共享、归档、临时销毁和永久销毁纳入可审批、可监控、可审计的生命周期闭环。
**依据**: 用户确认“不能修改”是不能降低密级；大屏密级按其全部展示数据的最高密级确定。复用 Sprint-13 最高密级原则、Sprint-24 大屏密级能力、Sprint-34 审计目录、Sprint-36 敏感识别与生命周期缺口证据、Sprint-38 接入主链和 Sprint-67/69 发布控制面。

| Feature | 优先级 | Task 数 | 估算 | 状态 |
|---------|--------|---------|------|------|
| F1-密级事实模型与只升不降内核 | P0 | 4 | 8～10 人日 | IN_PROGRESS |
| F2-接入定级与首次落盘准入 | P0 | 5 | 11～14 人日 | IN_PROGRESS |
| F3-血缘传播与建模发布门禁 | P0 | 5 | 12～15 人日 | IN_PROGRESS |
| F4-生命周期审批归档与销毁 | P0 | 6 | 15～20 人日 | IN_PROGRESS |
| F5-消费资产与服务密级传播 | P0 | 4 | 8～11 人日 | IN_PROGRESS |
| F6-大屏最高密级自动确定 | P0 | 5 | 10～13 人日 | IN_PROGRESS |
| F7-资产台账生命周期工作台与监控 | P1 | 5 | 10～13 人日 | IN_PROGRESS |
| F8-存量迁移与集成交付 | P0 | 4 | 10～13 人日 | IN_PROGRESS |

**统计**: READY=0, IN_PROGRESS=38, DONE=0, BLOCKED=0；总估算 84～109 人日。
**执行顺序**: F1 → F2 → F3/F4 → F5 → F6 → F7 → F8；F4 可在 F1 完成后并行，但永久销毁必须等待血缘影响分析稳定，F6 必须等待消费资产来源解析契约稳定。

**关键决策**:

- 密级不是普通标签；来源声明封存后不可覆盖，有效密级按 `max(previous, declared, detected, manualFloor, upstreams)` 单调升高。
- 新增不可变密级事实和追加式事件，现有 `classification` 字段保留为兼容投影，不直接扩张 CRITICAL 共享实体的可编辑语义。
- JDBC/API/Excel/CSV 在首次生产落盘前完成 seal；缺密级只允许隔离预检。
- 字段血缘优先精确传播，缺字段血缘时保守取上游资产最高密级；缺失或循环血缘阻断发布。
- 指标、Card、报表、API、数据产品和大屏均取全部来源最高密级；人工配置只能作为更高下限。
- 大屏解析全部页面、组件、下钻和 card/metric/dataset/SQL/API 数据源；Sprint-24“有原因可降密”路径受控退役。
- 生命周期统一视图复用既有审批事实；临时销毁可恢复，永久销毁双人复核并只处理 DTS 管理副本，绝不反向 DROP 外部源表。
- 存量迁移必须 dry-run、分批、幂等、可暂停；任何候选降级均阻断。
- F1～F8 共 38 个 Task 已完成编码；本轮新增生命周期时间轴/销毁证明和资产详情升密/审批入口
  已通过 14/14 定向源码契约、TypeScript 与 Chrome 95 构建。迁移、容器和部署后浏览器验收
  证据齐全前不标记 DONE。

## Sprint-73: 数据集市规划与维度建模产品化 (202607)

**目录**: `worklog/v2.2.3/sprint-73-202607-dimension-and-data-mart-modeling`
**状态**: READY（仅 F0/F1 可拉取；运行时 Feature 等待 G0）
**类型**: Warehouse Planning / Dimension Modeling / Product Convergence / Full-stack
**目标**: 让建模人员能在同一条主线中管理数据集市、登记业务维度、创建并完善维度表，清楚区分业务归属、应用范围、来源、命名、历史处理和数据保留，并在发布后由资产台账接收真实资产。
**依赖**: 复用 Sprint-67 的 WarehousePlan/DimensionDefinition/ModelSpec 主线和 Sprint-69 的发布控制面；DataWorks 仅作产品设计参考，不引入新的模型或资产 owner。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F0-交付与生产数据基线 | P0 | 2 | READY |
| F1-概念关系与契约收敛 | P0 | 2 | READY |
| F2-数据集市规划闭环 | P0 | 3 | DRAFT |
| F3-业务维度目录增强 | P0 | 3 | DRAFT |
| F4-维度表设计体验闭环 | P0 | 3 | DRAFT |
| F5-实现发布与资产交接 | P0 | 2 | DRAFT |

**统计**: READY=4, DRAFT=11, IN_PROGRESS=0, DONE=0, BLOCKED=0
**执行顺序**: F0/F1 → F2 → F3 → F4 → F5；F2 数据持久化与 F3 UI 壳层只可在 G0 通过并完成影响分析后按冻结契约并行。
**关键决策**:

- 数据集市是面向应用/主题/消费场景的规划对象，不是资产台账；复用业务分类页面和计划详情，不新增一级菜单。
- 业务分类继续由 `catalog_domain` 唯一拥有；DataMart 与业务分类多对多，维度和 ModelSpec 只增加稳定引用。
- `DimensionDefinition 1 → N DIMENSION ModelSpec`；同一计划、集市范围和 variant 默认只允许一个活动实现。
- 概念维度和维度表草稿不要求源表；进入实现前必须补齐已确认来源、锁定上游模型或受控生成策略。
- 保持 `DIMENSION→DWD`，不照搬 DataWorks 的独立 DIM 层；物理表名、装载方式、SCD 和数据保留期限分别管理。
- 只有 PUBLISHED 物理实现按统一资产键进入资产台账，草稿和概念定义不得提前登记为可消费资产。

**已知风险**: 当前运行实例健康且相关迁移已执行，但真实登录、认证 API、Chrome95、构建链和客户生产数据画像尚未完成；F0 未通过前 F2～F5 保持 DRAFT。

## Sprint-74: 建模创建旅程与阶段边界纠偏 (202607)

**目录**: `worklog/v2.2.3/sprint-74-202607-modeling-creation-journey-correction`
**状态**: DONE（2026-07-27 实现、部署、验收与影响审计完成）
**类型**: Product Journey / Modeling Contract / UI Convergence / Safe Compatibility
**目标**: 让建模人员先根据业务目的选择正确模型类型，独立完成逻辑设计，再按需选择普通配置或高级 dbt 形成实现，并只在真实发布后查看物理结果；每个阶段只提示当前必须处理的事项。
**依赖**: 复用 Sprint-67 的四层 canonical 对象、Sprint-69 发布控制面、Sprint-72 密级传播和 Sprint-73 数据集市/维度建模；不新建第二套模型、实现、发布或物理资产台账。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F0-评审与可验收基线 | P0 | 2 | DONE |
| F1-先选对模型再保存草稿 | P0 | 3 | DONE |
| F2-独立完成逻辑模型 | P0 | 3 | DONE |
| F3-实现方式与发布结果解耦 | P0 | 3 | DONE |
| F4-存量纠错治理策略与兼容 | P0 | 3 | DONE |
| F5-集成验收与安全交付 | P0 | 2 | DONE |

**统计**: DRAFT=0, READY=0, IN_PROGRESS=0, DONE=16, BLOCKED=0
**执行顺序**: F0 → F1 → F2 → F3 → F4 → F5；F1 类型/目标层边界和 F2 逻辑字段契约冻结后可做有限并行，F5 是唯一 Go/No-Go 出口。

**关键决策**:

- 新建不默认 FACT；先选择“稳定对象/业务事件/聚合结果/消费输出”再确定 DIMENSION/FACT/SUMMARY/APPLICATION。
- 新增 DESIGNED 门禁；逻辑模型不需要来源、物理名、装载、dbt 或发布证据即可独立完成。
- `ModelSpecRevision` 只拥有逻辑语义；物理名、装载、分区、保留归 `ModelImplementation.settings`。
- dbt 是数据实现方式之一，入口从“物理资产”移到“数据实现”；第三阶段对外改为只读“发布结果”。
- 模型类型与目标层分别呈现，但本 Sprint 保持 v2.2.3 经典映射：DIMENSION/FACT→DWD、SUMMARY→DWS、APPLICATION→ADS；不新增 DIM 或 DataWorks 五层策略。
- DataWorks 仅用于校正产品顺序；若未来确需独立 DIM 层，另立 ADR/Sprint，不在创建旅程纠偏中扩大范围。
- 页面默认只显示当前下一道门禁；未来发布要求和可选建议不计入当前待修复数量。
- 仅 DRAFT 且无实现/生命周期/发布候选时允许预检后追加 revision 改型，适用于当前“财务项目模型”候选纠错。

**完成证据**: `it/evidence/acceptance-summary.md`；真实认证 API、PostgreSQL、dbt compile、Chrome95、迁移和影响审计均已通过。IT-08 的完成边界是“未发布时不伪造物理资产”，真实物化闭环由 Sprint-76 承担。

## Sprint-75: 资产地图主题域导航与统计口径纠偏 (202607)

**目录**: `worklog/v2.2.3/sprint-75-202607-asset-map-domain-navigation`
**状态**: DRAFT（设计已确认，待 spec 复审；禁止编码）
**类型**: Navigation IA / Statistics Contract / UI Convergence / Bug Fix
**目标**: 让用户在资产地图左侧一眼看出哪个主题域有资产、哪个域有待处置，修正统计误报并把地图页收敛为纯概览入口。
**依赖**: 复用既有 CatalogDomain、CatalogAssetPortalService、canRead 可见性规则、资产地图和资产台账；不新增菜单或平行统计口径。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F0-评审与可验收基线 | P0 | 3 | DRAFT |
| F1-统计口径与可见性单一事实源 | P0 | 6 | DRAFT |
| F2-带统计的主题域树契约 | P0 | 3 | DRAFT |
| F3-主题域范围导航组件 | P0 | 4 | DRAFT |
| F4-地图页信息架构与控件收敛 | P1 | 4 | DRAFT |
| F5-界面中文化与枚举字典 | P0 | 5 | DRAFT |

**统计**: DRAFT=25, READY=0, IN_PROGRESS=0, DONE=0, BLOCKED=0
**执行顺序**: F0 → F1/F5-T01 → F2 → F3 → F4；F5 字典先于新组件接入。
**关键决策**: 可见性统计复用 `canRead`；主题域树和统计单一接口；`?domain=` 深链；资产地图只做概览，执行动作回资产台账；界面枚举走统一中文字典。
**已知风险**: 真实数据规模、浏览器登录基线和统计性能尚待 G0 复测。

## Sprint-76: 模型真实物化与物理资产闭环 (202607)

**目录**: `worklog/v2.2.3/sprint-76-202607-model-materialization-closure`
**状态**: IN_PROGRESS（架构已冻结，允许 DEV/TEST 实施；PG-01/02/03 与 F6 未关闭前 PROD NO-GO）
**类型**: Model Materialization / dbt Runtime / DAG Workflow / Release Governance / Physical Asset / Full-stack
**目标**: 让普通维度建模和高级 dbt 建模都通过“构建、提交上线”进入同一 ReleaseCandidate/dbt/Airflow 链，真实生成并核验 table/view；经独立审核/发布后原子登记物理资产与 MANUAL_ONLY plan binding。Airflow 是唯一调度真值，手工/CRON 共用 dbt task template，并在 ACTIVE+relation healthy 时显示上线完成。
**依赖**: 复用 Sprint-69 ReleaseCandidate、Sprint-72 密级门禁、Sprint-74 三阶段建模、现有 `modeling_pipeline_run`、DbtReleaseSubmissionService、DbtScopedProjectService 和 CatalogAssetKey；Build/Publish Intent 只能编排 canonical Candidate commands 且不拥有状态，禁止新建平行模型、运行、发布或资产台账。Sprint-36/F3 已 DONE，外部资产动作端口就绪；PROD 发布/CRON 仍等待 Sprint-76 Candidate duty resolver、本地双门接入和 IT-14。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F0-架构冻结与真实验收基线 | P0 | 2 | DONE |
| F1-普通实现可运行dbt制品 | P0 | 3 | DONE |
| F2-候选驱动物化编排与运行真值 | P0 | 4 | IN_PROGRESS |
| F3-真实关系核验与强绑定证据 | P0 | 4 | DONE |
| F4-发布治理与物理资产交接 | P0 | 3 | IN_PROGRESS |
| F5-建模与交付页面产品闭环 | P0 | 3 | DRAFT |
| F7-上线后计划DAG与持续计算 | P0 | 3 | DRAFT |
| F6-真实集成验收与安全交付 | P0 | 3 | DRAFT |

**统计**: DRAFT=12, READY=0, IN_PROGRESS=4, DONE=9, BLOCKED=0
**执行顺序**: F0 → F1 → F2 → F3 → F4 → F7 → F6；F5 在 F2/F3 契约冻结后可并行，最终等待 F4/F7。
**关键决策**:

- compile 不等于物化；必须同时具备 dbt build SUCCESS 与目标库 relation EXISTS。
- ReleaseCandidate.START_BUILD 是唯一构建状态迁移；模型详情和高级页可用同一 Build/Publish Intent 快捷入口，但 facade 不拥有状态。
- 快捷构建只创建/精确复用 SINGLE_MODEL candidate，批量候选冲突严格阻断；active claim 数据库唯一，客户端不能提交技术执行字段。
- Candidate 只允许一个 environment/executionTargetKey；P0 仅开放唯一实测 Postgres target；Airflow dagRunId 由 candidate/version/attempt 确定生成并用于超时对账。
- DRAFT 不占 active claim；失败候选保留 claim 供恢复，显式 CANCEL_CANDIDATE 审计化释放且不 DROP 已有关系；pipeline UNKNOWN 对账前禁止 retry。
- “提交上线”只记录 Publish Intent 并推进 RUN_QUALITY→SUBMIT_REVIEW；reviewer/operator 在 Candidate 工作台独立批准/发布，旧 lifecycle route 只兼容委托。
- mandatory local publication 在 Candidate 维度全有或全无；external sync 失败只降级健康；发布默认创建聚合全部 current PUBLISHED scope 的 MANUAL_ONLY binding。
- 普通 artifact 以 overlay 进入既有 scoped dbt project，不镜像成第二个 SQL 模型 owner。
- 普通实现的 source/ref 形成 dbt dependency graph；RELEASE_BUILD executor DAG 与 OPERATIONAL plan DAG 都 import `services/dts-airflow/extra` 中唯一版本化 Python task factory，Java 只生成 thin DAG，用户不手工连 DAG。
- 扩展 `modeling_pipeline_run` 作为唯一运行真值；RelationObservation 只保存强绑定核验证据。
- build-only relation 不登记为可消费资产；PUBLISHED 时统一写 CatalogDataset、输出 physicalAssetRef 和 lineage。
- Airflow 是 CRON/nextRun/DagRun/TaskInstance 唯一真值；平台只保存 desired binding 和业务 pipeline run，不自建 scheduler。
- 手工运行先落 durable OPERATIONAL_RUN 再触发 Airflow；CRON DagRun 首任务原子 open OPERATIONAL_RUN。
- binding 唯一 `(tenant,plan,environment,executionTargetKey)`，一个 schedule、无 scheduleKey；DAG 原子写入并经 Airflow parse/checksum/schedule 对账后 ACTIVE。
- tracked/shared dbt credential 必须迁移到既有数据源 secrets；平台只在宿主机 tmpfs 签发 task-scoped profile lease，Airflow 用固定 root+leaseId 只读挂载；warehouse secret 不得进入 Git/DAG/conf/XCom/API/DB/env/log/evidence。
- Airflow prepare/open/sync/probe/finalize/release 必须使用 pairwise service token 和 principal/path allowlist，禁止 header-only 与 `|| true` 伪成功。
- Candidate 生产权限采用 domain duty resolver + Sprint-36/F3 asset action policy 双门禁；Sprint-76 不复制权限表。
- PostgreSQL 是本 Sprint P0 真实 adapter；MySQL/达梦未实测时 capability fail-closed。

**已知风险**: 当前四个代表目标关系均不存在，candidate/run 记录为 0，现有 dbt DAG 为 schedule=None；共享 profile、Airflow callback 鉴权/静默失败、重复 DAG runtime 模板均为 PG-01/02 GAP；Sprint-36/F3 已交付 `canPerform`，但 Candidate 发布/计划链尚未消费，PG-03 为 DEPENDENCY_READY / LOCAL_INTEGRATION_PENDING。架构已允许 DEV/TEST 实施，但生产结论仍为 NO-GO；登录、Chrome95 和整包构建只在相关漂移或 F6 最终验收时定点执行。

## Sprint-77: DTS 全局帮助中心与页面说明收敛 (202607)

**目录**: `worklog/v2.2.3/sprint-77-202607-dts-global-help-center`
**状态**: IN_PROGRESS（源代码实施已获授权；真实登录与浏览器验收仍由 F0 阻断，未满足前不得标记 DONE）
**类型**: UI Productization / Global Help / Content Convergence
**目标**: 让首次使用 DTS 的用户从任意登录后页面打开全局帮助，获得与当前页面匹配的任务说明，并在完整帮助中心检索整个 DTS；建模页面不再承载通用产品教程。
**依赖**: 复用现有 Dashboard Header、Sheet、静态路由、Ctrl/Cmd+K 和 `docs/user-guide` 内容资产；不新增业务菜单、后端 API、数据库表或第二套权限体系。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F0-可验收基线 | P0 | 1 | BLOCKED |
| F1-全局帮助入口与主题中心 | P0 | 3 | IN_PROGRESS |
| F2-建模页面说明收敛 | P0 | 2 | IN_PROGRESS |

**统计**: DRAFT=0, READY=0, IN_PROGRESS=2, DONE=3, BLOCKED=1
**执行顺序**: F0 基线缺口登记 → F1/F2 连续编码 → 一次合并构建与契约验证 → 登录恢复后补真实 UI 证据。
**关键决策**: Header 问号而非右下角齿轮；右侧上下文 Sheet + `/settings/help` 完整中心；本地静态主题注册表；通用说明迁移、运行态信息原位保留。
**已知风险**: 默认 E2E 账号当前返回 401，共享 Playwright 会话被占用；本轮按用户要求不反复测试，最终只做一次合并验证，真实 UI DoD 仍需有效登录。

## Sprint-78: P0 安全与稳定性加固（评审缺口闭环） (202607)

**目录**: `worklog/v2.2.3/sprint-78-202607-p0-security-stability-hardening`
**状态**: DONE（F3/F4 已实施并验收，2026-07-31；F1 放弃、F2 暂缓；浏览器证据段 GAP 由 Sprint-77 F0 基线跟踪）
**类型**: Security Hardening / Ops Reliability / Delivery Cleanup
**目标**: 关闭 2026-07-29 评审确认的可立即落地的 P0 风险——Hetu 遗留代理从交付物中彻底移除、PostgreSQL 具备定时备份与已验证恢复。F1 因部分现场仅 PKI 登录、不可本地新建用户而放弃（ADR-78-09）；F2 暂缓待讨论。
**依赖**: 复用 init.sh 的 `PG_DB_*` 库清单口径、`biLinkUrl.ts` 既有 Hetu→`/bi` 重定向；未引入新容器、未动 `services/dts-pg/data`；浏览器验收基线缺口由 Sprint-77 F0 统一跟踪。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-新建用户初始口令安全治理 | P0 | 3 | ABANDONED（2026-07-29 决策，ADR-78-09） |
| F2-TLS私钥出库与部署期注入 | P0 | 3 | READY（暂缓） |
| F3-PostgreSQL定时备份与恢复验证 | P0 | 3 | DONE（IT-04/05 PASS） |
| F4-Hetu遗留代理移除与内置BI收敛 | P0 | 3 | DONE（IT-06/07 PASS，浏览器段 GAP） |

**统计**: READY=1, IN_PROGRESS=0, DONE=2, BLOCKED=0, ABANDONED=1
**执行顺序**: F3、F4 并行实施完成；Feature 内 T01 → T02 → T03 均闭环。
**关键决策**: 备份走宿主机 pg_dump + 14 天保留，不动 data 目录（ADR-78-06）——`bin/dts-backup` 已交付，9 库自动发现、故障注入/保留清理/恢复演练全部实测通过；Hetu 路由层硬删除不留开关（ADR-78-07）——compose 双文件 + file provider 清零，经用户批准受控重建 dts-proxy/dts-platform-webapp 后运行时 hetu 路由=0，旧路径全部回落 SPA；F1 放弃——PKI 现场无本地建用户流程（ADR-78-09）；F2 私钥出库暂缓，Git 历史旧私钥风险在 F2 落地前保持开放。
**已知风险**: Git 历史中的旧 p12 私钥不可召回（F2 暂缓期间风险持续）；备份 crontab 需现场按 `assets/runbook.md` 手动安装（`17 3 * * *`）；浏览器 smoke 证据段受共享登录基线 GAP 约束（`it/baseline.md`）。

## Sprint-79: 智能数据建模工作台收敛 (202607)

**目录**: `worklog/v2.2.3/sprint-79-202607-modeling-workspace-convergence`
**状态**: IN_PROGRESS（认证 UI/API、菜单恢复、关系图与租约并发基线已通过；代表数据、真实物化、Chrome 95 实机与退役门禁仍未解除）
**类型**: Architecture / UI Productization / Controlled Retirement / Full-stack
**目标**: 让用户在一个建模工作台内完成规划、标准、维度、四类逻辑模型、指标、关系查看以及发布/物化交接，不再在多组解释性页面和重复入口之间切换。
**依赖**: 复用 Sprint-67/73/74 的 WarehousePlan、DimensionDefinition、四类 ModelSpec 与阶段门禁，复用 Sprint-67 指标 owner 和 Sprint-69/76 ReleaseCandidate/物化控制面；DataWorks 原型只作为交互参考，不进入产品运行时。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F0-交付基线与退役证据 | P0 | 4 | IN_PROGRESS |
| F1-统一建模工作台壳层 | P0 | 3 | IN_PROGRESS |
| F2-单页模型编辑器 | P0 | 4 | IN_PROGRESS |
| F3-指标工具与关系图 | P0 | 3 | IN_PROGRESS |
| F4-发布物化短流程 | P0 | 3 | IN_PROGRESS |
| F5-旧页面受控退役 | P0 | 3 | IN_PROGRESS |

**统计**: REVIEW 修复任务 DONE=3、IN_PROGRESS=2；Sprint 仍为 IN_PROGRESS，真实物化、Chrome 95 实机与退役观测未闭环。
**执行顺序**: F0 → F1 → F2/F3 → F4 → F5；F2/F3 可在 Shell 契约冻结后并行，F5 必须等待功能等价、客户画像和两版本访问观测。
**关键决策**: `/modeling/workbench` 为推荐主入口；原有规划、维度、模型、指标菜单在两版本观测和客户画像门禁满足前继续可见；原型只提供 UI 规格；canonical owner 全部复用；“贴源表”映射来源注册/逆向候选而非第五类 ModelSpec；单页编辑不绕过三阶段门禁；8 条兼容路由两版本零访问后删，旧表另行审批。
**已知风险**: 认证 API/UI 已在系统 Chrome 150 复验，但 Chrome 95 兼容尚未补；当前本地仅有 6 个 DIMENSION 模型且 Candidate/implementation 为 0；客户环境规模与旧入口使用未知；Sprint-76 PROD 物化仍为 NO-GO。

## Sprint-80: 原型驱动的数据建模前端替换 (202607)

**目录**: `worklog/v2.2.3/sprint-80-202607-prototype-modeling-ui-replacement`
**状态**: READY_FOR_UI_REVIEW（当前 v223 演示环境已完成构建与集中 E2E，尚未部署客户生产）
**类型**: Frontend Architecture / Information Architecture / Controlled UI Retirement
**目标**: 以 `worklog/prototype/dm` 为唯一 UI 规格，用 `/data-modeling/**` 正式页面和独立顶级“数据建模”菜单替换旧建模展示层；未接后台的保存、提交、发布和物化继续失败关闭。
**依赖**: 继承 Sprint-79 已确认的 WarehousePlan、ModelSpec、StageGate、ReleaseCandidate、物化和审计 owner；本 Sprint 只重构前端、路由和菜单，不修改 Java/API/业务表。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F0-原型、旧 UI、共享契约、菜单和路由映射 | P0 | — | DONE |
| F1-页面框架、建模概览、数仓规划、数据标准 | P0 | — | DONE |
| F2-模型工作台、逆向建模、数据指标 | P0 | — | DONE |
| F3-通用工具、关系图和共用弹层 | P0 | — | DONE |
| F4-新菜单、静态路由、动态解析、旧页面退役 | P0 | — | DONE |
| F5-集中构建、源契约、Chrome 95/E2E 和 UI 评审 | P0 | — | DONE |

**统计**: Feature DONE=6；该 Sprint 未按独立 Task 文件拆分，当前交付状态为 READY_FOR_UI_REVIEW。
**执行顺序**: 原型映射 → 页面与共享组件 → 正式路由 → 顶级菜单 → 旧展示层删除 → 一次集中构建/E2E。
**关键决策**: `/data-modeling/**` 使用真实 URL；一级“数据建模”与“数据开发与运维”同级；旧展示组件物理删除；共享 contract/helper 先迁移；未接后台动作失败关闭，不模拟成功。
**已知风险**: 尚未部署客户生产；后台保存、提交、发布、导入、导出和物化需由 Sprint-81 接入唯一 canonical 控制面。

## Sprint-81: 数据建模后台重构与旧运行面物理退役 (202607)

**目录**: `worklog/v2.2.3/sprint-81-202607-modeling-backend-rearchitecture`
**状态**: IMPLEMENTED_SOURCE_VERIFIED（后台编码、物理退役、迁移契约和隔离 PostgreSQL 控制面 E2E 已完成；共享环境部署与客户环境画像/备份门禁待单独执行）
**类型**: Backend Architecture / Modular Monolith / Data Migration / Controlled Retirement
**目标**: 把 `dts-platform` 收敛为一套模块化建模控制面，使 Sprint-80 新前端只经 WarehousePlan→ModelSpec v2/revision→StageGate→Lifecycle→ReleaseCandidate→Materialization→DbtExecutionGateway→Airflow/dbt 完成真实后台旅程，并在同一 Sprint 重接、迁移后物理删除旧运行面。
**依赖**: 复用 `CatalogAssetType/CatalogAssetKey`、`gov_rule/gov_rule_version/gov_rule_binding/gov_quality_run`、公共 `AuditService`、Sprint-69/76 ReleaseCandidate/物化/Airflow/dbt 主链；中央审计历史和历史 Liquibase changelog 永久保留。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F0-基线与退役门禁 | P0 | 3 | SOURCE_COMPLETE / CUSTOMER_GATE |
| F1-模块化控制面边界 | P0 | 3 | COMPLETE |
| F2-唯一建模状态链 | P0 | 3 | COMPLETE |
| F3-跨域证据与耐久消息 | P0 | 4 | COMPLETE |
| F4-dbt执行网关与调度 | P0 | 3 | COMPLETE |
| F5-精确迁移与物理退役 | P0 | 4 | SOURCE_COMPLETE / CUSTOMER_GATE |
| F6-集成验收与发布门禁 | P0 | 3 | CONTROL_PLANE_PASS / DEPLOY_PENDING |

**统计**: COMPLETE=13，SOURCE_COMPLETE/CUSTOMER_GATE=7，CONTROL_PLANE_PASS/DEPLOY_PENDING=3；共 23 个 Task。
**执行顺序**: F0 → F1 → F2/F3 → F4 → F5 → F6；F5 每个批次在同 Sprint 内完成调用方重接、精确迁移/备份、停机复核和物理删除；最终 E2E 只在全部编码结束后集中执行。
**关键决策**: dts-platform 模块化单体；跨域单向 `integration→catalog identity→quality evidence→modeling`；事件 outbox 与 audit outbox 分表，审计耐久投递 dts-admin；立即退役 semantic/old plan/vNext HTTP 面，SQL model/business object/vNext service/old dbt run 先解耦后删；不保留长期 410/tombstone。
**已知风险**: 当前环境 legacy/run/candidate/materialization 数据为 0，但客户环境未知；质量模板 10、运行数据 0；任何客户 DROP 必须经过环境级画像、备份恢复和“零数据或已迁移”停机门禁。

## Sprint-82: 数据资产地图与治理台账工作台重构 (202608)

**目录**: worklog/v2.2.3/sprint-82-202608-data-asset-workbench-rearchitecture
**状态**: DONE（19/19 Task 完成；代码与 mock-API UI E2E 通过，真实联动待部署补证）
**类型**: Product Architecture / Frontend Refactoring / Governance Workflow
**目标**: 将资产地图收敛为概要分布和筛选下钻，将资产台账收敛为具体资产检索、标签和治理的唯一入口；台账每行只保留“治理资产”，通过单资产治理工作台完成任务引导并保持完整资产档案可达。
**依赖**: 复用 CatalogAssetType + CatalogAssetKey、现有 Catalog 查询、标签 CRUD/检索/能力校验/绑定接口、分类分级与生命周期、质量、血缘、权限和公共审计 owner；不新增重复控制面。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-地图与台账职责收敛 | P0 | 4 | DONE |
| F2-单资产治理工作台 | P0 | 5 | DONE |
| F3-标签与资产关联闭环 | P0 | 5 | DONE |
| F4-契约与交付验证 | P0 | 5 | DONE |

**统计**: PLANNED=0, IN_PROGRESS=0, DONE=19, BLOCKED=0；共 19 个 Task。
**执行顺序**: F1 → F2 → F3 → F4；编码期间只做必要 RED/GREEN 定向测试，F1～F3 全部编码完成后再统一执行类型检查、模块构建、Chrome 95 和核心 E2E。
**关键决策**: 不新增菜单和第三个资产页面；地图不承载标签维护和治理写操作；标签迁入台账；旧标签 URL 兼容跳转；行操作收敛为一个治理入口；工作台只做任务导引，完整详情继续由资产档案承载；第一阶段复用现有后端 API。
**已知风险**: 当前运行库没有标签及资产标签样本；资产身份缺失可能导致 404；标签权限只沿用现有 capability；最终不编译或重启容器，部署另行审批。

## Sprint-83: dbt 双向可视化建模与外部项目接入 (202608)

**目录**: `worklog/v2.2.3/sprint-83-202608-dbt-visual-roundtrip-modeling`
**状态**: IN_PROGRESS（S0 工程准入已通过；ModelSpec/dbt 后端主体已实现，用户可操作的 F2～F6 纵向闭环仍未完成；H83-01 + F0/T05 只阻断 S3 物化）
**类型**: Architecture / Product Design / dbt Integration / Full-stack
**目标**: 让建模人员在同一 canonical 模型上下文中完成业务可视化设计、选择或确认 dbt 物化实现并查看依赖与物化结果；SQL/dbt 技术正文仅在同一模型详情的显式高级实现中维护。外部 dbt 项目通过可审计的预检、冲突处理和幂等应用导入为 ModelSpec DRAFT + DBT_MANAGED Implementation Revision，不产生第二套模型、解析、发布或运行控制面。
**依赖**: 复用 Sprint-81 的 WarehousePlan→ModelSpec v2→StageGate→Lifecycle→ReleaseCandidate→Materialization→DbtExecutionGateway 主链；复用现有 dts.model-package/v1、ZIP inspector、preview/apply/retry、CatalogAssetKey、质量证据和公共审计 outbox。
**独立紧急前置**: `H83-01`（`assets/dbt-runtime-hotfix-prerequisite.md`）不计入下表 35 个 Task；它独立修复精确 PostgreSQL dbt runtime 并产生不可变候选/原始 RT-01 证据，F0/T05 是唯一认证登记 owner。两者只阻断 S3 发布物化，不能以客户包缺失为由延期。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F0-架构基线与产品决策冻结 | P0×5（T05 仅 S3） | 5 | IN_PROGRESS（T01～T04 DONE） |
| F1-统一dbt快照与可视化投影 | P0×4 / P1×1 | 5 | DRAFT |
| F2-业务可视化与高级dbt实现分层 | P0×2 / P1×3 | 5 | DRAFT |
| F3-外部dbt包逆向建模产品化 | P0×4 / P1×2 | 6 | DRAFT |
| F4-发布物化与资产证据闭环 | P1×4 | 4 | DRAFT |
| F5-安全审计与可运维收敛 | P0×3 / P1×1 / P2×1 | 5 | DRAFT |
| F6-真实端到端验收与旧入口退役 | P0×1 / P1×3 / P2×1 | 5 | DRAFT |

**统计**: F0/T01～T04 DONE；F0/T05 只阻断 S3；F1～F6 仍按用户可操作能力保持 DRAFT/IN_PROGRESS，不以组件或后台存在冒充完成。
**执行顺序**: Sprint-83a（S0 工程准入 → S1 统一表示 → S2 artifact-rich ZIP）→ Sprint-83b（S3 发布物化 → S4 source-only/漂移/恢复）→ Sprint-83c（S5 物理退役）。F0/T04 只评审当前待拉取切片；S1/S2 不等待 H83-01/F0/T05，S3 必须等待 `G0-RUNTIME=PASS`，P1/P2 不反向阻断 P0。
**关键决策**: D01～D12 均已确认：普通业务可视化隐藏 SQL/dbt 技术正文；DBT_MANAGED 只表示技术实现所有权；高级实现不新增菜单/清单；P0 artifact-rich 外部接入只接 ZIP；重新导入按技术三方比较并保留 ModelSpec 业务语义；PARTIAL 只表示已选合格项启动后的逐项混合结果；Catalog 使用 latestPublishedRef/servingRef 双指针，PUBLISHED 可发现、成功 MATERIALIZED 才切 serving；S3 样例显式加载、默认100/最大500并 fail-closed；dbt 兼容按 inspect/import/materialization 三轴认证；source-only 政策已冻结但实现排入 P1，只有 enforced 完整 name/data_type 字段契约才可导入，动态/macro/package 缺口阻断受影响闭包；Sprint-83 不做 ZIP 导出、Git push 或 ownership conversion。
**已知风险**: 当前 `dts-dbt:1.10.0` 实际 Core 为 `2.0.0-alpha.5`，H83-01 + F0/T05 未完成前 materialization 保持 NOT_CERTIFIED；尚无客户脱敏 dbt 包，因此 CUSTOMER-VALIDATION 保持 GAP 但不阻断通用实现；source parser 尚未投影 schema YAML columns，也未可靠传播自定义 macro 隐藏依赖；现有逆向 UI 为硬编码数据库表原型；后台 import、Catalog 双指针和物理预览主体契约仍待实现；具名残余风险 `R-DBT-LEGACY-DAG` 仍可由维护员触达，`R-DBT-LEGACY-PREVIEW` 仍可绕过 revision/evidence/密级/脱敏控制，旧 preview 必须在 S3 先完成安全遏制，两者再于 caller=0、迁移和回滚证据成立后退役；正式审计、三方漂移、PARTIAL、前向撤销与真实密级/脱敏/大字段性能证据仍有缺口。

## Sprint-84: 数据建模七入口真实能力收敛 (202608)

**目录**: `worklog/v2.2.3/sprint-84-202608-data-modeling-real-capabilities`
**状态**: IN_PROGRESS（一次性页面/API 勘察完成；规划与概览切片开始实施）
**类型**: UI Productization / Contract Wiring / Controlled Capability Retirement
**目标**: 将 Sprint-80 迁移的七个数据建模入口从静态原型收敛为真实数据、真实动作和完整状态；无既有 owner 的工具能力直接删除，不新增平行台账。
**依赖**: Sprint-81 canonical 后台；Sprint-83 ModelSpec/dbt 纵向主链；WarehousePlan、标准、指标、Catalog lineage 与公共审计 owner。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F0-真实性基线与纠偏门禁 | P0 | 2 | DONE |
| F1-规划与建模概览真实化 | P0 | 3 | IN_PROGRESS |
| F2-数据标准真实化 | P0 | 3 | READY |
| F3-数据指标真实化 | P0 | 3 | READY |
| F4-关系图与通用工具收敛 | P1 | 3 | READY |
| F5-集中验证与交付证据 | P0 | 2 | DRAFT |

**执行顺序**: F0 → F1/F2 → Sprint-83 ModelSpec/dbt 主链 → F3 → F4 → Sprint-83 F6 + Sprint-84 F5 一次集中 E2E。
**关键决策**: 页面是产品真值；不保留演示数据回退；无真实 owner 的控件删除；概览与关系图只做既有事实投影；所有写审计由服务端业务动作产生；不新增菜单、页面或平行表。
## Sprint-85: 数据资产体验收敛与功能串联 (202608)

**目录**: `worklog/v2.2.3/sprint-85-202608-data-asset-experience-convergence`
**状态**: DELIVERED（代码/契约/构建/部署证据齐；E2E 按 G4 登记 BLOCKED_E2E_INPUT，runbook 见 F5 feature）
**类型**: UX Productization / Feature Convergence / Contract Unification
**目标**: 资产地图→概览导航收敛；台账/搜索单一事实源+统一 URL 筛选协议；血缘单语义+契约下沉+URL 化；权限申请旅程串联；详情页统一与旧页退役。
**执行顺序**: F0 概览导航 → F1 单一事实源/筛选协议 → F2 血缘收敛 → F3 权限旅程 → F4 详情统一/旧页退役 → F5 集中验证。
**证据**: 提交 32292c7d5 / 42c722c79 / f72570ea9 / 336d7a179 / fda18014d；镜像 digest sha256:2442012bd2526389…（回滚锚点 e1c63c07d97c1bc62…）；node 契约 93/100（7 项基线既有失败）；Vitest 15/15。
**遗留**: E2E 实机一次执行（授权账号）；台账页 800 行契约超限（842 行）与 F5-T04 security 深链断言过期为基线债务，非本 sprint 引入。

