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
**状态**: DONE
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
| F3-基础数据页面完善 | P1 | 3 | IN_PROGRESS |
| F4-数据资产重构 | P0 | 4 | DONE |
| F5-治理运营三模块重构 | P0 | 4 | IN_PROGRESS |
| F6-指标工作台语义编排编辑器 | P0 | 4 | IN_PROGRESS |

**统计**: READY=5, IN_PROGRESS=5, DONE=15, BLOCKED=0
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

## v2.3 Backlog: 企业级资产与指标增强

| Item | Owner | 来源 | 状态 |
|------|-------|------|------|
| schema_version / metric_version_pin / breaking change review | Platform Catalog + Metrics Service | Sprint-31A RX / X4 | BACKLOG |
| SCD / conformed dimension / hierarchy 运行时 | Metrics Service | Sprint-32 F3 follow-up | BACKLOG |
| window / time intelligence / cohort / funnel DSL | Metrics Service | Sprint-32 F3 follow-up | BACKLOG |
| cube cache / cost-based routing | Platform Architecture | Sprint-31 F7 follow-up | BACKLOG |
| GraphQL / OData / semantic query API | Platform Architecture | Sprint-31/32 consumption follow-up | BACKLOG |
| differential privacy / k-anonymity | Security Architecture | Sprint-31A X5 follow-up | BACKLOG |
