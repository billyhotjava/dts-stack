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

## Sprint-9: GPMC 大屏跳转修复 + 甘特图弹层下钻 (202604)
**状态**: DONE
**类型**: Implementation（实施型）
**完成日期**: 2026-04-09

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-跳转引擎"无目标=不跳"修复 | 5 | DONE |
| F2-编辑器 ScreenJumpPicker UI 修复 | 4 | DONE |
| F3-JSON 实例死链清理 | 1 | DONE |
| F4-父项目汇总甘特组件改造 | 4 | DONE |
| F5-ProjectDetailGanttModal 弹层组件 | 2 | DONE |
| F6-gpmc-execution-gantt JSON 改造 | 1 | DONE |
| F7-文档与索引 | 1 | DONE |

**统计**: READY=0, IN_PROGRESS=0, DONE=18, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-9-202604/README.md`
