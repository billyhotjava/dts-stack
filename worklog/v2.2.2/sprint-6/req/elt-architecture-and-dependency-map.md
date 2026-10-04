# ELT Architecture And Dependency Map

## 目标

梳理“数据接入中心 + 数据开发中心”当前的系统链路、模块分工、关键依赖和异常链路，作为后续稳定性重构的参考基线。

## 模块地图

### 1. 数据接入中心

#### 前端

- `source/dts-platform-webapp/src/pages/explore/etl/TransformPage.tsx`
- `source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx`
- `source/dts-platform-webapp/src/pages/explore/etl/TransformDetailPage.tsx`
- `source/dts-platform-webapp/src/pages/explore/etl/TransformExecutionHistoryPage.tsx`
- API：
  - `source/dts-platform-webapp/src/api/ingestion.ts`

#### 后端

- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/web/rest/IngestionTaskResource.java`
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/IngestionTaskService.java`
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/DagPreheatService.java`
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/AirflowClient.java`

#### 外部依赖

- Airflow
- Addax
- 任务运行日志与执行状态同步链

### 2. 数据开发中心

#### 前端

- `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- `source/dts-platform-webapp/src/pages/explore/QueryWorkbenchPage.tsx`
- `source/dts-platform-webapp/src/pages/explore/etl/OrchestrationPage.tsx`
- `source/dts-platform-webapp/src/pages/explore/etl/ScriptStudioPage.tsx`

#### 后端

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/AirflowClient.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtDagService.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtRunResultService.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtOutputRelationService.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/RollbackCascadeService.java`

#### 外部依赖

- Airflow
- dbt
- 逻辑建模运行时目录
- 运行日志与构建结果同步链

## 主链路

### 接入中心主链

```text
页面创建/编辑任务
-> source/dts-platform-webapp/src/api/ingestion.ts
-> IngestionTaskResource.createTask / executeTask / executeTaskAsync / retryExecutionAsync
-> IngestionTaskService.create / update / execute / retryExecution
-> AddaxJobService + AirflowDagService + DagPreheatService
-> AirflowAdapter.triggerIfRequested
-> AirflowClient.triggerDag / getDagRunLookup / listTaskInstances / getTaskLog
-> AirflowExecutionSyncService.syncRunningExecutions / recoverStaleExecutions
-> TransformPage / TransformDetailPage / TransformExecutionHistoryPage 轮询 latest execution 和日志
```

### 开发中心主链

```text
页面触发 compile/test/build
-> SqlModelingPage toolbar actions
-> triggerDbtCompile / triggerDbtTest / triggerDbtRun
-> EtlResource.triggerDbtCompile / triggerDbtTest / triggerDbtRun
-> DbtSourceService / TopicBindingRuntimeService / DbtDagService.ensureDagForSelector
-> AirflowClient.getDag / setDagPaused / triggerDag / listDagRuns
-> dwh_*_dbt_manual DAG -> dbt compile/test/build
-> DbtRunResultService + externalRunLogService + dbt sync status
-> SqlModelingPage waitForBuildResult / release gate / quality gate / execution log
```

## 已验证代码链

### 接入中心

- 路由入口已确认在 `static-routes.tsx`
- 页面当前依赖的核心 API 是：
  - `/api/ingestion/tasks/{id}/execute/async`
  - `/api/ingestion/tasks/{id}/executions/latest`
  - `/api/ingestion/tasks/{id}/executions/{executionId}/retry/async`
  - `/api/ingestion/tasks/{id}/dag/rebuild`
- `TransformDetailPage` 和 `TransformPage` 都采用“先提交异步执行，再轮询 latest execution”的模式
- `TransformExecutionHistoryPage` 只是 `ExecutionHistoryTable` 的容器，没有单独的异常态治理逻辑

### 开发中心

- `SqlModelingPage` 已统一成顶部 `compile / test / build` 三个主执行入口
- 页面触发后依赖：
  - `getDbtSyncStatus()`
  - `triggerDbtCompile()`
  - `triggerDbtTest()`
  - `triggerDbtRun({ operation: "build" })`
- 页面自身有 `waitForBuildResult()` 轮询逻辑，但它依赖后端返回的 DAG 状态和 latestRun 语义稳定
- 后端真实执行边界在 `EtlResource.triggerAirflowDagOrThrow()`，这里同时承接了：
  - DAG 可见性检查
  - DAG 自动 unpause
  - 真正的 Airflow DAG trigger

## 异常链路

### 接入中心

- `DagPreheatService` 只是 best-effort 预热，失败只记日志，调用方完全感知不到
- `IngestionTaskService.executeAsync()` / `retryExecutionAsync()` 吞掉执行异常，只在服务端记日志
- 页面异步提交后只能得到 `submitted`，真实失败要靠后续 latest execution 轮询才能感知
- `AirflowExecutionSyncService` 负责把 Airflow 状态翻译回执行记录，但这条链只覆盖：
  - `running -> success`
  - `running -> failed`
  - stale execution 恢复
- 删除 / 重建 DAG / 重试等边缘入口都依赖同一个 `IngestionTaskService`，但没有成体系的资源层异常路径覆盖
- 当前最小回归面甚至还没进入运行态，先卡在测试编译漂移：
  - `IngestionTaskService` 构造器新增 `DagPreheatService`
  - `IngestionTaskServiceTest`
  - `IngestionTaskExecutionFilterTest`
  - `IngestionTaskFullRefreshExecutionTest`
    都没同步更新

### 开发中心

- `EtlResource.waitForDagRegistration()` 先轮询 `getDag()`，任何非 404 的 Airflow 异常都会被吞成“DAG 尚未注册”
- `EtlResource.triggerAirflowDagOrThrow()` 在错误传播上把 “DAG 不存在 / DAG paused / Airflow 401/500 / trigger 失败” 混在同一条诊断链里
- `RollbackCascadeService.triggerDbtFullRefresh()` 触发失败只记日志，不抛出，调用方无法知道 rebuild 没真正开始
- `DbtQualityGateService` 和 `DbtReleaseGateService` 已引入“未首跑 relation 缺失可降级”语义，但当前测试仍按旧阻断语义断言
- `DbtOutputRelationService.prepareRebuild()` 的行为也和测试预期有偏差，说明“安全重建 relation”的契约没有稳定下来
- `EtlResourceTest` 当前多数错误都是等待 DAG 可见性超时，说明资源层测试和新的 DAG 可见性判断语义没有一起收口

## 高风险依赖点

### 接入中心

- `DagPreheatService`
- `IngestionTaskService`
- `AirflowExecutionSyncService`
- `TransformDetailPage` 中的 execute/retry polling

### 开发中心

- `AirflowClient`
- `EtlResource`
- `DbtDagService`
- `DbtOutputRelationService`
- `RollbackCascadeService`
- `SqlModelingPage.waitForBuildResult`

## 当前已复现证据

### 接入中心

执行：

```bash
cd source/dts-ingestion
mvn -Dtest=IngestionTaskServiceTest,IngestionTaskExecutionFilterTest,AirflowExecutionSyncServiceTest,IngestionTaskResourceTest test
```

结果：

- 构建失败于 `testCompile`
- 直接原因是 `IngestionTaskService` 构造器增加了 `DagPreheatService`
- 三个测试类未同步构造参数

### 开发中心

执行：

```bash
cd source/dts-platform
mvn -Dtest=DbtDagServiceTest,DbtOutputRelationServiceTest,EtlResourceTest,DbtQualityGateServiceTest,DbtReleaseGateServiceTest test
```

结果：

- `DbtDagServiceTest` 通过
- `DbtOutputRelationServiceTest` 有 1 个失败
- `DbtQualityGateServiceTest` 有 1 个失败
- `DbtReleaseGateServiceTest` 有 4 个失败
- `EtlResourceTest` 有 5 个 error，其中 3 个是 15s 超时，2 个是 502 “DAG 尚未注册”

## 后续用途

本文件用于：

- 后续问题归类
- 首批修复范围确定
- 测试基线设计
- 回归门禁范围划定
