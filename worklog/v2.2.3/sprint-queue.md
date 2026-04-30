# Sprint Queue — v2.2.3

## Sprint-1: 架构加固 -- 高可用、安全、可观测性 (202604)
**状态**: READY
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
**状态**: READY
**类型**: Implementation（实施型）

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-分析卡片文件夹管理 | 4 | READY |

**统计**: READY=4, IN_PROGRESS=0, DONE=0, BLOCKED=0

## Sprint-3: IAM 修复 -- displayName 链路 bug 修复 (202604)
**状态**: READY
**类型**: Implementation（实施型，为 v2.3.0 IAM 重构做铺垫）

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-修复displayName链路bug | 4 | READY (T04 DONE) |

**统计**: READY=3, IN_PROGRESS=0, DONE=1, BLOCKED=0

## Sprint-4: 数据质量管控体系重构 (202604)
**状态**: DONE
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
