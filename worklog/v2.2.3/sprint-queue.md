# Sprint Queue — v2.2.3

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
**状态**: IN_PROGRESS
**类型**: Implementation（实施型）

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-分析卡片文件夹管理 | 4 | READY |

**统计**: READY=4, IN_PROGRESS=0, DONE=0, BLOCKED=0

## Sprint-3: IAM 修复 -- displayName 链路 bug 修复 (202604)
**状态**: IN_PROGRESS
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
**状态**: READY
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

**统计**: READY=30, IN_PROGRESS=0, DONE=0, BLOCKED=0
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

**统计**: READY=30, IN_PROGRESS=0, DONE=0, BLOCKED=0
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
- 独立于 Sprint-36；原 roadmap 数据管理生命周期顺延 Sprint-38、可观测性与高可用顺延 Sprint-39。

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
- **编号顺延**：原 roadmap 的 M04 生命周期 → Sprint-39，M09 告警+M11 高可用 → Sprint-40（本期客户 API 对接为现场优先需求，插队）。

## Sprint-35b: dts-metrics 架构收口与持久化硬化 (202606)
**状态**: IN_PROGRESS
**类型**: Architecture Hardening / Implementation（dts-metrics，跨服务依赖 dts-platform）
**目标**: 闭合 Sprint-35 的 dts-metrics 完成标准，落实架构评审 6 缺陷修复（metrics 侧），把服务从"内存态原型"推到"可水平部署、可验收"。

| Feature | 优先级 | Task 数 | 状态 |
|---------|--------|---------|------|
| F1-领域持久化层 | P0 | 5 | DONE |
| F2-安全链路统一与审计收口 | P0 | 4 | READY |
| F3-发布一致性与跨服务收口 | P0 | 3 | READY |
| F4-韧性与契约对齐 | P1 | 3 | IN_PROGRESS (T01 DONE) |
| F5-领域类型化 | P1 | 2 | READY |
| F6-IT准入与验收证据 | P0 | 3 | READY |

**统计**: READY=4, IN_PROGRESS=1, DONE=1, BLOCKED=0
**进度（2026-06-14）**: F1 持久化（🔴最严重缺陷 #1）+ F4-T01 RestClient 超时 经 Workflow 实现并自验绿（84 单测 + 4 Testcontainers IT，含并发乐观锁 409）；分支 feat/sprint-35b-dts-metrics-hardening 待提交。
**设计文档**: `worklog/v2.2.3/sprint-35b-202606/README.md`
**架构评审底稿**: `worklog/v2.2.3/sprint-35b-202606/assets/architecture-review.md`
**集成测试**: `worklog/v2.2.3/sprint-35b-202606/it/README.md`
**关键决策**:
- 定位为 Sprint-35 硬化续期（参照 31a/31b 对 31），不撞 roadmap 预留的 Sprint-39（M04 生命周期）/40（M09+M11）。
- 🔴 最严重缺陷=事实源零持久化（状态全在 ConcurrentHashMap）；修复序=持久化→安全对等→发布一致性→韧性/契约→类型化→IT。
- 持久化 datasource/Liquibase **mirror 同仓 dts-platform/dts-admin**，dts-metrics 用独立 schema；不自创基础设施。
- 跨服务边界：BI/lineage/audit register 端点本体属 platform 职责，本期只做 metrics 侧编排与调用 + 联调标注。
- F1+F2+F4-T01 经 Workflow 多代理在分支 `feat/sprint-35b-dts-metrics-hardening` 实现，design-first + 末段并行 build/test/review/gitnexus 影响分析。

## v2.3 Backlog: 企业级资产与指标增强

| Item | Owner | 来源 | 状态 |
|------|-------|------|------|
| schema_version / metric_version_pin / breaking change review | Platform Catalog + Metrics Service | Sprint-31A RX / X4 | BACKLOG |
| SCD / conformed dimension / hierarchy 运行时 | Metrics Service | Sprint-32 F3 follow-up | BACKLOG |
| window / time intelligence / cohort / funnel DSL | Metrics Service | Sprint-32 F3 follow-up | BACKLOG |
| cube cache / cost-based routing | Platform Architecture | Sprint-31 F7 follow-up | BACKLOG |
| GraphQL / OData / semantic query API | Platform Architecture | Sprint-31/32 consumption follow-up | BACKLOG |
| differential privacy / k-anonymity | Security Architecture | Sprint-31A X5 follow-up | BACKLOG |
