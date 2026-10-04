# T01: 执行器骨架与SPI接线

**优先级**: P0
**状态**: DONE
**依赖**: F1-T02

## 目标

新建 `ApiIngestionExecutor` 服务：异步执行 `ExecutionPlan`，线程池+并发上限+任务级互斥，把 `SourceConnectorRegistry` 从死代码接入真实调用链。

## 技术设计

- 调用链：内部触发端点（F3-T01）→ `SourceConnectorRegistry.find(sourceType)` → `ApiHttpSourceConnector.buildExecutionPlan(context)` → `ApiIngestionExecutor.execute(plan, executionId)`。
- 异步：复用 `AsyncExecutionConfig` 线程池；任务级互斥锁（同一 task 不并发，对齐旧 DAG `max_active_runs=1` 语义）；执行超时可配（替代写死的 `execution_timeout=15min`）。
- 进度与结果写 `IngestionExecution`（rowsRead/rowsWritten/failureCategory），状态机对齐 JDBC 路径。
- `SourceConnectorContext` 由 task 实体组装（sourceType/sourceConfig/syncMode/streams）。

## 影响范围

- 新增 `service/etl/api/ApiIngestionExecutor.java`
- `service/etl/connector/SourceConnectorRegistry.java`（接线）
- `config/AsyncExecutionConfig.java`（执行池）

## 验证

- [x] 单测：同 task 并发触发第二次被拒/排队
- [x] 单测：超时中断置 FAILED
- [x] gitnexus_impact 评估 IngestionExecution 写路径无回归

## 完成标准

- [x] mock plan 可被执行并正确落 IngestionExecution 状态

## 进展记录

### 2026-06-12

- 新增 `ApiIngestionExecutor` / `ApiIngestionResult` 骨架，执行器内置 task 级互斥锁；默认 runner 先返回 0 行成功，真实 HTTP 拉取/落库在 T02/T05 完成。
- `IngestionTaskService` 的 API 分支已接入 `SourceConnectorRegistry.find` → `SourceConnector.buildExecutionPlan` → `ApiIngestionExecutor.execute`，并写回 `IngestionExecution.status/rowsRead/rowsWritten/endTime`；非 API 任务仍走原 Addax/Airflow 分支。
- `ApiIngestionExecutor` 已消费 `ApiProperties.executionTimeout`，超时后取消执行线程并抛 `API_RUNTIME_TIMEOUT`；服务层捕获后将 `IngestionExecution` 标记为 `failed`，写入 failureCategory/advice。
- 新增单测：
  - `ApiIngestionExecutorTest#execute_shouldRejectConcurrentExecutionForSameTask`
  - `ApiIngestionExecutorTest#execute_shouldTimeoutAndInterruptLongRunningPlan`
  - `IngestionTaskServiceTest#execute_shouldMarkApiExecutionFailedWhenExecutorTimesOut`
  - `IngestionTaskServiceTest#execute_shouldRunApiTaskThroughConnectorRegistryAndApiExecutor`
- 已跑：
  - `./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-ingestion -Dtest=IngestionTaskServiceTest#execute_shouldMarkApiExecutionFailedWhenExecutorTimesOut,ApiIngestionExecutorTest test`
  - `./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-ingestion -Dtest=IngestionTaskServiceTest#execute_shouldRunApiTaskThroughConnectorRegistryAndApiExecutor,ApiIngestionExecutorTest test`
  - `./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-ingestion -Dtest=IngestionTaskServiceTest,IngestionTaskFullRefreshExecutionTest,IngestionTaskExecutionFilterTest,ApiIngestionExecutorTest,ApiHttpSourceConnectorTest,IngestionSourceResolverTest test`
