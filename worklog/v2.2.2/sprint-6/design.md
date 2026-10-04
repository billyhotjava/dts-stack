# Sprint-6 设计：ELT 稳定性治理

## 背景

当前“数据接入中心”和“数据开发中心”的主路径已经能够走通，但在异常路径上仍表现出明显脆弱性：

- Airflow / dbt / Addax 等异步执行链存在触发、注册、同步和反馈不一致的问题
- 页面状态、后端状态、执行状态三者容易偏离
- 错误在部分链路中被吞掉、被改写或被泛化成不准确的 message
- 测试体系对 happy path 有一定覆盖，但对异常路径和跨模块联动覆盖不够

这意味着系统“能用”，但还远没达到“稳定可运维、可回归、可持续迭代”的程度。

## 本 Sprint 要解决什么

本 Sprint 要同时交付三层结果：

1. **诊断**
   把 ELT 体系的主链路、异常链路、依赖关系和脆弱点画清楚。
2. **质量基线**
   建立最小可执行的回归门禁，覆盖接入中心和开发中心。
3. **首批修复**
   修掉最影响稳定性和易用性的 P0 / P1 问题。

## 架构分层

### 1. 交互层

- 接入中心页面：
  - `TransformPage`
  - `TransformCreatePage`
  - `TransformDetailPage`
  - `TransformExecutionHistoryPage`
- 开发中心页面：
  - `SqlModelingPage`
  - `QueryWorkbenchPage`
  - `OrchestrationPage`
  - `ScriptStudioPage`

### 2. 编排接入层

- `source/dts-platform-webapp/src/api/ingestion.ts`
- `source/dts-platform-webapp/src/api/platformApi.ts`
- `source/dts-ingestion/src/main/java/.../web/rest`
- `source/dts-platform/src/main/java/.../web/rest`

### 3. 领域服务层

- 接入中心核心：
  - `IngestionTaskService`
  - `AirflowAdapter`
  - `AirflowExecutionSyncService`
  - `DagPreheatService`
- 开发中心核心：
  - `EtlResource`
  - `DbtDagService`
  - `DbtRunResultService`
  - `DbtOutputRelationService`
  - `RollbackCascadeService`

### 4. 执行与外部系统层

- Airflow
- Addax
- dbt
- 运行这些组件的 Docker 容器

### 5. 反馈与可观测层

- 执行状态同步
- 历史记录
- 日志回显
- 页面状态反馈
- 发布/门禁结果展示

## 问题分类

### 1. 契约不一致

- 前端认为“成功触发”
- 后端认为“请求已提交”
- 执行层其实未真正运行

### 2. 异步编排不稳定

- DAG 未注册
- DAG 已注册但 paused
- 触发成功但状态同步滞后
- 重试或重建语义不明确

### 3. 错误传播失真

- 真实错误被吞掉
- 真实错误被改写成别的问题
- 页面只看到泛化的 `Network Error` 或“请稍后重试”

### 4. 可观测性不足

- 页面不能准确表达“运行中 / 成功 / 失败 / 部分成功”
- 日志入口存在但内容不可靠
- 历史记录和真实执行不一致

### 5. 边界条件脆弱

- 重复点击
- 超时
- 中断
- 部分成功
- 依赖组件短暂不可用

### 6. 测试基线缺口

- 单测集中在 happy path
- 异常路径覆盖不足
- 前端关键页面几乎没有对应行为测试
- E2E 现有用例没有覆盖“任务执行链”和“开发中心发布链”

## 测试分层设计

### 单元测试

目标：收住服务语义和错误分类。

### 资源层 / 集成测试

目标：收住 REST 契约、返回码、返回字段和 message 语义。

### E2E 冒烟

目标：覆盖用户真实关键路径。

### 人工异常路径验收

目标：覆盖难以完全 mock 的容器 / 调度 / 时序问题。

## 首批修复优先级

### P0

- 接入中心的 Airflow 触发 / 预热 / 状态同步异常
- 开发中心的 dbt / Airflow 触发链错误传播

### P1

- 执行历史、日志回显、运行结果同步
- 发布门禁 / 重建 / 回滚链的一致性
- 页面状态反馈与真实执行结果一致性

### P2

- 表单边界情况
- 交互易用性细节
- 非阻断型性能优化

## 执行阶段

### 阶段 A：系统诊断

- 梳理接入中心 / 开发中心架构与异常链路
- 形成风险点和依赖图

### 阶段 B：质量基线

- 固定最小回归命令
- 固定最小 E2E 冒烟流
- 固定人工异常路径清单

### 阶段 C：接入中心首批修复

- 优先修异步执行、状态同步、日志回显

### 阶段 D：开发中心首批修复

- 优先修 dbt/Airflow 编排、门禁和回滚链

### 阶段 E：收口验收

- 跑最小回归
- 跑 E2E 冒烟
- 走人工异常路径
- 沉淀未修问题到 backlog

## 非目标

- 不在本 Sprint 内解决所有历史 ELT 问题
- 不做 UI 全面改版
- 不做非 ELT 模块的系统治理
