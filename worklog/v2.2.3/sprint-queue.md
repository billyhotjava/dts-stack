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
